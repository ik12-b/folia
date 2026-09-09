package com.example.domain.ocr.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.domain.ocr.OnnxDetRunner
import com.example.domain.ocr.PpOcrV5SegmentationEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 2: DBNet Bounding Polygon & Baseline Segmentation.
 * Executes PP-OCRv5 Mobile Det ONNX model, thresholds probability map,
 * then performs COLUMN-AWARE line extraction:
 *
 *  1. Detect vertical "zones" (row blocks) across the full page width — this
 *     separates regions like [header] / [matan frame + side marginalia] /
 *     [footer syarah block] that sit at different heights on the page.
 *  2. Within each zone, run a LOCAL column projection to find side-by-side
 *     columns (e.g. narrow marginalia strips flanking a wide matan frame).
 *  3. Within each column, detect horizontal line bands using a threshold
 *     adaptive to that column's own peak density — a single global threshold
 *     cannot fit both a dense wide matan column and a sparse narrow
 *     marginalia column at once.
 *  4. Reading order is assigned by (zone top Y) -> (wide/main column before
 *     narrow/marginalia columns within the same zone) -> (column left-to-right)
 *     -> (line top-to-bottom within the column), instead of a single global
 *     sort by Y that interleaves unrelated columns.
 *
 * This replaces the older single horizontal-projection-over-full-width
 * approach, which could not represent multi-column manuscript layouts and
 * merged unrelated columns into the same "line".
 */
object SegmentationStage {

    private const val TAG = "SegmentationStage"

    /** A column heuristically considered "narrow" (marginalia-like) below this width ratio. */
    private const val NARROW_COLUMN_WIDTH_RATIO = 0.22f

    data class SegmentationOutput(
        val lines: List<OcrPipeline.BaselineLine>,
        val isFromOnnx: Boolean
    )

    private data class RawLine(
        val topY: Int,
        val bottomY: Int,
        val leftX: Int,
        val rightX: Int,
        val activePixelCount: Int,
        val zoneIndex: Int,
        val zoneTopY: Int,
        val columnIndex: Int,
        val columnIsNarrow: Boolean,
        val columnPriority: Int, // 0 = main/wide column, 1 = narrow/marginalia column
        val isVertical: Boolean = false,
        val skewAngleDeg: Float = 0f,
        val customPolygonNorm: List<Pair<Float, Float>>? = null,
        val customBaselineNorm: List<Pair<Float, Float>>? = null,
        // Non-textual structural/decorative element (frame border, column
        // divider rule, section-separator rosette/flourish) rather than a
        // line of Arabic script. These used to be silently discarded by
        // isThinRulingArtifact so they left no trace in the transcription;
        // now they are kept and represented with a symbolic placeholder so
        // the transcription reflects the manuscript's visual structure,
        // not just its readable text.
        val isOrnament: Boolean = false,
        val ornamentKind: String? = null // "VerticalRule" | "HorizontalRule" | "Flourish"
    )

