package com.example.visualassistant.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Burns caption text onto a frame bitmap and saves via MediaStore into
 * Pictures/FastVLM_Captions/. Call from a background dispatcher; fire-and-forget from the loop.
 */
class CaptionedImageSaver(private val context: Context) {

    suspend fun saveAsync(
        source: Bitmap,
        caption: String,
        sessionId: String,
        frameIndex: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val burned = burnInCaption(source, caption)
            val name = "fastvlm_${sessionId}_${frameIndex}_${System.currentTimeMillis()}.jpg"
            writeToGallery(burned, name)
            if (burned !== source) burned.recycle()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save captioned frame", t)
        }
    }

    private fun burnInCaption(source: Bitmap, caption: String): Bitmap {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val width = mutable.width
        val height = mutable.height

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (height * 0.035f).coerceIn(28f, 64f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val horizontalPadding = (width * 0.04f).toInt()
        val maxTextWidth = width - horizontalPadding * 2
        val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()

        val barPadding = (height * 0.02f)
        val barHeight = layout.height + barPadding * 2
        val barTop = height - barHeight

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, barTop, width.toFloat(), height.toFloat(), barPaint)
        canvas.save()
        canvas.translate(horizontalPadding.toFloat(), barTop + barPadding)
        layout.draw(canvas)
        canvas.restore()
        return mutable
    }

    private fun writeToGallery(bitmap: Bitmap, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/FastVLM_Captions"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert returned null")

        resolver.openOutputStream(uri).use { out: OutputStream? ->
            requireNotNull(out) { "Null OutputStream for $uri" }
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                error("JPEG compress failed")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    companion object {
        private const val TAG = "CaptionedImageSaver"
    }
}
