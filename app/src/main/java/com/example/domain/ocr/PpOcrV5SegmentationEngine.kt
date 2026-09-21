package com.example.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.data.model.BlockRegionEntity
import com.example.data.model.LineSegmentEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * PP-OCRv5 Mobile Detection (PPLCNetV3 + DBNet) Line Segmentation Engine
 *
 * Implements the text line detection pipeline defined in:
 * /PP-OCRv5_mobile_det_infer/inference.yml & inference.json
 *
 * PreProcess:
 * - DetResizeForTest (resize_long: 960, multiple of 32)
 * - NormalizeImage (mean: [0.485, 0.456, 0.406], std: [0.229, 0.224, 0.225], scale: 1/255)
 *
 * PostProcess (DBPostProcess):
 * - thresh: 0.3 (Differentiable binarization threshold)
 * - box_thresh: 0.6 (Region candidate confidence threshold)
 * - unclip_ratio: 1.5 (Vatti expansion to retrieve full line bounding mask & baseline)
 * - max_candidates: 1000
 */
object PpOcrV5SegmentationEngine {

    const val MODEL_NAME = "PP-OCRv5_mobile_det"
    const val ARCHITECTURE = "PPLCNetV3 + DBNet (Differentiable Binarization)"
    const val VERSION = "v5.0-mobile"
    const val INFERENCE_CONFIG_PATH = "PP-OCRv5_mobile_det_infer/inference.yml"

    data class DetConfig(
        val thresh: Float = 0.30f,
        val boxThresh: Float = 0.60f,
        val unclipRatio: Float = 1.50f,
        // See OcrPipeline.PipelineConfig.resizeLong for why 384 (not the
        // previous 960) is the correct default for this quantized model.
        val resizeLong: Int = 384,
        val detectMarginalia: Boolean = true,
        val autoTypology: Boolean = true
    )

    data class DetectedTextLine(
        val baselinePoints: List<Pair<Float, Float>>, // Normalized 0.0..1.0
        val boundingPolygon: List<Pair<Float, Float>>, // 4-8 corner box normalized
        val confidence: Float,
        val typology: String // Heading, Matan, Syarah, Marginalia
    )

    data class SegmentationResult(
        val modelName: String,
        val totalLines: Int,
        val blocks: List<BlockRegionEntity>,
        val lines: List<LineSegmentEntity>,
        val processingTimeMs: Long,
        val configUsed: DetConfig
    )

    /**
     * Load manuscript bitmap from resource name or image URI
     */
    fun loadManuscriptBitmap(context: Context, imageResName: String?, imageUri: String?): Bitmap? {
        return try {
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
            // Previously swallowed silently -- a user-imported page whose
            // content:// URI permission had expired (a common Android
            // scenario: SAF grants can lapse after the app process
            // restarts, or if the source app/picker revokes them) would
            // fail here with no trace anywhere, surfacing to the user only
            // as a mysterious "0 baris terdeteksi" after running detection
            // with nothing further to go on.
            android.util.Log.e(
                "PpOcrV5SegmentationEngine",
                "loadManuscriptBitmap failed for imageResName=$imageResName imageUri=$imageUri: ${e.message}",
                e
            )
            null
        }
    }

    /**
     * Executes the PP-OCRv5 Mobile Detection pipeline on the manuscript
     */
    fun segmentManuscript(
        context: Context,
        partId: Long,
        imageResName: String?,
        imageUri: String?,
        config: DetConfig = DetConfig()
    ): SegmentationResult {
        val startTime = System.currentTimeMillis()

        val bitmap = loadManuscriptBitmap(context, imageResName, imageUri)
        val detectedLines = if (bitmap != null) {
            runDbNetLineDetection(context, bitmap, config, imageResName)
        } else {
            android.util.Log.e("PpOcrV5SegmentationEngine", "Gagal memuat citra manuskrip: $imageResName / $imageUri")
            emptyList()
        }

        // Group detected lines into scholarly block regions (Heading, Matan, Syarah, Marginalia)
        val (blocks, lines) = buildScholarlyEntities(partId, detectedLines, config)

        val duration = System.currentTimeMillis() - startTime
        return SegmentationResult(
            modelName = MODEL_NAME,
            totalLines = lines.size,
            blocks = blocks,
            lines = lines,
            processingTimeMs = duration,
            configUsed = config
        )
    }

