package com.example.visualassistant.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visualassistant.gallery.CaptionedImageSaver
import com.example.visualassistant.stats.FrameRecord
import com.example.visualassistant.stats.SessionSnapshot
import com.example.visualassistant.stats.SessionStatsCollector
import com.example.visualassistant.tts.CaptionSpeaker
import com.example.visualassistant.tts.EngineInfo
import com.example.visualassistant.tts.VoiceInfo
import com.example.visualassistant.camera.NsdHelper
import com.example.visualassistant.vlm.CaptionRedundancyFilter
import com.example.visualassistant.vlm.FastVlmEngine
import com.example.visualassistant.vlm.FastVlmImagePreprocessor
import com.example.visualassistant.vlm.FrameSimilarityFilter
import com.example.visualassistant.vlm.ModelLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class LoopPhase {
    IDLE,
    CAPTURING,
    INFERRING,
    SPEAKING,
    /** TTS finished but next caption not ready yet. */
    WAITING_CAPTION,
    ENDING,
    ENDED
}

enum class AppScreen {
    HOME,
    CAPTURE,
    STATS
}

data class HighlightRange(val start: Int, val end: Int)

data class SessionUiState(
    val screen: AppScreen = AppScreen.HOME,
    val modelStatus: String = "Preparing model…",
    val modelReady: Boolean = false,
    val modelLoading: Boolean = false,
    val downloadProgressText: String? = null,
    val downloadFraction: Float? = null,
    val errorMessage: String? = null,
    val phase: LoopPhase = LoopPhase.IDLE,
    val isWarmingUp: Boolean = false,
    val caption: String = "",
    val notificationMessage: String? = null,
    val highlight: HighlightRange? = null,
    val frameIndex: Int = 0,
    val isMockModel: Boolean = false,
    val stats: SessionSnapshot? = null,
    val similarityThreshold: Int = 30,
    val overlapThreshold: Int = 4,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val availableVoices: List<VoiceInfo> = emptyList(),
    val selectedVoiceName: String? = null,
    val availableEngines: List<EngineInfo> = emptyList(),
    val selectedEnginePackage: String? = null,
    val piHostname: String = "cameraGlasses.local",
    val glassesOnline: Boolean = false,
    val capturedBitmap: Bitmap? = null
)

