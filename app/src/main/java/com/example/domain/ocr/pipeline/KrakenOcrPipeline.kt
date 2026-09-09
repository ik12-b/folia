package com.example.domain.ocr.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Concrete implementation of OcrPipeline coordinating full end-to-end on-device OCR/HTR inference.
 */
class KrakenOcrPipeline : OcrPipeline {

    companion object {
        private const val TAG = "KrakenOcrPipeline"
    }

    override suspend fun run(
        context: Context,
        bitmap: Bitmap,
        partId: Long,
        layerId: Long?,
        config: OcrPipeline.PipelineConfig,
        imageResName: String?,
        onProgress: (stage: String, progress: Float) -> Unit
    ): OcrPipeline.PipelineResult {
        // imageResName is threaded through to RecognitionStage below so that,
        // if ONNX recognition fails and the pipeline falls back to bundled
        // sample transcriptions, the correct sample (p1 vs p2) is chosen
        // instead of always defaulting to page 1.
        val startTotal = System.currentTimeMillis()
        val stageTimes = mutableMapOf<String, Long>()

        // 1. Preprocessing Stage
        onProgress("Pra-pemrosesan citra & scaling (32-stride)...", 0.15f)
        val t0 = System.currentTimeMillis()
        val preprocessed = PreprocessStage.process(bitmap, config)
        stageTimes["Preprocess"] = System.currentTimeMillis() - t0

        // 2. Segmentation Stage (PP-OCRv5 Det ONNX)
        onProgress("Deteksi Garis & Baseline (PP-OCRv5 DBNet ONNX)...", 0.35f)
        val t1 = System.currentTimeMillis()
        val segOutput = SegmentationStage.segment(context, preprocessed.processedBitmap, config)
        stageTimes["Segmentation"] = System.currentTimeMillis() - t1

        // 3. Line Extraction Stage
        onProgress("Pemotongan Polygon Garis & Baseline Dewarping...", 0.55f)
        val t2 = System.currentTimeMillis()
        val linesWithBitmaps = LineExtractor.extractLines(bitmap, segOutput.lines)
        stageTimes["LineExtraction"] = System.currentTimeMillis() - t2

        // 4. Recognition Stage (Muharaf Kraken HTR ONNX)
        val t3 = System.currentTimeMillis()
        val recOutput = if (config.runRecognition) {
            onProgress("Pengenalan Teks Manuskrip (Muharaf CTC ONNX)...", 0.75f)
            RecognitionStage.recognize(
                context = context,
                lines = linesWithBitmaps,
                imageResName = imageResName
            ) { current, total ->
                val ratio = current.toFloat() / total.toFloat()
                onProgress("Mengenali baris $current / $total...", 0.60f + ratio * 0.25f)
            }
        } else {
            RecognitionStage.RecognitionOutput(linesWithBitmaps, false)
        }
        stageTimes["Recognition"] = System.currentTimeMillis() - t3

        // 5. Post-Processing Stage
        onProgress("Penyusunan urutan baca filologi & normalisasi rasm...", 0.90f)
        val t4 = System.currentTimeMillis()
        val postOutput = PostProcessStage.process(
            lines = recOutput.transcribedLines,
            partId = partId,
            layerId = layerId,
            config = config
        )
        stageTimes["PostProcess"] = System.currentTimeMillis() - t4

        val totalTime = System.currentTimeMillis() - startTotal
        onProgress("Pipeline selesai!", 1.0f)

        Log.i(TAG, "Kraken pipeline executed in ${totalTime}ms for ${postOutput.lines.size} lines. Stages: $stageTimes")

        return OcrPipeline.PipelineResult(
            totalLines = postOutput.lines.size,
            blocks = postOutput.blocks,
            lines = postOutput.lines,
            transcriptions = postOutput.transcriptions,
            executionTimeMs = totalTime,
            stageTimesMs = stageTimes,
            isFullOnnx = segOutput.isFromOnnx || recOutput.isFromOnnx
        )
    }
}
