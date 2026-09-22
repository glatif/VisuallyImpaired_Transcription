package com.example.visualassistant.vlm

import org.junit.Assert.assertEquals
import org.junit.Test

class FastVlmImagePreprocessorTest {

    @Test
    fun `target size matches FastVLM requirement of 1024`() {
        assertEquals(1024, FastVlmImagePreprocessor.TARGET_SIZE)
    }
}
