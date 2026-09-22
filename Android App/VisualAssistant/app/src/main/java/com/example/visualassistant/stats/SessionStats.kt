package com.example.visualassistant.stats

import android.os.Debug

data class FrameRecord(
    val frameIndex: Int,
    val captureTimestampMs: Long,
    val prepDurationMs: Long,
    val networkDurationMs: Long,
    val inferenceDurationMs: Long,
    val ttftMs: Long,
    val decodeMs: Long,
    val captionLength: Int,
    val ttsDurationMs: Long,
    val memoryPssKb: Int,
    val heapUsedMb: Long,
    val payloadSizeKb: Double
)

data class SessionSnapshot(
    val sessionId: String,
    val framesProcessed: Int,
    val avgPrepMs: Double,
    val avgNetworkMs: Double,
    val avgInferenceMs: Double,
    val avgTtftMs: Double,
    val avgDecodeMs: Double,
    val avgCombinedMs: Double,
    val avgPayloadKb: Double,
    val minInferenceMs: Long,
    val maxInferenceMs: Long,
    val peakPssKb: Int,
    val avgPssKb: Double,
    val peakHeapMb: Long,
    val avgHeapMb: Double,
    val sessionDurationMs: Long,
    val frames: List<FrameRecord>
)

/**
 * In-memory stats accumulator for one captioning session.
 */
class SessionStatsCollector(val sessionId: String) {
    private val frames = mutableListOf<FrameRecord>()
    private val pssSamples = mutableListOf<Int>()
    private val heapSamples = mutableListOf<Long>()
    var startedAtMs: Long = 0L
        private set
    var endedAtMs: Long = 0L
        private set

    fun markStarted(now: Long = System.currentTimeMillis()) {
        startedAtMs = now
        endedAtMs = 0L
        frames.clear()
        pssSamples.clear()
        heapSamples.clear()
    }

    fun markEnded(now: Long = System.currentTimeMillis()) {
        endedAtMs = now
    }

    fun sampleMemory(): Pair<Int, Long> {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val pss = memInfo.totalPss
        val runtime = Runtime.getRuntime()
        val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        pssSamples += pss
        heapSamples += heapMb
        return pss to heapMb
    }

    fun recordFrame(record: FrameRecord) {
        frames += record
    }

    fun snapshot(): SessionSnapshot {
        val prep = frames.map { it.prepDurationMs }
        val network = frames.map { it.networkDurationMs }
        val inference = frames.map { it.inferenceDurationMs }
        val ttft = frames.map { it.ttftMs }
        val decode = frames.map { it.decodeMs }
        val payloads = frames.map { it.payloadSizeKb }
        
        val end = if (endedAtMs > 0) endedAtMs else System.currentTimeMillis()
        val duration = if (startedAtMs > 0) end - startedAtMs else 0L
        
        val prepAvg = if (prep.isEmpty()) 0.0 else prep.average()
        val inferenceAvg = if (inference.isEmpty()) 0.0 else inference.average()
        
        return SessionSnapshot(
            sessionId = sessionId,
            framesProcessed = frames.size,
            avgPrepMs = prepAvg,
            avgNetworkMs = if (network.isEmpty()) 0.0 else network.average(),
            avgInferenceMs = inferenceAvg,
            avgTtftMs = if (ttft.isEmpty()) 0.0 else ttft.average(),
            avgDecodeMs = if (decode.isEmpty()) 0.0 else decode.average(),
            avgCombinedMs = prepAvg + inferenceAvg,
            avgPayloadKb = if (payloads.isEmpty()) 0.0 else payloads.average(),
            minInferenceMs = inference.minOrNull() ?: 0L,
            maxInferenceMs = inference.maxOrNull() ?: 0L,
            peakPssKb = pssSamples.maxOrNull() ?: 0,
            avgPssKb = if (pssSamples.isEmpty()) 0.0 else pssSamples.average(),
            peakHeapMb = heapSamples.maxOrNull() ?: 0L,
            avgHeapMb = if (heapSamples.isEmpty()) 0.0 else heapSamples.average(),
            sessionDurationMs = duration,
            frames = frames.toList()
        )
    }
}
