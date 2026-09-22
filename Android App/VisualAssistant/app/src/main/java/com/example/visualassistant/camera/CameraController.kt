package com.example.visualassistant.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.visualassistant.vlm.FrameSimilarityFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Remote Camera Controller: Communicates with Raspberry Pi Zero Flask server over Wi-Fi.
 */
class CameraController {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val hasher = FrameSimilarityFilter()

    private val _latestHash = MutableStateFlow(0L)
    val latestHash: StateFlow<Long> = _latestHash.asStateFlow()

    private val _latestPreview = MutableStateFlow<Bitmap?>(null)
    /**
     * Flow of the most recent preview frames fetched from the Pi.
     */
    val latestPreview: StateFlow<Bitmap?> = _latestPreview.asStateFlow()

    private var pollJob: Job? = null
    private var isPaused = false

    /**
     * Toggles the background polling loop. Use this to freeze the preview
     * during heavy tasks like AI inference to prevent GPU contention.
     */
    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    /**
     * Starts the background polling loop to fetch preview frames from the Pi.
     */
    fun startNetworkStream(scope: CoroutineScope, piHostname: String) {
        stop()
        isPaused = false
        pollJob = scope.launch(Dispatchers.IO) {
            val url = "http://$piHostname:5000/latest_hash_frame"
            val request = Request.Builder().url(url).build()

            while (true) {
                if (isPaused) {
                    delay(500)
                    continue
                }
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null) {
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    // 1. Update Preview UI
                                    _latestPreview.value = bitmap

                                    // 2. Extract Hash for Motion Gate
                                    _latestHash.value = hasher.extractHash(bitmap)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network polling failed: ${e.message}")
                }
                delay(150) // Matches previous hashing frequency
            }
        }
    }

    /**
     * Stops the network stream and clears state.
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        _latestHash.value = 0L
        _latestPreview.value = null
    }

    /**
     * Triggers a high-res capture on the Pi and returns the Bitmap plus its raw byte size.
     */
    suspend fun takePictureWithMetadata(piHostname: String): Pair<Bitmap, Long> {
        val url = "http://$piHostname:5000/capture"
        val request = Request.Builder().url(url).build()

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Capture failed: ${response.code}")
                val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty capture response")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalStateException("Failed to decode capture")
                bitmap to bytes.size.toLong()
            }
        }
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
