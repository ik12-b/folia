package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BlockRegionEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentMetadataEntity
import com.example.data.model.DocumentPartEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity
import com.example.data.model.OcrModelEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.TranscriptionLayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoliaDao {
    // Projects
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    // Documents
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun getAllDocumentsList(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocumentById(documentId: Long): Flow<DocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Long)

    // Document Metadata
    @Query("SELECT * FROM document_metadata WHERE documentId = :documentId LIMIT 1")
    fun observeMetadata(documentId: Long): Flow<DocumentMetadataEntity?>

    @Query("SELECT * FROM document_metadata WHERE documentId = :documentId LIMIT 1")
    suspend fun getMetadata(documentId: Long): DocumentMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadata(meta: DocumentMetadataEntity)

    // Document Parts (Pages)
    @Query("SELECT * FROM document_parts WHERE documentId = :documentId ORDER BY pageNumber ASC")
    fun getPartsForDocument(documentId: Long): Flow<List<DocumentPartEntity>>

    @Query("SELECT * FROM document_parts WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPartsForDocumentList(documentId: Long): List<DocumentPartEntity>

    @Query("SELECT * FROM document_parts WHERE id = :partId")
    suspend fun getPartById(partId: Long): DocumentPartEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: DocumentPartEntity): Long

    @Update
    suspend fun updatePart(part: DocumentPartEntity)

    @Query("DELETE FROM document_parts WHERE id = :partId")
    suspend fun deletePart(partId: Long)

    @Query("DELETE FROM line_segments WHERE partId = :partId")
    suspend fun deleteLinesForPart(partId: Long)

    @Query("DELETE FROM block_regions WHERE partId = :partId")
    suspend fun deleteBlocksForPart(partId: Long)

    @Query("DELETE FROM line_transcriptions WHERE lineId IN (SELECT id FROM line_segments WHERE partId = :partId)")
    suspend fun deleteTranscriptionsForPart(partId: Long)

    // Block Regions
    @Query("SELECT * FROM block_regions WHERE partId = :partId ORDER BY readingOrder ASC")
    fun getBlocksForPart(partId: Long): Flow<List<BlockRegionEntity>>

    @Query("SELECT * FROM block_regions WHERE partId = :partId ORDER BY readingOrder ASC")
    suspend fun getBlocksForPartList(partId: Long): List<BlockRegionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: BlockRegionEntity): Long

    @Update
    suspend fun updateBlock(block: BlockRegionEntity)

    @Query("DELETE FROM block_regions WHERE id = :blockId")
    suspend fun deleteBlock(blockId: Long)

    // Line Segments
    @Query("SELECT * FROM line_segments WHERE partId = :partId ORDER BY orderIndex ASC")
    fun getLinesForPart(partId: Long): Flow<List<LineSegmentEntity>>

    @Query("SELECT * FROM line_segments WHERE partId IN (SELECT id FROM document_parts WHERE documentId = :documentId) ORDER BY partId ASC, orderIndex ASC")
    fun getLinesForDocument(documentId: Long): Flow<List<LineSegmentEntity>>

    @Query("SELECT * FROM line_segments WHERE partId = :partId ORDER BY orderIndex ASC")
    suspend fun getLinesForPartList(partId: Long): List<LineSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: LineSegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<LineSegmentEntity>): List<Long>

    @Update
    suspend fun updateLine(line: LineSegmentEntity)

    @Query("SELECT * FROM line_segments WHERE id = :lineId")
    suspend fun getLineById(lineId: Long): LineSegmentEntity?

    @Query("UPDATE line_segments SET baselinePointsJson = :baselineJson, maskPolygonJson = :maskPolygonJson, typology = COALESCE(:typology, typology) WHERE id = :lineId")
    suspend fun updateLineBoundaries(lineId: Long, baselineJson: String, maskPolygonJson: String, typology: String? = null)

    @Query("DELETE FROM line_segments WHERE id = :lineId")
    suspend fun deleteLine(lineId: Long)

    // Transcription Layers
    @Query("SELECT * FROM transcription_layers WHERE documentId = :documentId")
    fun getLayersForDocument(documentId: Long): Flow<List<TranscriptionLayerEntity>>

    @Query("SELECT * FROM transcription_layers WHERE documentId = :documentId")
    suspend fun getLayersForDocumentList(documentId: Long): List<TranscriptionLayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: TranscriptionLayerEntity): Long

    @Query("DELETE FROM transcription_layers WHERE id = :layerId")
    suspend fun deleteLayer(layerId: Long)

    // Line Transcriptions
    @Query("SELECT * FROM line_transcriptions WHERE layerId = :layerId")
    fun getTranscriptionsForLayer(layerId: Long): Flow<List<LineTranscriptionEntity>>

    @Query("SELECT * FROM line_transcriptions WHERE layerId = :layerId")
    suspend fun getTranscriptionsForLayerList(layerId: Long): List<LineTranscriptionEntity>

    @Query("SELECT * FROM line_transcriptions WHERE lineId = :lineId AND layerId = :layerId LIMIT 1")
    suspend fun getTranscriptionForLineAndLayer(lineId: Long, layerId: Long): LineTranscriptionEntity?

    @Query("SELECT * FROM line_transcriptions WHERE lineId IN (:lineIds)")
    fun getTranscriptionsForLines(lineIds: List<Long>): Flow<List<LineTranscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTranscription(transcription: LineTranscriptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTranscriptions(transcriptions: List<LineTranscriptionEntity>)

    @Query("DELETE FROM line_transcriptions WHERE layerId = :layerId")
    suspend fun deleteTranscriptionsForLayer(layerId: Long)

    @Query("SELECT * FROM line_transcriptions WHERE text LIKE '%' || :query || '%'")
    suspend fun searchTranscriptions(query: String): List<LineTranscriptionEntity>

    // OCR Models
    @Query("SELECT * FROM ocr_models ORDER BY isDefault DESC, name ASC")
    fun getAllModels(): Flow<List<OcrModelEntity>>

    @Query("SELECT * FROM ocr_models WHERE type = :type AND isActive = 1 LIMIT 1")
    suspend fun getActiveModelForType(type: String): OcrModelEntity?

    @Query("UPDATE ocr_models SET isActive = 0 WHERE type = :type")
    suspend fun deactivateAllModelsOfType(type: String)

    @Query("UPDATE ocr_models SET isActive = 1 WHERE id = :modelId")
    suspend fun activateModel(modelId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: OcrModelEntity): Long

    @Update
    suspend fun updateModel(model: OcrModelEntity)
}
