package com.example.data.repository

import android.content.Context
import com.example.data.dao.FoliaDao
import com.example.data.model.BlockRegionEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentMetadataEntity
import com.example.data.model.DocumentPartEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity
import com.example.data.model.OcrModelEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.TranscriptionLayerEntity
import com.example.domain.ScholarlyExportService
import com.example.domain.ocr.PpOcrV5SegmentationEngine
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

class FoliaRepository(
    private val context: Context,
    private val dao: FoliaDao
) {

    val allProjects: Flow<List<ProjectEntity>> = dao.getAllProjects()
    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()
    val allModels: Flow<List<OcrModelEntity>> = dao.getAllModels()

    /**
     * Imports a user-picked .onnx file, registers it as a new OcrModelEntity
     * row (not yet active), and returns the created row's id — or null if the
     * import failed (bad/corrupt file, unsupported type, etc).
     *
     * `type` must be either "Segmentation" (line detection) or
     * "Transcription CTC" (HTR/recognition) — this determines which runner
     * (OnnxDetRunner vs OnnxRecRunner) will later load the file.
     */
    suspend fun importOcrModel(
        inputStream: java.io.InputStream,
        displayName: String,
        fileName: String,
        type: String
    ): Long? {
        val importedPath = when (type) {
            "Segmentation" -> com.example.domain.ocr.OnnxDetRunner.importCustomModel(context, inputStream, fileName)
            else -> com.example.domain.ocr.OnnxRecRunner.importCustomModel(context, inputStream, fileName)
        } ?: return null

        val fileSizeMb = try {
            java.io.File(importedPath).length() / (1024f * 1024f)
        } catch (e: Exception) {
            0f
        }

        return dao.insertModel(
            OcrModelEntity(
                name = displayName.ifBlank { fileName },
                type = type,
                architecture = "Custom Imported ONNX",
                version = "custom",
                fileSizeMb = fileSizeMb,
                isInstalled = true,
                huggingFaceRepo = "Diimpor dari perangkat",
                isDefault = false,
                filePath = importedPath,
                isActive = false
            )
        )
    }

    /**
     * Activates the given model: marks it isActive in the DB (deactivating
     * any other model of the same type), and hot-swaps the corresponding
     * ONNX runner to load that file immediately. Returns true only if BOTH
     * the DB update and the runner reload succeeded — on runner failure the
     * DB is rolled back so the UI never shows an "active" model that isn't
     * actually loaded.
     */
    suspend fun setActiveModel(model: OcrModelEntity): Boolean {
        // Legacy/seed rows (bundled in assets) have no filePath — activating
        // them just means clearing any user override so the runner falls
        // back to its normal asset-discovery behavior.
        val pathToLoad = model.filePath.ifBlank { null }

        val runnerOk = when (model.type) {
            "Segmentation" -> com.example.domain.ocr.OnnxDetRunner.setActiveModelPath(context, pathToLoad)
            else -> com.example.domain.ocr.OnnxRecRunner.setActiveModelPath(context, pathToLoad)
        }
        if (!runnerOk) return false

        dao.deactivateAllModelsOfType(model.type)
        dao.activateModel(model.id)
        return true
    }

    suspend fun getAllDocumentsList(): List<DocumentEntity> = dao.getAllDocumentsList()

    suspend fun ensureInitialized() {
        val docs = dao.getAllDocumentsList()
        if (docs.isEmpty()) {
            com.example.data.db.FoliaDatabase.populateInitialKitabs(dao)
        }
    }

    fun observeDocument(documentId: Long): Flow<DocumentEntity?> =
        dao.observeDocumentById(documentId)

    fun observeMetadata(documentId: Long): Flow<DocumentMetadataEntity?> =
        dao.observeMetadata(documentId)

    fun observeParts(documentId: Long): Flow<List<DocumentPartEntity>> =
        dao.getPartsForDocument(documentId)

    fun observeBlocks(partId: Long): Flow<List<BlockRegionEntity>> =
        dao.getBlocksForPart(partId)

    fun observeLines(partId: Long): Flow<List<LineSegmentEntity>> =
        dao.getLinesForPart(partId)

    fun observeLinesForDocument(documentId: Long): Flow<List<LineSegmentEntity>> =
        dao.getLinesForDocument(documentId)

    fun observeLayers(documentId: Long): Flow<List<TranscriptionLayerEntity>> =
        dao.getLayersForDocument(documentId)

    fun observeTranscriptions(layerId: Long): Flow<List<LineTranscriptionEntity>> =
        dao.getTranscriptionsForLayer(layerId)

    fun observeTranscriptionsForLines(lineIds: List<Long>): Flow<List<LineTranscriptionEntity>> =
        dao.getTranscriptionsForLines(lineIds)

    suspend fun createLayer(documentId: Long, name: String, copyFromLayerId: Long? = null): Long {
        val newLayerId = dao.insertLayer(
            TranscriptionLayerEntity(
                documentId = documentId,
                name = name,
                isDefault = false,
                isGroundTruth = name.contains("Ground Truth", ignoreCase = true)
            )
        )

        // If copying from an existing layer, duplicate its transcriptions
        if (copyFromLayerId != null) {
            val sourceTrans = dao.getTranscriptionsForLayerList(copyFromLayerId)
            val copiedTrans = sourceTrans.map {
                it.copy(id = 0, layerId = newLayerId, updatedAt = System.currentTimeMillis())
            }
            dao.insertOrUpdateTranscriptions(copiedTrans)
        }

        return newLayerId
    }

    suspend fun deleteLayer(layerId: Long) {
        dao.deleteTranscriptionsForLayer(layerId)
        dao.deleteLayer(layerId)
    }

    suspend fun createDocument(
        title: String,
        script: String = "Arabic",
        direction: String = "RTL",
        sampleType: String = "empty" // "empty", "demo_fath", "demo_safinah"
    ): Long {
        val docId = dao.insertDocument(
            DocumentEntity(
                title = title,
                mainScript = script,
                readDirection = direction
            )
        )

        // Metadata
        dao.insertOrUpdateMetadata(
            DocumentMetadataEntity(
                documentId = docId,
                author = "غير معروف (Unknown)",
                language = script
            )
        )

        // Create 3 standard scholarly layers
        val rawLayerId = dao.insertLayer(
            TranscriptionLayerEntity(
                documentId = docId,
                name = "Raw HTR (Draft)",
                isDefault = true,
                isGroundTruth = false
            )
        )
        val diploLayerId = dao.insertLayer(
            TranscriptionLayerEntity(
                documentId = docId,
                name = "Diplomatic (Diplomatik)",
                isDefault = false,
                isGroundTruth = true
            )
        )
        val normLayerId = dao.insertLayer(
            TranscriptionLayerEntity(
                documentId = docId,
                name = "Normalized (Edisi Kritis)",
                isDefault = false,
                isGroundTruth = false
            )
        )

        // Initial Part
        val partId = dao.insertPart(
            DocumentPartEntity(
                documentId = docId,
                pageNumber = 1,
                imageResName = if (sampleType == "demo_safinah") "manuscript_p2" else "manuscript_p1",
                workflowState = "new"
            )
        )

        return docId
    }

    suspend fun addPageToDocument(documentId: Long, imageUri: String? = null, insertAfterIndex: Int? = null): Long {
        val existing = dao.getPartsForDocumentList(documentId)
        val nextNumber = if (insertAfterIndex != null) insertAfterIndex + 2 else existing.size + 1
        
        // If inserting in the middle, increment page numbers of subsequent pages
        if (insertAfterIndex != null && insertAfterIndex < existing.size) {
            for (i in (existing.size - 1) downTo (insertAfterIndex + 1)) {
                val part = existing[i]
                dao.updatePart(part.copy(pageNumber = part.pageNumber + 1))
            }
        }

        val partId = dao.insertPart(
            DocumentPartEntity(
                documentId = documentId,
                pageNumber = nextNumber,
                imageResName = if (nextNumber % 2 == 0) "manuscript_p2" else "manuscript_p1",
                imageUri = imageUri,
                workflowState = "created"
            )
        )
        return partId
    }

    suspend fun deletePage(partId: Long, documentId: Long): Boolean {
        val existing = dao.getPartsForDocumentList(documentId)
        if (existing.size <= 1) {
            // Cannot delete the only remaining page
            return false
        }

        dao.deleteTranscriptionsForPart(partId)
        dao.deleteLinesForPart(partId)
        dao.deleteBlocksForPart(partId)
        dao.deletePart(partId)

        // Renumber remaining pages
        val remaining = dao.getPartsForDocumentList(documentId)
        for ((idx, part) in remaining.withIndex()) {
            val correctNumber = idx + 1
            if (part.pageNumber != correctNumber) {
                dao.updatePart(part.copy(pageNumber = correctNumber))
            }
        }
        return true
    }

    suspend fun saveTranscriptionText(lineId: Long, layerId: Long, newText: String, confidence: Float = 1.0f) {
        val existing = dao.getTranscriptionForLineAndLayer(lineId, layerId)
        if (existing != null) {
            dao.insertOrUpdateTranscription(
                existing.copy(
                    text = newText,
                    avgConfidence = confidence,
                    isVerified = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            dao.insertOrUpdateTranscription(
                LineTranscriptionEntity(
                    lineId = lineId,
                    layerId = layerId,
                    text = newText,
                    avgConfidence = confidence,
                    isVerified = true
                )
            )
        }
    }

    private val ocrPipeline: com.example.domain.ocr.pipeline.OcrPipeline = com.example.domain.ocr.pipeline.KrakenOcrPipeline()

    suspend fun runAutoSegmentation(
        partId: Long,
        config: com.example.domain.ocr.PpOcrV5SegmentationEngine.DetConfig = com.example.domain.ocr.PpOcrV5SegmentationEngine.DetConfig(),
        onProgress: (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): Int {
        val part = dao.getPartById(partId) ?: return 0
        val bitmap = com.example.domain.ocr.PpOcrV5SegmentationEngine.loadManuscriptBitmap(context, part.imageResName, part.imageUri) ?: return 0

        val pipelineConfig = com.example.domain.ocr.pipeline.OcrPipeline.PipelineConfig(
            thresh = config.thresh,
            boxThresh = config.boxThresh,
            unclipRatio = config.unclipRatio,
            resizeLong = config.resizeLong,
            runRecognition = true,
            detectMarginalia = config.detectMarginalia,
            autoTypology = config.autoTypology
        )

        val defaultLayerId = dao.getLayersForDocumentList(part.documentId).firstOrNull()?.id

        val result = ocrPipeline.run(
            context = context,
            bitmap = bitmap,
            partId = partId,
            layerId = defaultLayerId,
            config = pipelineConfig,
            imageResName = part.imageResName,
            onProgress = onProgress
        )

        // Clear existing generated blocks & lines to prevent duplication
        dao.deleteTranscriptionsForPart(partId)
        dao.deleteLinesForPart(partId)
        dao.deleteBlocksForPart(partId)

        // Insert detected Block Regions
        val blockIdMap = mutableMapOf<String, Long>()
        result.blocks.forEach { block ->
            val insertedBlockId = dao.insertBlock(
                block.copy(id = 0, partId = partId)
            )
            blockIdMap[block.typology] = insertedBlockId
        }

        // Insert detected Line Segments with baselines & masks
        val linesToInsert = result.lines.map { line ->
            line.copy(
                id = 0,
                partId = partId,
                blockId = blockIdMap[line.typology]
            )
        }
        dao.insertLines(linesToInsert)

        // Seed transcriptions across available layers for newly detected lines
        val insertedLines = dao.getLinesForPartList(partId)
        val allLayers = dao.getLayersForDocumentList(part.documentId)

        if (linesToInsert.isEmpty()) {
            android.util.Log.e("FoliaRepository", "Gagal segmentasi: Model ONNX tidak menemukan baris teks pada partId=$partId")
        }

        if (allLayers.isNotEmpty() && insertedLines.isNotEmpty()) {
            for (layer in allLayers) {
                val transcriptions = insertedLines.mapIndexed { idx, line ->
                    val recTrans = result.transcriptions.getOrNull(idx)
                    val baseText = if (recTrans != null && recTrans.text.isNotBlank()) {
                        recTrans.text
                    } else {
                        ""
                    }

                    val fixedBaseText = com.example.domain.NormalizationHelper.fixFragmentedArabicSpacing(baseText)
                    val finalTx = if (layer.name.contains("Normalized", ignoreCase = true)) {
                        com.example.domain.NormalizationHelper.normalizeArabic(fixedBaseText)
                    } else {
                        fixedBaseText
                    }

                    val conf = if (recTrans != null && recTrans.avgConfidence > 0f) {
                        recTrans.avgConfidence
                    } else {
                        0.0f
                    }

                    LineTranscriptionEntity(
                        lineId = line.id,
                        layerId = layer.id,
                        text = finalTx,
                        avgConfidence = if (layer.isGroundTruth) 1.0f else conf,
                        isVerified = layer.isGroundTruth
                    )
                }
                dao.insertOrUpdateTranscriptions(transcriptions)
            }
        }

        dao.updatePart(part.copy(workflowState = "segmented"))
        return linesToInsert.size
    }

    suspend fun runAutoTranscription(
        partId: Long,
        layerId: Long,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        val part = dao.getPartById(partId) ?: return 0
        val lines = dao.getLinesForPartList(partId)
        if (lines.isEmpty()) {
            android.util.Log.e("FoliaRepository", "Gagal transkripsi: Tidak ada baris teks tersegmentasi pada partId=$partId")
            return 0
        }

        val bitmap = com.example.domain.ocr.PpOcrV5SegmentationEngine.loadManuscriptBitmap(context, part.imageResName, part.imageUri)
        var count = 0

        // Parse polygon points from line entities
        val baselineLines = lines.map { line ->
            val polyPoints = parsePointsFromJson(line.maskPolygonJson ?: line.baselinePointsJson)
            val basePoints = parsePointsFromJson(line.baselinePointsJson)
            com.example.domain.ocr.pipeline.OcrPipeline.BaselineLine(
                orderIndex = line.orderIndex,
                baseline = basePoints,
                polygon = if (polyPoints.isNotEmpty()) polyPoints else basePoints,
                typology = line.typology,
                confidence = 0.95f
            )
        }

        val linesWithCrops = if (bitmap != null) {
            com.example.domain.ocr.pipeline.LineExtractor.extractLines(bitmap, baselineLines)
        } else {
            baselineLines
        }

        // Run Muharaf ONNX Recognition
        val recOutput = com.example.domain.ocr.pipeline.RecognitionStage.recognize(
            context = context,
            lines = linesWithCrops,
            imageResName = part.imageResName,
            onProgress = onProgress
        )

        for ((index, line) in lines.withIndex()) {
            val recognized = recOutput.transcribedLines.getOrNull(index)
            val rawText = recognized?.transcribedText ?: ""
            val text = com.example.domain.NormalizationHelper.fixFragmentedArabicSpacing(rawText)
            val conf = recognized?.recConfidence ?: 0.0f

            dao.insertOrUpdateTranscription(
                LineTranscriptionEntity(
                    lineId = line.id,
                    layerId = layerId,
                    text = text,
                    avgConfidence = conf,
                    isVerified = false
                )
            )
            count++
        }

        dao.updatePart(part.copy(workflowState = "transcribed"))
        return count
    }

    private fun parsePointsFromJson(json: String?): List<Pair<Float, Float>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            json.split(";").mapNotNull { token ->
                val parts = token.trim().split(",")
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

    suspend fun addBlockRegion(partId: Long, typology: String, label: String, colorHex: String, polygonJson: String): Long {
        return dao.insertBlock(
            BlockRegionEntity(
                partId = partId,
                typology = typology,
                label = label,
                colorHex = colorHex,
                polygonPointsJson = polygonJson
            )
        )
    }

    suspend fun addLineSegment(partId: Long, blockId: Long?, baselineJson: String, typology: String = "Matan"): Long {
        val existing = dao.getLinesForPartList(partId)
        return dao.insertLine(
            LineSegmentEntity(
                partId = partId,
                blockId = blockId,
                orderIndex = existing.size + 1,
                baselinePointsJson = baselineJson,
                typology = typology
            )
        )
    }

    suspend fun updateLine(line: LineSegmentEntity) {
        dao.updateLine(line)
    }

    suspend fun updateLineBoundaries(lineId: Long, baselineJson: String, maskPolygonJson: String, typology: String? = null) {
        dao.updateLineBoundaries(lineId, baselineJson, maskPolygonJson, typology)
    }

    suspend fun getLineById(lineId: Long): LineSegmentEntity? {
        return dao.getLineById(lineId)
    }

    suspend fun deleteLine(lineId: Long) {
        dao.deleteLine(lineId)
    }

    suspend fun deleteBlock(blockId: Long) {
        dao.deleteBlock(blockId)
    }

    suspend fun updateMetadata(meta: DocumentMetadataEntity) {
        dao.insertOrUpdateMetadata(meta)
    }

    suspend fun prepareExportData(documentId: Long, layerId: Long): List<ScholarlyExportService.PageExportData> {
        val parts = dao.getPartsForDocumentList(documentId)
        val result = mutableListOf<ScholarlyExportService.PageExportData>()

        for (part in parts) {
            val blocks = dao.getBlocksForPartList(part.id)
            val lines = dao.getLinesForPartList(part.id)
            val linesWithTrans = lines.map { line ->
                val tr = dao.getTranscriptionForLineAndLayer(line.id, layerId)
                Pair(line, tr)
            }
            result.add(
                ScholarlyExportService.PageExportData(
                    part = part,
                    blocks = blocks,
                    linesWithTranscriptions = linesWithTrans
                )
            )
        }
        return result
    }

    suspend fun deleteDocument(documentId: Long) {
        dao.deleteDocument(documentId)
    }

    /**
     * Reorders existing line segments according to classical manuscript reading hierarchy:
     * 1. Heading / Basmalah
     * 2. Central Frame - Matan & Syarah (strictly continuous top-to-bottom)
     * 3. Right Margin (Hasyiyah Kanan)
     * 4. Left Margin (Hasyiyah Kiri)
     * 5. Bottom Footnotes (Ta'liqah Bawah)
     */
    suspend fun reorderLinesScholarly(partId: Long): Int {
        val lines = dao.getLinesForPartList(partId)
        if (lines.isEmpty()) return 0

        data class LineBounds(
            val line: LineSegmentEntity,
            val minX: Float,
            val maxX: Float,
            val minY: Float,
            val maxY: Float
        )

        val parsed = lines.map { line ->
            val polyPts = try {
                line.maskPolygonJson.split(";").mapNotNull {
                    val p = it.split(",")
                    if (p.size == 2) Pair(p[0].trim().toFloat(), p[1].trim().toFloat()) else null
                }
            } catch (e: Exception) {
                emptyList()
            }
            val basePts = try {
                line.baselinePointsJson.split(";").mapNotNull {
                    val p = it.split(",")
                    if (p.size == 2) Pair(p[0].trim().toFloat(), p[1].trim().toFloat()) else null
                }
            } catch (e: Exception) {
                emptyList()
            }
            val allPts = if (polyPts.isNotEmpty()) polyPts else basePts
            val minX = allPts.minOfOrNull { it.first } ?: 0.5f
            val maxX = allPts.maxOfOrNull { it.first } ?: 0.5f
            val minY = allPts.minOfOrNull { it.second } ?: 0.5f
            val maxY = allPts.maxOfOrNull { it.second } ?: 0.5f
            LineBounds(line, minX, maxX, minY, maxY)
        }

        fun getCat(b: LineBounds): Int {
            val midX = (b.minX + b.maxX) / 2f
            val width = b.maxX - b.minX
            val height = b.maxY - b.minY
            val isVertical = height > width * 2.0f && width < 0.08f

            return when {
                b.minY < 0.16f && width > 0.28f -> 0
                b.minY > 0.86f && b.maxY > 0.89f -> 4
                !isVertical && b.minX >= 0.14f && b.maxX <= 0.86f && width > 0.25f -> 1
                midX >= 0.68f || (isVertical && midX >= 0.50f) -> 2
                midX <= 0.32f || (isVertical && midX < 0.50f) -> 3
                width > 0.25f -> 1
                midX > 0.50f -> 2
                else -> 3
            }
        }

        val sorted = parsed.sortedWith { a, b ->
            val catA = getCat(a)
            val catB = getCat(b)
            if (catA != catB) {
                catA.compareTo(catB)
            } else {
                when (catA) {
                    0, 2, 3 -> a.minY.compareTo(b.minY)
                    1 -> {
                        val yDiff = a.minY - b.minY
                        val avgH = kotlin.math.max(0.01f, ((a.maxY - a.minY) + (b.maxY - b.minY)) / 2f)
                        if (kotlin.math.abs(yDiff) <= avgH * 0.45f) {
                            b.maxX.compareTo(a.maxX) // RTL
                        } else {
                            yDiff.compareTo(0f)
                        }
                    }
                    4 -> {
                        val yDiff = a.minY - b.minY
                        if (kotlin.math.abs(yDiff) <= 0.02f) {
                            b.maxX.compareTo(a.maxX)
                        } else {
                            yDiff.compareTo(0f)
                        }
                    }
                    else -> a.minY.compareTo(b.minY)
                }
            }
        }

        sorted.forEachIndexed { index, item ->
            val newOrder = index + 1
            if (item.line.orderIndex != newOrder) {
                dao.updateLine(item.line.copy(orderIndex = newOrder))
            }
        }

        return sorted.size
    }
}
