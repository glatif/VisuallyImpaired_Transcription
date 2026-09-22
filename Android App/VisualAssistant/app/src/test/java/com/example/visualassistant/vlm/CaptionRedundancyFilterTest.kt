package com.example.visualassistant.vlm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptionRedundancyFilterTest {

    private lateinit var filter: CaptionRedundancyFilter

    @Before
    fun setUp() {
        filter = CaptionRedundancyFilter(overlapThreshold = 3)
    }

    @Test
    fun `first caption is not redundant`() {
        val result = filter.isRedundant("A red car parked on the street")
        assertFalse(result)
    }

    @Test
    fun `blank caption is considered redundant`() {
        assertTrue(filter.isRedundant(""))
        assertTrue(filter.isRedundant("   "))
    }

    @Test
    fun `caption with high meaningful word overlap is marked redundant`() {
        // First caption meaningful words: [red, car, parked, street]
        filter.isRedundant("A red car is parked on the street")

        // Second caption meaningful words: [red, car, parked, avenue]
        // Overlap: [red, car, parked] -> count = 3 >= threshold(3)
        val isRedundant = filter.isRedundant("The red car was parked on an avenue")
        assertTrue(isRedundant)
    }

    @Test
    fun `caption with low overlap is not marked redundant`() {
        // First caption meaningful words: [red, car, parked, street]
        filter.isRedundant("A red car is parked on the street")

        // Second caption meaningful words: [blue, bicycle, leaning, wall]
        // Overlap: [] -> count = 0 < threshold(3)
        val isRedundant = filter.isRedundant("A blue bicycle is leaning against the wall")
        assertFalse(isRedundant)
    }

    @Test
    fun `stop words and VLM filler words are ignored during overlap calculation`() {
        // Filler/stop words: "this", "image", "shows", "depicts", "a", "the"
        // Meaningful words 1: [wooden, table, kitchen]
        filter.isRedundant("This image depicts a wooden table in the kitchen")

        // Meaningful words 2: [metal, chair, garden]
        // Overlap: [] (fillers like "image", "shows", "a" are stripped)
        val isRedundant = filter.isRedundant("This photo shows a metal chair in the garden")
        assertFalse(isRedundant)
    }

    @Test
    fun `reset clears previous state and allows subsequent caption`() {
        filter.isRedundant("A red car parked on the street")
        filter.reset()

        // After reset, same/similar caption should not be marked redundant
        val isRedundant = filter.isRedundant("A red car parked on the street")
        assertFalse(isRedundant)
    }
}
