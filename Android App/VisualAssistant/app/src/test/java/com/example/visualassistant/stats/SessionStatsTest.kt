package com.example.visualassistant.stats

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SessionStatsTest {

    private lateinit var collector: SessionStatsCollector

    @Before
    fun setUp() {
        collector = SessionStatsCollector("session_123")
    }

    @Test
    fun `snapshot calculates correct metrics for empty frames`() {
        collector.markStarted(1000L)
        collector.markEnded(2000L)

        val snapshot = collector.snapshot()

        assertEquals("session_123", snapshot.sessionId)
        assertEquals(0, snapshot.framesProcessed)
        assertEquals(0.0, snapshot.avgPrepMs, 0.001)
        assertEquals(0.0, snapshot.avgInferenceMs, 0.001)
        assertEquals(0.0, snapshot.avgTtftMs, 0.001)
        assertEquals(0.0, snapshot.avgDecodeMs, 0.001)
        assertEquals(0.0, snapshot.avgCombinedMs, 0.001)
        assertEquals(0L, snapshot.minInferenceMs)
        assertEquals(0L, snapshot.maxInferenceMs)
        assertEquals(1000L, snapshot.sessionDurationMs)
    }

    @Test
    fun `snapshot calculates accurate averages min max and combined totals`() {
        collector.markStarted(10000L)

        val frame1 = FrameRecord(
            frameIndex = 0,
            captureTimestampMs = 10100L,
            prepDurationMs = 20L,
            inferenceDurationMs = 2000L,
            ttftMs = 1500L,
            decodeMs = 500L,
            captionLength = 25,
            ttsDurationMs = 1200L,
            memoryPssKb = 102400,
            heapUsedMb = 50L
        )

        val frame2 = FrameRecord(
            frameIndex = 1,
            captureTimestampMs = 13000L,
            prepDurationMs = 10L,
            inferenceDurationMs = 3000L,
            ttftMs = 2100L,
            decodeMs = 900L,
            captionLength = 30,
            ttsDurationMs = 1500L,
            memoryPssKb = 104857,
            heapUsedMb = 55L
        )

        collector.recordFrame(frame1)
        collector.recordFrame(frame2)
        collector.markEnded(18000L)

        val snapshot = collector.snapshot()

        assertEquals("session_123", snapshot.sessionId)
        assertEquals(2, snapshot.framesProcessed)
        assertEquals(15.0, snapshot.avgPrepMs, 0.001) // (20 + 10) / 2
        assertEquals(2500.0, snapshot.avgInferenceMs, 0.001) // (2000 + 3000) / 2
        assertEquals(1800.0, snapshot.avgTtftMs, 0.001) // (1500 + 2100) / 2
        assertEquals(700.0, snapshot.avgDecodeMs, 0.001) // (500 + 900) / 2
        assertEquals(2515.0, snapshot.avgCombinedMs, 0.001) // 15.0 + 2500.0
        assertEquals(2000L, snapshot.minInferenceMs)
        assertEquals(3000L, snapshot.maxInferenceMs)
        assertEquals(8000L, snapshot.sessionDurationMs)
        assertEquals(2, snapshot.frames.size)
    }
}