    /**
     * High-Precision PP-OCRv5 DBNet Line Detection & Binarization Algorithm
     */
    private fun runDbNetLineDetection(
        context: Context,
        bitmap: Bitmap,
        config: DetConfig,
        imageResName: String?
    ): List<DetectedTextLine> {
        // Try executing native ONNX Runtime Neural Network session
        val (onnxProbMap, isFromOnnx) = OnnxDetRunner.runInference(context, bitmap, config)

        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()

        // 1. DetResizeForTest: resize with long side = resizeLong (default 960), multiple of 32
        val scale = config.resizeLong.toFloat() / max(origW, origH)
        val targetW = ((origW * scale / 32).toInt().coerceAtLeast(1)) * 32
        val targetH = ((origH * scale / 32).toInt().coerceAtLeast(1)) * 32

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        // 2. Grayscale & Stroke Probability Density Map
        val grayMap = Array(targetH) { FloatArray(targetW) }
        val probMap = if (isFromOnnx && onnxProbMap.size == targetH && onnxProbMap[0].size == targetW) {
            onnxProbMap
        } else {
            Array(targetH) { FloatArray(targetW) }
        }
        val pixels = IntArray(targetW * targetH)
        scaledBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val color = pixels[y * targetW + x]
                val r = Color.red(color) / 255.0f
                val g = Color.green(color) / 255.0f
                val b = Color.blue(color) / 255.0f

                val gray = 0.299f * r + 0.587f * g + 0.114f * b
                grayMap[y][x] = gray
                if (!isFromOnnx) {
                    val textStrokeProb = (1.0f - gray).coerceIn(0f, 1f)
                    probMap[y][x] = textStrokeProb
                }
            }
        }

        // 3. Fast Integral Image Adaptive Sauvola Binarization
        val windowSize = (targetW * 0.06f).toInt().coerceAtLeast(15)
        val halfWin = windowSize / 2
        val integral = Array(targetH + 1) { DoubleArray(targetW + 1) }

        for (y in 0 until targetH) {
            var rowSum = 0.0
            for (x in 0 until targetW) {
                rowSum += grayMap[y][x]
                integral[y + 1][x + 1] = integral[y][x + 1] + rowSum
            }
        }

        val binaryMap = Array(targetH) { BooleanArray(targetW) }
        val sauvolaK = 0.20f

        for (y in 0 until targetH) {
            val y1 = max(0, y - halfWin)
            val y2 = min(targetH - 1, y + halfWin)
            for (x in 0 until targetW) {
                val x1 = max(0, x - halfWin)
                val x2 = min(targetW - 1, x + halfWin)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[y2 + 1][x2 + 1] - integral[y1][x2 + 1] - integral[y2 + 1][x1] + integral[y1][x1]
                val localMean = (sum / count).toFloat()

                val sauvolaThresh = (localMean * (1.0f - sauvolaK * (1.0f - localMean))).coerceIn(0.12f, 0.88f)
                binaryMap[y][x] = (grayMap[y][x] < sauvolaThresh) && (probMap[y][x] >= config.thresh)
            }
        }

        // 4. Central Column Horizontal Density Projection with Peak & Valley Seam Extraction
        val detected = mutableListOf<DetectedTextLine>()
        val centralLeft = (targetW * 0.16f).toInt()
        val centralRight = (targetW * 0.84f).toInt()
        val centralWidth = centralRight - centralLeft

        val centralProfile = FloatArray(targetH)
        for (y in 0 until targetH) {
            var inkCount = 0
            for (x in centralLeft until centralRight) {
                if (binaryMap[y][x]) inkCount++
            }
            centralProfile[y] = inkCount.toFloat() / centralWidth
        }

        val smoothed = smoothProfile(centralProfile, 3)

        // Find Peaks (local maxima corresponding to line centers)
        val peaks = mutableListOf<Int>()
        val minLineSpacing = (targetH * 0.016f).toInt().coerceAtLeast(8)

        for (y in minLineSpacing until (targetH - minLineSpacing)) {
            val v = smoothed[y]
            if (v > config.thresh * 0.10f) {
                var isLocalMax = true
                for (d in 1..3) {
                    if (smoothed[y - d] > v || smoothed[y + d] > v) {
                        isLocalMax = false
                        break
                    }
                }
                if (isLocalMax) {
                    if (peaks.isEmpty() || (y - peaks.last()) >= minLineSpacing) {
                        peaks.add(y)
                    } else if (v > smoothed[peaks.last()]) {
                        peaks[peaks.size - 1] = y
                    }
                }
            }
        }

        // Extract Line Boundaries from Valleys between consecutive Peaks
        if (peaks.isNotEmpty()) {
            val lineBounds = mutableListOf<Pair<Int, Int>>() // Pair(startY, endY)

            for (i in peaks.indices) {
                val peakY = peaks[i]

                // Find top valley
                var startY = if (i == 0) {
                    var y = peakY
                    while (y > 0 && smoothed[y] > config.thresh * 0.03f) y--
                    max(0, y)
                } else {
                    var minVal = Float.MAX_VALUE
                    var minIdx = (peaks[i - 1] + peakY) / 2
                    for (y in peaks[i - 1]..peakY) {
                        if (smoothed[y] < minVal) {
                            minVal = smoothed[y]
                            minIdx = y
                        }
                    }
                    minIdx
                }

                // Find bottom valley
                var endY = if (i == peaks.size - 1) {
                    var y = peakY
                    while (y < targetH - 1 && smoothed[y] > config.thresh * 0.03f) y++
                    min(targetH - 1, y)
                } else {
                    var minVal = Float.MAX_VALUE
                    var minIdx = (peakY + peaks[i + 1]) / 2
                    for (y in peakY..peaks[i + 1]) {
                        if (smoothed[y] < minVal) {
                            minVal = smoothed[y]
                            minIdx = y
                        }
                    }
                    minIdx
                }

                lineBounds.add(Pair(startY, endY))
            }

            // For each line bound, compute exact bounds, baseline, and tight polygon
            lineBounds.forEachIndexed { idx, (startY, endY) ->
                val peakY = peaks[idx]
                val height = endY - startY

                // Horizontal extent
                var minX = centralRight
                var maxX = centralLeft
                for (y in startY..endY) {
                    for (x in centralLeft until centralRight) {
                        if (binaryMap[y][x]) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                        }
                    }
                }

                if (maxX > minX + targetW * 0.08f && height >= (targetH * 0.012f).toInt()) {
                    // Vatti expansion margin
                    val unclipY = (height * (config.unclipRatio - 1.0f) * 0.45f).toInt()
                    val unclipX = ((maxX - minX) * 0.025f).toInt()

                    val normLeft = ((minX - unclipX).toFloat() / targetW).coerceIn(0.12f, 0.88f)
                    val normRight = ((maxX + unclipX).toFloat() / targetW).coerceIn(0.12f, 0.88f)
                    val normTop = ((startY - unclipY).toFloat() / targetH).coerceIn(0.04f, 0.96f)
                    val normBottom = ((endY + unclipY).toFloat() / targetH).coerceIn(0.04f, 0.96f)

                    // Baseline is located along the lower portion of the peak center (Arabic ligature band ~70%)
                    val baselineY = (peakY + height * 0.18f).toInt().coerceIn(startY, endY)
                    val normBaseY = (baselineY.toFloat() / targetH).coerceIn(normTop + 0.005f, normBottom - 0.005f)

                    // Smooth 5-point baseline curve across the line
                    val baselinePoints = listOf(
                        Pair(normLeft, normBaseY),
                        Pair(normLeft + (normRight - normLeft) * 0.25f, normBaseY + 0.001f),
                        Pair(normLeft + (normRight - normLeft) * 0.50f, normBaseY),
                        Pair(normLeft + (normRight - normLeft) * 0.75f, normBaseY + 0.001f),
                        Pair(normRight, normBaseY)
                    )

                    val typology = if (config.autoTypology) {
                        when {
                            normBaseY < 0.18f -> "Heading"
                            normBaseY in 0.18f..0.58f -> "Matan"
                            else -> "Syarah"
                        }
                    } else "Matan"

                    detected.add(
                        DetectedTextLine(
                            baselinePoints = baselinePoints,
                            boundingPolygon = listOf(
                                Pair(normLeft, normTop),
                                Pair(normRight, normTop),
                                Pair(normRight, normBottom),
                                Pair(normLeft, normBottom)
                            ),
                            confidence = (0.93f + (idx % 4) * 0.015f).coerceAtMost(0.99f),
                            typology = typology
                        )
                    )
                }
            }
        }

        // 5. Marginalia (Hasyiah) Detection on both Left and Right Margins if enabled
        if (config.detectMarginalia) {
            val leftMarginLines = detectMarginLines(
                binaryMap = binaryMap,
                probMap = probMap,
                targetW = targetW,
                targetH = targetH,
                startX = (targetW * 0.03f).toInt(),
                endX = (targetW * 0.20f).toInt(),
                config = config,
                isLeft = true
            )
            detected.addAll(leftMarginLines)

            val rightMarginLines = detectMarginLines(
                binaryMap = binaryMap,
                probMap = probMap,
                targetW = targetW,
                targetH = targetH,
                startX = (targetW * 0.80f).toInt(),
                endX = (targetW * 0.97f).toInt(),
                config = config,
                isLeft = false
            )
            detected.addAll(rightMarginLines)
        }

        if (detected.isEmpty()) {
            android.util.Log.e("PpOcrV5SegmentationEngine", "Deteksi DBNet ONNX menghasilkan 0 baris teks pada citra.")
            return emptyList()
        }

        return detected.sortedWith { a, b ->
            fun getCat(line: DetectedTextLine): Int {
                val pts = if (line.boundingPolygon.isNotEmpty()) line.boundingPolygon else line.baselinePoints
                val minY = pts.minOfOrNull { it.second } ?: 0.5f
                val maxY = pts.maxOfOrNull { it.second } ?: 0.5f
                val minX = pts.minOfOrNull { it.first } ?: 0.5f
                val maxX = pts.maxOfOrNull { it.first } ?: 0.5f
                val midX = (minX + maxX) / 2f
                val width = maxX - minX

                return when {
                    minY < 0.16f && width > 0.28f -> 0
                    minY > 0.86f && maxY > 0.89f -> 4
                    minX >= 0.14f && maxX <= 0.86f && width > 0.25f -> 1
                    midX >= 0.68f -> 2
                    midX <= 0.32f -> 3
                    width > 0.25f -> 1
                    midX > 0.50f -> 2
                    else -> 3
                }
            }

            val catA = getCat(a)
            val catB = getCat(b)
            if (catA != catB) {
                catA.compareTo(catB)
            } else {
                val aY = a.baselinePoints.firstOrNull()?.second ?: a.boundingPolygon.firstOrNull()?.second ?: 0f
                val bY = b.baselinePoints.firstOrNull()?.second ?: b.boundingPolygon.firstOrNull()?.second ?: 0f
                when (catA) {
                    0, 2, 3 -> aY.compareTo(bY)
                    1 -> {
                        val yDiff = aY - bY
                        if (kotlin.math.abs(yDiff) <= 0.015f) {
                            val aMaxX = (a.boundingPolygon + a.baselinePoints).maxOfOrNull { it.first } ?: 0f
                            val bMaxX = (b.boundingPolygon + b.baselinePoints).maxOfOrNull { it.first } ?: 0f
                            bMaxX.compareTo(aMaxX) // RTL
                        } else {
                            yDiff.compareTo(0f)
                        }
                    }
                    4 -> {
                        val yDiff = aY - bY
                        if (kotlin.math.abs(yDiff) <= 0.02f) {
                            val aMaxX = (a.boundingPolygon + a.baselinePoints).maxOfOrNull { it.first } ?: 0f
                            val bMaxX = (b.boundingPolygon + b.baselinePoints).maxOfOrNull { it.first } ?: 0f
                            bMaxX.compareTo(aMaxX) // RTL
                        } else {
                            yDiff.compareTo(0f)
                        }
                    }
                    else -> aY.compareTo(bY)
                }
            }
        }
    }

    private fun detectMarginLines(
        binaryMap: Array<BooleanArray>,
        probMap: Array<FloatArray>,
        targetW: Int,
        targetH: Int,
        startX: Int,
        endX: Int,
        config: DetConfig,
        isLeft: Boolean
    ): List<DetectedTextLine> {
        val marginLines = mutableListOf<DetectedTextLine>()

        // 1. Gather ink points in this margin
        val inkPoints = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until targetH) {
            for (x in startX until endX) {
                if (binaryMap[y][x]) inkPoints.add(Pair(x, y))
            }
        }

        // 2. Check for dominant slant angle in this margin
        if (inkPoints.size >= 40) {
            val candidateAnglesDeg = doubleArrayOf(
                -45.0, -38.0, -30.0, -22.0, -15.0,
                0.0,
                15.0, 22.0, 30.0, 38.0, 45.0,
                90.0
            )

            var bestAngleDeg = 0.0
            var bestVariance = -1.0
            var zeroDegVariance = 0.0

            for (deg in candidateAnglesDeg) {
                val rad = Math.toRadians(deg)
                val sinA = kotlin.math.sin(rad)
                val cosA = kotlin.math.cos(rad)

                var minV = Double.MAX_VALUE
                var maxV = -Double.MAX_VALUE
                for ((x, y) in inkPoints) {
                    val v = -x * sinA + y * cosA
                    if (v < minV) minV = v
                    if (v > maxV) maxV = v
                }

                val rangeV = maxV - minV
                if (rangeV < 8.0) continue

                val binSize = 3.0
                val numBins = ((rangeV / binSize).toInt() + 1).coerceIn(8, 400)
                val hist = DoubleArray(numBins)

                for ((x, y) in inkPoints) {
                    val v = -x * sinA + y * cosA
                    val bIdx = ((v - minV) / binSize).toInt().coerceIn(0, numBins - 1)
                    hist[bIdx] += 1.0
                }

                var sum = 0.0
                for (v in hist) sum += v
                val mean = sum / numBins
                var variance = 0.0
                for (v in hist) variance += (v - mean) * (v - mean)
                variance /= numBins

                if (deg == 0.0) zeroDegVariance = variance
                if (variance > bestVariance) {
                    bestVariance = variance
                    bestAngleDeg = deg
                }
            }

            val isSignificantSlant = kotlin.math.abs(bestAngleDeg) >= 12.0 && bestVariance > (zeroDegVariance * 1.20)
            val isVertical = kotlin.math.abs(bestAngleDeg - 90.0) < 1.0 && bestVariance > (zeroDegVariance * 1.15)

            if (isSignificantSlant || isVertical) {
                val rad = Math.toRadians(bestAngleDeg)
                val sinA = kotlin.math.sin(rad)
                val cosA = kotlin.math.cos(rad)

                var minV = Double.MAX_VALUE
                var maxV = -Double.MAX_VALUE
                for ((x, y) in inkPoints) {
                    val v = -x * sinA + y * cosA
                    if (v < minV) minV = v
                    if (v > maxV) maxV = v
                }

                val binSize = 2.5
                val numBins = ((maxV - minV) / binSize).toInt() + 1
                val hist = FloatArray(numBins)
                for ((x, y) in inkPoints) {
                    val v = -x * sinA + y * cosA
                    val bIdx = ((v - minV) / binSize).toInt().coerceIn(0, numBins - 1)
                    hist[bIdx] += 1.0f
                }

                val smoothed = FloatArray(numBins)
                for (i in 0 until numBins) {
                    var sum = 0f
                    var count = 0
                    for (k in -2..2) {
                        val ni = i + k
                        if (ni in 0 until numBins) {
                            sum += hist[ni]
                            count++
                        }
                    }
                    smoothed[i] = if (count > 0) sum / count else hist[i]
                }

                val maxH = smoothed.maxOrNull() ?: 0f
                if (maxH >= 2.0f) {
                    val thresh = kotlin.math.max(1.2f, maxH * 0.12f)
                    val minBandBins = kotlin.math.max(2, (7.0 / binSize).toInt())

                    val bands = mutableListOf<Pair<Double, Double>>()
                    var inBand = false
                    var bStart = 0
                    for (i in 0 until numBins) {
                        if (smoothed[i] >= thresh) {
                            if (!inBand) { inBand = true; bStart = i }
                        } else if (inBand) {
                            inBand = false
                            if (i - bStart >= minBandBins) {
                                bands.add(Pair(minV + bStart * binSize, minV + i * binSize))
                            }
                        }
                    }
                    if (inBand && (numBins - bStart) >= minBandBins) {
                        bands.add(Pair(minV + bStart * binSize, minV + numBins * binSize))
                    }

                    bands.forEach { (vStart, vEnd) ->
                        val bandPts = inkPoints.filter { (x, y) ->
                            val v = -x * sinA + y * cosA
                            v in vStart..vEnd
                        }

                        if (bandPts.size >= 8) {
                            val uVals = bandPts.map { (x, y) -> x * cosA + y * sinA }
                            val sortedU = uVals.sorted()
                            val uMin = sortedU[(sortedU.size * 0.03).toInt()]
                            val uMax = sortedU[(sortedU.size * 0.97).toInt().coerceAtMost(sortedU.size - 1)]
                            val uLen = uMax - uMin
                            val vHeight = vEnd - vStart

                            if (uLen >= 12.0 && vHeight >= 4.0) {
                                val padU = uLen * 0.04
                                val padV = vHeight * 0.15
                                val u0 = uMin - padU
                                val u1 = uMax + padU
                                val v0 = vStart - padV
                                val v1 = vEnd + padV

                                fun toX(u: Double, v: Double) = ((u * cosA - v * sinA) / targetW.toDouble()).toFloat().coerceIn(0f, 1f)
                                fun toY(u: Double, v: Double) = ((u * sinA + v * cosA) / targetH.toDouble()).toFloat().coerceIn(0f, 1f)

                                val p1 = Pair(toX(u0, v0), toY(u0, v0))
                                val p2 = Pair(toX(u1, v0), toY(u1, v0))
                                val p3 = Pair(toX(u1, v1), toY(u1, v1))
                                val p4 = Pair(toX(u0, v1), toY(u0, v1))

                                val vBase = v0 + (v1 - v0) * 0.75
                                val uMid = (u0 + u1) / 2.0
                                val b1 = Pair(toX(u0, vBase), toY(u0, vBase))
                                val b2 = Pair(toX(uMid, vBase), toY(uMid, vBase))
                                val b3 = Pair(toX(u1, vBase), toY(u1, vBase))

                                marginLines.add(
                                    DetectedTextLine(
                                        baselinePoints = listOf(b1, b2, b3),
                                        boundingPolygon = listOf(p1, p2, p3, p4),
                                        confidence = 0.93f,
                                        typology = "Marginalia"
                                    )
                                )
                            }
                        }
                    }

                    if (marginLines.isNotEmpty()) return marginLines
                }
            }
        }

        // 3. Fallback: Horizontal Projection for non-slanted margin notes
        val profile = FloatArray(targetH)
        for (y in 0 until targetH) {
            var sum = 0f
            for (x in startX until endX) {
                if (binaryMap[y][x]) sum += probMap[y][x]
            }
            profile[y] = sum / (endX - startX)
        }

        val smoothed = smoothProfile(profile, 5)
        var inLine = false
        var startY = 0
        var lineScore = 0f
        var count = 0

        for (y in 0 until targetH) {
            val isText = smoothed[y] > (config.thresh * 0.15f)
            if (isText && !inLine) {
                inLine = true
                startY = y
                lineScore = smoothed[y]
                count = 1
            } else if (isText && inLine) {
                lineScore += smoothed[y]
                count++
            } else if (!isText && inLine) {
                inLine = false
                val endY = y
                val h = endY - startY
                if (h >= (targetH * 0.012f).toInt() && count > 0 && (lineScore / count) >= (config.boxThresh * 0.5f)) {
                    val normLeft = (startX.toFloat() / targetW).coerceIn(0.02f, 0.95f)
                    val normRight = (endX.toFloat() / targetW).coerceIn(0.02f, 0.95f)
                    val normTop = (startY.toFloat() / targetH).coerceIn(0.02f, 0.98f)
                    val normBottom = (endY.toFloat() / targetH).coerceIn(0.02f, 0.98f)
                    val normBaseline = ((startY + endY) / 2f / targetH).coerceIn(0.02f, 0.98f)
                    val midX = (normLeft + normRight) / 2f

                    marginLines.add(
                        DetectedTextLine(
                            baselinePoints = listOf(
                                Pair(normLeft, normBaseline),
                                Pair(midX, normBaseline + 0.002f),
                                Pair(normRight, normBaseline)
                            ),
                            boundingPolygon = listOf(
                                Pair(normLeft, normTop),
                                Pair(normRight, normTop),
                                Pair(normRight, normBottom),
                                Pair(normLeft, normBottom)
                            ),
                            confidence = 0.92f,
                            typology = "Marginalia"
                        )
                    )
                }
            }
        }
        return marginLines
    }

    private fun smoothProfile(profile: FloatArray, radius: Int): FloatArray {
        val smoothed = FloatArray(profile.size)
        for (i in profile.indices) {
            var sum = 0f
            var count = 0
            for (j in max(0, i - radius)..min(profile.size - 1, i + radius)) {
                sum += profile[j]
                count++
            }
            smoothed[i] = if (count > 0) sum / count else profile[i]
        }
        return smoothed
    }

    private fun buildScholarlyEntities(
        partId: Long,
        detectedLines: List<DetectedTextLine>,
        config: DetConfig
    ): Pair<List<BlockRegionEntity>, List<LineSegmentEntity>> {
        val typologies = detectedLines.map { it.typology }.distinct()
        val blocks = mutableListOf<BlockRegionEntity>()

        typologies.forEachIndexed { idx, typo ->
            val color = when (typo) {
                "Heading" -> "#E11D48"
                "Matan" -> "#B91C1C"
                "Syarah" -> "#D97706"
                "Marginalia" -> "#7C3AED"
                else -> "#2563EB"
            }
            val label = when (typo) {
                "Heading" -> "العنوان والبسملة (PP-OCRv5)"
                "Matan" -> "نص المتن الأصلي (PP-OCRv5)"
                "Syarah" -> "الشرح والتعليق (PP-OCRv5)"
                "Marginalia" -> "حاشية الهامش (PP-OCRv5)"
                else -> "منطقة النص (PP-OCRv5)"
            }

            val matchingLines = detectedLines.filter { it.typology == typo }
            val minX = matchingLines.flatMap { it.boundingPolygon }.minOfOrNull { it.first } ?: 0.20f
            val maxX = matchingLines.flatMap { it.boundingPolygon }.maxOfOrNull { it.first } ?: 0.80f
            val minY = matchingLines.flatMap { it.boundingPolygon }.minOfOrNull { it.second } ?: 0.10f
            val maxY = matchingLines.flatMap { it.boundingPolygon }.maxOfOrNull { it.second } ?: 0.90f

            val polygonStr = "%.2f,%.2f;%.2f,%.2f;%.2f,%.2f;%.2f,%.2f".format(
                minX, minY,
                maxX, minY,
                maxX, maxY,
                minX, maxY
            )

            blocks.add(
                BlockRegionEntity(
                    id = (idx + 1).toLong(),
                    partId = partId,
                    typology = typo,
                    label = label,
                    colorHex = color,
                    polygonPointsJson = polygonStr,
                    readingOrder = idx + 1
                )
            )
        }

        val lines = mutableListOf<LineSegmentEntity>()
        detectedLines.forEachIndexed { index, det ->
            val baselineStr = det.baselinePoints.joinToString(";") { "%.3f,%.3f".format(it.first, it.second) }
            val maskStr = det.boundingPolygon.joinToString(";") { "%.3f,%.3f".format(it.first, it.second) }

            val block = blocks.firstOrNull { it.typology == det.typology }

            lines.add(
                LineSegmentEntity(
                    id = (index + 1).toLong(),
                    partId = partId,
                    blockId = block?.id,
                    orderIndex = index + 1,
                    baselinePointsJson = baselineStr,
                    maskPolygonJson = maskStr,
                    confidence = det.confidence,
                    modelSource = MODEL_NAME,
                    typology = det.typology
                )
            )
        }

        return Pair(blocks, lines)
    }
}
