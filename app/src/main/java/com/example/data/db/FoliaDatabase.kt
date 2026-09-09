package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProjectEntity::class,
        DocumentEntity::class,
        DocumentPartEntity::class,
        BlockRegionEntity::class,
        LineSegmentEntity::class,
        TranscriptionLayerEntity::class,
        LineTranscriptionEntity::class,
        DocumentMetadataEntity::class,
        OcrModelEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class FoliaDatabase : RoomDatabase() {
    abstract fun foliaDao(): FoliaDao

    companion object {
        @Volatile
        private var INSTANCE: FoliaDatabase? = null

        fun getInstance(context: Context, scope: CoroutineScope): FoliaDatabase {
            return INSTANCE ?: synchronized(this) {
                var instance: FoliaDatabase? = null
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    FoliaDatabase::class.java,
                    "folia_manuscripts.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        scope.launch(Dispatchers.IO) {
                            instance?.let { database ->
                                val count = database.foliaDao().getAllDocumentsList().size
                                if (count == 0) {
                                    populateInitialKitabs(database.foliaDao())
                                }
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateInitialKitabs(dao: FoliaDao) {
            // Models
            dao.insertModel(
                OcrModelEntity(
                    id = 1,
                    name = "Muharaf Kraken Arabic HTR (Best ONNX)",
                    type = "Transcription CTC",
                    architecture = "Kraken 1D-CNN + BiLSTM + CTC (174 Classes)",
                    version = "v2.1-best",
                    fileSizeMb = 20.1f,
                    isInstalled = true,
                    huggingFaceRepo = "assets/models/muharaf_rec_best.onnx",
                    isDefault = true,
                    isActive = true
                )
            )
            dao.insertModel(
                OcrModelEntity(
                    id = 2,
                    name = "PP-OCRv5 Mobile Det (DBNet ONNX)",
                    type = "Segmentation",
                    architecture = "PPLCNetV3 + DBNet (Differentiable Binarization)",
                    version = "v5.0-mobile",
                    fileSizeMb = 4.8f,
                    isInstalled = true,
                    huggingFaceRepo = "assets/models/ppocrv5_det.onnx",
                    isDefault = true,
                    isActive = true
                )
            )
            dao.insertModel(
                OcrModelEntity(
                    id = 3,
                    name = "KitabHTR-Tiny (Arabic/Jawi)",
                    type = "Transcription CTC",
                    architecture = "CRNN-CTC (ResNet8 + BiLSTM + CTC)",
                    version = "v1.4-tiny",
                    fileSizeMb = 18.4f,
                    isInstalled = true,
                    huggingFaceRepo = "Ik45/KitabHTR-Tiny",
                    isDefault = false,
                    isActive = false
                )
            )

            // Project 1
            val projId = dao.insertProject(
                ProjectEntity(
                    id = 1,
                    name = "Korpus Manuskrip Nusantara & Timur Tengah",
                    description = "Digitalisasi teks Kitab Kuning, Matan, dan Hasyiah klasik dengan segmentasi berbasis baseline ONNX."
                )
            )

            // Document 1: Fath al-Qarib
            val doc1Id = dao.insertDocument(
                DocumentEntity(
                    id = 1,
                    projectId = projId,
                    title = "فتح القريب المجيب في شرح ألفاظ التقريب",
                    mainScript = "Arabic",
                    readDirection = "RTL",
                    lineOffset = "baseline"
                )
            )

            dao.insertOrUpdateMetadata(
                DocumentMetadataEntity(
                    id = 1,
                    documentId = doc1Id,
                    author = "محمد بن قاسم الغزي (ابن قاسم)",
                    dateText = "918 H / 1512 M",
                    copier = "أحمد بن عبد الله السنباوي",
                    repository = "دار الكتب والوثائق القومية - القاهرة",
                    shelfmark = "MS-Fiqh-Shafi'i-1402",
                    language = "العربية (Arabic)",
                    scriptType = "خط النسخ الكلاسيكي (Classical Naskh)",
                    notes = "نسخة عتيقة بخط نسخ متقن مع حواشٍ بهامش الصفحة وإبراز للمتن بالحمرة.",
                    ppOcrModelUsed = "PP-OCRv5_mobile_det"
                )
            )

            // Layers for Doc 1
            dao.insertLayer(
                TranscriptionLayerEntity(
                    id = 1,
                    documentId = doc1Id,
                    name = "Muharaf HTR (CTC)",
                    isDefault = true,
                    isGroundTruth = false
                )
            )
            dao.insertLayer(
                TranscriptionLayerEntity(
                    id = 2,
                    documentId = doc1Id,
                    name = "Ground Truth / Tahqiq",
                    isDefault = false,
                    isGroundTruth = true
                )
            )
            dao.insertLayer(
                TranscriptionLayerEntity(
                    id = 3,
                    documentId = doc1Id,
                    name = "Normalized (Diacritic Stripped)",
                    isDefault = false,
                    isGroundTruth = false
                )
            )

            // Page 1 (Created clean - lines generated 100% via ONNX model)
            dao.insertPart(
                DocumentPartEntity(
                    id = 1,
                    documentId = doc1Id,
                    pageNumber = 1,
                    imageResName = "manuscript_p1",
                    width = 1200,
                    height = 1600,
                    workflowState = "new"
                )
            )

            // Page 2 (Created clean - lines generated 100% via ONNX model)
            dao.insertPart(
                DocumentPartEntity(
                    id = 2,
                    documentId = doc1Id,
                    pageNumber = 2,
                    imageResName = "manuscript_p2",
                    width = 1200,
                    height = 1600,
                    workflowState = "new"
                )
            )

            // Document 2: Safinat an-Naja
            val doc2Id = dao.insertDocument(
                DocumentEntity(
                    id = 2,
                    projectId = projId,
                    title = "سفينة النجاة فيما يجب على العبد لمولاه",
                    mainScript = "Arabic",
                    readDirection = "RTL",
                    lineOffset = "baseline"
                )
            )

            dao.insertOrUpdateMetadata(
                DocumentMetadataEntity(
                    id = 2,
                    documentId = doc2Id,
                    author = "سالم بن سمير الحضرمي",
                    dateText = "1271 H / 1854 M",
                    copier = "محمد صالح البتاوي",
                    repository = "المكتبة الوطنية الإندونيسية - جاكرتا",
                    shelfmark = "MS-KB-F-42",
                    language = "العربية / Pegon",
                    scriptType = "خط البيغون / الجاوي (Pegon / Jawi)",
                    notes = "مخطوطة جاوي ببيغون مع ترقيم دقيق.",
                    ppOcrModelUsed = "PP-OCRv5_mobile_det"
                )
            )

            dao.insertLayer(
                TranscriptionLayerEntity(
                    id = 4,
                    documentId = doc2Id,
                    name = "Muharaf HTR (CTC)",
                    isDefault = true,
                    isGroundTruth = false
                )
            )
            dao.insertPart(
                DocumentPartEntity(
                    id = 3,
                    documentId = doc2Id,
                    pageNumber = 1,
                    imageResName = "manuscript_p1",
                    width = 1200,
                    height = 1600,
                    workflowState = "new"
                )
            )
        }
    }
}
