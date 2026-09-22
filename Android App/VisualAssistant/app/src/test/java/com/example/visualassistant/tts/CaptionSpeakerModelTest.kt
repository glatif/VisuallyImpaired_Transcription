package com.example.visualassistant.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionSpeakerModelTest {

    @Test
    fun `VoiceInfo data class holds correct values`() {
        val voice = VoiceInfo(
            name = "en-us-x-sfg-local",
            displayName = "Sfg [English (United States)]",
            locale = "English (United States)"
        )

        assertEquals("en-us-x-sfg-local", voice.name)
        assertEquals("Sfg [English (United States)]", voice.displayName)
        assertEquals("English (United States)", voice.locale)
    }

    @Test
    fun `EngineInfo data class holds correct values`() {
        val engine = EngineInfo(
            label = "Google Speech Services",
            packageName = "com.google.android.tts"
        )

        assertEquals("Google Speech Services", engine.label)
        assertEquals("com.google.android.tts", engine.packageName)
    }

    @Test
    fun `RangeEvent data class holds correct values`() {
        val rangeEvent = CaptionSpeaker.RangeEvent(
            utteranceId = "utt_1234",
            start = 0,
            end = 12
        )

        assertEquals("utt_1234", rangeEvent.utteranceId)
        assertEquals(0, rangeEvent.start)
        assertEquals(12, rangeEvent.end)
    }
}
