package com.example.visualassistant.vlm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.visualassistant.BuildConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * LiteRT-LM wrapper for FastVLM-0.5B on GPU (text + vision).
 */
class FastVlmEngine(
    private val appContext: Context
) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedBackend: Backend? = null
    private var loadedVisionBackend: Backend? = null

    val isLoaded: Boolean get() = engine?.isInitialized() == true
    /** Always false — LiteRT path has no mock engine. */
    val isMock: Boolean get() = false

    suspend fun loadModel(modelPath: String): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            releaseLocked()

            val file = File(modelPath)
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(
                    IllegalStateException("Model file not found: $modelPath")
                )
            }

            try {
                Engine.setNativeMinLogSeverity(LogSeverity.INFO)
            } catch (t: Throwable) {
                Log.w(TAG, "setNativeMinLogSeverity failed", t)
            }

            // Attempt to initialize GMS LiteRT for improved Adreno GPU support
            try {
                com.google.android.gms.tflite.java.TfLite.initialize(appContext)
                    .addOnSuccessListener {
                        Log.i(TAG, "GMS LiteRT initialized successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "GMS LiteRT initialization failed: ${e.message}")
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "GMS LiteRT init call skipped or failed", t)
            }

            // Using filesDir instead of cacheDir to ensure the GPU cache is permanent.
            val cacheDir = File(appContext.filesDir, "litertlm_cache").also { it.mkdirs() }
            val config = EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                cacheDir = cacheDir.absolutePath,
            )
            Log.i(
                TAG,
                "Initializing LiteRT-LM Engine " +
                    "version=${BuildConfig.LITERTLM_VERSION} " +
                    "backend=${config.backend} visionBackend=${config.visionBackend} " +
                    "model=${file.name} sizeMb=${file.length() / (1024 * 1024)}"
            )

            try {
                System.loadLibrary("litertlm_jni")
            } catch (t: Throwable) {
                Log.w(TAG, "loadLibrary(litertlm_jni) skipped or redundant", t)
            }

            val eng = Engine(config)
            try {
                eng.initialize()
            } catch (t: Throwable) {
                Log.e(TAG, "Engine.initialize() failed", t)
                try {
                    eng.close()
                } catch (_: Throwable) {
                }
                return@withContext Result.failure(t)
            }

            engine = eng
            loadedBackend = config.backend
            loadedVisionBackend = config.visionBackend
            Log.i(TAG, "Engine initialized successfully (GPU requested)")
            Result.success(
                "Model loaded — FastVLM-0.5B (LiteRT-LM ${BuildConfig.LITERTLM_VERSION}, GPU)"
            )
        }
    }

    /**
     * Performs a dummy inference to warm up the GPU delegate (shader compilation + weight baking).
     */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        val dummy = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        try {
            Log.i(TAG, "Starting GPU engine warm-up...")
            caption(dummy, "warmup")
            Log.i(TAG, "GPU engine warm-up complete")
        } catch (t: Throwable) {
            Log.w(TAG, "Warm-up failed (non-fatal)", t)
        } finally {
            dummy.recycle()
        }
    }

    /**
     * Caption [bitmap] with a short live-captioning prompt.
     */
    suspend fun caption(
        bitmap: Bitmap,
        prompt: String = DEFAULT_PROMPT,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS
    ): CaptionResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val eng = engine
                if (eng == null || !eng.isInitialized()) {
                    Log.e(TAG, "caption called but Engine not loaded")
                    return@withLock CaptionResult(
                        text = "",
                        error = "Engine not loaded",
                        prepMs = 0,
                        ttftMs = 0,
                        decodeMs = 0,
                        totalMs = 0,
                        tokenChunks = 0
                    )
                }

                val prepStart = System.nanoTime()
                val modelBitmap = FastVlmImagePreprocessor.preprocess(bitmap)
                val jpeg = bitmapToJpegBytes(modelBitmap)
                if (modelBitmap !== bitmap) {
                    modelBitmap.recycle()
                }
                val prepMs = nanosToMs(System.nanoTime() - prepStart)

                val modelStart = System.nanoTime()
                var firstTokenNanos = 0L
                var chunks = 0
                val sb = StringBuilder()

                try {
                    eng.createConversation(
                        ConversationConfig(
                            samplerConfig = SamplerConfig(
                                topK = 40,
                                topP = 0.9,
                                temperature = 0.2,
                                seed = 0
                            ),
                            maxOutputToken = maxOutputTokens
                        )
                    ).use { conversation ->
                        val message = Message.user(
                            Contents.of(
                                Content.ImageBytes(jpeg),
                                Content.Text(prompt),
                            )
                        )
                        conversation.sendMessageAsync(message)
                            .catch { e ->
                                Log.e(TAG, "sendMessageAsync stream error", e)
                                throw e
                            }
                            .collect { partial ->
                                if (firstTokenNanos == 0L) {
                                    firstTokenNanos = System.nanoTime()
                                }
                                chunks++
                                sb.append(partial.toString())
                            }
                    }
                } catch (t: Throwable) {
                    val modelMs = nanosToMs(System.nanoTime() - modelStart)
                    Log.e(TAG, "caption failed after ${modelMs}ms", t)
                    return@withLock CaptionResult(
                        text = "",
                        error = t.message ?: t.javaClass.simpleName,
                        prepMs = prepMs,
                        ttftMs = 0,
                        decodeMs = 0,
                        totalMs = modelMs,
                        tokenChunks = chunks
                    )
                }

                val end = System.nanoTime()
                val modelMs = nanosToMs(end - modelStart)
                val ttftMs = if (firstTokenNanos > 0L) {
                    nanosToMs(firstTokenNanos - modelStart)
                } else {
                    modelMs
                }
                val decodeMs = if (firstTokenNanos > 0L) {
                    nanosToMs(end - firstTokenNanos)
                } else {
                    0L
                }

                if (prompt != "warmup") {
                    Log.d(
                        TAG,
                        "Breakdown: prep=${prepMs}ms, encoder=${ttftMs}ms, decoder=${decodeMs}ms, total=${modelMs}ms"
                    )
                }

                val text = sb.toString().trim()
                CaptionResult(
                    text = text,
                    error = if (text.isEmpty()) "Empty model output" else null,
                    prepMs = prepMs,
                    ttftMs = ttftMs,
                    decodeMs = decodeMs,
                    totalMs = modelMs,
                    tokenChunks = chunks
                )
            }
        }

    suspend fun release() = withContext(Dispatchers.IO) {
        mutex.withLock { releaseLocked() }
    }

    private fun releaseLocked() {
        val eng = engine ?: return
        engine = null
        loadedBackend = null
        loadedVisionBackend = null
        try {
            eng.close()
            Log.i(TAG, "Engine closed")
        } catch (t: Throwable) {
            Log.e(TAG, "Engine.close() failed", t)
        }
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
            throw IllegalStateException("JPEG compress failed")
        }
        return out.toByteArray()
    }

    private fun nanosToMs(nanos: Long): Long = nanos / 1_000_000L

    data class CaptionResult(
        val text: String,
        val error: String?,
        val prepMs: Long,
        val ttftMs: Long,
        val decodeMs: Long,
        val totalMs: Long,
        val tokenChunks: Int
    )

    companion object {
        private const val TAG = "FastVlmLiteRt"
        const val DEFAULT_MAX_OUTPUT_TOKENS = 128
        const val DEFAULT_PROMPT = """Describe this image in one short sentence""""
    }
}
