package com.example.domain.ocr.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.example.data.model.BlockRegionEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity

/**
 * Kraken-Style Unified OCR & HTR Modular Pipeline for Historical Manuscripts.
 *
 * Architecture:
 * 1. Preprocess Stage (Contrast / Adaptive Binarization / Scaling)
 * 2. Segmentation Stage (PP-OCRv5 Mobile Det DBNet -> Baselines + Polygons + Typology)
 * 3. Line Extraction Stage (Dewarping & Slicing Along Baselines)
 * 4. Recognition Stage (Muharaf Kraken ONNX HTR -> CTC Greedy Decoding)
 * 5. Post-Process Stage (RTL Ordering + Arabic Normalization + Scholarly Entity Mapping)
 */
interface OcrPipeline {

    data class PipelineConfig(
        val doBinarize: Boolean = false,
        // The bundled ppocrv5_det_int8.onnx model was verified (by running
        // real ONNX inference on sample manuscript pages) to produce a
        // near-empty probability map when the long side is resized to
        // 960px -- the model's activation collapses to <0.2% of pixels
        // regardless of how much text is actually on the page. Sweeping
        // resizeLong showed a stable, correctly-shaped detection response
        // (~20% ink-pixel ratio, matching real text density, with bands
        // that line up with true text lines) in the 256-416px range, with
        // 384 as a good default. Values at/above ~448px again collapse to
        // near-empty output. This is a property of this specific
        // quantized model's expected input scale, not a general DBNet
        // requirement -- a different detection model may need a different
        // value.
        val resizeLong: Int = 384,
        val thresh: Float = 0.30f,
        val boxThresh: Float = 0.60f,
        val unclipRatio: Float = 1.50f,
        val runRecognition: Boolean = true,
        val detectMarginalia: Boolean = true,
        val autoTypology: Boolean = true,
        val normalizeArabic: Boolean = false
    )

    data class BaselineLine(
        val orderIndex: Int,
        val baseline: List<Pair<Float, Float>>, // Normalized (X, Y)
        val polygon: List<Pair<Float, Float>>,  // Normalized (X, Y) bounding polygon
        val typology: String,                   // Heading | Matan | Syarah | Marginalia
        val confidence: Float,
        val lineBitmap: Bitmap? = null,
        val transcribedText: String = "",
        val recConfidence: Float = 0f,
        // Which detected column/region this line belongs to (0 = leftmost region
        // in reading order). Used to keep multi-column manuscript layouts
        // (marginalia | matan frame | marginalia) from being interleaved when
        // sorted purely by vertical position.
        val columnIndex: Int = 0,
        // Reading priority within a page: lower runs first. Distinct from
        // typology so the same typology label can still be ordered correctly
        // across columns (e.g. "Marginalia" left vs right).
        val readingPriority: Int = 0
    )

    data class PipelineResult(
        val totalLines: Int,
        val blocks: List<BlockRegionEntity>,
        val lines: List<LineSegmentEntity>,
        val transcriptions: List<LineTranscriptionEntity>,
        val executionTimeMs: Long,
        val stageTimesMs: Map<String, Long>,
        val isFullOnnx: Boolean
    )

    suspend fun run(
        context: Context,
        bitmap: Bitmap,
        partId: Long,
        layerId: Long?,
        config: PipelineConfig = PipelineConfig(),
        imageResName: String? = null,
        onProgress: (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): PipelineResult
}