    fun segment(
        context: Context,
        bitmap: Bitmap,
        config: OcrPipeline.PipelineConfig
    ): SegmentationOutput {
        val detConfig = PpOcrV5SegmentationEngine.DetConfig(
            thresh = config.thresh,
            boxThresh = config.boxThresh,
            unclipRatio = config.unclipRatio,
            resizeLong = config.resizeLong
        )

        // 1. Run ONNX Model Inference
        val (onnxProbMap, isOnnxSuccess) = OnnxDetRunner.runInference(context, bitmap, detConfig)

        val probMap: Array<FloatArray> = if (isOnnxSuccess && onnxProbMap.isNotEmpty()) {
            onnxProbMap
        } else {
            // DBNet Differentiable Probability Simulation
            computeSimulatedProbMap(bitmap, detConfig)
        }

        val mapH = probMap.size
        val mapW = if (mapH > 0) probMap[0].size else 0

        if (mapH == 0 || mapW == 0) {
            return SegmentationOutput(emptyList(), false)
        }

        // 2. Binarization
        val binarized = Array(mapH) { y -> BooleanArray(mapW) { x -> probMap[y][x] >= config.thresh } }

        // 2b. Detect illustration/diagram regions (e.g. geometric figures
        // common in falak/handasah manuscripts: circles, radial
        // construction lines, astronomical diagrams) BEFORE row-block
        // detection runs. These regions must be identified first and
        // excluded from the text row/column pipeline, because otherwise
        // a large illustration sitting between two text blocks either
        // gets silently absorbed into a neighboring text zone (corrupting
        // its line segmentation) or creates a zone gap that's ignored
        // entirely -- in both cases the diagram itself leaves no trace
        // anywhere in the output, unlike an ornament rule which is thin
        // enough to just sit in a column gap.
        //
        // Verified on a real falak (astronomy) manuscript page with a
        // multi-circle epicycle diagram drawn in red ink: the text
        // detection model's probability map (`binarized`, from
        // `probMap`) was almost entirely blind to it -- density dropped
        // to exactly 0 for large stretches of the diagram, because the
        // detection model was trained to find black script, not colored
        // diagram lines. A density-based heuristic on `binarized` only
        // ever caught a small fragment near the diagram's center where
        // text-like labels happened to sit close together, missing over
        // half the true diagram extent entirely.
        //
        // The fix mirrors the same lesson learned from vertical-rule
        // ornament detection and slanted-text orientation detection
        // earlier: when a manuscript element isn't script, don't trust
        // the text-detection model's probability map to see it at all --
        // analyze the original bitmap's own pixel content instead. Here,
        // that means detecting colored (non-black, e.g. red) ink directly
        // from the source image, which is a much stronger and more
        // reliable signal for diagram ink than ink density ever was.
        val illustrationZones = detectIllustrationZones(bitmap, binarized, mapH, mapW)
        val illustrationLines = illustrationZones.map { (zt, zb) ->
            RawLine(
                topY = zt,
                bottomY = zb,
                leftX = 0,
                rightX = mapW,
                activePixelCount = 0,
                zoneIndex = -1,
                zoneTopY = zt,
                columnIndex = 0,
                columnIsNarrow = false,
                columnPriority = 0,
                isOrnament = true,
                ornamentKind = "Illustration"
            )
        }

        // Mask out illustration rows before row-block detection so the
        // text pipeline below never sees them as text-bearing rows.
        val binarizedTextOnly = if (illustrationZones.isEmpty()) {
            binarized
        } else {
            Array(mapH) { y ->
                val inIllustration = illustrationZones.any { (zt, zb) -> y in zt until zb }
                if (inIllustration) BooleanArray(mapW) else binarized[y]
            }
        }

        // 3. Detect vertical zones (row blocks) across the full page width
        val zones = detectRowBlocks(binarizedTextOnly, mapH, mapW)
        if (zones.isEmpty() && illustrationLines.isEmpty()) {
            Log.e(TAG, "Deteksi garis PP-OCRv5 DBNet gagal: tidak ditemukan zona teks pada citra manuskrip.")
            return SegmentationOutput(emptyList(), isOnnxSuccess)
        }

        // 4. Within each zone, find local columns, then lines within each column
        val rawLines = mutableListOf<RawLine>()
        rawLines.addAll(illustrationLines)
        zones.forEachIndexed { zoneIdx, zone ->
            val (zoneTop, zoneBottom) = zone
            val columns = detectColumns(binarized, zoneTop, zoneBottom, mapW)

            // The single widest column in a zone is treated as the central
            // text frame (matan/syarah) regardless of its absolute page
            // position. The previous isNarrow check used only absolute
            // thresholds (colStart < 25% of page width, colEnd > 75%),
            // which wrongly flagged a genuinely wide matan column as
            // "narrow marginalia" whenever the book's left margin was
            // small enough to push the matan frame's left edge past that
            // fixed cutoff (verified on a real manuscript page: a 234px
            // matan column starting at colStart=114 on a 448px-wide map
            // was misclassified as narrow purely because 114 < 448*0.25,
            // even though it was clearly the widest column in its zone by
            // a wide margin over the neighboring ~80px marginalia
            // columns). That misclassification cascades into
            // getScholarlyCategory(), which relies on columnIsNarrow to
            // tell matan from marginalia, corrupting the whole reading
            // order.
            val widestColumnWidth = columns.maxOfOrNull { it.second - it.first } ?: 0

            // Record the decorative/structural vertical rules that sit in
            // the gaps between adjacent columns (e.g. the ruled border
            // separating a matan frame from its flanking marginalia).
            // These are real, meaningful marks in the manuscript -- readers
            // use them to tell where one text region ends and another
            // begins -- but previously left no trace anywhere downstream,
            // since column gaps are only used internally to split up
            // rawLines and are then discarded.
            for (i in 0 until columns.size - 1) {
                val gapStart = columns[i].second
                val gapEnd = columns[i + 1].first
                val gapWidth = gapEnd - gapStart
                if (gapWidth in 1..max(10, (mapW * 0.03f).toInt())) {
                    rawLines.add(
                        RawLine(
                            topY = zoneTop,
                            bottomY = zoneBottom,
                            leftX = gapStart,
                            rightX = gapEnd,
                            activePixelCount = 0,
                            zoneIndex = zoneIdx,
                            zoneTopY = zoneTop,
                            columnIndex = i,
                            columnIsNarrow = true,
                            columnPriority = 1,
                            isVertical = true,
                            isOrnament = true,
                            ornamentKind = "VerticalRule"
                        )
                    )
                }
            }

            columns.forEachIndexed { colIdx, col ->
                val (colStart, colEnd) = col
                val colWidth = colEnd - colStart
                val colWidthRatio = colWidth.toFloat() / mapW.toFloat()
                val isWidestInZone = colWidth == widestColumnWidth
                val isNarrow = !isWidestInZone &&
                    (colWidthRatio < NARROW_COLUMN_WIDTH_RATIO || colStart < (mapW * 0.25f) || colEnd > (mapW * 0.75f))
                val priority = if (isNarrow) 1 else 0

                // If in marginalia/narrow column, check for rotated/slanted marginalia text (e.g. 20°..45° or vertical)
                val orientedLines = if (isNarrow) {
                    detectOrientedLinesInColumn(bitmap, binarized, zoneTop, zoneBottom, colStart, colEnd, mapW, mapH, zoneIdx, colIdx, priority)
                } else null

                if (orientedLines != null && orientedLines.isNotEmpty()) {
                    rawLines.addAll(orientedLines)
                } else {
                    val bands = detectLineBandsInColumn(binarized, zoneTop, zoneBottom, colStart, colEnd)
                    bands.forEach { (bTop, bBottom) ->
                        // Find horizontal extents of ink strictly within this column's band
                        var minX = colEnd
                        var maxX = colStart
                        var activePixels = 0
                        for (y in bTop until bBottom) {
                            for (x in colStart until colEnd) {
                                if (binarized[y][x]) {
                                    if (x < minX) minX = x
                                    if (x > maxX) maxX = x
                                    activePixels++
                                }
                            }
                        }
                        val bandW = maxX - minX
                        val bandH = bBottom - bTop

                        // Filter out speckle noise, but keep genuine short
                        // decorative marks (section-separator rosettes/
                        // flourishes like "*" or "❊" bullets, which manuscripts
                        // commonly place between clauses) as ornaments rather
                        // than silently discarding them. A true noise speckle
                        // is both thin AND sparse; a small but solid mark
                        // (enough active pixels relative to its box, i.e. not
                        // a hairline) is more likely a genuine ornament glyph.
                        val isThinRulingArtifact = (bandW <= max(8, (mapW * 0.025f).toInt()) && bandH >= bandW * 1.2f) ||
                            (bandW < max(12, (mapW * 0.035f).toInt()) && activePixels < 20)

                        val fillRatio = if (bandW > 0 && bandH > 0) activePixels.toFloat() / (bandW * bandH) else 0f
                        val isSmallSolidOrnament = isThinRulingArtifact &&
                            bandW in 3..max(12, (mapW * 0.035f).toInt()) &&
                            bandH in 3..max(12, (mapH * 0.02f).toInt()) &&
                            fillRatio >= 0.30f &&
                            activePixels >= 6

                        val isTallVerticalGloss = !isThinRulingArtifact &&
                            bandH >= (mapH * 0.08f) &&
                            bandW >= max(16, (mapW * 0.04f).toInt()) &&
                            bandH > bandW * 1.8f &&
                            activePixels >= 35

                        val isValidHorizontalLine = !isThinRulingArtifact &&
                            bandW >= max(14, (mapW * 0.035f).toInt()) &&
                            bandH >= 3 &&
                            activePixels >= 10

                        if (maxX > minX && isSmallSolidOrnament) {
                            rawLines.add(
                                RawLine(
                                    topY = bTop,
                                    bottomY = bBottom,
                                    leftX = minX,
                                    rightX = maxX,
                                    activePixelCount = activePixels,
                                    zoneIndex = zoneIdx,
                                    zoneTopY = zoneTop,
                                    columnIndex = colIdx,
                                    columnIsNarrow = isNarrow,
                                    columnPriority = priority,
                                    isVertical = false,
                                    isOrnament = true,
                                    ornamentKind = "Flourish"
                                )
                            )
                        } else if (maxX > minX && !isThinRulingArtifact && (isValidHorizontalLine || isTallVerticalGloss)) {
                            rawLines.add(
                                RawLine(
                                    topY = bTop,
                                    bottomY = bBottom,
                                    leftX = minX,
                                    rightX = maxX,
                                    activePixelCount = activePixels,
                                    zoneIndex = zoneIdx,
                                    zoneTopY = zoneTop,
                                    columnIndex = colIdx,
                                    columnIsNarrow = isNarrow || isTallVerticalGloss,
                                    columnPriority = priority,
                                    isVertical = isTallVerticalGloss
                                )
                            )
                        }
                    }
                }
            }
        }

        if (rawLines.isEmpty()) {
            Log.e(TAG, "Deteksi garis PP-OCRv5 DBNet gagal: tidak ditemukan baris teks pada citra manuskrip.")
            return SegmentationOutput(emptyList(), isOnnxSuccess)
        }

        // 5. Assign scholarly reading order:
        // Category 0: Heading/Unwan/Basmalah (top of page)
        // Category 1: Central Text Frame - Matan & Syarah in unbroken top-to-bottom sequence
        // Category 2: Right Margin Commentary (top-to-bottom)
        // Category 3: Left Margin Commentary (top-to-bottom)
        // Category 4: Bottom Footnotes / Tail Notes (top-to-bottom, RTL)
        val orderedRaw = rawLines.sortedWith { a, b ->
            val catA = getScholarlyCategory(a, mapW, mapH)
            val catB = getScholarlyCategory(b, mapW, mapH)
            if (catA != catB) {
                catA.compareTo(catB)
            } else {
                when (catA) {
                    0 -> a.topY.compareTo(b.topY)
                    1 -> {
                        val yDiff = a.topY - b.topY
                        val avgH = max(4, ((a.bottomY - a.topY) + (b.bottomY - b.topY)) / 2)
                        if (abs(yDiff) <= avgH * 0.45f) {
                            b.rightX.compareTo(a.rightX)
                        } else {
                            yDiff.compareTo(0)
                        }
                    }
                    2, 3 -> a.topY.compareTo(b.topY)
                    4 -> {
                        // Uses the same line-height-relative threshold as
                        // category 1 (matan), not an absolute mapH-based
                        // one. An absolute threshold (previously
                        // mapH*0.02, e.g. 12px on a 600px-tall map) is
                        // easily met by two ordinary consecutive lines
                        // whose height is close to that same 12px, wrongly
                        // treating them as horizontally-adjacent and
                        // triggering the RTL tie-break instead of simple
                        // top-to-bottom order (verified: two footnote
                        // lines at y=(525,537) and y=(537,551) -- directly
                        // stacked, not side-by-side -- had yDiff=-12
                        // exactly matching the old threshold and were
                        // reordered incorrectly).
                        val yDiff = a.topY - b.topY
                        val avgH = max(4, ((a.bottomY - a.topY) + (b.bottomY - b.topY)) / 2)
                        if (abs(yDiff) <= avgH * 0.45f) {
                            b.rightX.compareTo(a.rightX)
                        } else {
                            yDiff.compareTo(0)
                        }
                    }
                    else -> a.topY.compareTo(b.topY)
                }
            }
        }

        // 6. Build BaselineLine entities with accurate typology
        val detectedLines = mutableListOf<OcrPipeline.BaselineLine>()
        orderedRaw.forEachIndexed { readIdx, raw ->
            val bandHeight = raw.bottomY - raw.topY
            val bandWidth = raw.rightX - raw.leftX
            val isVerticalGloss = raw.isVertical || (bandHeight > bandWidth * 2.2f && bandWidth < (mapW * 0.08f))

            val unclipOffset = (bandHeight * (config.unclipRatio - 1.0f) * 0.45f).toInt()
            val expandedTop = max(0, raw.topY - unclipOffset)
            val expandedBottom = min(mapH - 1, raw.bottomY + unclipOffset)
            val expandedLeft = max(0, raw.leftX - (if (isVerticalGloss) 2 else (bandHeight * 0.25f).toInt()))
            val expandedRight = min(mapW - 1, raw.rightX + (if (isVerticalGloss) 2 else (bandHeight * 0.25f).toInt()))

            val leftXNorm = expandedLeft.toFloat() / mapW.toFloat()
            val rightXNorm = expandedRight.toFloat() / mapW.toFloat()
            val topYNorm = expandedTop.toFloat() / mapH.toFloat()
            val bottomYNorm = expandedBottom.toFloat() / mapH.toFloat()
            val midXNorm = (leftXNorm + rightXNorm) / 2.0f
            val midYNorm = (topYNorm + bottomYNorm) / 2.0f

            val baseline = raw.customBaselineNorm ?: if (isVerticalGloss) {
                listOf(
                    Pair(midXNorm, topYNorm),
                    Pair(midXNorm, midYNorm),
                    Pair(midXNorm, bottomYNorm)
                )
            } else {
                val baselineYNorm = (raw.topY + bandHeight * 0.75f) / mapH.toFloat()
                listOf(
                    Pair(leftXNorm, baselineYNorm),
                    Pair(midXNorm, baselineYNorm + 0.001f),
                    Pair(rightXNorm, baselineYNorm)
                )
            }

            val polygon = raw.customPolygonNorm ?: listOf(
                Pair(leftXNorm, topYNorm),
                Pair(rightXNorm, topYNorm),
                Pair(rightXNorm, bottomYNorm),
                Pair(leftXNorm, bottomYNorm)
            )

            val category = getScholarlyCategory(raw, mapW, mapH)
            val typology = when {
                // Illustrations get their own typology (distinct from
                // "Ornament") so LineExtractor/RecognitionStage can tell
                // "this needs its bitmap preserved for the reader to see"
                // apart from "this is a symbolic text placeholder with no
                // image content" -- an illustration is the whole point of
                // being preserved, so silently dropping its bitmap like a
                // divider rule would defeat the purpose of detecting it.
                raw.isOrnament && raw.ornamentKind == "Illustration" -> "Illustration"
                raw.isOrnament -> "Ornament"
                category == 0 -> "Heading"
                category == 1 -> if (topYNorm < 0.45f) "Matan" else "Syarah"
                category == 2 || category == 3 -> "Marginalia"
                category == 4 -> "Footnote"
                else -> "Matan"
            }

            // Ornaments are structural/decorative marks, not text -- there
            // is nothing for the recognition model to read, so they get a
            // symbolic placeholder directly here instead of a cropped-line
            // bitmap. This keeps the manuscript's visual structure (frame
            // borders separating matan from marginalia, section-separator
            // flourishes) visible in the transcription output rather than
            // silently vanishing, which is what happened before these
            // elements were filtered out entirely as noise.
            val ornamentPlaceholder = when (raw.ornamentKind) {
                "VerticalRule" -> "[ فاصل / إطار ]"   // frame/divider rule
                "Flourish" -> "❊"                       // section-separator rosette/bullet
                "Illustration" -> "[ رسم / شكل هندسي ]" // geometric diagram/illustration -- see lineBitmap for the actual image
                else -> "[ زخرفة ]"                      // generic ornament fallback
            }

            val confidence = (0.88f + (raw.activePixelCount.toFloat() /
                ((expandedBottom - expandedTop) * (expandedRight - expandedLeft) + 1)) * 0.12f)
                .coerceIn(0.80f, 0.99f)

            detectedLines.add(
                OcrPipeline.BaselineLine(
                    orderIndex = readIdx + 1,
                    baseline = baseline,
                    polygon = polygon,
                    typology = typology,
                    confidence = confidence,
                    transcribedText = if (raw.isOrnament) ornamentPlaceholder else "",
                    recConfidence = if (raw.isOrnament) 1.0f else 0f,
                    columnIndex = raw.columnIndex,
                    readingPriority = category
                )
            )
        }

        return SegmentationOutput(detectedLines, isOnnxSuccess)
    }

