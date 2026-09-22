package com.example.visualassistant.viewmodel

import com.example.visualassistant.tts.EngineInfo
import com.example.visualassistant.tts.VoiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SessionUiStateTest {

    @Test
    fun `default SessionUiState has expected defaults`() {
        val state = SessionUiState()

        assertEquals(AppScreen.HOME, state.screen)
        assertEquals(LoopPhase.IDLE, state.phase)
        assertFalse(state.modelReady)
        assertFalse(state.modelLoading)
        assertFalse(state.isWarmingUp)
        assertEquals("", state.caption)
        assertNull(state.errorMessage)
        assertNull(state.notificationMessage)
        assertEquals(30, state.similarityThreshold)
        assertEquals(4, state.overlapThreshold)
        assertEquals(1.0f, state.speechRate, 0.001f)
        assertEquals(1.0f, state.speechPitch, 0.001f)
        assertEquals(emptyList<VoiceInfo>(), state.availableVoices)
        assertNull(state.selectedVoiceName)
        assertEquals(emptyList<EngineInfo>(), state.availableEngines)
        assertNull(state.selectedEnginePackage)
    }

    @Test
    fun `copy method correctly updates state properties`() {
        val initial = SessionUiState()
        val voice = VoiceInfo("en-us-x-sfg", "English (US) Voice 1", "en_US")
        val engine = EngineInfo("Google Speech Services", "com.google.android.tts")

        val updated = initial.copy(
            screen = AppScreen.CAPTURE,
            phase = LoopPhase.INFERRING,
            modelReady = true,
            caption = "A red car on the road",
            similarityThreshold = 20,
            overlapThreshold = 2,
            speechRate = 1.2f,
            speechPitch = 0.9f,
            availableVoices = listOf(voice),
            selectedVoiceName = voice.name,
            availableEngines = listOf(engine),
            selectedEnginePackage = engine.packageName
        )

        assertEquals(AppScreen.CAPTURE, updated.screen)
        assertEquals(LoopPhase.INFERRING, updated.phase)
        assertEquals("A red car on the road", updated.caption)
        assertEquals(20, updated.similarityThreshold)
        assertEquals(2, updated.overlapThreshold)
        assertEquals(1.2f, updated.speechRate, 0.001f)
        assertEquals(0.9f, updated.speechPitch, 0.001f)
        assertEquals(1, updated.availableVoices.size)
        assertEquals("en-us-x-sfg", updated.selectedVoiceName)
        assertEquals(1, updated.availableEngines.size)
        assertEquals("com.google.android.tts", updated.selectedEnginePackage)
    }
}
