package com.example.visualassistant.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class VoiceInfo(
    val name: String,
    val displayName: String,
    val locale: String
)

data class EngineInfo(
    val label: String,
    val packageName: String
)

/**
 * Wraps [TextToSpeech] and exposes word-range highlight events via [onRangeStart] (API 26+).
 * Supports offline local voice filtering, pitch/rate controls, and engine switching.
 */
class CaptionSpeaker(context: Context) {

    data class RangeEvent(val utteranceId: String, val start: Int, val end: Int)

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var currentEnginePackage: String? = null

    private val _ranges = MutableSharedFlow<RangeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val ranges: SharedFlow<RangeEvent> = _ranges.asSharedFlow()

    private val _done = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val done: SharedFlow<String> = _done.asSharedFlow()

    val isReady: Boolean get() = ready.get()

    suspend fun init(enginePackageName: String? = null): Boolean = suspendCancellableCoroutine { cont ->
        var resumed = false
        if (tts != null) {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (t: Throwable) {
                Log.w(TAG, "Error shutting down previous TTS instance", t)
            }
            tts = null
            ready.set(false)
        }

        currentEnginePackage = enginePackageName
        val listener = TextToSpeech.OnInitListener { status ->
            if (resumed) return@OnInitListener
            resumed = true
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                attachProgressListener()
                ready.set(true)
                cont.resume(true)
            } else {
                ready.set(false)
                cont.resume(false)
            }
        }

        tts = if (!enginePackageName.isNullOrEmpty()) {
            TextToSpeech(appContext, listener, enginePackageName)
        } else {
            TextToSpeech(appContext, listener)
        }
    }

    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { _done.tryEmit(it) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { _done.tryEmit(it) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error utterance=$utteranceId code=$errorCode")
                utteranceId?.let { _done.tryEmit(it) }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != null) {
                    _ranges.tryEmit(RangeEvent(utteranceId, start, end))
                }
            }
        })
    }

    /**
     * Returns only local offline-capable voices for text-to-speech.
     */
    fun getAvailableLocalVoices(): List<VoiceInfo> {
        val engine = tts ?: return emptyList()
        val rawVoices = try {
            engine.voices ?: return emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to retrieve TTS voices", t)
            return emptyList()
        }

        return rawVoices.filter { voice ->
            val isEnglish = voice.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true)
            val isNetworkRequired = voice.isNetworkConnectionRequired ||
                (voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) == true)
            isEnglish && !isNetworkRequired
        }.map { voice ->
            val lang = voice.locale.displayLanguage.ifBlank { voice.locale.language }
            val country = voice.locale.displayCountry
            val locStr = if (country.isNotBlank()) "$lang ($country)" else lang
            
            // Clean up voice name for display
            val cleanName = voice.name
                .removePrefix("en-us-x-")
                .removePrefix("en-gb-x-")
                .replace("-local", "")
                .replace("-network", "")
                .replace("_", " ")

            VoiceInfo(
                name = voice.name,
                displayName = "${cleanName.capitalizeLocale()} [$locStr]",
                locale = locStr
            )
        }.sortedBy { it.displayName }
    }

    /**
     * Returns all installed TTS engines on the Android system.
     */
    fun getInstalledEngines(): List<EngineInfo> {
        val engine = tts ?: return emptyList()
        return try {
            engine.engines.map { info ->
                EngineInfo(
                    label = info.label,
                    packageName = info.name
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to get installed TTS engines", t)
            emptyList()
        }
    }

    /**
     * Applies voice, speech rate, and pitch settings.
     */
    fun applySettings(voiceName: String?, speechRate: Float, pitch: Float) {
        val engine = tts ?: return
        if (!ready.get()) return

        try {
            engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

            if (!voiceName.isNullOrEmpty()) {
                val targetVoice = engine.voices?.find { it.name == voiceName }
                if (targetVoice != null) {
                    engine.voice = targetVoice
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to apply TTS settings", t)
        }
    }

    /**
     * Speaks sample text for voice testing.
     */
    fun previewSpeech(text: String, voiceName: String?, speechRate: Float, pitch: Float) {
        val engine = tts ?: return
        if (!ready.get()) return
        stop()
        applySettings(voiceName, speechRate, pitch)
        val utteranceId = "preview_${UUID.randomUUID().toString().take(6)}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Creates an intent to launch system TTS settings for downloading new offline voice packs.
     */
    fun getInstallTtsIntent(): Intent {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Speak [text] and suspend until the utterance completes (or [stop] is called).
     * Returns the utterance id used for correlating [ranges].
     */
    suspend fun speakAndWait(text: String): String {
        check(ready.get()) { "TTS not ready" }
        val engine = tts ?: error("TTS null")
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            return utteranceId
        }
        try {
            done.first { it == utteranceId }
        } catch (c: kotlinx.coroutines.CancellationException) {
            engine.stop()
            throw c
        }
        return utteranceId
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }

    private fun String.capitalizeLocale(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    companion object {
        private const val TAG = "CaptionSpeaker"
    }
}
