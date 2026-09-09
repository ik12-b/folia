package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "documents",
    indices = [Index(value = ["projectId"])]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val title: String,
    val mainScript: String = "Arabic", // Arabic, Jawi, Ottoman Turkish, Latin
    val readDirection: String = "RTL", // RTL, LTR
    val lineOffset: String = "baseline", // baseline, top, bottom
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_parts",
    indices = [Index(value = ["documentId"])]
)
data class DocumentPartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val imageResName: String? = null, // e.g. "manuscript_p1"
    val imageUri: String? = null,
    val width: Int = 1200,
    val height: Int = 1600,
    val workflowState: String = "transcribed" // created, converted, segmented, transcribed
)

@Entity(
    tableName = "block_regions",
    indices = [Index(value = ["partId"])]
)
data class BlockRegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partId: Long,
    val typology: String = "Matan", // Matan, Syarah, Marginalia, Footnote, Heading, Poetry
    val label: String = "",
    val colorHex: String = "#B91C1C",
    val polygonPointsJson: String, // "x1,y1;x2,y2;..." (normalized 0.0..1.0)
    val readingOrder: Int = 1
)

@Entity(
    tableName = "line_segments",
    indices = [Index(value = ["partId"]), Index(value = ["blockId"])]
)
data class LineSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partId: Long,
    val blockId: Long? = null,
    val orderIndex: Int = 1,
    val baselinePointsJson: String, // "x1,y1;x2,y2;..." (PP-OCRv5 baseline vector)
    val maskPolygonJson: String = "", // "x1,y1;x2,y2;x3,y3;x4,y4" (PP-OCRv5 DBNet polygon bounding box)
    val confidence: Float = 0.95f, // PP-OCRv5 detection confidence score
    val modelSource: String = "PP-OCRv5_mobile_det", // Model engine origin
    val typology: String = "Matan" // Heading, Matan, Syarah, Marginalia
)

@Entity(
    tableName = "transcription_layers",
    indices = [Index(value = ["documentId"])]
)
data class TranscriptionLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val name: String, // "KitabHTR Draft", "Ground Truth", "Normalized / Tahqiq"
    val isDefault: Boolean = false,
    val isGroundTruth: Boolean = false
)

@Entity(
    tableName = "line_transcriptions",
    indices = [Index(value = ["lineId"]), Index(value = ["layerId"])]
)
data class LineTranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lineId: Long,
    val layerId: Long,
    val text: String,
    val avgConfidence: Float = 0.96f,
    val characterConfidencesJson: String = "", // e.g. "0.98,0.95,0.99,..."
    val isVerified: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "document_metadata",
    indices = [Index(value = ["documentId"], unique = true)]
)
data class DocumentMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val author: String = "",
    val dateText: String = "",
    val copier: String = "",
    val repository: String = "",
    val shelfmark: String = "",
    val language: String = "Arabic",
    val scriptType: String = "Naskh / Maghrebi",
    val notes: String = "",
    val ppOcrModelUsed: String = "PP-OCRv5_mobile_det"
)

@Entity(tableName = "ocr_models")
data class OcrModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "Transcription CTC" or "Segmentation"
    val architecture: String, // e.g. "KitabHTR-Tiny (CRNN-CTC)", "Kraken-Light-Seg"
    val version: String = "1.0",
    val fileSizeMb: Float = 18.4f,
    val isInstalled: Boolean = true,
    val huggingFaceRepo: String = "Ik45/KitabHTR-Tiny",
    val isDefault: Boolean = false,
    // Absolute on-device path to the .onnx file backing this entry. Empty for
    // legacy/seed rows whose model lives in app assets rather than imported
    // storage — those are resolved by name via the asset fallback in
    // OnnxDetRunner/OnnxRecRunner instead.
    val filePath: String = "",
    // Whether this is the model currently loaded for its `type` (Segmentation
    // or Transcription CTC). Exactly one model per type should be active at
    // a time; switching sets this true on the chosen row and false on all
    // other rows of the same type.
    val isActive: Boolean = false
)
