package com.example.visualassistant.vlm

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FrameSimilarityFilterTest {

    private lateinit var filter: FrameSimilarityFilter

    @Before
    fun setUp() {
        filter = FrameSimilarityFilter()
    }

    @Test
    fun `identical hashes have Hamming distance of zero`() {
        val distance = filter.calculateDistance(0x123456789ABCDEFL, 0x123456789ABCDEFL)
        assertEquals(0, distance)
    }

    @Test
    fun `completely inverted hashes have Hamming distance of 64`() {
        val h1 = 0x0L
        val h2 = -1L // 0xFFFFFFFFFFFFFFFFL in 64-bit signed Long
        val distance = filter.calculateDistance(h1, h2)
        assertEquals(64, distance)
    }

    @Test
    fun `single bit difference has Hamming distance of 1`() {
        val h1 = 0b0001L
        val h2 = 0b0011L
        val distance = filter.calculateDistance(h1, h2)
        assertEquals(1, distance)
    }

    @Test
    fun `multi bit differences correctly calculated`() {
        val h1 = 0x0FL // 0000 1111
        val h2 = 0xF0L // 1111 0000
        val distance = filter.calculateDistance(h1, h2)
        assertEquals(8, distance)
    }
}