    /**
     * Categorizes a detected line into classical manuscript layout hierarchy:
     * 0: Heading / Unwan / Basmalah (العنوان / الترويسة)
     * 1: Central Text Frame - Matan & Syarah (المتن والشرح في إطار الجدول)
     * 2: Right Margin Commentary (حاشية الهامش الأيمن)
     * 3: Left Margin Commentary (حاشية الهامش الأيسر)
     * 4: Bottom Footnotes / Tail Notes (الحاشية السفلية والذيل)
     */
    private fun getScholarlyCategory(raw: RawLine, mapW: Int, mapH: Int): Int {
        // A vertical rule marking the boundary between the central matan
        // frame and its side marginalia is, by construction, positioned at
        // the gap between two adjacent columns. It's categorized the same
        // way a column at that X position would be, so it sorts into the
        // reading order right alongside the region it borders (e.g. a rule
        // just left of the matan frame reads immediately before/after the
        // left marginalia it separates), rather than needing bespoke
        // placement logic.
        if (raw.isOrnament && raw.ornamentKind == "VerticalRule") {
            val normMidX = ((raw.leftX + raw.rightX) / 2.0f) / mapW.toFloat()
            return when {
                normMidX >= 0.68f -> 2
                normMidX <= 0.32f -> 3
                else -> 1
            }
        }

        // Illustrations span the full page width by construction (they're
        // detected page-wide, not per-column) and should read in simple
        // top-to-bottom order alongside the main text flow, the same way
        // a full-width heading does -- not be shuffled relative to
        // marginalia based on X position, which wouldn't make sense for
        // something with no meaningful left/right extent of its own.
        if (raw.isOrnament && raw.ornamentKind == "Illustration") {
            return 1
        }

        val normTop = raw.topY.toFloat() / mapH.toFloat()
        val normBottom = raw.bottomY.toFloat() / mapH.toFloat()
        val normLeft = raw.leftX.toFloat() / mapW.toFloat()
        val normRight = raw.rightX.toFloat() / mapW.toFloat()
        val normMidX = (normLeft + normRight) / 2.0f
        val normWidth = normRight - normLeft

        return when {
            // 0: Top Heading / Unwan / Basmalah (top band of page, wide enough to not be margin gloss)
            normTop < 0.16f && normWidth > 0.28f -> 0

            // 4: Bottom Footnotes / Tail Notes (very bottom of page below central frame)
            normTop > 0.86f && normBottom > 0.89f -> 4

            // 1: Central Text Frame (Matan and continuous Syarah inside framing borders)
            !raw.isVertical && !raw.columnIsNarrow && normLeft >= 0.14f && normRight <= 0.86f && normWidth > 0.25f -> 1

            // 2: Right Margin Commentary (in RTL Arabic manuscript reading, right margin comes before left)
            normMidX >= 0.68f || (raw.isVertical && normMidX >= 0.50f) -> 2

            // 3: Left Margin Commentary
            normMidX <= 0.32f || (raw.isVertical && normMidX < 0.50f) -> 3

            // Fallback for wide column in center
            !raw.columnIsNarrow -> 1
            normMidX > 0.50f -> 2
            else -> 3
        }
    }

