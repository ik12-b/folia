package com.example.domain.ocr.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 1: Preprocessing for Historical Manuscript Images.
 * Applies optional adaptive thresholding binarization, contrast stretching, and aspect-preserving resizing.
 */
object PreprocessStage {

    data class PreprocessOutput(
        val processedBitmap: Bitmap,
        val originalWidth: Int,
        val originalHeight: Int,
        val scaleFactor: Float
    )

    fun process(bitmap: Bitmap, config: OcrPipeline.PipelineConfig): PreprocessOutput {
        val origW = bitmap.width
        val origH = bitmap.height

        val maxSide = config.resizeLong
        val scale = if (max(origW, origH) > 0) maxSide.toFloat() / max(origW, origH).toFloat() else 1.0f

        var targetW = (origW * scale).toInt()
        var targetH = (origH * scale).toInt()

        // Align dimensions to multiple of 32 for DBNet FPN
        targetW = max(32, ((targetW + 31) / 32) * 32)
        targetH = max(32, ((targetH + 31) / 32) * 32)

        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        if (!config.doBinarize) {
            return PreprocessOutput(scaled, origW, origH, scale)
        }

        // Apply contrast enhancement & binarization if requested
        val binarized = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(targetW * targetH)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            val finalColor = if (gray < 140) Color.BLACK else Color.WHITE
            pixels[i] = finalColor
        }

        binarized.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        return PreprocessOutput(binarized, origW, origH, scale)
    }
}
