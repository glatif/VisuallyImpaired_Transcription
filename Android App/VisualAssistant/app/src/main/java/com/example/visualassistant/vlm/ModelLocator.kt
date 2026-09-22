package com.example.visualassistant.vlm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads / locates the GPU FastVLM LiteRT-LM package:
 *   litert-community/FastVLM-0.5B → FastVLM-0.5B.litertlm
 *
 * Not the Qualcomm `.qualcomm.sm87xx.litertlm` NPU variants (Pixel 8 / Tensor G3).
 */
class ModelLocator(private val context: Context) {

    data class DownloadProgress(
        val fileName: String,
        val fileIndex: Int,
        val fileCount: Int,
        val fileBytesRead: Long,
        val fileTotalBytes: Long,
        val overallFraction: Float
    )

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .build()

    fun modelDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "models/FastVLM-0.5B-LiteRT").also { it.mkdirs() }
    }

    fun modelFile(): File = File(modelDir(), MODEL_FILENAME)

    fun isModelPresent(): Boolean {
        val f = modelFile()
        return f.exists() && f.length() > MIN_BYTES
    }

    fun needsDownload(): Boolean = !isModelPresent()

    suspend fun downloadModelIfNeeded(): Result<File> = withContext(Dispatchers.IO) {
        val target = modelFile()
        try {
            if (isModelPresent()) {
                return@withContext Result.success(target)
            }
            downloadFile(MODEL_FILENAME, target).getOrElse {
                return@withContext Result.failure(it)
            }
            if (!isModelPresent()) {
                return@withContext Result.failure(
                    IllegalStateException("Downloaded file missing or too small: ${target.length()}")
                )
            }
            Result.success(target)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            _progress.value = null
        }
    }

    private fun downloadFile(name: String, target: File): Result<Unit> {
        val url = "$HF_BASE/$name"
        Log.i(TAG, "Downloading $url")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Download failed for $name: HTTP ${response.code}")
                )
            }
            val body = response.body
                ?: return Result.failure(IllegalStateException("Empty body: $name"))
            val total = body.contentLength()
            val tmp = File(target.parentFile, "$name.part")
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        val fileFrac = if (total > 0) readTotal.toFloat() / total else 0f
                        _progress.value = DownloadProgress(
                            fileName = name,
                            fileIndex = 0,
                            fileCount = 1,
                            fileBytesRead = readTotal,
                            fileTotalBytes = total,
                            overallFraction = fileFrac.coerceIn(0f, 1f)
                        )
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
        return Result.success(Unit)
    }

    companion object {
        private const val TAG = "ModelLocator"
        private const val HF_BASE =
            "https://huggingface.co/litert-community/FastVLM-0.5B/resolve/main"
        /** GPU/CPU package — not Qualcomm NPU `.qualcomm.sm*.litertlm`. */
        const val MODEL_FILENAME = "FastVLM-0.5B.litertlm"
        /** Guard against truncated downloads (~1.16 GB expected). */
        private const val MIN_BYTES = 500L * 1024L * 1024L
    }
}
