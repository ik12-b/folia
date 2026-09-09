package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockRegionEntity
import com.example.data.model.LineSegmentEntity
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ScholarBlue
import com.example.ui.viewmodel.CanvasToolMode
import kotlin.math.max
import kotlin.math.min

/**
 * Cached line geometry representation with parsed bounding box, baseline and typology
 */
data class CachedManuscriptLine(
    val id: Long,
    val orderIndex: Int,
    val typology: String,
    val confidence: Float,
    val baseline: List<Offset>,
    val boundingBox: List<Offset>,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val avgY: Float
)

data class CachedManuscriptBlock(
    val id: Long,
    val typology: String,
    val label: String,
    val colorHex: String,
    val polygon: List<Offset>
)

/**
 * Custom Canvas Overlay Component
 *
 * Renders high-resolution manuscript/PDF pages with vector overlays retrieved from the Room database:
 * - PP-OCRv5 DBNet Bounding Boxes (`maskPolygonJson`) with typology colors
 * - PP-OCRv5 Baselines (`baselinePointsJson`)
 * - Scholarly Block Regions (`polygonPointsJson`)
 * - Line numbering badges, confidence tags, and glowing selection indicators
 */
@Composable
fun ManuscriptOverlayCanvas(
    imageResName: String?,
    imageUri: String? = null,
    blocks: List<BlockRegionEntity>,
    lines: List<LineSegmentEntity>,
    selectedLineId: Long?,
    canvasToolMode: CanvasToolMode = CanvasToolMode.SELECT_LINE,
    showBoundingBoxes: Boolean = true,
    showBaselines: Boolean = true,
    showLineBadges: Boolean = true,
    showBlockRegions: Boolean = true,
    showConfidenceTags: Boolean = true,
    scale: Float = 1.0f,
    onScaleChange: (Float) -> Unit = {},
    onLineSelected: (Long) -> Unit = {},
    onPolygonCompleted: (String) -> Unit = {},
    onBaselineCompleted: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentScale by rememberUpdatedState(scale)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "manuscript_scale_anim"
    )

    // Load Bitmap safely (cached per image source)
    val bitmap = remember(imageResName, imageUri) {
        loadBitmapSafely(context, imageResName, imageUri)
    }

    // Cache Block Entities from Room
    val cachedBlocks = remember(blocks) {
        blocks.map { b ->
            CachedManuscriptBlock(
                id = b.id,
                typology = b.typology,
                label = b.label,
                colorHex = b.colorHex,
                polygon = parsePointList(b.polygonPointsJson)
            )
        }
    }

    // Cache Line Entities from Room (PP-OCRv5 DBNet Bounding Boxes & Baselines)
    val cachedLines = remember(lines) {
        lines.mapIndexed { index, line ->
            var baselinePts = parsePointList(line.baselinePointsJson)
            var boxPts = parsePointList(line.maskPolygonJson)

            // Reciprocal fallbacks ensuring both bounding box and baseline always exist
            if (boxPts.isEmpty() && baselinePts.isNotEmpty()) {
                val minX = baselinePts.minOf { it.x }
                val maxX = baselinePts.maxOf { it.x }
                val avgY = baselinePts.map { it.y }.average().toFloat()
                boxPts = listOf(
                    Offset(minX, (avgY - 0.026f).coerceAtLeast(0.01f)),
                    Offset(maxX, (avgY - 0.026f).coerceAtLeast(0.01f)),
                    Offset(maxX, (avgY + 0.018f).coerceAtMost(0.99f)),
                    Offset(minX, (avgY + 0.018f).coerceAtMost(0.99f))
                )
            } else if (baselinePts.isEmpty() && boxPts.isNotEmpty()) {
                val minX = boxPts.minOf { it.x }
                val maxX = boxPts.maxOf { it.x }
                val maxY = boxPts.maxOf { it.y }
                val midX = (minX + maxX) / 2f
                val baselineY = maxY - 0.005f
                baselinePts = listOf(
                    Offset(minX, baselineY),
                    Offset(midX, baselineY),
                    Offset(maxX, baselineY)
                )
            }

            val allX = (boxPts.map { it.x } + baselinePts.map { it.x })
            val allY = (boxPts.map { it.y } + baselinePts.map { it.y })
            val minX = allX.minOrNull() ?: 0.2f
            val maxX = allX.maxOrNull() ?: 0.8f
            val minY = allY.minOrNull() ?: 0.1f
            val maxY = allY.maxOrNull() ?: 0.9f
            val avgY = if (baselinePts.isNotEmpty()) baselinePts.map { it.y }.average().toFloat() else (minY + maxY) / 2f

            CachedManuscriptLine(
                id = line.id,
                orderIndex = if (line.orderIndex > 0) line.orderIndex else index + 1,
                typology = line.typology,
                confidence = line.confidence,
                baseline = baselinePts,
                boundingBox = boxPts,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                avgY = avgY
            )
        }
    }

    // Interactive Drawing Points for manual segmentation
    val currentDrawPoints = remember { mutableStateListOf<Offset>() }

    // Native Paints for Badges and Labels
    val badgeTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val tagTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("manuscript_overlay_canvas_container")
            .pointerInput(Unit) {
                // Non-blocking 2-finger pinch detector: allows single-finger vertical scroll/pager gestures to pass through
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.size >= 2) {
                            val p0 = activePointers[0].position
                            val p1 = activePointers[1].position
                            val prev0 = activePointers[0].previousPosition
                            val prev1 = activePointers[1].previousPosition
                            val currentDist = (p0 - p1).getDistance()
                            val prevDist = (prev0 - prev1).getDistance()
                            if (prevDist > 10f && currentDist > 10f) {
                                val zoomFactor = currentDist / prevDist
                                val newScale = (currentScale * zoomFactor).coerceIn(0.8f, 5.0f)
                                currentOnScaleChange(newScale)
                            }
                        }
                    } while (activePointers.isNotEmpty())
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Maintain the exact aspect ratio of the manuscript bitmap to prevent coordinate misalignment
        val imageAspect = if (bitmap != null && bitmap.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else {
            1200f / 1600f // Default 3:4 manuscript aspect ratio
        }

        val containerAspect = if (containerHeight.value > 0) containerWidth.value / containerHeight.value else 0.75f

        val (fittedWidth, fittedHeight) = if (containerAspect > imageAspect) {
            val h = containerHeight * 0.96f
            val w = h * imageAspect
            Pair(w, h)
        } else {
            val w = containerWidth * 0.96f
            val h = w / imageAspect
            Pair(w, h)
        }

        val finalWidth = (fittedWidth * animatedScale).coerceAtLeast(80.dp)
        val finalHeight = (fittedHeight * animatedScale).coerceAtLeast(80.dp)

        Box(
            modifier = Modifier
                .size(finalWidth, finalHeight)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF8F4EB))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(canvasToolMode, cachedLines, cachedBlocks) {
                        val inputWidth = this.size.width.toFloat()
                        val inputHeight = this.size.height.toFloat()
                        detectTapGestures(
                            onTap = { tapOffset ->
                                val normX = if (inputWidth > 0f) (tapOffset.x / inputWidth).coerceIn(0f, 1f) else 0f
                                val normY = if (inputHeight > 0f) (tapOffset.y / inputHeight).coerceIn(0f, 1f) else 0f

                                when (canvasToolMode) {
                                    CanvasToolMode.SELECT_LINE, CanvasToolMode.VIEW_PAN -> {
                                        // Find line whose bounding box contains tap or closest baseline
                                        val clickedLine = cachedLines.firstOrNull { line ->
                                            normX in (line.minX - 0.05f)..(line.maxX + 0.05f) &&
                                                    normY in (line.minY - 0.02f)..(line.maxY + 0.02f)
                                        } ?: cachedLines.minByOrNull { line ->
                                            kotlin.math.abs(normY - line.avgY)
                                        }

                                        if (clickedLine != null) {
                                            onLineSelected(clickedLine.id)
                                        }
                                    }
                                    CanvasToolMode.DRAW_BASELINE -> {
                                        currentDrawPoints.add(Offset(normX, normY))
                                        if (currentDrawPoints.size >= 2) {
                                            val str = currentDrawPoints.joinToString(";") { "%.3f,%.3f".format(it.x, it.y) }
                                            onBaselineCompleted(str)
                                            currentDrawPoints.clear()
                                        }
                                    }
                                    CanvasToolMode.DRAW_REGION_POLYGON -> {
                                        currentDrawPoints.add(Offset(normX, normY))
                                        if (currentDrawPoints.size >= 4) {
                                            val str = currentDrawPoints.joinToString(";") { "%.3f,%.3f".format(it.x, it.y) }
                                            onPolygonCompleted(str)
                                            currentDrawPoints.clear()
                                        }
                                    }
                                }
                            },
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    onScaleChange(1.0f)
                                } else {
                                    onScaleChange(2.0f)
                                }
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. Draw High-Res Manuscript Image Base
                if (bitmap != null) {
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                    )
                } else {
                    // Realistic Parchment Texture Base
                    drawRect(color = Color(0xFFF6EBD9))
                    drawRect(
                        color = Color(0xFFE2D0B6).copy(alpha = 0.3f),
                        topLeft = Offset(canvasWidth * 0.05f, canvasHeight * 0.04f),
                        size = Size(canvasWidth * 0.90f, canvasHeight * 0.92f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // 2. Draw Block Regions from Room Database
                if (showBlockRegions) {
                    cachedBlocks.forEach { block ->
                        val polygon = block.polygon
                        if (polygon.isNotEmpty()) {
                            val path = Path().apply {
                                moveTo(polygon[0].x * canvasWidth, polygon[0].y * canvasHeight)
                                for (i in 1 until polygon.size) {
                                    lineTo(polygon[i].x * canvasWidth, polygon[i].y * canvasHeight)
                                }
                                close()
                            }
                            val blockColor = parseHexColor(block.colorHex)
                            drawPath(
                                path = path,
                                color = blockColor.copy(alpha = 0.06f),
                                style = Fill
                            )
                            drawPath(
                                path = path,
                                color = blockColor.copy(alpha = 0.45f),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            )
                        }
                    }
                }

                // 3. Draw PP-OCRv5 DBNet Bounding Boxes & Baselines from Room Database
                cachedLines.forEach { line ->
                    val isSelected = line.id == selectedLineId
                    val typoColor = getTypologyColor(line.typology)

                    // Draw Bounding Box Polygon
                    if (showBoundingBoxes && line.boundingBox.isNotEmpty()) {
                        val boxPath = Path().apply {
                            moveTo(line.boundingBox[0].x * canvasWidth, line.boundingBox[0].y * canvasHeight)
                            for (i in 1 until line.boundingBox.size) {
                                lineTo(line.boundingBox[i].x * canvasWidth, line.boundingBox[i].y * canvasHeight)
                            }
                            close()
                        }

                        // Fill translucent (ultra-soft unselected, radiant gold when selected)
                        drawPath(
                            path = boxPath,
                            color = if (isSelected) GoldPrimary.copy(alpha = 0.22f) else typoColor.copy(alpha = 0.05f),
                            style = Fill
                        )

                        // Border stroke (clean 1.1dp to eliminate heavy window-grid effect)
                        drawPath(
                            path = boxPath,
                            color = if (isSelected) GoldPrimary else typoColor.copy(alpha = 0.45f),
                            style = Stroke(width = if (isSelected) 2.4.dp.toPx() else 1.1.dp.toPx())
                        )

                        // Corner vertex handles (only on selected for clutter-free reading)
                        if (isSelected) {
                            line.boundingBox.forEach { pt ->
                                drawCircle(
                                    color = if (isSelected) GoldPrimary else typoColor,
                                    radius = if (isSelected) 3.5.dp.toPx() else 2.2.dp.toPx(),
                                    center = Offset(pt.x * canvasWidth, pt.y * canvasHeight)
                                )
                            }
                        }
                    }

                    // Draw Baseline
                    if (showBaselines && line.baseline.isNotEmpty()) {
                        val strokeColor = if (isSelected) GoldPrimary else typoColor.copy(alpha = 0.85f)
                        val strokeWidth = if (isSelected) 3.0.dp.toPx() else 1.6.dp.toPx()

                        if (line.baseline.size == 2) {
                            drawLine(
                                color = strokeColor,
                                start = Offset(line.baseline[0].x * canvasWidth, line.baseline[0].y * canvasHeight),
                                end = Offset(line.baseline[1].x * canvasWidth, line.baseline[1].y * canvasHeight),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        } else {
                            val path = Path().apply {
                                moveTo(line.baseline[0].x * canvasWidth, line.baseline[0].y * canvasHeight)
                                for (i in 1 until line.baseline.size) {
                                    lineTo(line.baseline[i].x * canvasWidth, line.baseline[i].y * canvasHeight)
                                }
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Baseline point dots (only when selected)
                        if (isSelected) {
                            line.baseline.forEach { bPt ->
                                drawCircle(
                                    color = if (isSelected) GoldPrimary else typoColor,
                                    radius = if (isSelected) 3.0.dp.toPx() else 2.0.dp.toPx(),
                                    center = Offset(bPt.x * canvasWidth, bPt.y * canvasHeight)
                                )
                            }
                        }
                    }

                    // Line Index Badge (Scholarly gutter badge - does not overlap right-margin text)
                    if (showLineBadges && line.baseline.isNotEmpty()) {
                        val isMarginRight = line.minX > 0.65f
                        val isMarginLeft = line.maxX < 0.35f

                        val badgeCenter = when {
                            isMarginRight -> {
                                // In the gutter to the left of the right margin text
                                Offset(
                                    x = (line.minX * canvasWidth - 10.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                    y = line.avgY * canvasHeight
                                )
                            }
                            isMarginLeft -> {
                                // In the outer left gutter
                                Offset(
                                    x = (line.minX * canvasWidth - 10.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                    y = line.avgY * canvasHeight
                                )
                            }
                            else -> {
                                // Central frame: on the right gutter
                                Offset(
                                    x = (line.maxX * canvasWidth + 10.dp.toPx()).coerceAtMost(canvasWidth - 10.dp.toPx()),
                                    y = line.avgY * canvasHeight
                                )
                            }
                        }

                        // Badge dark background pill
                        drawCircle(
                            color = Color(0xEE0F172A),
                            radius = 7.0.dp.toPx(),
                            center = badgeCenter
                        )

                        // Badge border ring
                        drawCircle(
                            color = if (isSelected) GoldPrimary else typoColor,
                            radius = 7.0.dp.toPx(),
                            center = badgeCenter,
                            style = Stroke(width = if (isSelected) 2.0.dp.toPx() else 1.2.dp.toPx())
                        )

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "${line.orderIndex}",
                                badgeCenter.x,
                                badgeCenter.y + 3.0.dp.toPx(),
                                badgeTextPaint
                            )
                        }
                    }
                }

                // 4. In-progress Manual Annotation Points
                if (currentDrawPoints.isNotEmpty()) {
                    val points = currentDrawPoints.map { Offset(it.x * canvasWidth, it.y * canvasHeight) }
                    drawPoints(
                        points = points,
                        pointMode = PointMode.Points,
                        color = Color(0xFFEF4444),
                        strokeWidth = 10.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    if (points.size >= 2) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFFEF4444),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

/**
 * Utility functions for coordinate parsing and safe bitmap decoding
 */
private fun parsePointList(pointsJson: String): List<Offset> {
    if (pointsJson.isBlank()) return emptyList()
    return try {
        pointsJson.split(";").mapNotNull { pairStr ->
            val parts = pairStr.split(",")
            if (parts.size == 2) {
                val x = parts[0].trim().toFloatOrNull()
                val y = parts[1].trim().toFloatOrNull()
                if (x != null && y != null) Offset(x, y) else null
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = android.graphics.Color.parseColor("#$clean")
        Color(colorInt)
    } catch (e: Exception) {
        ScholarBlue
    }
}

private fun getTypologyColor(typology: String): Color {
    return when (typology.lowercase()) {
        "heading", "عنوان" -> Color(0xFFE11D48) // Crimson Rose for Chapter Rubrics
        "matan", "متن" -> Color(0xFF0284C7) // Sky Sapphire Blue for Core Matan
        "syarah", "شرح" -> Color(0xFFD97706) // Warm Amber Ochre for Explanatory Syarah
        "marginalia", "حاشية", "هامش" -> Color(0xFF8B5CF6) // Soft Lavender Amethyst for Marginalia
        "footnote", "تعليقة", "ذيل" -> Color(0xFF059669) // Emerald Jade for Footnotes
        else -> Color(0xFF0284C7)
    }
}

private fun loadBitmapSafely(context: Context, imageResName: String?, imageUri: String?): Bitmap? {
    return try {
        if (!imageUri.isNullOrEmpty()) {
            val uri = android.net.Uri.parse(imageUri)
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
}
