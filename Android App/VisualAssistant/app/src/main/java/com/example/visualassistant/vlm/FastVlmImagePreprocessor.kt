package com.example.visualassistant.vlm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max

/**
 * FastVLM-0.5B input prep (matches Apple `FastVLMImageProcessor` / LiteRT
 * `FastVlmDataProcessorConfig` defaults):
 * 1. Pad to square (shorter edge centered on black)
 * 2. Scale to [TARGET]×[TARGET] (model tensor is 1024×1024)
 *
 * Sending camera full-res (e.g. 3072×4080) only adds JPEG decode + resize work
 * inside LiteRT; encoder cost is unchanged at 1024².
 */
object FastVlmImagePreprocessor {
    const val TARGET_SIZE = 1024

    fun preprocess(source: Bitmap): Bitmap {
        if (source.width == TARGET_SIZE && source.height == TARGET_SIZE) {
            return source
        }
        val squared = expandToSquare(source)
        val scaled = Bitmap.createScaledBitmap(squared, TARGET_SIZE, TARGET_SIZE, true)
        if (squared !== source) {
            squared.recycle()
        }
        return scaled
    }

    private fun expandToSquare(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w == h) return src

        val size = max(w, h)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val left = (size - w) / 2
        val top = (size - h) / 2
        canvas.drawBitmap(src, left.toFloat(), top.toFloat(), null)
        return out
    }
}