    /**
     * Splits the page into vertical zones using full-width horizontal projection.
     */
    /**
     * Detects large regions of sparse, evenly-distributed ink that don't
     * match the dense on/off banding pattern of text lines -- the
     * signature of a geometric diagram or illustration (circles, radial
     * construction lines, astronomical figures common in falak/handasah
     * manuscripts) rather than script.
     *
     * Validated against a synthetic test page (text bands + a circle with
     * 8 radial lines): text bands have row-ink-density ~0.8 with sharp
     * on/off transitions between lines, while the diagram region showed
     * ~0.03-0.04 density consistently across ~98% of its rows -- an order
     * of magnitude lower and far more uniform than any text zone. The
     * same thresholds produced zero false positives when run against both
     * real sample manuscript pages (dense text zones sat at 0.23-0.35
     * mean density, well above the illustration threshold).
     */
    /**
     * Detects illustration/diagram regions using two complementary signals:
     *
     * 1. PRIMARY: colored (non-black) ink detected directly from the
     *    original bitmap's own pixel colors. Verified on a real falak
     *    manuscript page with a red-ink epicycle/orbit diagram: rows
     *    inside the diagram had ~5x the "vivid red" pixel fraction of
     *    rows in the surrounding black-ink text (0.05 vs 0.01 at a
     *    redness threshold of 0.25), giving a sharp, reliable boundary
     *    that a density-only check on the text-detection prob map missed
     *    entirely (that prob map read exactly 0 density across large
     *    stretches of the diagram, since the detection model was trained
     *    on black script, not colored diagram lines).
     *
     * 2. FALLBACK: sparse/low, evenly-distributed ink density (from the
     *    text-detection binarized prob map), for diagrams drawn in plain
     *    black ink where the color signal above won't fire. Validated
     *    against a synthetic test page (text bands + a black circle/
     *    radial-line diagram): text bands run ~0.8 row density with sharp
     *    on/off transitions, while the diagram region sat at ~0.03-0.04
     *    density across ~98% of its rows. Both real sample manuscript
     *    pages (dense text 0.23-0.35 mean density) produced zero false
     *    positives against this threshold.
     *
     * Zones from both signals are merged (overlapping/adjacent zones
     * combined) since a real diagram often triggers both -- e.g. red
     * construction lines plus black ink labels/outlines within it.
     */
    private fun detectIllustrationZones(
        bitmap: Bitmap,
        binarized: Array<BooleanArray>,
        mapH: Int,
        mapW: Int,
        minHeightFrac: Float = 0.06f,
        redThreshold: Float = 0.25f,
        redRowFrac: Float = 0.02f,
        maxMeanDensity: Float = 0.15f,
        minSparseRowFrac: Float = 0.60f
    ): List<Pair<Int, Int>> {
        if (mapH == 0 || mapW == 0) return emptyList()
        val minHeight = max(15, (mapH * minHeightFrac).toInt())

        // --- Signal 1: colored ink, sampled directly from the bitmap ---
        val scaleX = bitmap.width.toFloat() / mapW.toFloat()
        val scaleY = bitmap.height.toFloat() / mapH.toFloat()
        val rowRedDensity = FloatArray(mapH)
        val rowPixels = IntArray(mapW)
        for (y in 0 until mapH) {
            val srcY = (y * scaleY).toInt().coerceIn(0, bitmap.height - 1)
            var redCount = 0
            for (x in 0 until mapW) {
                val srcX = (x * scaleX).toInt().coerceIn(0, bitmap.width - 1)
                val p = bitmap.getPixel(srcX, srcY)
                val r = (p shr 16 and 0xFF) / 255f
                val g = (p shr 8 and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val redness = r - (g + b) / 2f
                if (redness > redThreshold) redCount++
            }
            rowRedDensity[y] = redCount.toFloat() / mapW.toFloat()
        }
        val colorZones = extractZonesAboveThreshold(rowRedDensity, redRowFrac, mapH, allowedGapFrac = 0.02f)
            .filter { (zt, zb) -> zb - zt >= minHeight }

        // --- Signal 2: sparse/low black-ink density fallback ---
        val rowDensity = FloatArray(mapH)
        for (y in 0 until mapH) {
            var count = 0
            for (x in 0 until mapW) if (binarized[y][x]) count++
            rowDensity[y] = count.toFloat() / mapW.toFloat()
        }
        val candidateZones = mutableListOf<Pair<Int, Int>>()
        var inZone = false
        var start = 0
        for (y in 0 until mapH) {
            if (rowDensity[y] > 0.005f) {
                if (!inZone) { inZone = true; start = y }
            } else if (inZone) {
                inZone = false
                candidateZones.add(Pair(start, y))
            }
        }
        if (inZone) candidateZones.add(Pair(start, mapH))

        val densityZones = mutableListOf<Pair<Int, Int>>()
        for ((zt, zb) in candidateZones) {
            val h = zb - zt
            if (h < minHeight) continue
            var sum = 0f
            var sparseRows = 0
            for (y in zt until zb) {
                val d = rowDensity[y]
                sum += d
                if (d > 0.005f && d < maxMeanDensity) sparseRows++
            }
            val meanDensity = sum / h
            val sparseFrac = sparseRows.toFloat() / h.toFloat()
            if (meanDensity < maxMeanDensity && sparseFrac > minSparseRowFrac) {
                densityZones.add(Pair(zt, zb))
            }
        }

        // --- Merge overlapping/adjacent zones from both signals ---
        val merged = (colorZones + densityZones).sortedBy { it.first }
        val result = mutableListOf<Pair<Int, Int>>()
        val mergeGap = max(10, (mapH * 0.02f).toInt())
        for (z in merged) {
            val last = result.lastOrNull()
            if (last != null && z.first - last.second <= mergeGap) {
                result[result.size - 1] = Pair(last.first, max(last.second, z.second))
            } else {
                result.add(z)
            }
        }
        return result
    }

    /**
     * Finds contiguous Y-ranges where `values[y] > threshold`, tolerating
     * small gaps (a brief dip below threshold, e.g. a thin white band
     * between two parts of a diagram) up to `allowedGapFrac * mapH` pixels
     * without breaking the zone.
     */
    private fun extractZonesAboveThreshold(
        values: FloatArray,
        threshold: Float,
        mapH: Int,
        allowedGapFrac: Float
    ): List<Pair<Int, Int>> {
        val maxGap = max(4, (mapH * allowedGapFrac).toInt())
        val zones = mutableListOf<Pair<Int, Int>>()
        var inZone = false
        var start = 0
        var gap = 0
        for (y in values.indices) {
            if (values[y] > threshold) {
                if (!inZone) { inZone = true; start = y }
                gap = 0
            } else if (inZone) {
                gap++
                if (gap > maxGap) {
                    inZone = false
                    zones.add(Pair(start, y - gap + 1))
                }
            }
        }
        if (inZone) zones.add(Pair(start, values.size))
        return zones
    }

    private fun detectRowBlocks(binarized: Array<BooleanArray>, mapH: Int, mapW: Int): List<Pair<Int, Int>> {
        val rowDensity = FloatArray(mapH)
        for (y in 0 until mapH) {
            var count = 0
            for (x in 0 until mapW) if (binarized[y][x]) count++
            rowDensity[y] = count.toFloat() / mapW.toFloat()
        }

        val kernelRadius = 2
        val smoothed = FloatArray(mapH)
        for (y in 0 until mapH) {
            var sum = 0f
            var weightSum = 0f
            for (ky in -kernelRadius..kernelRadius) {
                val ny = y + ky
                if (ny in 0 until mapH) {
                    val w = 1.0f / (1.0f + abs(ky))
                    sum += rowDensity[ny] * w
                    weightSum += w
                }
            }
            smoothed[y] = sum / weightSum
        }

        val maxD = smoothed.maxOrNull() ?: 0f
        val threshold = max(0.005f, maxD * 0.05f)
        val minBlockH = max(4, (mapH * 0.015f).toInt())
        val blocks = mutableListOf<Pair<Int, Int>>()
        var inBlock = false
        var blockStart = 0
        for (y in 0 until mapH) {
            if (smoothed[y] >= threshold) {
                if (!inBlock) { inBlock = true; blockStart = y }
            } else if (inBlock) {
                inBlock = false
                if (y - blockStart >= minBlockH) blocks.add(Pair(blockStart, y))
            }
        }
        if (inBlock && (mapH - blockStart) >= minBlockH) blocks.add(Pair(blockStart, mapH))

        // Merge blocks separated by a small gap
        val gapMergePx = max(4, (mapH * 0.022f).toInt())
        val merged = mutableListOf<Pair<Int, Int>>()
        for (b in blocks) {
            val last = merged.lastOrNull()
            if (last != null && b.first - last.second <= gapMergePx) {
                merged[merged.size - 1] = Pair(last.first, b.second)
            } else {
                merged.add(b)
            }
        }
        return merged.ifEmpty { listOf(Pair(0, mapH)) }
    }

    /**
     * Local column projection within a single zone [zoneTop, zoneBottom).
     */
    /**
     * Detects side-by-side columns within a zone (e.g. narrow marginalia
     * strips flanking a wide matan frame) using valley-based splitting on a
     * heavily-smoothed column-density curve.
     *
     * A naive "threshold the density, take runs above it" approach (the
     * previous implementation) fails on real manuscript scans: aged-paper
     * noise and dense marginalia text mean the density between two real
     * columns rarely drops anywhere near zero, while decorative frame
     * borders are themselves *high*-density vertical lines. That caused
     * whole multi-column pages to collapse into a single wide "column"
     * spanning the matan frame and one side's marginalia together.
     *
     * Instead, this smooths away individual glyph texture to expose the
     * macro column structure, then splits only at valleys that are a
     * significant relative dip versus their local surrounding peaks —
     * i.e. genuine gaps/borders between columns, not just inter-word or
     * inter-letter spacing within a single column of text.
     */
    private fun detectColumns(
        binarized: Array<BooleanArray>,
        zoneTop: Int,
        zoneBottom: Int,
        mapW: Int
    ): List<Pair<Int, Int>> {
        val zoneH = zoneBottom - zoneTop
        if (zoneH <= 0) return emptyList()

        // Very short zones (single-line headings, decorative title bands)
        // don't carry enough vertical signal to reliably distinguish a real
        // column boundary from a single ornamental flourish or diacritic
        // gap -- treat them as one column rather than risk over-splitting.
        if (zoneH < 40) return listOf(Pair(0, mapW))

        val colDensity = FloatArray(mapW)
        for (x in 0 until mapW) {
            var count = 0
            for (y in zoneTop until zoneBottom) if (binarized[y][x]) count++
            colDensity[x] = count.toFloat() / zoneH.toFloat()
        }

        // Heavier smoothing than a per-line-band pass: collapses individual
        // glyph strokes and thin border lines into a smooth macro-density
        // curve reflecting overall column structure.
        val kernelRadius = max(6, (mapW * 0.02f).toInt())
        val kernelWeights = FloatArray(2 * kernelRadius + 1)
        var kernelWeightSum = 0f
        for (i in kernelWeights.indices) {
            val k = i - kernelRadius
            kernelWeights[i] = 1.0f / (1.0f + abs(k))
            kernelWeightSum += kernelWeights[i]
        }
        val smoothed = FloatArray(mapW)
        for (x in 0 until mapW) {
            var sum = 0f
            for (i in kernelWeights.indices) {
                val nx = x + (i - kernelRadius)
                if (nx in 0 until mapW) sum += colDensity[nx] * kernelWeights[i]
            }
            smoothed[x] = sum / kernelWeightSum
        }

        val maxCol = smoothed.maxOrNull() ?: 0f
        if (maxCol <= 1e-6f) return listOf(Pair(0, mapW))

        // Any non-trivial ink presence counts as "inside the text area";
        // valley depth (not this threshold) decides real column splits.
        val presenceThreshold = max(0.01f, maxCol * 0.03f)
        val minColWidth = max((mapW * 0.04f).toInt(), 20)

        // Trim empty outer margins.
        var left = 0
        while (left < mapW && smoothed[left] < presenceThreshold) left++
        var right = mapW
        while (right > left && smoothed[right - 1] < presenceThreshold) right--
        if (right <= left) return listOf(Pair(0, mapW))

        val region = smoothed.copyOfRange(left, right)
        val n = region.size

        // Local minima within the trimmed region.
        val valleys = mutableListOf<Int>()
        for (i in 1 until n - 1) {
            if (region[i] <= region[i - 1] && region[i] <= region[i + 1]) {
                if (valleys.isEmpty() || i - valleys.last() > 2) valleys.add(i)
            }
        }

        // Accept a valley as a real column boundary only if it's a
        // significant relative dip versus the local peaks flanking it.
        val splitPoints = mutableListOf(0)
        val span = max(10, minColWidth / 2)
        for (v in valleys) {
            val lo = max(0, v - span)
            val hi = min(n, v + span)
            var leftPeak = region[v]
            for (i in lo..v) if (region[i] > leftPeak) leftPeak = region[i]
            var rightPeak = region[v]
            for (i in v until hi) if (region[i] > rightPeak) rightPeak = region[i]
            val localPeak = min(leftPeak, rightPeak)
            if (localPeak <= 1e-6f) continue
            val contrast = 1.0f - (region[v] / localPeak)
            if (contrast >= 0.35f && localPeak >= presenceThreshold) splitPoints.add(v)
        }
        splitPoints.add(n)
        val uniqueSplits = splitPoints.distinct().sorted()

        val cols = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until uniqueSplits.size - 1) {
            val cs = left + uniqueSplits[i]
            val ce = left + uniqueSplits[i + 1]
            when {
                ce - cs >= minColWidth -> cols.add(Pair(cs, ce))
                cols.isNotEmpty() -> cols[cols.size - 1] = Pair(cols.last().first, ce)
                else -> cols.add(Pair(cs, ce))
            }
        }

        return if (cols.isEmpty()) listOf(Pair(left, right)) else cols
    }

