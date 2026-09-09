package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.data.db.FoliaDatabase
import com.example.data.model.BlockRegionEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentMetadataEntity
import com.example.data.model.DocumentPartEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity
import com.example.data.model.OcrModelEntity
import com.example.data.model.TranscriptionLayerEntity
import com.example.data.repository.FoliaRepository
import com.example.domain.NormalizationHelper
import com.example.domain.ScholarlyExportService
import com.example.domain.TextAlignmentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class CanvasToolMode {
    VIEW_PAN,
    DRAW_REGION_POLYGON,
    DRAW_BASELINE,
    SELECT_LINE
}

data class LineWithTranscription(
    val line: LineSegmentEntity,
    val transcription: LineTranscriptionEntity?,
    val block: BlockRegionEntity?
)

data class UndoSnapshot(
    val lineId: Long,
    val previousText: String
)

data class ManuscriptProgress(
    val completedLines: Int = 0,
    val totalLines: Int = 0,
    val pageCompletedLines: Int = 0,
    val pageTotalLines: Int = 0,
    val overallPercentage: Float = 0f,
    val pagePercentage: Float = 0f
)

class FoliaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FoliaRepository(
        application,
        FoliaDatabase.getInstance(application, viewModelScope).foliaDao()
    )

    // Theme Mode Preference (System-wide Dark Mode)
    private val _themeMode = MutableStateFlow<ThemeMode>(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // App readiness gate for the splash screen: becomes true once the ONNX
    // model warm-up and repository/database initialization below have
    // completed, so MainActivity can hold the splash screen visible for
    // that entire span instead of it disappearing before the app is
    // actually ready to show real content (e.g. workspace opening on a
    // freshly-imported document before its OCR engines are warmed up).
    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()

    // Library Data
    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val documents: StateFlow<List<DocumentEntity>> = allDocuments

    val allModels: StateFlow<List<OcrModelEntity>> = repository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Document State
    private val _selectedDocumentId = MutableStateFlow<Long?>(null)
    val selectedDocumentId: StateFlow<Long?> = _selectedDocumentId.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentEntity?>(null)
    val currentDocument: StateFlow<DocumentEntity?> = _currentDocument.asStateFlow()

    private val _currentMetadata = MutableStateFlow<DocumentMetadataEntity?>(null)
    val currentMetadata: StateFlow<DocumentMetadataEntity?> = _currentMetadata.asStateFlow()

    private val _parts = MutableStateFlow<List<DocumentPartEntity>>(emptyList())
    val parts: StateFlow<List<DocumentPartEntity>> = _parts.asStateFlow()

    private val _activePageIndex = MutableStateFlow(0)
    val activePageIndex: StateFlow<Int> = _activePageIndex.asStateFlow()

    val activePart: StateFlow<DocumentPartEntity?> = kotlinx.coroutines.flow.combine(_parts, _activePageIndex) { pList, idx ->
        if (pList.isNotEmpty() && idx in pList.indices) pList[idx] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _layers = MutableStateFlow<List<TranscriptionLayerEntity>>(emptyList())
    val layers: StateFlow<List<TranscriptionLayerEntity>> = _layers.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<Long?>(null)
    val selectedLayerId: StateFlow<Long?> = _selectedLayerId.asStateFlow()

    // Page-specific entities
    private val _blocks = MutableStateFlow<List<BlockRegionEntity>>(emptyList())
    val blocks: StateFlow<List<BlockRegionEntity>> = _blocks.asStateFlow()

    private val _lines = MutableStateFlow<List<LineSegmentEntity>>(emptyList())
    val lines: StateFlow<List<LineSegmentEntity>> = _lines.asStateFlow()

    private val _transcriptions = MutableStateFlow<List<LineTranscriptionEntity>>(emptyList())
    val transcriptions: StateFlow<List<LineTranscriptionEntity>> = _transcriptions.asStateFlow()

    // All Lines in the entire manuscript (document-level progress tracking)
    private val _allDocumentLines = MutableStateFlow<List<LineSegmentEntity>>(emptyList())
    val allDocumentLines: StateFlow<List<LineSegmentEntity>> = _allDocumentLines.asStateFlow()

    // Dynamic Progress Tracking (Manuscript Overall + Active Page)
    val manuscriptProgress: StateFlow<ManuscriptProgress> = kotlinx.coroutines.flow.combine(
        _allDocumentLines,
        _lines,
        _transcriptions
    ) { allLines, pageLines, trList ->
        val trMap = trList.associateBy { it.lineId }
        val completedDocLines = allLines.count { line ->
            trMap[line.id]?.text?.trim()?.isNotEmpty() == true
        }
        val totalDocLines = allLines.size
        val completedPageLines = pageLines.count { line ->
            trMap[line.id]?.text?.trim()?.isNotEmpty() == true
        }
        val totalPageLines = pageLines.size

        val overallPct = if (totalDocLines > 0) (completedDocLines.toFloat() / totalDocLines).coerceIn(0f, 1f) else 0f
        val pagePct = if (totalPageLines > 0) (completedPageLines.toFloat() / totalPageLines).coerceIn(0f, 1f) else 0f

        ManuscriptProgress(
            completedLines = completedDocLines,
            totalLines = totalDocLines,
            pageCompletedLines = completedPageLines,
            pageTotalLines = totalPageLines,
            overallPercentage = overallPct,
            pagePercentage = pagePct
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ManuscriptProgress()
    )

    // Selected Line (Synchronized between Writer & Viewer)
    private val _selectedLineId = MutableStateFlow<Long?>(null)
    val selectedLineId: StateFlow<Long?> = _selectedLineId.asStateFlow()

    // Interactive Canvas Tools State
    private val _canvasToolMode = MutableStateFlow(CanvasToolMode.VIEW_PAN)
    val canvasToolMode: StateFlow<CanvasToolMode> = _canvasToolMode.asStateFlow()

    private val _selectedTypology = MutableStateFlow("Matan")
    val selectedTypology: StateFlow<String> = _selectedTypology.asStateFlow()

    // Undo / Redo Buffers
    private val undoStack = mutableListOf<UndoSnapshot>()
    private val redoStack = mutableListOf<UndoSnapshot>()

    // Dialog & Tool Overlays
    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showGlyphQADialog = MutableStateFlow(false)
    val showGlyphQADialog: StateFlow<Boolean> = _showGlyphQADialog.asStateFlow()

    private val _showAlignmentDialog = MutableStateFlow(false)
    val showAlignmentDialog: StateFlow<Boolean> = _showAlignmentDialog.asStateFlow()

    private val _showModelManagerDialog = MutableStateFlow(false)
    val showModelManagerDialog: StateFlow<Boolean> = _showModelManagerDialog.asStateFlow()

    private val _showDocInfoDialog = MutableStateFlow(false)
    val showDocInfoDialog: StateFlow<Boolean> = _showDocInfoDialog.asStateFlow()

    private val _showNewDocDialog = MutableStateFlow(false)
    val showNewDocDialog: StateFlow<Boolean> = _showNewDocDialog.asStateFlow()

    private val _showLayerManagerDialog = MutableStateFlow(false)
    val showLayerManagerDialog: StateFlow<Boolean> = _showLayerManagerDialog.asStateFlow()

    private val _showPpOcrConfigDialog = MutableStateFlow(false)
    val showPpOcrConfigDialog: StateFlow<Boolean> = _showPpOcrConfigDialog.asStateFlow()

    // Line Editor Modal State
    private val _showLineEditModal = MutableStateFlow(false)
    val showLineEditModal: StateFlow<Boolean> = _showLineEditModal.asStateFlow()

    private val _editingLineId = MutableStateFlow<Long?>(null)
    val editingLineId: StateFlow<Long?> = _editingLineId.asStateFlow()

    private val _ppOcrConfig = MutableStateFlow(com.example.domain.ocr.PpOcrV5SegmentationEngine.DetConfig())
    val ppOcrConfig: StateFlow<com.example.domain.ocr.PpOcrV5SegmentationEngine.DetConfig> = _ppOcrConfig.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Alignment state
    private val _alignmentResult = MutableStateFlow<TextAlignmentEngine.AlignmentResult?>(null)
    val alignmentResult: StateFlow<TextAlignmentEngine.AlignmentResult?> = _alignmentResult.asStateFlow()

    // Export generated text & PDF
    private val _exportedContent = MutableStateFlow("")
    val exportedContent: StateFlow<String> = _exportedContent.asStateFlow()
    private val _exportedFormat = MutableStateFlow("PDF_OVERLAY")
    val exportedFormat: StateFlow<String> = _exportedFormat.asStateFlow()
    private val _exportedPdfResult = MutableStateFlow<ScholarlyExportService.PdfExportResult?>(null)
    val exportedPdfResult: StateFlow<ScholarlyExportService.PdfExportResult?> = _exportedPdfResult.asStateFlow()

    private var documentJob: Job? = null
    private var partContentJob: Job? = null
    private var continuousTextDebounceJob: Job? = null

    init {
        // Load saved theme preference (default to DARK for late-night scholarly focus)
        val prefs = application.getSharedPreferences("folia_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        _themeMode.value = try {
            ThemeMode.valueOf(savedTheme)
        } catch (e: Exception) {
            ThemeMode.DARK
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Minimum splash visibility so the brand mark doesn't just
            // flash and vanish on fast devices/warm caches where warm-up
            // below finishes in a handful of milliseconds -- a splash that
            // disappears near-instantly reads as a glitch, not a loading
            // state, even though technically nothing was wrong. This runs
            // concurrently with the real work below, so it only adds
            // wait time when the real work is already faster than this
            // floor; slower real loading is never delayed further by it.
            val minSplashDurationJob = launch { delay(350) }

            // Warm up ONNX Runtime sessions for detection & recognition models
            try {
                com.example.domain.ocr.OnnxDetRunner.getStatus(application)
                com.example.domain.ocr.OnnxRecRunner.getStatus(application)
            } catch (e: Exception) {
                // Non-blocking warmup
            }

            try {
                repository.ensureInitialized()
                val docList = repository.getAllDocumentsList()
                if (docList.isNotEmpty() && _selectedDocumentId.value == null) {
                    selectDocument(docList.first().id)
                }
            } finally {
                // Wait out whichever finishes last: the real initialization
                // work above, or the minimum-visibility floor. Always
                // reached even if initialization threw -- a failed warm-up
                // should surface as a normal in-app error state, not an
                // indefinitely stuck splash screen.
                minSplashDurationJob.join()
                _isAppReady.value = true
            }
        }

        // Reactively observe transcriptions whenever selected layer changes
        viewModelScope.launch(Dispatchers.IO) {
            _selectedLayerId.collect { layerId ->
                if (layerId != null) {
                    repository.observeTranscriptions(layerId).collect { trList ->
                        _transcriptions.value = trList
                    }
                } else {
                    _transcriptions.value = emptyList()
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = getApplication<Application>().getSharedPreferences("folia_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode.name).apply()
        val modeLabel = when (mode) {
            ThemeMode.DARK -> "Mode Gelap aktif (Sesi Malam)"
            ThemeMode.LIGHT -> "Mode Terang aktif"
            ThemeMode.SYSTEM -> "Tema mengikuti sistem"
        }
        _statusMessage.value = modeLabel
    }

    fun toggleDarkMode() {
        val current = _themeMode.value
        val next = if (current == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }

    fun selectDocument(docId: Long) {
        _selectedDocumentId.value = docId
        _activePageIndex.value = 0
        _selectedLineId.value = null
        undoStack.clear()
        redoStack.clear()

        documentJob?.cancel()
        documentJob = viewModelScope.launch(Dispatchers.IO) {
            launch {
                repository.observeDocument(docId).collect { doc ->
                    _currentDocument.value = doc
                }
            }
            launch {
                repository.observeLinesForDocument(docId).collect { linesList ->
                    _allDocumentLines.value = linesList
                }
            }
            launch {
                repository.observeMetadata(docId).collect { meta ->
                    _currentMetadata.value = meta
                }
            }
            launch {
                repository.observeLayers(docId).collect { layerList ->
                    _layers.value = layerList
                    val currentSelected = _selectedLayerId.value
                    if (currentSelected == null || layerList.none { it.id == currentSelected }) {
                        _selectedLayerId.value = layerList.firstOrNull { it.isDefault }?.id ?: layerList.firstOrNull()?.id
                    }
                }
            }
            launch {
                repository.observeParts(docId).collect { partList ->
                    _parts.value = partList
                    if (partList.isNotEmpty()) {
                        val safeIdx = _activePageIndex.value.coerceIn(0, partList.size - 1)
                        val activePart = partList[safeIdx]
                        loadPartContent(activePart.id)
                    } else {
                        _blocks.value = emptyList()
                        _lines.value = emptyList()
                        _selectedLineId.value = null
                    }
                }
            }
        }
    }

    private fun loadPartContent(partId: Long) {
        partContentJob?.cancel()
        partContentJob = viewModelScope.launch(Dispatchers.IO) {
            launch {
                repository.observeBlocks(partId).collect { blockList ->
                    _blocks.value = blockList
                }
            }
            launch {
                repository.observeLines(partId).collect { lineList ->
                    _lines.value = lineList
                    val currentSelectedLine = _selectedLineId.value
                    if (lineList.isEmpty()) {
                        _selectedLineId.value = null
                    } else if (currentSelectedLine == null || lineList.none { it.id == currentSelectedLine }) {
                        _selectedLineId.value = lineList.firstOrNull()?.id
                    }
                }
            }
        }
    }

    fun selectPage(index: Int) {
        val partsList = _parts.value
        if (index in partsList.indices) {
            _activePageIndex.value = index
            val part = partsList[index]
            loadPartContent(part.id)
        }
    }

    fun addNewPage(insertAfterCurrent: Boolean = false) {
        val docId = _selectedDocumentId.value ?: return
        val currentIdx = _activePageIndex.value
        viewModelScope.launch(Dispatchers.IO) {
            val insertAfterIndex = if (insertAfterCurrent) currentIdx else null
            val newPartId = repository.addPageToDocument(docId, insertAfterIndex = insertAfterIndex)
            _statusMessage.value = "Halaman kosong baru berhasil ditambahkan"
            val targetIdx = if (insertAfterCurrent) currentIdx + 1 else _parts.value.size
            _activePageIndex.value = targetIdx
            loadPartContent(newPartId)
        }
    }

    fun deleteCurrentPage() {
        val docId = _selectedDocumentId.value ?: return
        val currentParts = _parts.value
        if (currentParts.size <= 1) {
            _statusMessage.value = "Tidak dapat menghapus satu-satunya halaman dokumen"
            return
        }

        val currentIdx = _activePageIndex.value
        val activePart = currentParts.getOrNull(currentIdx) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.deletePage(activePart.id, docId)
            if (success) {
                val newIndex = (currentIdx - 1).coerceAtLeast(0)
                _activePageIndex.value = newIndex
                _statusMessage.value = "Halaman ${activePart.pageNumber} berhasil dihapus"
            } else {
                _statusMessage.value = "Gagal menghapus halaman"
            }
        }
    }

    fun selectLayer(layerId: Long) {
        _selectedLayerId.value = layerId
    }

    fun addNewLayer(name: String, copyFromLayerId: Long?) {
        val docId = _selectedDocumentId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newId = repository.createLayer(docId, name, copyFromLayerId)
            _selectedLayerId.value = newId
            _statusMessage.value = "Layer '$name' berhasil dibuat"
        }
    }

    fun deleteLayer(layerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLayer(layerId)
            val remaining = _layers.value.filter { it.id != layerId }
            if (_selectedLayerId.value == layerId) {
                _selectedLayerId.value = remaining.firstOrNull()?.id
            }
            _statusMessage.value = "Layer dihapus"
        }
    }

    fun selectLine(lineId: Long) {
        _selectedLineId.value = lineId
    }

    fun updateLineText(lineId: Long, newText: String, targetLayerId: Long? = null) {
        val layerId = targetLayerId ?: _selectedLayerId.value ?: return
        val currentTr = _transcriptions.value.firstOrNull { it.lineId == lineId && it.layerId == layerId }
        val oldText = currentTr?.text ?: ""

        if (oldText != newText) {
            undoStack.add(UndoSnapshot(lineId, oldText))
            redoStack.clear()
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveTranscriptionText(lineId, layerId, newText)
        }
    }

    fun updatePageContinuousText(fullText: String, targetLayerId: Long? = null, immediate: Boolean = false) {
        val activePart = _parts.value.getOrNull(_activePageIndex.value) ?: return
        val layerId = targetLayerId ?: _selectedLayerId.value ?: return

        continuousTextDebounceJob?.cancel()
        continuousTextDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) {
                delay(320) // Smooth debouncing: prevents DB write lockups during fast typing
            }
            val rawLines = fullText.split("\n")
            val existingLines = _lines.value
            for (i in rawLines.indices) {
                val lineText = rawLines[i]
                if (i < existingLines.size) {
                    val line = existingLines[i]
                    repository.saveTranscriptionText(line.id, layerId, lineText)
                } else {
                    val lineId = repository.addLineSegment(
                        partId = activePart.id,
                        blockId = _blocks.value.firstOrNull()?.id,
                        baselineJson = "0.20,${0.20 + (i * 0.05)};0.80,${0.20 + (i * 0.05)}",
                        typology = "Matan"
                    )
                    repository.saveTranscriptionText(lineId, layerId, lineText)
                }
            }
        }
    }

    fun copyTextToLayer(lineId: Long, sourceText: String, targetLayerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveTranscriptionText(lineId, targetLayerId, sourceText)
            _statusMessage.value = "Teks disalin ke layer tujuan"
        }
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        val snapshot = undoStack.removeAt(undoStack.size - 1)
        val layerId = _selectedLayerId.value ?: return

        val currentTr = _transcriptions.value.firstOrNull { it.lineId == snapshot.lineId }
        val currentText = currentTr?.text ?: ""
        redoStack.add(UndoSnapshot(snapshot.lineId, currentText))

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveTranscriptionText(snapshot.lineId, layerId, snapshot.previousText)
        }
        _selectedLineId.value = snapshot.lineId
    }

    fun performRedo() {
        if (redoStack.isEmpty()) return
        val snapshot = redoStack.removeAt(redoStack.size - 1)
        val layerId = _selectedLayerId.value ?: return

        val currentTr = _transcriptions.value.firstOrNull { it.lineId == snapshot.lineId }
        val currentText = currentTr?.text ?: ""
        undoStack.add(UndoSnapshot(snapshot.lineId, currentText))

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveTranscriptionText(snapshot.lineId, layerId, snapshot.previousText)
        }
        _selectedLineId.value = snapshot.lineId
    }

    fun setCanvasToolMode(mode: CanvasToolMode) {
        _canvasToolMode.value = mode
    }

    fun setSelectedTypology(typology: String) {
        _selectedTypology.value = typology
    }

    fun setShowPpOcrConfigDialog(show: Boolean) {
        _showPpOcrConfigDialog.value = show
    }

    fun updatePpOcrConfig(config: com.example.domain.ocr.PpOcrV5SegmentationEngine.DetConfig) {
        _ppOcrConfig.value = config
    }

    fun runAutoSegmentation(config: com.example.domain.ocr.PpOcrV5SegmentationEngine.DetConfig? = null) {
        val activePart = _parts.value.getOrNull(_activePageIndex.value) ?: return
        val detConfig = config ?: _ppOcrConfig.value
        if (config != null) {
            _ppOcrConfig.value = config
        }
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Menjalankan Pipeline ONNX (PP-OCRv5 Det + Muharaf HTR)..."
            val count = repository.runAutoSegmentation(activePart.id, detConfig) { stage, _ ->
                _statusMessage.value = stage
            }
            _selectedLineId.value = null
            loadPartContent(activePart.id)
            _statusMessage.value = "Selesai: $count baris berhasil disegmentasi & ditranskripsi via ONNX."
        }
    }

    fun reorderLinesScholarly() {
        val activePart = _parts.value.getOrNull(_activePageIndex.value) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Mengurutkan baris sesuai kaidah filologi manuskrip..."
            val count = repository.reorderLinesScholarly(activePart.id)
            loadPartContent(activePart.id)
            _statusMessage.value = "Selesai: $count baris diurutkan (Judul → Matan/Syarah → Hasyiyah Kanan → Hasyiyah Kiri → Catatan Bawah)."
        }
    }

    fun runAutoTranscription() {
        val activePart = _parts.value.getOrNull(_activePageIndex.value) ?: return
        val layerId = _selectedLayerId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Menjalankan inferensi Muharaf Kraken HTR ONNX..."
            val count = repository.runAutoTranscription(activePart.id, layerId) { cur, tot ->
                _statusMessage.value = "Muharaf HTR: Mengenali baris $cur / $tot..."
            }
            _statusMessage.value = "Transkripsi Muharaf ONNX selesai untuk $count baris."
        }
    }

    fun applyNormalizationToActiveLine() {
        val lineId = _selectedLineId.value ?: return
        val layerId = _selectedLayerId.value ?: return
        val currentTr = _transcriptions.value.firstOrNull { it.lineId == lineId } ?: return
        val normalized = NormalizationHelper.normalizeArabic(currentTr.text)
        updateLineText(lineId, normalized, layerId)
        _statusMessage.value = "Teks dinormalisasi (Hamzah & Harakat diseragamkan)"
    }

    fun fixArabicSpacingForActiveLineOrPage() {
        val layerId = _selectedLayerId.value ?: return
        val lineId = _selectedLineId.value
        if (lineId != null) {
            val currentTr = _transcriptions.value.firstOrNull { it.lineId == lineId }
            if (currentTr != null) {
                val fixed = NormalizationHelper.fixFragmentedArabicSpacing(currentTr.text)
                updateLineText(lineId, fixed, layerId)
                _statusMessage.value = "Spasi kata Arab pada baris ini berhasil diperbaiki"
                return
            }
        }
        // If no line selected, fix for entire current page lines
        val linesList = _lines.value
        if (linesList.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                for (line in linesList) {
                    val tr = _transcriptions.value.firstOrNull { it.lineId == line.id && it.layerId == layerId }
                    if (tr != null && tr.text.isNotBlank()) {
                        val fixed = NormalizationHelper.fixFragmentedArabicSpacing(tr.text)
                        if (fixed != tr.text) {
                            repository.saveTranscriptionText(line.id, layerId, fixed)
                        }
                    }
                }
                _statusMessage.value = "Seluruh spasi huruf & kata Arab di halaman ini berhasil dirapikan"
            }
        }
    }

    fun stripTashkeelFromActiveLine() {
        val lineId = _selectedLineId.value ?: return
        val layerId = _selectedLayerId.value ?: return
        val currentTr = _transcriptions.value.firstOrNull { it.lineId == lineId } ?: return
        val stripped = NormalizationHelper.stripTashkeel(currentTr.text)
        updateLineText(lineId, stripped, layerId)
        _statusMessage.value = "Harakat/Tashkeel dibersihkan"
    }

    fun addManualRegion(points: String) {
        val activePart = _parts.value.getOrNull(_activePageIndex.value) ?: return
        val typo = _selectedTypology.value
        val color = when (typo) {
            "Matan" -> "#B91C1C"
            "Syarah" -> "#D97706"
            "Marginalia" -> "#7C3AED"
            "Footnote" -> "#0D9488"
            "Heading" -> "#E11D48"
            else -> "#2563EB"
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBlockRegion(activePart.id, typo, typo, color, points)
            _statusMessage.value = "Region $typo ditambahkan"
        }
    }

    fun addManualBaseline(baselinePoints: String) {
        val activePart = _parts.value.getOrNull(_activePageIndex.value) ?: return
        val activeBlock = _blocks.value.firstOrNull { it.typology == _selectedTypology.value } ?: _blocks.value.firstOrNull()
        viewModelScope.launch(Dispatchers.IO) {
            val lineId = repository.addLineSegment(activePart.id, activeBlock?.id, baselinePoints, _selectedTypology.value)
            _selectedLineId.value = lineId
            _statusMessage.value = "Garis baseline ditambahkan"
        }
    }

    fun deleteSelectedLine() {
        val lineId = _selectedLineId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLine(lineId)
            _selectedLineId.value = _lines.value.firstOrNull { it.id != lineId }?.id
            _statusMessage.value = "Baris dihapus"
        }
    }

    fun openLineEditModal(lineId: Long? = null) {
        val targetId = lineId ?: _selectedLineId.value ?: _lines.value.firstOrNull()?.id
        _editingLineId.value = targetId
        if (targetId != null) {
            _selectedLineId.value = targetId
        }
        _showLineEditModal.value = true
    }

    fun closeLineEditModal() {
        _showLineEditModal.value = false
    }

    fun setEditingLineId(lineId: Long) {
        _editingLineId.value = lineId
        _selectedLineId.value = lineId
    }

    fun saveLineBoundaries(lineId: Long, baselineJson: String, maskPolygonJson: String, typology: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLineBoundaries(lineId, baselineJson, maskPolygonJson, typology)
            _statusMessage.value = "Batas baris berhasil diperbarui"
        }
    }

    fun runTextAlignment(referenceText: String) {
        val activeLines = _lines.value
        val trMap = _transcriptions.value.associateBy { it.lineId }
        val fullOcr = activeLines.mapNotNull { trMap[it.id]?.text }.joinToString("\n")

        val result = TextAlignmentEngine.align(fullOcr, referenceText)
        _alignmentResult.value = result
    }

    fun exportDocument(format: String) {
        val doc = _currentDocument.value ?: return
        val layerId = _selectedLayerId.value ?: return
        val meta = _currentMetadata.value

        viewModelScope.launch(Dispatchers.IO) {
            val exportPages = repository.prepareExportData(doc.id, layerId)
            if (format == "PDF_OVERLAY") {
                val pdfResult = ScholarlyExportService.exportToManuscriptOverlayPdf(
                    context = getApplication(),
                    document = doc,
                    metadata = meta,
                    pages = exportPages,
                    renderBoundingBoxes = true,
                    renderTranscribedOverlay = true
                )
                _exportedPdfResult.value = pdfResult
                val summary = buildString {
                    append("=== DOKUMEN PDF NASKAH + OVERLAY TRANSKRIPSI ===\n\n")
                    append("Judul Dokumen  : ${doc.title}\n")
                    append("Jumlah Halaman : ${pdfResult.pageCount} lembar\n")
                    append("Jumlah Baris   : ${pdfResult.totalLines} baris (${pdfResult.totalTranscribedLines} tertranskripsi)\n")
                    append("Ukuran File    : ${pdfResult.fileSizeBytes / 1024} KB\n")
                    append("Lokasi File    : ${pdfResult.file.absolutePath}\n\n")
                    append("Deskripsi Fitur PDF:\n")
                    append("• Citra naskah asli resolusi tinggi sebagai kanvas dasar setiap lembar.\n")
                    append("• Kotak bounding box DBNet dan baseline vector tiap baris.\n")
                    append("• Teks transkripsi tertata langsung di atas baris (overlay) dengan tipografi Arab.\n")
                    append("• Badge nomor urut baris dan header metadata filologi lengkap.")
                }
                _exportedContent.value = summary
            } else {
                val content = when (format) {
                    "PAGE_XML" -> ScholarlyExportService.exportToPageXml(doc, meta, exportPages)
                    "ALTO_XML" -> ScholarlyExportService.exportToAltoXml(doc, meta, exportPages)
                    "PLAIN_TXT" -> ScholarlyExportService.exportToPlainText(doc, exportPages)
                    "KAGGLE_JSON" -> ScholarlyExportService.exportToKaggleTrainingJson(doc, exportPages)
                    else -> ScholarlyExportService.exportToPageXml(doc, meta, exportPages)
                }
                _exportedContent.value = content
            }
            _exportedFormat.value = format
            _showExportDialog.value = true
        }
    }

    fun createNewDocument(title: String, script: String, samplePreset: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val docId = repository.createDocument(title, script, "RTL", samplePreset)
            selectDocument(docId)
            _showNewDocDialog.value = false
            _statusMessage.value = "Dokumen '$title' berhasil dibuat"
        }
    }

    fun deleteDocument(docId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDocument(docId)
            if (_selectedDocumentId.value == docId) {
                _selectedDocumentId.value = null
                _currentDocument.value = null
            }
            _statusMessage.value = "Dokumen dihapus"
        }
    }

    fun updateMetadata(author: String, date: String, copier: String, repo: String, shelfmark: String, notes: String) {
        val docId = _selectedDocumentId.value ?: return
        val meta = DocumentMetadataEntity(
            documentId = docId,
            author = author,
            dateText = date,
            copier = copier,
            repository = repo,
            shelfmark = shelfmark,
            notes = notes
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMetadata(meta)
            _currentMetadata.value = meta
            _showDocInfoDialog.value = false
            _statusMessage.value = "Metadata dokumen diperbarui"
        }
    }

    fun createTranscriptionLayer(name: String, copyFromLayerId: Long? = null) = addNewLayer(name, copyFromLayerId)
    fun deleteTranscriptionLayer(layerId: Long) = deleteLayer(layerId)
    fun setDefaultTranscriptionLayer(layerId: Long) {
        selectLayer(layerId)
    }

    fun runAlignment(referenceText: String) = runTextAlignment(referenceText)
    fun applyAlignment(alignmentText: String) {
        updatePageContinuousText(alignmentText)
        _showAlignmentDialog.value = false
        _statusMessage.value = "Hasil alignment diterapkan ke naskah"
    }

    fun updateLineConfidence(lineId: Long, conf: Float) {
        // No-op / quality tag update
    }

    fun setActiveModel(model: OcrModelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.setActiveModel(model)
            _statusMessage.value = if (ok) {
                "Model ${model.name} kini aktif digunakan"
            } else {
                "Gagal mengaktifkan ${model.name}: berkas model tidak valid atau rusak"
            }
        }
    }

    /**
     * Imports a user-picked .onnx file as a new model entry of the given
     * type, WITHOUT activating it automatically — the person still chooses
     * when to switch via setActiveModel, so a bad/incompatible model never
     * silently replaces a working one.
     */
    fun importOcrModel(inputStream: java.io.InputStream, displayName: String, fileName: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.importOcrModel(inputStream, displayName, fileName, type)
            _statusMessage.value = if (id != null) {
                "Model \"$displayName\" berhasil diimpor. Pilih \"Jadikan Aktif\" untuk menggunakannya."
            } else {
                "Gagal mengimpor model: berkas .onnx tidak valid atau rusak"
            }
        }
    }

    /**
     * Convenience overload for the Model Manager UI: resolves the picked
     * content:// Uri to a stream and a display name via the ContentResolver,
     * then delegates to importOcrModel. Kept separate so importOcrModel
     * itself stays easily unit-testable with a plain InputStream.
     */
    fun importOcrModelFromUri(contentResolver: android.content.ContentResolver, uri: android.net.Uri, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val displayName = queryDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "model.onnx"
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    _statusMessage.value = "Gagal membuka berkas yang dipilih"
                    return@launch
                }
                val id = repository.importOcrModel(stream, displayName, displayName, type)
                _statusMessage.value = if (id != null) {
                    "Model \"$displayName\" berhasil diimpor. Pilih \"Jadikan Aktif\" untuk menggunakannya."
                } else {
                    "Gagal mengimpor model: berkas .onnx tidak valid, rusak, atau bukan format ONNX"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Gagal mengimpor model: ${e.message}"
            }
        }
    }

    private fun queryDisplayName(contentResolver: android.content.ContentResolver, uri: android.net.Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Dialog Toggles
    fun setShowExportDialog(show: Boolean) { _showExportDialog.value = show }
    fun setShowGlyphQADialog(show: Boolean) { _showGlyphQADialog.value = show }
    fun setShowAlignmentDialog(show: Boolean) { _showAlignmentDialog.value = show }
    fun setShowModelManagerDialog(show: Boolean) { _showModelManagerDialog.value = show }
    fun setShowDocInfoDialog(show: Boolean) { _showDocInfoDialog.value = show }
    fun setShowNewDocDialog(show: Boolean) { _showNewDocDialog.value = show }
    fun setShowLayerManagerDialog(show: Boolean) { _showLayerManagerDialog.value = show }
    fun clearStatusMessage() { _statusMessage.value = null }
}