/**
 * Capture+infer pipeline overlaps TTS playback:
 *   frame N TTS  ||  capture+infer frame N+1
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val modelLocator = ModelLocator(application)
    private val engine = FastVlmEngine(application)
    private val speaker = CaptionSpeaker(application)
    private val gallery = CaptionedImageSaver(application)
    private val frameFilter = FrameSimilarityFilter()
    private val nsdHelper = NsdHelper(application)

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        SessionUiState(
            similarityThreshold = prefs.getInt(KEY_SIMILARITY, 30),
            overlapThreshold = prefs.getInt(KEY_OVERLAP, 4),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f),
            speechPitch = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f),
            selectedVoiceName = prefs.getString(KEY_SELECTED_VOICE, null),
            selectedEnginePackage = prefs.getString(KEY_SELECTED_ENGINE, null),
            piHostname = prefs.getString(KEY_PI_HOSTNAME, "cameraGlasses.local") ?: "cameraGlasses.local"
        )
    )
    val ui: StateFlow<SessionUiState> = _ui.asStateFlow()

    private val captionFilter = CaptionRedundancyFilter(overlapThreshold = _ui.value.overlapThreshold)

    private var loopJob: Job? = null
    private var statsCollector: SessionStatsCollector? = null
    private var currentUtteranceId: String? = null
    private var takePictureWithMetadata: (suspend (String) -> Pair<Bitmap, Long>)? = null
    private var hashFlow: StateFlow<Long>? = null
    private var setCameraPaused: ((Boolean) -> Unit)? = null
    private var unbindCamera: (() -> Unit)? = null
    private var ttsInitialized = false
    private var sessionPendingCamera = false

    /** Reference hash of the last frame sent for inference. */
    private var lastProcessedHash = 0L

    private val isInferenceBusy = AtomicBoolean(false)
    private val inferenceTaskChannel = Channel<InferenceTask>(Channel.CONFLATED)
    private val speechTaskChannel = Channel<SpeechTask>(Channel.BUFFERED)

    private var notificationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var fallbackResolutionJob: Job? = null

    private data class InferenceTask(
        val bitmap: Bitmap,
        val payloadSize: Long,
        val captureStart: Long,
        val networkMs: Long,
        val index: Int
    )

    private data class SpeechTask(
        val text: String,
        val bitmap: Bitmap,
        val captureStart: Long,
        val networkMs: Long,
        val payloadSize: Long,
        val prepMs: Long,
        val result: FastVlmEngine.CaptionResult,
        val index: Int
    )

    init {
        viewModelScope.launch {
            speaker.ranges.collect { event ->
                if (event.utteranceId == currentUtteranceId) {
                    _ui.update {
                        it.copy(highlight = HighlightRange(event.start, event.end))
                    }
                }
            }
        }
        initTts(enginePackage = _ui.value.selectedEnginePackage)
        ensureModelReady()
        startHeartbeat()
        startDiscovery()
    }

    private fun startDiscovery() {
        nsdHelper.onDeviceFound = { ip ->
            if (_ui.value.piHostname != ip) {
                Log.i(TAG, "Discovered camera at $ip, stopping discovery and updating storage.")
                setPiHostname(ip)
                nsdHelper.stopDiscovery()
                fallbackResolutionJob?.cancel()
            }
        }
        nsdHelper.startDiscovery()

        // Fallback: If NSD fails, try to resolve the name directly after 5 seconds
        fallbackResolutionJob?.cancel()
        fallbackResolutionJob = viewModelScope.launch(Dispatchers.IO) {
            delay(5000)
            if (!_ui.value.glassesOnline) {
                val hostname = "cameraGlasses.local"
                Log.i(TAG, "NSD silent. Attempting direct fallback resolution for $hostname...")
                try {
                    val address = java.net.InetAddress.getByName(hostname)
                    val ip = address.hostAddress
                    if (ip != null) {
                        Log.i(TAG, "Fallback RESOLVED $hostname to $ip")
                        withContext(Dispatchers.Main) {
                            setPiHostname(ip)
                            nsdHelper.stopDiscovery()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback resolution failed: ${e.message}")
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            var consecutiveFailures = 0

            while (true) {
                val host = _ui.value.piHostname
                val url = "http://$host:5000/status"
                Log.v(TAG, "[Heartbeat] Pinging $url ...")
                
                val isOnline = try {
                    client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.v(TAG, "[Heartbeat] SUCCESS: Pi is online")
                            consecutiveFailures = 0
                            true
                        } else {
                            Log.w(TAG, "[Heartbeat] FAILED: HTTP ${response.code}")
                            consecutiveFailures++
                            false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[Heartbeat] ERROR connecting to $host: ${e.message}")
                    consecutiveFailures++
                    false
                }

                // Only show offline if we've failed multiple times to avoid flickering on poor Wi-Fi
                if (isOnline || consecutiveFailures >= 2) {
                    _ui.update { it.copy(glassesOnline = isOnline) }
                }
                
                delay(3000)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun initTts(enginePackage: String? = null) {
        viewModelScope.launch {
            val ok = speaker.init(enginePackage)
            ttsInitialized = ok
            if (ok) {
                val localVoices = speaker.getAvailableLocalVoices()
                val engines = speaker.getInstalledEngines()
                
                // Fallback to first available local voice if current selected is invalid
                val currentVoice = _ui.value.selectedVoiceName
                val validVoice = if (localVoices.any { it.name == currentVoice }) {
                    currentVoice
                } else {
                    localVoices.firstOrNull()?.name
                }

                _ui.update {
                    it.copy(
                        availableVoices = localVoices,
                        selectedVoiceName = validVoice,
                        availableEngines = engines
                    )
                }
                speaker.applySettings(
                    voiceName = validVoice,
                    speechRate = _ui.value.speechRate,
                    pitch = _ui.value.speechPitch
                )
            }
        }
    }

    fun refreshTtsVoices() {
        initTts(enginePackage = _ui.value.selectedEnginePackage)
    }

    fun clearError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    private fun showNotification(message: String) {
        notificationJob?.cancel()
        notificationJob = viewModelScope.launch {
            _ui.update { it.copy(notificationMessage = message) }
            delay(2000)
            _ui.update { it.copy(notificationMessage = null) }
        }
    }

    fun ensureModelReady() {
        loadModel(downloadIfMissing = true)
    }

    fun loadModel(downloadIfMissing: Boolean = true) {
        if (_ui.value.modelLoading) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    modelLoading = true,
                    modelReady = false,
                    errorMessage = null,
                    downloadProgressText = null,
                    downloadFraction = null,
                    modelStatus = if (modelLocator.needsDownload() && downloadIfMissing) {
                        "Downloading model weights…"
                    } else {
                        "Loading model…"
                    }
                )
            }
            try {
                if (downloadIfMissing && modelLocator.needsDownload()) {
                    _ui.update {
                        it.copy(modelStatus = "Downloading FastVLM-0.5B.litertlm (~1.1 GB)…")
                    }
                    val progressJob = launch {
                        modelLocator.progress.collect { p ->
                            if (p != null) {
                                val filePct = if (p.fileTotalBytes > 0) {
                                    (100.0 * p.fileBytesRead / p.fileTotalBytes).toInt()
                                } else {
                                    0
                                }
                                _ui.update {
                                    it.copy(
                                        downloadFraction = p.overallFraction,
                                        downloadProgressText =
                                            "${p.fileName}: $filePct% " +
                                                "(${p.fileBytesRead / (1024 * 1024)} / " +
                                                "${(p.fileTotalBytes.coerceAtLeast(0) / (1024 * 1024))} MB)",
                                        modelStatus = "Downloading ${p.fileName}…"
                                    )
                                }
                            }
                        }
                    }
                    val dl = modelLocator.downloadModelIfNeeded()
                    progressJob.cancel()
                    dl.getOrThrow()
                    _ui.update {
                        it.copy(
                            downloadFraction = 1f,
                            downloadProgressText = "Download complete — initializing GPU engine…",
                            modelStatus = "Loading model…"
                        )
                    }
                }

                val result = engine.loadModel(modelLocator.modelFile().absolutePath)
                result.fold(
                    onSuccess = { status ->
                        // Step 2: GPU Warm-up phase
                        _ui.update {
                            it.copy(
                                isWarmingUp = true,
                                modelStatus = "Optimizing GPU engine…"
                            )
                        }
                        
                        try {
                            engine.warmUp()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Warm-up failed, continuing anyway", t)
                        }

                        _ui.update {
                            it.copy(
                                modelLoading = false,
                                modelReady = true,
                                isWarmingUp = false,
                                modelStatus = status,
                                isMockModel = false,
                                downloadProgressText = null,
                                downloadFraction = null
                            )
                        }
                    },
                    onFailure = { err ->
                        _ui.update {
                            it.copy(
                                modelLoading = false,
                                modelReady = false,
                                modelStatus = "Load failed",
                                errorMessage = err.message,
                                downloadProgressText = null,
                                downloadFraction = null
                            )
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "loadModel failed", t)
                _ui.update {
                    it.copy(
                        modelLoading = false,
                        modelReady = false,
                        modelStatus = "Download / load failed",
                        errorMessage = t.message ?: "Unknown error",
                        downloadProgressText = null,
                        downloadFraction = null
                    )
                }
            }
        }
    }

    fun registerCamera(
        takePicture: suspend (String) -> Pair<Bitmap, Long>,
        hashFlow: StateFlow<Long>? = null,
        setPaused: (Boolean) -> Unit,
        unbind: () -> Unit
    ) {
        this.takePictureWithMetadata = takePicture
        this.hashFlow = hashFlow
        this.setCameraPaused = setPaused
        this.unbindCamera = unbind
        if (sessionPendingCamera && loopJob?.isActive != true) {
            sessionPendingCamera = false
            beginLoop()
        }
    }

    fun startSession() {
        if (!_ui.value.modelReady) {
            _ui.update { it.copy(errorMessage = "Load the model first") }
            return
        }
        if (loopJob?.isActive == true) return

        stopHeartbeat() // Stop polling while in session

        viewModelScope.launch {
            if (!ttsInitialized) {
                ttsInitialized = speaker.init(_ui.value.selectedEnginePackage)
                if (!ttsInitialized) {
                    _ui.update { it.copy(errorMessage = "TextToSpeech init failed") }
                    return@launch
                }
            }

            speaker.applySettings(
                voiceName = _ui.value.selectedVoiceName,
                speechRate = _ui.value.speechRate,
                pitch = _ui.value.speechPitch
            )

            _ui.update {
                it.copy(
                    screen = AppScreen.CAPTURE,
                    phase = LoopPhase.IDLE,
                    caption = "",
                    highlight = null,
                    frameIndex = 0,
                    stats = null,
                    errorMessage = null
                )
            }

            sessionPendingCamera = true
            if (takePictureWithMetadata != null) {
                sessionPendingCamera = false
                beginLoop()
            }
        }
    }

    private fun beginLoop() {
        if (loopJob?.isActive == true) return
        val sessionId = UUID.randomUUID().toString().take(8)
        val collector = SessionStatsCollector(sessionId).also {
            it.markStarted()
            statsCollector = it
        }

        // Reset state for the new session.
        lastProcessedHash = 0L
        captionFilter.reset()
        isInferenceBusy.set(false)

        loopJob = viewModelScope.launch(Dispatchers.Default) {
            // Launch the three overlapping stages of the pipeline
            launch { startWatcherLoop(collector) }
            launch { startInferenceWorker(collector) }
            launch { startSpeechWorker(collector) }
        }
    }

    private suspend fun startWatcherLoop(collector: SessionStatsCollector) {
        var index = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()

                // 1. Motion Gate: Wait for a different scene before taking a high-res shot.
                val flow = hashFlow
                if (flow != null && lastProcessedHash != 0L) {
                    var sceneChanged = false
                    while (!sceneChanged) {
                        currentCoroutineContext().ensureActive()
                        
                        // If inference is busy, we wait.
                        if (isInferenceBusy.get()) {
                            delay(150)
                            continue
                        }

                        val currentHash = flow.value
                        if (currentHash != 0L) {
                            val dist = frameFilter.calculateDistance(currentHash, lastProcessedHash)
                            if (dist >= _ui.value.similarityThreshold) {
                                Log.d(TAG, "Watcher: Motion detected (dist=$dist).")
                                sceneChanged = true
                            }
                        }
                        if (!sceneChanged) delay(150)
                    }
                }

                _ui.update {
                    if (it.phase == LoopPhase.IDLE || it.phase == LoopPhase.WAITING_CAPTION) {
                        it.copy(phase = LoopPhase.CAPTURING)
                    } else {
                        it
                    }
                }

                val captureStart = System.currentTimeMillis()
                val captureResult = try {
                    (takePictureWithMetadata ?: throw IllegalStateException("Camera lost"))(_ui.value.piHostname)
                } catch (e: Exception) {
                    Log.e(TAG, "Watcher: Capture failed: ${e.message}")
                    showNotification("Network lag - retrying...")
                    delay(2000)
                    continue
                }

                val (fullResBitmap, payloadSize) = captureResult
                val networkMs = System.currentTimeMillis() - captureStart
                
                // Update reference hash AFTER successful capture.
                lastProcessedHash = flow?.value ?: 0L

                inferenceTaskChannel.send(
                    InferenceTask(fullResBitmap, payloadSize, captureStart, networkMs, index++)
                )
                
                _ui.update {
                    if (it.phase == LoopPhase.CAPTURING) {
                        it.copy(phase = LoopPhase.WAITING_CAPTION)
                    } else {
                        it
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Watcher loop cancelled")
        }
    }

    private suspend fun startInferenceWorker(collector: SessionStatsCollector) {
        for (task in inferenceTaskChannel) {
            isInferenceBusy.set(true)
            try {
                val fullResBitmap = task.bitmap
                
                // Efficiency Check: Bypass scaling if already 1024x1024
                val prepStart = System.currentTimeMillis()
                val proxyBitmap = if (fullResBitmap.width == 1024 && fullResBitmap.height == 1024) {
                    fullResBitmap
                } else {
                    val p = FastVlmImagePreprocessor.preprocess(fullResBitmap)
                    fullResBitmap.recycle()
                    p
                }
                val prepMs = System.currentTimeMillis() - prepStart

                _ui.update {
                    it.copy(
                        phase = LoopPhase.INFERRING,
                        capturedBitmap = proxyBitmap
                    )
                }

                // INFERENCE SHIELDING: Pause background tasks
                setCameraPaused?.invoke(true)
                val result = try {
                    kotlinx.coroutines.withTimeout(20000) {
                        engine.caption(proxyBitmap)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.e(TAG, "Inference: caption failed", t)
                    FastVlmEngine.CaptionResult(
                        text = "",
                        error = t.message ?: "Timeout",
                        prepMs = 0, ttftMs = 0, decodeMs = 0, totalMs = 0, tokenChunks = 0
                    )
                } finally {
                    setCameraPaused?.invoke(false)
                }

                val captionText = result.text.ifBlank {
                    "Caption failed: ${result.error ?: "empty output"}"
                }

                if (captionFilter.isRedundant(captionText)) {
                    Log.i(TAG, "Inference: Dropping redundant caption")
                    _ui.update { it.copy(capturedBitmap = null, phase = LoopPhase.WAITING_CAPTION) }
                    continue
                }

                speechTaskChannel.send(
                    SpeechTask(
                        captionText, proxyBitmap, task.captureStart, task.networkMs, task.payloadSize, prepMs, result, task.index
                    )
                )

                // STABILITY: Small breather to prevent GPU hammering on Snapdragon 4 Gen 2
                delay(500)
            } finally {
                isInferenceBusy.set(false)
            }
        }
    }

    private suspend fun startSpeechWorker(collector: SessionStatsCollector) {
        for (task in speechTaskChannel) {
            val captionText = task.text
            val proxyBitmap = task.bitmap

            _ui.update {
                it.copy(
                    phase = LoopPhase.SPEAKING,
                    caption = captionText,
                    highlight = null,
                    frameIndex = task.index + 1
                )
            }

            val sessionId = collector.sessionId
            val bitmapCopy = proxyBitmap.copy(proxyBitmap.config ?: Bitmap.Config.ARGB_8888, false)
            viewModelScope.launch {
                gallery.saveAsync(bitmapCopy, captionText, sessionId, task.index)
                bitmapCopy.recycle()
            }

            // Speak via TTS
            val ttsStart = System.currentTimeMillis()
            currentUtteranceId = null
            try {
                val utteranceId = speaker.speakAndWait(captionText)
                currentUtteranceId = utteranceId
            } catch (c: CancellationException) {
                speaker.stop()
                throw c
            }
            val ttsMs = System.currentTimeMillis() - ttsStart

            val (pss, heap) = collector.sampleMemory()
            collector.recordFrame(
                FrameRecord(
                    frameIndex = task.index,
                    captureTimestampMs = task.captureStart,
                    prepDurationMs = task.prepMs,
                    networkDurationMs = task.networkMs,
                    inferenceDurationMs = task.result.totalMs,
                    ttftMs = task.result.ttftMs,
                    decodeMs = task.result.decodeMs,
                    captionLength = captionText.length,
                    ttsDurationMs = ttsMs,
                    memoryPssKb = pss,
                    heapUsedMb = heap,
                    payloadSizeKb = task.payloadSize / 1024.0
                )
            )

            _ui.update {
                it.copy(
                    capturedBitmap = null,
                    highlight = null,
                    phase = LoopPhase.WAITING_CAPTION
                )
            }
        }
    }

    fun endSession() {
        val screen = _ui.value.screen
        val phase = _ui.value.phase
        if (screen != AppScreen.CAPTURE) return
        if (phase == LoopPhase.ENDING || phase == LoopPhase.ENDED) return

        viewModelScope.launch {
            _ui.update { it.copy(phase = LoopPhase.ENDING) }
            sessionPendingCamera = false
            loopJob?.cancel()
            loopJob?.join()
            loopJob = null

            speaker.stop()
            unbindCamera?.invoke()
            unbindCamera = null
            takePictureWithMetadata = null

            engine.release()

            val collector = statsCollector
            collector?.markEnded()
            val snap = collector?.snapshot()

            _ui.update {
                it.copy(
                    phase = LoopPhase.ENDED,
                    screen = AppScreen.STATS,
                    modelReady = false,
                    modelStatus = "Model unloaded — load again to start",
                    isMockModel = false,
                    highlight = null,
                    stats = snap
                )
            }
            statsCollector = null
        }
    }

    fun backToHome() {
        _ui.update {
            it.copy(
                screen = AppScreen.HOME,
                modelStatus = "Preparing model…",
                modelReady = false
            )
        }
        ensureModelReady()
        startHeartbeat() // Resume heartbeat when returning home
    }

    fun setSimilarityThreshold(value: Int) {
        _ui.update { it.copy(similarityThreshold = value) }
        prefs.edit().putInt(KEY_SIMILARITY, value).apply()
    }

    fun setOverlapThreshold(value: Int) {
        _ui.update { it.copy(overlapThreshold = value) }
        captionFilter.overlapThreshold = value
        prefs.edit().putInt(KEY_OVERLAP, value).apply()
    }

    fun setPiHostname(hostname: String) {
        _ui.update { it.copy(piHostname = hostname) }
        prefs.edit().putString(KEY_PI_HOSTNAME, hostname).apply()
    }

    fun setSpeechRate(rate: Float) {
        val cleanRate = (rate * 10).toInt() / 10f
        _ui.update { it.copy(speechRate = cleanRate) }
        prefs.edit().putFloat(KEY_SPEECH_RATE, cleanRate).apply()
        speaker.applySettings(
            voiceName = _ui.value.selectedVoiceName,
            speechRate = cleanRate,
            pitch = _ui.value.speechPitch
        )
    }

    fun setSpeechPitch(pitch: Float) {
        val cleanPitch = (pitch * 10).toInt() / 10f
        _ui.update { it.copy(speechPitch = cleanPitch) }
        prefs.edit().putFloat(KEY_SPEECH_PITCH, cleanPitch).apply()
        speaker.applySettings(
            voiceName = _ui.value.selectedVoiceName,
            speechRate = _ui.value.speechRate,
            pitch = cleanPitch
        )
    }

    fun setSelectedVoice(voiceName: String) {
        _ui.update { it.copy(selectedVoiceName = voiceName) }
        prefs.edit().putString(KEY_SELECTED_VOICE, voiceName).apply()
        speaker.applySettings(
            voiceName = voiceName,
            speechRate = _ui.value.speechRate,
            pitch = _ui.value.speechPitch
        )
    }

    fun setSelectedEngine(packageName: String) {
        _ui.update { it.copy(selectedEnginePackage = packageName) }
        prefs.edit().putString(KEY_SELECTED_ENGINE, packageName).apply()
        initTts(enginePackage = packageName)
    }

    fun previewVoice() {
        speaker.previewSpeech(
            text = "Visual Assistant active. Position obstacle in front.",
            voiceName = _ui.value.selectedVoiceName,
            speechRate = _ui.value.speechRate,
            pitch = _ui.value.speechPitch
        )
    }

    fun getInstallTtsIntent(): Intent {
        return speaker.getInstallTtsIntent()
    }

    override fun onCleared() {
        nsdHelper.stopDiscovery()
        stopHeartbeat()
        loopJob?.cancel()
        speaker.stop()
        speaker.shutdown()
        viewModelScope.launch {
            engine.release()
        }
        super.onCleared()
    }

    companion object {
        private const val TAG = "SessionViewModel"
        private const val PREFS_NAME = "demo_settings"
        private const val KEY_SIMILARITY = "similarity_threshold"
        private const val KEY_OVERLAP = "overlap_threshold"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_SELECTED_ENGINE = "selected_engine"
        private const val KEY_PI_HOSTNAME = "pi_hostname"
    }
}