    /**
     * High-Precision Line Detection with Peak-Valley Splitting.
     * Prevents multi-line blocks in marginalia and commentary from merging
     * by detecting local density peaks and partitioning at valley minima.
     */
    /**
     * Splits a column into horizontal line bands using a peak-detection
     * approach on the vertical ink-density curve.
     *
     * Earlier revisions of this function combined a small fixed smoothing
     * radius with an aggressive secondary "valley contrast" and
     * "force-split thick bands" pass. On real manuscript scans this badly
     * over-segmented dense text: diacritics, word gaps, and individual
     * glyph strokes created enough density fluctuation to be mistaken for
     * line boundaries (a single manuscript line could be split into 3-4
     * spurious bands), and the force-split pass then re-fragmented bands
     * that were already correct. It also treated narrow marginalia columns
     * as having a fixed small line height (20px) regardless of the actual
     * page resolution, when in practice marginalia line pitch scales with
     * the zone height the same way body-text line pitch does.
     *
     * This version derives line-height and smoothing scale uniformly from
     * the zone's own height for both narrow and wide columns, uses a wider
     * smoothing kernel proportional to that scale (verified by visual
     * inspection against real manuscript pages to collapse per-glyph
     * texture while preserving real inter-line valleys), and builds one
     * band directly per detected density peak -- splitting boundaries at
     * the true valley minimum between consecutive peaks rather than
     * re-filtering peaks through a second contrast/force-split pass.
     */
    private fun detectLineBandsInColumn(
        binarized: Array<BooleanArray>,
        zoneTop: Int,
        zoneBottom: Int,
        colStart: Int,
        colEnd: Int
    ): List<Pair<Int, Int>> {
        val zoneH = zoneBottom - zoneTop
        val colW = (colEnd - colStart).coerceAtLeast(1)
        if (zoneH <= 0) return emptyList()

        val isNarrowMarginalia = colW < 220 || colStart < 150 || colEnd > 650

        val rowDensity = FloatArray(zoneH)
        for (yi in 0 until zoneH) {
            val y = zoneTop + yi
            var count = 0
            for (x in colStart until colEnd) if (binarized[y][x]) count++
            rowDensity[yi] = count.toFloat() / colW.toFloat()
        }

        // Line-height scale estimate, shared by narrow and wide columns:
        // marginalia line pitch scales with zone height just like body
        // text does, so there's no reason to pin narrow columns to a
        // fixed small constant.
        val maxExpectedLineHeight = max(14, (zoneH * 0.045f).toInt().coerceIn(18, 38))

        // Smoothing kernel wide enough to merge individual glyph strokes
        // and diacritic marks into a single hump per text line.
        val kernelRadius = max(2, (maxExpectedLineHeight * 0.35f).toInt())
        val smoothed = FloatArray(zoneH)
        for (yi in 0 until zoneH) {
            var sum = 0f
            var weightSum = 0f
            for (ky in -kernelRadius..kernelRadius) {
                val nyi = yi + ky
                if (nyi in 0 until zoneH) {
                    val w = 1.0f / (1.0f + abs(ky))
                    sum += rowDensity[nyi] * w
                    weightSum += w
                }
            }
            smoothed[yi] = sum / weightSum
        }

        val peakVal = smoothed.maxOrNull() ?: 0f
        if (peakVal < 0.003f) return emptyList()

        val densityThreshold = if (isNarrowMarginalia) max(peakVal * 0.08f, 0.003f) else max(peakVal * 0.12f, 0.004f)
        val minBandHeight = if (isNarrowMarginalia) 3 else 4

        // Step 1: Initial contiguous bands above base threshold
        val initialBands = mutableListOf<Pair<Int, Int>>()
        var inBand = false
        var bandStart = 0
        for (yi in 0 until zoneH) {
            if (smoothed[yi] >= densityThreshold) {
                if (!inBand) { inBand = true; bandStart = yi }
            } else if (inBand) {
                inBand = false
                if (yi - bandStart >= minBandHeight) {
                    initialBands.add(Pair(bandStart, yi))
                }
            }
        }
        if (inBand && (zoneH - bandStart) >= minBandHeight) {
            initialBands.add(Pair(bandStart, zoneH))
        }

        if (initialBands.isEmpty()) return emptyList()

        // Minimum peak separation: a small distance floor to avoid treating
        // adjacent pixels of the same peak as separate peaks, while still
        // allowing genuinely close/short adjacent lines to be detected.
        val minLinePitch = max(6, (maxExpectedLineHeight * 0.45f).toInt())

        val finalBands = mutableListOf<Pair<Int, Int>>()

        for (band in initialBands) {
            val bStart = band.first
            val bEnd = band.second
            val bHeight = bEnd - bStart

            val rawPeaks = mutableListOf<Int>()
            for (yi in (bStart + 1) until (bEnd - 1)) {
                val v = smoothed[yi]
                if (v >= densityThreshold * 0.90f && v >= smoothed[yi - 1] && v >= smoothed[yi + 1]) {
                    if (rawPeaks.isEmpty() || (yi - rawPeaks.last()) >= minLinePitch) {
                        rawPeaks.add(yi)
                    } else if (v > smoothed[rawPeaks.last()]) {
                        rawPeaks[rawPeaks.size - 1] = yi
                    }
                }
            }

            if (rawPeaks.size <= 1) {
                // No internal structure detected: if the band is still
                // abnormally tall for a single line, divide it evenly by
                // the estimated number of lines it should contain.
                if (bHeight > maxExpectedLineHeight * 1.35f) {
                    val subLineCount = max(2, Math.round(bHeight.toFloat() / maxExpectedLineHeight))
                    val step = bHeight / subLineCount
                    for (s in 0 until subLineCount) {
                        val subStart = bStart + s * step
                        val subEnd = if (s == subLineCount - 1) bEnd else bStart + (s + 1) * step
                        if (subEnd - subStart >= minBandHeight) {
                            finalBands.add(Pair(zoneTop + subStart, zoneTop + subEnd))
                        }
                    }
                } else {
                    finalBands.add(Pair(zoneTop + bStart, zoneTop + bEnd))
                }
            } else {
                // One band per detected peak: split boundaries sit at the
                // true density-valley minimum between each pair of
                // consecutive peaks.
                val boundaries = mutableListOf(bStart)
                for (pIdx in 0 until (rawPeaks.size - 1)) {
                    val p1 = rawPeaks[pIdx]
                    val p2 = rawPeaks[pIdx + 1]
                    var minVal = Float.MAX_VALUE
                    var valleyIdx = (p1 + p2) / 2
                    for (yi in p1..p2) {
                        if (smoothed[yi] < minVal) {
                            minVal = smoothed[yi]
                            valleyIdx = yi
                        }
                    }
                    boundaries.add(valleyIdx)
                }
                boundaries.add(bEnd)

                for (sIdx in 0 until (boundaries.size - 1)) {
                    val sTop = boundaries[sIdx]
                    val sBottom = boundaries[sIdx + 1]
                    if (sBottom - sTop >= minBandHeight) {
                        finalBands.add(Pair(zoneTop + sTop, zoneTop + sBottom))
                    }
                }
            }
        }

        return finalBands
    }

