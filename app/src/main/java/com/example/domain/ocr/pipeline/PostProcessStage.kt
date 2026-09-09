package com.example.domain.ocr.pipeline

import com.example.data.model.BlockRegionEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity
import com.example.domain.NormalizationHelper

/**
 * Stage 5: Post-Processing & Entity Generation.
 * Sorts lines in RTL scholarly reading order, normalizes Arabic rasm/tashkeel, and constructs Room entities.
 */
object PostProcessStage {

    data class PostProcessOutput(
        val blocks: List<BlockRegionEntity>,
        val lines: List<LineSegmentEntity>,
        val transcriptions: List<LineTranscriptionEntity>
    )

    fun process(
        lines: List<OcrPipeline.BaselineLine>,
        partId: Long,
        layerId: Long?,
        config: OcrPipeline.PipelineConfig
    ): PostProcessOutput {
        // 1. Sort lines using the column-aware reading order already computed
        // in SegmentationStage: zone/column grouping is preserved via
        // orderIndex (assigned there), not re-derived from raw Y position —
        // sorting by Y alone would interleave unrelated columns (e.g. matan
        // and marginalia rows that happen to sit at similar heights).
        val sortedLines = lines.sortedWith(
            compareBy<OcrPipeline.BaselineLine> {
                if (it.typology == "Heading") 0 else 1
            }.thenBy { it.orderIndex }
        )

        // 2. Generate Unique Blocks for Distinct Typologies
        val typologyGroups = sortedLines.groupBy { it.typology }
        val generatedBlocks = mutableListOf<BlockRegionEntity>()

        typologyGroups.keys.forEachIndexed { index, typo ->
            val colorHex = when (typo) {
                "Heading" -> "#E11D48"
                "Matan" -> "#B91C1C"
                "Syarah" -> "#D97706"
                "Marginalia" -> "#7C3AED"
                "Footnote" -> "#0D9488"
                "Ornament" -> "#6B7280"
                "Illustration" -> "#059669"
                else -> "#2563EB"
            }
            val label = when (typo) {
                "Heading" -> "العنوان والترويسة (Heading)"
                "Matan" -> "نص المتن الأساسي (Matan)"
                "Syarah" -> "شرح الألفاظ (Syarah)"
                "Marginalia" -> "حاشية وهوامش (Marginalia)"
                "Ornament" -> "زخارف وفواصل (Ornament)"
                "Illustration" -> "رسوم وأشكال هندسية (Illustration)"
                else -> "نص عام (General Text)"
            }

            // Envelope polygon for the block
            val linesInGroup = typologyGroups[typo] ?: emptyList()
            val minX = linesInGroup.flatMap { it.polygon }.minOfOrNull { it.first } ?: 0.20f
            val maxX = linesInGroup.flatMap { it.polygon }.maxOfOrNull { it.first } ?: 0.80f
            val minY = linesInGroup.flatMap { it.polygon }.minOfOrNull { it.second } ?: 0.10f
            val maxY = linesInGroup.flatMap { it.polygon }.maxOfOrNull { it.second } ?: 0.90f

            val blockPolygon = "%.2f,%.2f;%.2f,%.2f;%.2f,%.2f;%.2f,%.2f".format(
                minX, minY,
                maxX, minY,
                maxX, maxY,
                minX, maxY
            )

            generatedBlocks.add(
                BlockRegionEntity(
                    id = (index + 1).toLong(),
                    partId = partId,
                    typology = typo,
                    label = label,
                    colorHex = colorHex,
                    polygonPointsJson = blockPolygon,
                    readingOrder = index + 1
                )
            )
        }

        // 3. Generate LineSegment Entities
        val generatedLines = sortedLines.mapIndexed { index, line ->
            val baselineJson = line.baseline.joinToString(";") { "%.4f,%.4f".format(it.first, it.second) }
            val polygonJson = line.polygon.joinToString(";") { "%.4f,%.4f".format(it.first, it.second) }

            LineSegmentEntity(
                id = (index + 1).toLong(),
                partId = partId,
                blockId = null, // Will be mapped by repository
                orderIndex = index + 1,
                baselinePointsJson = baselineJson,
                maskPolygonJson = polygonJson,
                typology = line.typology
            )
        }

        // 4. Generate LineTranscription Entities
        val targetLayerId = layerId ?: 1L
        val generatedTranscriptions = sortedLines.mapIndexed { index, line ->
            val rawText = line.transcribedText
            val healedSpacing = NormalizationHelper.fixFragmentedArabicSpacing(rawText)
            val finalText = if (config.normalizeArabic) {
                NormalizationHelper.normalizeArabic(healedSpacing)
            } else {
                healedSpacing
            }

            LineTranscriptionEntity(
                id = (index + 1).toLong(),
                lineId = (index + 1).toLong(),
                layerId = targetLayerId,
                text = finalText,
                avgConfidence = line.recConfidence.coerceIn(0.70f, 0.99f),
                isVerified = false
            )
        }

        return PostProcessOutput(generatedBlocks, generatedLines, generatedTranscriptions)
    }
}
