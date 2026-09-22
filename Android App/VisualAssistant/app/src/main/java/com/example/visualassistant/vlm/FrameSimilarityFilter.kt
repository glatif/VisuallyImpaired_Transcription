package com.example.visualassistant.vlm

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy

/**
 * Filters camera frames based on perceptual similarity using dHash (Difference Hash).
 * Upgraded to scan the WHOLE buffer for 100% coverage and maximum noise resistance.
 */
class FrameSimilarityFilter {

    /**
     * Calculates the Hamming distance between two 64-bit hashes.
     */
    fun calculateDistance(h1: Long, h2: Long): Int {
        return java.lang.Long.bitCount(h1 xor h2)
    }

    /**
     * Generates a 64-bit dHash from a Bitmap.
     */
    fun extractHash(bitmap: Bitmap): Long {
        val width = bitmap.width
        val height = bitmap.height
        val gridW = 9
        val gridH = 8

        val sums = LongArray(gridW * gridH)
        val counts = IntArray(gridW * gridH)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val gridY = (y * gridH) / height
            val rowOffset = y * width
            for (x in 0 until width) {
                val gridX = (x * gridW) / width
                val cellIndex = gridY * gridW + gridX

                val pixel = pixels[rowOffset + x]
                // Convert to grayscale using standard ITU-R 601 Luma coefficients
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                sums[cellIndex] += gray.toLong()
                counts[cellIndex]++
            }
        }

        return computeHashFromSums(sums, counts, gridW, gridH)
    }

    /**
     * Generates a 64-bit dHash by scanning EVERY pixel in the image.
     * Maps pixels to a 9x8 grid and calculates region means.
     */
    fun extractHash(image: ImageProxy): Long {
        val yBuffer = image.planes[0].buffer
        val width = image.width
        val height = image.height
        val rowStride = image.planes[0].rowStride

        val gridW = 9
        val gridH = 8
        
        // We use a LongArray for sums to prevent overflow on very large buffers.
        val sums = LongArray(gridW * gridH)
        val counts = IntArray(gridW * gridH)
        
        // --- Full Buffer Scan ---
        // Iterates through every pixel once, mapping each to its corresponding grid cell.
        for (y in 0 until height) {
            val gridY = (y * gridH) / height
            val rowOffset = y * rowStride
            for (x in 0 until width) {
                val gridX = (x * gridW) / width
                val cellIndex = gridY * gridW + gridX
                
                // Get pixel value (unsigned 8-bit)
                val pixel = yBuffer.get(rowOffset + x).toInt() and 0xFF
                sums[cellIndex] += pixel.toLong()
                counts[cellIndex]++
            }
        }

        return computeHashFromSums(sums, counts, gridW, gridH)
    }

    private fun computeHashFromSums(sums: LongArray, counts: IntArray, gridW: Int, gridH: Int): Long {
        // Calculate final region means
        val intensityGrid = IntArray(gridW * gridH)
        for (i in 0 until (gridW * gridH)) {
            intensityGrid[i] = if (counts[i] > 0) (sums[i] / counts[i]).toInt() else 0
        }

        // Generate hash by comparing adjacent pixels in rows (horizontal gradients)
        var hash = 0L
        var bitIndex = 0
        for (y in 0 until gridH) {
            for (x in 0 until (gridW - 1)) {
                if (intensityGrid[y * gridW + x] > intensityGrid[y * gridW + x + 1]) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        
        // Avoid 0L to distinguish from "unitialized" state.
        return if (hash == 0L) 1L else hash
    }
}
