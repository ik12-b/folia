package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.collection.LruCache
import com.example.data.model.LineSegmentEntity
import kotlin.math.max
import kotlin.math.min

/**
 * Helper to cache and extract high-definition line strip snippets from the manuscript page image.
 * This allows scholars to view the exact original handwriting line directly alongside the transcription.
 */
object ManuscriptSnippetHelper {

    private val fullImageCache = object : LruCache<String, Bitmap>(4) {}
    private val lineSnippetCache = object : LruCache<String, Bitmap>(64) {}

    fun getManuscriptBitmap(context: Context, imageResName: String?, imageUri: String?): Bitmap? {
        val cacheKey = imageUri ?: imageResName ?: "default"
        fullImageCache.get(cacheKey)?.let { return it }

        val loaded = try {
            if (!imageUri.isNullOrEmpty()) {
                val uri = Uri.parse(imageUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val resName = when (imageResName) {
                    "manuscript_p2" -> "manuscript_p2"
                    else -> "manuscript_p1"
                }
                val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                if (resId != 0) {
                    BitmapFactory.decodeResource(context.resources, resId)
                } else null
            }
        } catch (e: Exception) {
            null
        }

        if (loaded != null) {
            fullImageCache.put(cacheKey, loaded)
        }
        return loaded
    }

    /**
     * Extracts a clean, high-resolution horizontal crop snippet of a specific LineSegmentEntity.
     */
    fun cropLineSnippet(
        context: Context,
        line: LineSegmentEntity,
        imageResName: String?,
        imageUri: String?
    ): Bitmap? {
        val snippetKey = "${line.partId}_${line.id}_${line.orderIndex}"
        lineSnippetCache.get(snippetKey)?.let { return it }

        val fullBitmap = getManuscriptBitmap(context, imageResName, imageUri) ?: return null
        val imgW = fullBitmap.width
        val imgH = fullBitmap.height

        // 1. Try bounding polygon (maskPolygonJson)
        val polyPoints = parsePoints(line.maskPolygonJson)
        val baselinePoints = parsePoints(line.baselinePointsJson)

        val rect = if (polyPoints.size >= 3) {
            val minX = (polyPoints.minOf { it.first } * imgW).toInt().coerceIn(0, imgW - 1)
            val maxX = (polyPoints.maxOf { it.first } * imgW).toInt().coerceIn(0, imgW)
            val minY = (polyPoints.minOf { it.second } * imgH).toInt().coerceIn(0, imgH - 1)
            val maxY = (polyPoints.maxOf { it.second } * imgH).toInt().coerceIn(0, imgH)
            val padY = ((maxY - minY) * 0.15f).toInt()
            val padX = ((maxX - minX) * 0.05f).toInt()
            Rect(
                (minX - padX).coerceAtLeast(0),
                (minY - padY).coerceAtLeast(0),
                (maxX + padX).coerceAtMost(imgW),
                (maxY + padY).coerceAtMost(imgH)
            )
        } else if (baselinePoints.isNotEmpty()) {
            val minX = (baselinePoints.minOf { it.first } * imgW).toInt().coerceIn(0, imgW - 1)
            val maxX = (baselinePoints.maxOf { it.first } * imgW).toInt().coerceIn(0, imgW)
            val avgY = (baselinePoints.map { it.second }.average() * imgH).toInt()
            val estimatedLineH = (imgH * 0.035f).toInt().coerceAtLeast(30)
            Rect(
                minX.coerceAtLeast(0),
                (avgY - estimatedLineH).coerceAtLeast(0),
                maxX.coerceAtMost(imgW),
                (avgY + (estimatedLineH * 0.5f).toInt()).coerceAtMost(imgH)
            )
        } else {
            null
        }

        if (rect == null || rect.width() < 10 || rect.height() < 8) return null

        return try {
            val cropped = Bitmap.createBitmap(fullBitmap, rect.left, rect.top, rect.width(), rect.height())
            lineSnippetCache.put(snippetKey, cropped)
            cropped
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePoints(json: String): List<Pair<Float, Float>> {
        if (json.isBlank()) return emptyList()
        return try {
            json.split(";").mapNotNull { pairStr ->
                val parts = pairStr.trim().split(",")
                if (parts.size == 2) {
                    val x = parts[0].toFloatOrNull()
                    val y = parts[1].toFloatOrNull()
                    if (x != null && y != null) Pair(x, y) else null
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
