package com.example.domain.ocr.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 3: Kraken Line Extractor & Dewarping.
 * Crops polygonal text lines or baseline-aligned bounding strips from manuscript image.
 */
object LineExtractor {

    private const val TAG = "LineExtractor"

    /**
     * Extracts individual line bitmap snippets based on normalized bounding polygons
     */
    fun extractLines(
        sourceBitmap: Bitmap,
        lines: List<OcrPipeline.BaselineLine>
    ): List<OcrPipeline.BaselineLine> {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        return lines.map { line ->
            // Ornaments (frame rules, flourishes) carry no script to crop
            // a recognition strip for -- skip the crop work entirely.
            // Illustrations are the opposite case: they ARE the content
            // worth preserving, so their bitmap must still be cropped and
            // kept even though (like ornaments) they never go through
            // text recognition.
            if (line.typology == "Ornament") return@map line

            val croppedBitmap = try {
                cropLinePolygon(sourceBitmap, line.polygon, width, height)
            } catch (e: Exception) {
                Log.w(TAG, "Failed polygon crop for line ${line.orderIndex}, fallback to rect: ${e.message}")
                cropLineRectFallback(sourceBitmap, line.polygon, width, height)
            }

            line.copy(lineBitmap = croppedBitmap)
        }
    }

    private fun cropLinePolygon(
        source: Bitmap,
        polygon: List<Pair<Float, Float>>,
        imgW: Int,
        imgH: Int
    ): Bitmap? {
        if (polygon.size < 3) return null

        val pxPoints = polygon.map { Pair(it.first * imgW, it.second * imgH) }
        val minX = pxPoints.minOf { it.first }.toInt().coerceIn(0, imgW - 1)
        val maxX = pxPoints.maxOf { it.first }.toInt().coerceIn(minX + 1, imgW)
        val minY = pxPoints.minOf { it.second }.toInt().coerceIn(0, imgH - 1)
        val maxY = pxPoints.maxOf { it.second }.toInt().coerceIn(minY + 1, imgH)

        val cropW = maxX - minX
        val cropH = maxY - minY
        if (cropW <= 4 || cropH <= 4) return null

        val cropped = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val path = Path()

        pxPoints.forEachIndexed { index, pt ->
            val localX = pt.first - minX
            val localY = pt.second - minY
            if (index == 0) {
                path.moveTo(localX, localY)
            } else {
                path.lineTo(localX, localY)
            }
        }
        path.close()

        canvas.clipPath(path)
        val srcRect = Rect(minX, minY, maxX, maxY)
        val dstRect = Rect(0, 0, cropW, cropH)
        canvas.drawBitmap(source, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        return cropped
    }

    private fun cropLineRectFallback(
        source: Bitmap,
        polygon: List<Pair<Float, Float>>,
        imgW: Int,
        imgH: Int
    ): Bitmap? {
        if (polygon.isEmpty()) return null
        val minX = (polygon.minOf { it.first } * imgW).toInt().coerceIn(0, imgW - 1)
        val maxX = (polygon.maxOf { it.first } * imgW).toInt().coerceIn(minX + 1, imgW)
        val minY = (polygon.minOf { it.second } * imgH).toInt().coerceIn(0, imgH - 1)
        val maxY = (polygon.maxOf { it.second } * imgH).toInt().coerceIn(minY + 1, imgH)

        val w = maxX - minX
        val h = maxY - minY
        if (w <= 4 || h <= 4) return null

        return Bitmap.createBitmap(source, minX, minY, w, h)
    }
}