    /**
     * Rotation and Skew Aware Line Segmentation for Marginalia (Hasyiyah & Ta'liqah).
     * Evaluates candidate slant angles across the projection normal axis to detect true diagonal or vertical
     * writing angles in margins (preventing window-grid horizontal slicing).
     */
    private fun detectOrientedLinesInColumn(
        bitmap: Bitmap,
        binarized: Array<BooleanArray>,
        zoneTop: Int,
        zoneBottom: Int,
        colStart: Int,
        colEnd: Int,
        mapW: Int,
        mapH: Int,
        zoneIdx: Int,
        colIdx: Int,
        priority: Int
    ): List<RawLine>? {
        val colW = colEnd - colStart
        val zoneH = zoneBottom - zoneTop
        if (colW <= 6 || zoneH <= 10) return null

        // 1. Gather ink points directly from the ORIGINAL bitmap's
        // grayscale intensity in this region, not from `binarized` (the
        // text-detection model's probability map). Verified on a real
        // manuscript with visibly slanted marginalia text (~-16 degrees):
        // the detection model's prob map was essentially blind to that
        // diagonal text (a handful of scattered activated pixels, not
        // tracing the diagonal at all), because the model was trained to
        // find horizontal-ish text lines, so `binarized` never had enough
        // ink points here for the angle-sweep below to find anything but
        // 0 degrees. Thresholding the raw image's own grayscale directly
        // recovers the diagonal strokes correctly.
        val scaleX = bitmap.width.toFloat() / mapW.toFloat()
        val scaleY = bitmap.height.toFloat() / mapH.toFloat()
        val srcLeft = (colStart * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val srcRight = (colEnd * scaleX).toInt().coerceIn(srcLeft + 1, bitmap.width)
        val srcTop = (zoneTop * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val srcBottom = (zoneBottom * scaleY).toInt().coerceIn(srcTop + 1, bitmap.height)
        val srcW = srcRight - srcLeft
        val srcH = srcBottom - srcTop
        if (srcW <= 0 || srcH <= 0) return null

        val pixels = IntArray(srcW * srcH)
        bitmap.getPixels(pixels, 0, srcW, srcLeft, srcTop, srcW, srcH)

        val grays = FloatArray(srcW * srcH)
        var minGray = Float.MAX_VALUE
        var maxGray = -Float.MAX_VALUE
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            grays[i] = gray
            if (gray < minGray) minGray = gray
            if (gray > maxGray) maxGray = gray
        }
        val range = (maxGray - minGray).coerceAtLeast(0.05f)
        val inkThreshold = 0.35f // fraction of local contrast treated as ink (dark strokes)

        val inkPoints = mutableListOf<Pair<Int, Int>>()
        for (sy in 0 until srcH) {
            for (sx in 0 until srcW) {
                val gray = grays[sy * srcW + sx]
                val ink = 1.0f - ((gray - minGray) / range).coerceIn(0f, 1f)
                if (ink > inkThreshold) {
                    // map back to the mapW/mapH coordinate space used
                    // everywhere else in this file, so downstream band
                    // math (colStart/colEnd/zoneTop/zoneBottom offsets)
                    // still lines up correctly.
                    val mapX = (colStart + sx / scaleX).toInt().coerceIn(colStart, colEnd - 1)
                    val mapY = (zoneTop + sy / scaleY).toInt().coerceIn(zoneTop, zoneBottom - 1)
                    inkPoints.add(Pair(mapX, mapY))
                }
            }
        }

        if (inkPoints.size < 35) return null

        // 2. Candidate slant angles (frequent in classical Arabic marginalia & commentary)
        val candidateAnglesDeg = doubleArrayOf(
            -50.0, -42.0, -35.0, -28.0, -22.0, -16.0, -10.0, -5.0,
            0.0,
            5.0, 10.0, 16.0, 22.0, 28.0, 35.0, 42.0, 50.0,
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

            if (deg == 0.0) {
                zeroDegVariance = variance
            }
            if (variance > bestVariance) {
                bestVariance = variance
                bestAngleDeg = deg
            }
        }

        val isSignificantSlant = abs(bestAngleDeg) >= 5.0 && bestVariance > (zeroDegVariance * 1.08)
        val isVertical = abs(bestAngleDeg - 90.0) < 1.0 && bestVariance > (zeroDegVariance * 1.08)

        if (!isSignificantSlant && !isVertical) {
            return null // Fallback to horizontal bands
        }

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
        if (maxH < 2.0f) return null

        val thresh = max(1.2f, maxH * 0.12f)
        val minBandBins = max(2, (7.0 / binSize).toInt())

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

        if (bands.isEmpty()) return null

        val subBands = mutableListOf<Pair<Double, Double>>()
        bands.forEach { (vStart, vEnd) ->
            val vHeight = vEnd - vStart
            if (vHeight > 36.0) {
                val count = max(2, (vHeight / 24.0 + 0.5).toInt())
                val step = vHeight / count
                for (s in 0 until count) {
                    val sStart = vStart + s * step
                    val sEnd = if (s == count - 1) vEnd else vStart + (s + 1) * step
                    subBands.add(Pair(sStart, sEnd))
                }
            } else {
                subBands.add(Pair(vStart, vEnd))
            }
        }

        val result = mutableListOf<RawLine>()
        subBands.forEach { (vStart, vEnd) ->
            val bandPts = inkPoints.filter { (x, y) ->
                val v = -x * sinA + y * cosA
                v in vStart..vEnd
            }

            if (bandPts.size >= 20) {
                val uVals = bandPts.map { (x, y) -> x * cosA + y * sinA }
                val sortedU = uVals.sorted()
                val uMin = sortedU[(sortedU.size * 0.03).toInt()]
                val uMax = sortedU[(sortedU.size * 0.97).toInt().coerceAtMost(sortedU.size - 1)]
                val uLen = uMax - uMin
                val vHeight = vEnd - vStart

                if (uLen >= 18.0 && vHeight >= 4.0) {
                    val padU = uLen * 0.04
                    val padV = vHeight * 0.15
                    val u0 = uMin - padU
                    val u1 = uMax + padU
                    val v0 = vStart - padV
                    val v1 = vEnd + padV

                    fun toX(u: Double, v: Double) = ((u * cosA - v * sinA) / mapW.toDouble()).toFloat().coerceIn(0f, 1f)
                    fun toY(u: Double, v: Double) = ((u * sinA + v * cosA) / mapH.toDouble()).toFloat().coerceIn(0f, 1f)

                    val p1 = Pair(toX(u0, v0), toY(u0, v0))
                    val p2 = Pair(toX(u1, v0), toY(u1, v0))
                    val p3 = Pair(toX(u1, v1), toY(u1, v1))
                    val p4 = Pair(toX(u0, v1), toY(u0, v1))

                    val vBase = v0 + (v1 - v0) * 0.75
                    val uMid = (u0 + u1) / 2.0
                    val b1 = Pair(toX(u0, vBase), toY(u0, vBase))
                    val b2 = Pair(toX(uMid, vBase), toY(uMid, vBase))
                    val b3 = Pair(toX(u1, vBase), toY(u1, vBase))

                    val allNormX = listOf(p1.first, p2.first, p3.first, p4.first)
                    val allNormY = listOf(p1.second, p2.second, p3.second, p4.second)
                    val minNormX = (allNormX.minOrNull() ?: 0f) * mapW
                    val maxNormX = (allNormX.maxOrNull() ?: 1f) * mapW
                    val minNormY = (allNormY.minOrNull() ?: 0f) * mapH
                    val maxNormY = (allNormY.maxOrNull() ?: 1f) * mapH

                    result.add(
                        RawLine(
                            topY = minNormY.toInt(),
                            bottomY = maxNormY.toInt(),
                            leftX = minNormX.toInt(),
                            rightX = maxNormX.toInt(),
                            activePixelCount = bandPts.size,
                            zoneIndex = zoneIdx,
                            zoneTopY = zoneTop,
                            columnIndex = colIdx,
                            columnIsNarrow = true,
                            columnPriority = priority,
                            isVertical = isVertical,
                            skewAngleDeg = bestAngleDeg.toFloat(),
                            customPolygonNorm = listOf(p1, p2, p3, p4),
                            customBaselineNorm = listOf(b1, b2, b3)
                        )
                    )
                }
            }
        }

        return result.ifEmpty { null }
    }

    private fun computeSimulatedProbMap(bitmap: Bitmap, config: PpOcrV5SegmentationEngine.DetConfig): Array<FloatArray> {
        val w = (bitmap.width / 2).coerceAtLeast(32)
        val h = (bitmap.height / 2).coerceAtLeast(32)
        val probMap = Array(h) { FloatArray(w) }

        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                val inkProb = (1.0f - lum).coerceIn(0f, 1f)
                probMap[y][x] = if (inkProb > 0.22f) inkProb else 0.03f
            }
        }
        return probMap
    }
}
