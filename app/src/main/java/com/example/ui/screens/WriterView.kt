package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Margin
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockRegionEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentPartEntity
import com.example.data.model.LineSegmentEntity
import com.example.data.model.LineTranscriptionEntity
import com.example.data.model.TranscriptionLayerEntity
import com.example.ui.components.ManuscriptOverlayCanvas
import com.example.ui.components.ManuscriptSnippetHelper
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMuted
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkText
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ParchmentBg
import com.example.ui.theme.ParchmentBorder
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.ParchmentSurface
import com.example.ui.theme.ParchmentSurfaceVariant
import com.example.ui.theme.ParchmentText
import com.example.ui.theme.ScholarBlue
import com.example.ui.theme.ScholarBlueAccent
import com.example.ui.theme.ScholarBlueDark
import com.example.ui.theme.ScholarBlueLight
import com.example.ui.viewmodel.CanvasToolMode

enum class WordDocumentMode {
    PRINT_LAYOUT,      // 📄 Lembaran Kertas Utuh (A4/Folio Naskah Fisik)
    LINE_SYNCHRONIZED, // 🔍 Selaras Naskah (Line-by-Line with Original Line Snippet Crop)
    SPLIT_REFERENCE    // 🖼️ Naskah & Teks Berdampingan (Split View)
}

data class TypefaceOption(
    val name: String,
    val category: String,
    val fontFamily: FontFamily
)

val WORD_TYPEFACES = listOf(
    TypefaceOption("Amiri Quran", "Naskh Arab / Jawi", FontFamily.Serif),
    TypefaceOption("Scheherazade New", "Arab Tradisional", FontFamily.Serif),
    TypefaceOption("Times New Roman", "Serif Akademik", FontFamily.Serif),
    TypefaceOption("Calibri", "Standar Office", FontFamily.SansSerif),
    TypefaceOption("Arial", "Sans-Serif Universal", FontFamily.SansSerif),
    TypefaceOption("Georgia", "Serif Literer", FontFamily.Serif),
    TypefaceOption("Courier New", "Monospace", FontFamily.Monospace)
)

val WORD_FONT_SIZES = listOf(10f, 11f, 12f, 13f, 14f, 16f, 18f, 20f, 24f, 28f, 32f)

val WORD_COLOR_PALETTE = listOf(
    Color(0xFF000000) to "Hitam Tinta",
    Color(0xFF1E4976) to "Biru Oxford",
    Color(0xFFC59B27) to "Emas Manuskrip",
    Color(0xFFDC2626) to "Merah Rubrik",
    Color(0xFF059669) to "Hijau Zamrud",
    Color(0xFF7C3AED) to "Ungu Ametis",
    Color(0xFF475569) to "Abu-abu Arang"
)

val WORD_HIGHLIGHT_PALETTE = listOf(
    Color.Transparent to "Tanpa Sorotan",
    Color(0xFFFEF08A) to "Kuning Stabilo",
    Color(0xFFBBF7D0) to "Hijau Stabilo",
    Color(0xFFBAE6FD) to "Biru Muda",
    Color(0xFFFBCFE8) to "Merah Muda",
    Color(0xFFFED7AA) to "Oranye Lembut"
)

/**
 * WriterView - Scholarly Manuscript Transcription & Document Studio
 * Integrates discrete paper sheets, interlinear manuscript line snippets,
 * split reference previews, and Arabic orthographic correction tools.
 */
@Composable
fun WriterView(
    document: DocumentEntity?,
    parts: List<DocumentPartEntity> = emptyList(),
    activePageIndex: Int = 0,
    activePart: DocumentPartEntity?,
    layers: List<TranscriptionLayerEntity>,
    selectedLayerId: Long?,
    lines: List<LineSegmentEntity>,
    blocks: List<BlockRegionEntity>,
    transcriptions: List<LineTranscriptionEntity>,
    selectedLineId: Long?,
    isDarkMode: Boolean = true,
    onSelectPage: (Int) -> Unit = {},
    onAddNewPage: () -> Unit = {},
    onDeleteCurrentPage: () -> Unit = {},
    onLineSelected: (Long) -> Unit,
    onTextChange: (lineId: Long, text: String, targetLayerId: Long?) -> Unit,
    onContinuousTextChange: (fullText: String, targetLayerId: Long?) -> Unit = { _, _ -> },
    onCopyTextToLayer: (lineId: Long, sourceText: String, targetLayerId: Long) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectLayer: (Long) -> Unit,
    onOpenLayerManager: () -> Unit,
    onNormalize: () -> Unit,
    onFixArabicSpacing: () -> Unit = {},
    onStripTashkeel: () -> Unit,
    onRunAutoSegmentation: () -> Unit = {},
    onOpenLineEditModal: (Long) -> Unit = {},
    onOpenAlignment: () -> Unit,
    onOpenGlyphQA: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenDocInfo: () -> Unit,
    onDeleteSelectedLine: () -> Unit,
    onSwipeToViewerHint: () -> Unit,
    onBackToLibrary: () -> Unit,
    isBarsVisible: Boolean = true,
    onUserInteraction: () -> Unit = {},
    isBarsPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onToggleBars: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Full-screen and auto-hiding bars state
    var internalBarsVisible by remember { mutableStateOf(true) }
    var internalBarsPinned by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val autoHideDelayMs = 3500L

    val effectiveBarsVisible = isBarsVisible && internalBarsVisible
    val effectiveBarsPinned = isBarsPinned || internalBarsPinned

    // Pager for multi-page discrete sheets
    val pageCount = parts.size.coerceAtLeast(1)
    val wordFolioPagerState = rememberPagerState(
        initialPage = activePageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    // Sync from external page selection
    LaunchedEffect(activePageIndex) {
        if (activePageIndex in 0 until pageCount && wordFolioPagerState.currentPage != activePageIndex) {
            wordFolioPagerState.animateScrollToPage(activePageIndex)
        }
    }

    // Sync from vertical scroll/pager gesture
    LaunchedEffect(wordFolioPagerState) {
        snapshotFlow { wordFolioPagerState.currentPage }.collect { page ->
            if (page != activePageIndex && page in parts.indices) {
                onSelectPage(page)
            }
        }
    }

    // Transcription Map for fast lookup
    val transcriptionMap = remember(transcriptions) {
        transcriptions.associateBy { it.lineId }
    }

    // Continuous page text compiled from lines (sorted by scholarly reading order)
    val initialPageText = remember(lines, transcriptions, activePart?.id) {
        lines.sortedBy { it.orderIndex }.joinToString("\n") { line -> transcriptionMap[line.id]?.text ?: "" }
    }
    var documentText by remember(activePart?.id) { mutableStateOf(initialPageText) }

    LaunchedEffect(lines, transcriptions, activePart?.id) {
        val compiled = lines.sortedBy { it.orderIndex }.joinToString("\n") { line -> transcriptionMap[line.id]?.text ?: "" }
        if (compiled != documentText && compiled.isNotBlank() && documentText.isBlank()) {
            documentText = compiled
        }
    }

    // Microsoft Word UI Mode & States
    var wordDocMode by remember { mutableStateOf(WordDocumentMode.PRINT_LAYOUT) }
    var zoomPercent by remember { mutableFloatStateOf(100f) }
    var showRuler by remember { mutableStateOf(false) }
    var showTashkeelBar by remember { mutableStateOf(true) }

    // --- Rich Typography & Formatting States ---
    var selectedTypeface by remember { mutableStateOf(WORD_TYPEFACES.first()) }
    var fontSizePt by remember { mutableFloatStateOf(14f) }
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }
    var isStrikethrough by remember { mutableStateOf(false) }
    var textColor by remember { mutableStateOf(Color(0xFF000000)) }
    var textHighlightColor by remember { mutableStateOf(Color.Transparent) }
    var textAlign by remember { mutableStateOf(TextAlign.Right) }
    var lineSpacingMultiplier by remember { mutableFloatStateOf(1.65f) }

    // --- Margin Controls (cm) ---
    var leftIndentCm by remember { mutableFloatStateOf(0.0f) }
    var rightIndentCm by remember { mutableFloatStateOf(0.0f) }
    var firstLineIndentCm by remember { mutableFloatStateOf(0.0f) }

    // Dropdown Dialog States
    var showTypefaceDropdown by remember { mutableStateOf(false) }
    var showFontSizeDropdown by remember { mutableStateOf(false) }
    var showTextColorDropdown by remember { mutableStateOf(false) }
    var showHighlightDropdown by remember { mutableStateOf(false) }
    var showLayerDropdown by remember { mutableStateOf(false) }

    // Multi-level Undo / Redo History Stack
    val undoStack = remember(activePart?.id) { mutableStateListOf<String>() }
    val redoStack = remember(activePart?.id) { mutableStateListOf<String>() }

    fun handleWordUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(documentText)
            documentText = previous
            onContinuousTextChange(previous, selectedLayerId)
            onUndo()
        }
    }

    fun handleWordRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(documentText)
            documentText = next
            onContinuousTextChange(next, selectedLayerId)
            onRedo()
        }
    }

    // Word Statistics
    val wordCount by remember(documentText) {
        derivedStateOf {
            if (documentText.isBlank()) 0
            else documentText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        }
    }

    // Arabic Tashkeel & Diacritics
    val tashkeelChars = listOf(
        "َ" to "Fathah",
        "ِ" to "Kasrah",
        "ُ" to "Dammah",
        "ً" to "Tanwin Fathah",
        "ٍ" to "Tanwin Kasrah",
        "ٌ" to "Tanwin Dammah",
        "ّ" to "Shaddah",
        "ْ" to "Sukun",
        "ٰ" to "Alif Khanjariyah",
        "ء" to "Hamzah",
        "ـ" to "Kashida"
    )

    val activeLayer = remember(layers, selectedLayerId) {
        layers.firstOrNull { it.id == selectedLayerId } ?: layers.firstOrNull()
    }

    // Scholarly Workspace Colors
    val wordBlue = if (isDarkMode) ScholarBlueDark else ScholarBlue
    val deskBg = if (isDarkMode) DarkBg else ParchmentBg
    val paperBg = Color(0xFFFFFFFF) // Kertas di Writer Studio selalu putih clean
    val paperBorder = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
    val ribbonBg = if (isDarkMode) DarkSurface else ParchmentSurface
    val ribbonBorder = if (isDarkMode) DarkBorder else ParchmentBorder
    val actualTextColor = if (textColor == Color(0xFF000000)) Color(0xFF0F172A) else textColor

    fun triggerInteraction() {
        internalBarsVisible = true
        lastInteractionTime = System.currentTimeMillis()
        onUserInteraction()
    }

    // Auto-hide countdown effect: hides bars after inactivity unless pinned or dropdowns open
    LaunchedEffect(
        effectiveBarsVisible,
        effectiveBarsPinned,
        lastInteractionTime,
        showTypefaceDropdown,
        showFontSizeDropdown,
        showTextColorDropdown,
        showHighlightDropdown,
        showLayerDropdown
    ) {
        if (effectiveBarsVisible && !effectiveBarsPinned) {
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = (autoHideDelayMs - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            if (!showTypefaceDropdown && !showFontSizeDropdown && !showTextColorDropdown && !showHighlightDropdown && !showLayerDropdown) {
                internalBarsVisible = false
            }
        }
    }

    // Responsive padding that smoothly animates when bars appear or hide
    val topBarPadding by animateDpAsState(
        targetValue = if (effectiveBarsVisible) (if (showTashkeelBar) 146.dp else 114.dp) else 4.dp,
        animationSpec = tween(durationMillis = 280),
        label = "writer_top_padding"
    )
    val bottomBarPadding by animateDpAsState(
        targetValue = if (effectiveBarsVisible) 26.dp else 4.dp,
        animationSpec = tween(durationMillis = 280),
        label = "writer_bottom_padding"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(deskBg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    triggerInteraction()
                    waitForUpOrCancellation()
                }
            }
            .testTag("writer_screen")
    ) {
        // --- Top Bar Section (Overlaid at Top with Auto-Hide) ---
        AnimatedVisibility(
            visible = effectiveBarsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("writer_top_bars_container")
            ) {
                // --- 1. Compact Scholarly Title Bar (36dp) ---
                Surface(
                    color = wordBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF0D47A1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "W",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "${document?.title ?: "Transkripsi"}.docx",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF86EFAC), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Tersimpan", fontSize = 8.5.sp, color = Color.White)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { handleWordUndo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.size(26.dp).testTag("writer_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    IconButton(
                        onClick = { handleWordRedo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.size(26.dp).testTag("writer_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    IconButton(
                        onClick = { onOpenExport() },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan / Ekspor", tint = Color.White, modifier = Modifier.size(14.dp))
                    }

                    IconButton(
                        onClick = {
                            triggerInteraction()
                            internalBarsPinned = !internalBarsPinned
                            onTogglePin()
                        },
                        modifier = Modifier.size(26.dp).testTag("writer_pin_bars_button")
                    ) {
                        Icon(
                            imageVector = if (effectiveBarsPinned) Icons.Default.PushPin else Icons.Default.Fullscreen,
                            contentDescription = if (effectiveBarsPinned) "Bilah Disematkan" else "Layar Penuh Otomatis",
                            tint = if (effectiveBarsPinned) GoldPrimary else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            internalBarsVisible = false
                        },
                        modifier = Modifier.size(26.dp).testTag("writer_hide_bars_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Sembunyikan Bilah",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // --- 2. Discrete Sheet Selector & 3-Way Mode Bar ---
        Surface(
            color = if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Multi-page navigation chips
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    parts.forEachIndexed { idx, part ->
                        val isSelected = idx == activePageIndex
                        val chipBg = if (isSelected) (if (isDarkMode) ScholarBlue else ScholarBlueDark) else (if (isDarkMode) DarkSurface else ParchmentSurface)
                        val chipTextCol = if (isSelected) Color.White else (if (isDarkMode) DarkText else ParchmentText)
                        val chipBorderCol = if (isSelected) GoldPrimary else (if (isDarkMode) DarkBorder else ParchmentBorder)

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(chipBg)
                                .border(0.5.dp, chipBorderCol, RoundedCornerShape(4.dp))
                                .clickable { onSelectPage(idx) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isSelected) GoldPrimary else (if (isDarkMode) DarkMuted else ParchmentMuted),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Hal. ${part.pageNumber}",
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = chipTextCol
                            )
                        }
                    }

                    // Add Page Button
                    IconButton(
                        onClick = { onAddNewPage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Halaman",
                            tint = GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 3-Way Mode Selector: Lembaran vs Selaras Naskah vs Split
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDarkMode) DarkSurface else ParchmentSurface)
                        .border(0.5.dp, if (isDarkMode) DarkBorder else ParchmentBorder, RoundedCornerShape(4.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. Lembaran
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (wordDocMode == WordDocumentMode.PRINT_LAYOUT) wordBlue else Color.Transparent)
                            .clickable { wordDocMode = WordDocumentMode.PRINT_LAYOUT }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Lembaran",
                            fontSize = 9.5.sp,
                            fontWeight = if (wordDocMode == WordDocumentMode.PRINT_LAYOUT) FontWeight.Bold else FontWeight.Normal,
                            color = if (wordDocMode == WordDocumentMode.PRINT_LAYOUT) Color.White else (if (isDarkMode) DarkMuted else ParchmentMuted)
                        )
                    }

                    // 2. Selaras Naskah
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (wordDocMode == WordDocumentMode.LINE_SYNCHRONIZED) wordBlue else Color.Transparent)
                            .clickable { wordDocMode = WordDocumentMode.LINE_SYNCHRONIZED }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Selaras Naskah",
                            fontSize = 9.5.sp,
                            fontWeight = if (wordDocMode == WordDocumentMode.LINE_SYNCHRONIZED) FontWeight.Bold else FontWeight.Normal,
                            color = if (wordDocMode == WordDocumentMode.LINE_SYNCHRONIZED) Color.White else (if (isDarkMode) DarkMuted else ParchmentMuted)
                        )
                    }

                    // 3. Split Naskah & Teks
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (wordDocMode == WordDocumentMode.SPLIT_REFERENCE) wordBlue else Color.Transparent)
                            .clickable { wordDocMode = WordDocumentMode.SPLIT_REFERENCE }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Split Naskah",
                            fontSize = 9.5.sp,
                            fontWeight = if (wordDocMode == WordDocumentMode.SPLIT_REFERENCE) FontWeight.Bold else FontWeight.Normal,
                            color = if (wordDocMode == WordDocumentMode.SPLIT_REFERENCE) Color.White else (if (isDarkMode) DarkMuted else ParchmentMuted)
                        )
                    }
                }
            }
        }

        // --- 3. Streamlined Formatting Toolbar (Single Ergonomic Row) ---
        Surface(
            color = ribbonBg,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = ribbonBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clipboard Copy
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", documentText))
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin Semua", tint = if (isDarkMode) DarkText else ParchmentText, modifier = Modifier.size(13.dp))
                }

                WordVerticalDivider(isDarkMode)

                // 1. Typeface Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant)
                            .border(0.5.dp, if (isDarkMode) DarkBorder else ParchmentBorder, RoundedCornerShape(3.dp))
                            .clickable { showTypefaceDropdown = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedTypeface.name,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkMode) DarkText else ParchmentText
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (isDarkMode) DarkMuted else ParchmentMuted, modifier = Modifier.size(12.dp))
                    }

                    DropdownMenu(
                        expanded = showTypefaceDropdown,
                        onDismissRequest = { showTypefaceDropdown = false },
                        modifier = Modifier.background(if (isDarkMode) DarkSurface else ParchmentSurface)
                    ) {
                        WORD_TYPEFACES.forEach { tf ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tf.name,
                                            fontFamily = tf.fontFamily,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTypeface.name == tf.name) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedTypeface.name == tf.name) GoldPrimary else (if (isDarkMode) DarkText else ParchmentText)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(${tf.category})", fontSize = 9.sp, color = if (isDarkMode) DarkMuted else ParchmentMuted)
                                    }
                                },
                                onClick = {
                                    selectedTypeface = tf
                                    showTypefaceDropdown = false
                                }
                            )
                        }
                    }
                }

                // 2. Font Size Controls
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant)
                            .border(0.5.dp, if (isDarkMode) DarkBorder else ParchmentBorder, RoundedCornerShape(3.dp))
                            .clickable { showFontSizeDropdown = true }
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${fontSizePt.toInt()}pt",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) DarkText else ParchmentText
                        )
                    }

                    DropdownMenu(
                        expanded = showFontSizeDropdown,
                        onDismissRequest = { showFontSizeDropdown = false },
                        modifier = Modifier.background(if (isDarkMode) DarkSurface else ParchmentSurface)
                    ) {
                        WORD_FONT_SIZES.forEach { size ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${size.toInt()} pt",
                                        fontSize = 11.sp,
                                        fontWeight = if (fontSizePt == size) FontWeight.Bold else FontWeight.Normal,
                                        color = if (fontSizePt == size) GoldPrimary else (if (isDarkMode) DarkText else ParchmentText)
                                    )
                                },
                                onClick = {
                                    fontSizePt = size
                                    showFontSizeDropdown = false
                                }
                            )
                        }
                    }
                }

                WordMiniButton(icon = Icons.Default.Add, label = "A+", isDarkMode = isDarkMode) {
                    fontSizePt = (fontSizePt + 1f).coerceAtMost(36f)
                }
                WordMiniButton(icon = Icons.Default.Remove, label = "A-", isDarkMode = isDarkMode) {
                    fontSizePt = (fontSizePt - 1f).coerceAtLeast(8f)
                }

                WordVerticalDivider(isDarkMode)

                // 3. Bold, Italic, Underline, Strikethrough
                WordToggleButton(icon = Icons.Default.FormatBold, label = "B", isSelected = isBold, isDarkMode = isDarkMode) { isBold = !isBold }
                WordToggleButton(icon = Icons.Default.FormatItalic, label = "I", isSelected = isItalic, isDarkMode = isDarkMode) { isItalic = !isItalic }
                WordToggleButton(icon = Icons.Default.FormatUnderlined, label = "U", isSelected = isUnderline, isDarkMode = isDarkMode) { isUnderline = !isUnderline }
                WordToggleButton(icon = Icons.Default.FormatStrikethrough, label = "S", isSelected = isStrikethrough, isDarkMode = isDarkMode) { isStrikethrough = !isStrikethrough }

                WordVerticalDivider(isDarkMode)

                // 4. Alignment Buttons
                WordToggleButton(icon = Icons.Default.FormatAlignRight, label = "R", isSelected = textAlign == TextAlign.Right, isDarkMode = isDarkMode) { textAlign = TextAlign.Right }
                WordToggleButton(icon = Icons.Default.FormatAlignCenter, label = "C", isSelected = textAlign == TextAlign.Center, isDarkMode = isDarkMode) { textAlign = TextAlign.Center }
                WordToggleButton(icon = Icons.Default.FormatAlignLeft, label = "L", isSelected = textAlign == TextAlign.Left, isDarkMode = isDarkMode) { textAlign = TextAlign.Left }
                WordToggleButton(icon = Icons.Default.FormatAlignJustify, label = "J", isSelected = textAlign == TextAlign.Justify, isDarkMode = isDarkMode) { textAlign = TextAlign.Justify }

                WordVerticalDivider(isDarkMode)

                // 5. Margin Ruler Toggle
                WordToggleButton(
                    icon = Icons.Default.Straighten,
                    label = "Penggaris",
                    isSelected = showRuler,
                    isDarkMode = isDarkMode
                ) { showRuler = !showRuler }

                // 6. Layer Switcher
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(ScholarBlue.copy(alpha = 0.15f))
                            .border(0.5.dp, ScholarBlue.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .clickable { showLayerDropdown = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = ScholarBlue, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = activeLayer?.name ?: "Layer",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) ScholarBlueLight else ScholarBlueDark,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ScholarBlue, modifier = Modifier.size(11.dp))
                    }

                    DropdownMenu(
                        expanded = showLayerDropdown,
                        onDismissRequest = { showLayerDropdown = false },
                        modifier = Modifier.background(if (isDarkMode) DarkSurface else ParchmentSurface)
                    ) {
                        layers.forEach { layer ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = layer.name,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (layer.id == selectedLayerId) FontWeight.Bold else FontWeight.Normal,
                                            color = if (layer.id == selectedLayerId) GoldPrimary else (if (isDarkMode) DarkText else ParchmentText)
                                        )
                                        if (layer.isGroundTruth) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("(GT)", fontSize = 9.sp, color = Color(0xFF10B981))
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectLayer(layer.id)
                                    showLayerDropdown = false
                                }
                            )
                        }
                        HorizontalDivider(color = if (isDarkMode) DarkBorder else ParchmentBorder)
                        DropdownMenuItem(
                            text = { Text("Kelola Layer...", fontSize = 11.sp, color = ScholarBlue) },
                            onClick = {
                                showLayerDropdown = false
                                onOpenLayerManager()
                            }
                        )
                    }
                }
            }
        }

        // --- 4. Optional Tashkeel (Arabic Diacritics & Smart Fixer) Quick Insert Bar ---
        AnimatedVisibility(
            visible = showTashkeelBar,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vokalisasi:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) GoldLight else GoldDark
                    )

                    tashkeelChars.forEach { (char, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isDarkMode) DarkSurface else ParchmentSurface)
                                .border(0.5.dp, if (isDarkMode) DarkBorder else ParchmentBorder, RoundedCornerShape(3.dp))
                            .clickable {
                                undoStack.add(documentText)
                                documentText += char
                                onContinuousTextChange(documentText, selectedLayerId)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ـ$char",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) DarkText else ParchmentText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Smart Arabic Spacing & Ligature Fixer Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(ScholarBlue.copy(alpha = 0.2f))
                            .border(0.5.dp, ScholarBlue.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .clickable { onFixArabicSpacing() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = ScholarBlue, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Rapikan Spasi Arab", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ScholarBlue)
                        }
                    }

                    // Normalize & Strip helpers
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .clickable { onNormalize() }
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text("Auto-Normalisasi", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDarkMode) DarkSurface else ParchmentSurface)
                            .clickable { onStripTashkeel() }
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text("Hapus Harakat", fontSize = 9.sp, color = if (isDarkMode) DarkMuted else ParchmentMuted)
                    }
                }
            }
        }

        // --- 5. Optional Interactive Margin Ruler ---
        AnimatedVisibility(
            visible = showRuler,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WordInteractiveRuler(
                leftIndentCm = leftIndentCm,
                rightIndentCm = rightIndentCm,
                firstLineIndentCm = firstLineIndentCm,
                isDarkMode = isDarkMode,
                onLeftIndentChanged = { leftIndentCm = it },
                onRightIndentChanged = { rightIndentCm = it },
                onFirstLineIndentChanged = { firstLineIndentCm = it }
            )
        }
            }
        }

        // --- 6. Main Workspace Area According to Mode (Full-Screen Canvas) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarPadding, bottom = bottomBarPadding)
                .background(deskBg)
        ) {
            when (wordDocMode) {
                WordDocumentMode.PRINT_LAYOUT -> {
                    // MODE 1: Lembaran Kertas Dokumen Fisik Per Halaman (Discrete A4/Folio Paper Sheet)
                    VerticalPager(
                        state = wordFolioPagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = true,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        val partForPage = parts.getOrNull(pageIdx)
                        val isCurrentPage = pageIdx == activePageIndex

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = paperBg,
                                shape = RoundedCornerShape(3.dp),
                                shadowElevation = 8.dp,
                                tonalElevation = 2.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = paperBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 520.dp)
                                    .defaultMinSize(minHeight = (560f * (zoomPercent / 100f)).dp)
                                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(3.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = (560f * (zoomPercent / 100f)).dp)
                                        .padding(
                                            start = (20.dp + (leftIndentCm * 14).dp).coerceAtLeast(12.dp),
                                            end = (20.dp + (rightIndentCm * 14).dp).coerceAtLeast(12.dp),
                                            top = 20.dp,
                                            bottom = 20.dp
                                        ),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Header Lembar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = ScholarBlue,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = document?.title ?: "Naskah Nusantara",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF475569),
                                                    maxLines = 1
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(GoldContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Lembar ${partForPage?.pageNumber ?: (pageIdx + 1)}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoldDark
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            color = Color(0xFFE2E8F0),
                                            thickness = 0.75.dp,
                                            modifier = Modifier.padding(bottom = 14.dp)
                                        )

                                        // Konten Lembar
                                        if (isCurrentPage && lines.isEmpty()) {
                                            // Empty Page Guide Card with One-Click Extraction
                                            EmptyPageExtractionCard(
                                                pageNumber = partForPage?.pageNumber ?: (pageIdx + 1),
                                                isDarkMode = isDarkMode,
                                                onRunExtraction = onRunAutoSegmentation
                                            )
                                        }

                                        BasicTextField(
                                            value = if (isCurrentPage) documentText else lines.joinToString("\n") { transcriptionMap[it.id]?.text ?: "" },
                                            onValueChange = { newText ->
                                                if (isCurrentPage) {
                                                    undoStack.add(documentText)
                                                    documentText = newText
                                                    onContinuousTextChange(newText, selectedLayerId)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = (420f * (zoomPercent / 100f)).dp)
                                                .background(if (textHighlightColor != Color.Transparent) textHighlightColor.copy(alpha = 0.2f) else Color.Transparent)
                                                .testTag("word_document_editor"),
                                            textStyle = TextStyle(
                                                fontSize = (fontSizePt * (zoomPercent / 100f)).sp,
                                                lineHeight = (fontSizePt * lineSpacingMultiplier * (zoomPercent / 100f)).sp,
                                                fontFamily = selectedTypeface.fontFamily,
                                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = when {
                                                    isUnderline && isStrikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                                                    isUnderline -> TextDecoration.Underline
                                                    isStrikethrough -> TextDecoration.LineThrough
                                                    else -> TextDecoration.None
                                                },
                                                textAlign = textAlign,
                                                textDirection = if (textAlign == TextAlign.Right) TextDirection.Rtl else TextDirection.Ltr,
                                                color = actualTextColor
                                            ),
                                            cursorBrush = SolidColor(ScholarBlue),
                                            decorationBox = { innerTextField ->
                                                if (documentText.isEmpty() && lines.isEmpty()) {
                                                    Text(
                                                        text = "Ketik transkripsi untuk Lembar ${partForPage?.pageNumber ?: (pageIdx + 1)} di sini...\n\n" +
                                                                "Atau klik tombol 'Deteksi Baris & HTR Naskah' di atas untuk mengisi transkripsi secara otomatis.",
                                                        fontSize = (fontSizePt * (zoomPercent / 100f)).sp,
                                                        lineHeight = (fontSizePt * lineSpacingMultiplier * (zoomPercent / 100f)).sp,
                                                        fontFamily = selectedTypeface.fontFamily,
                                                        textAlign = textAlign,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }

                                    // Pinned Bottom Page Footer
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(
                                            color = Color(0xFFE2E8F0),
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "— ${partForPage?.pageNumber ?: (pageIdx + 1)} —",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }
                }

                WordDocumentMode.LINE_SYNCHRONIZED -> {
                    // MODE 2: Selaras Naskah (Interlinear Line-by-Line with Original Manuscript Line Snippet Crop)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = paperBg,
                            shape = RoundedCornerShape(4.dp),
                            shadowElevation = 6.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, paperBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header Selaras Naskah
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ViewStream, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Transkripsi Selaras Citra Naskah (Lembar ${activePart?.pageNumber ?: (activePageIndex + 1)})",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkMode) DarkText else ParchmentText
                                        )
                                    }
                                    Text(
                                        text = "${lines.size} Baris Terdeteksi",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ScholarBlue
                                    )
                                }

                                HorizontalDivider(color = paperBorder, thickness = 0.5.dp)

                                if (lines.isEmpty()) {
                                    EmptyPageExtractionCard(
                                        pageNumber = activePart?.pageNumber ?: (activePageIndex + 1),
                                        isDarkMode = isDarkMode,
                                        onRunExtraction = onRunAutoSegmentation
                                    )
                                } else {
                                    lines.forEach { line ->
                                        val isSelected = line.id == selectedLineId
                                        val lineText = transcriptionMap[line.id]?.text ?: ""

                                        // Render Interlinear Line Card with Manuscript Snippet Crop
                                        InterlinearLineCard(
                                            line = line,
                                            text = lineText,
                                            isSelected = isSelected,
                                            imageResName = activePart?.imageResName,
                                            imageUri = activePart?.imageUri,
                                            selectedTypeface = selectedTypeface,
                                            fontSizePt = fontSizePt,
                                            isBold = isBold,
                                            isItalic = isItalic,
                                            textAlign = textAlign,
                                            actualTextColor = actualTextColor,
                                            isDarkMode = isDarkMode,
                                            onLineSelected = { onLineSelected(line.id) },
                                            onTextChange = { onTextChange(line.id, it, selectedLayerId) },
                                            onOpenLineEdit = { onOpenLineEditModal(line.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                WordDocumentMode.SPLIT_REFERENCE -> {
                    // MODE 3: Split Naskah & Teks (Side-by-Side Reference Preview)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Left Pane: Interactive Manuscript Reference with Active Line Highlight
                        Surface(
                            color = if (isDarkMode) DarkSurface else ParchmentSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, paperBorder),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ManuscriptOverlayCanvas(
                                    imageResName = activePart?.imageResName,
                                    blocks = blocks,
                                    lines = lines,
                                    selectedLineId = selectedLineId,
                                    canvasToolMode = CanvasToolMode.SELECT_LINE,
                                    scale = 1.0f,
                                    onScaleChange = {},
                                    onLineSelected = onLineSelected,
                                    onPolygonCompleted = {},
                                    onBaselineCompleted = {},
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    color = if (isDarkMode) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "Citra Naskah Hal. ${activePart?.pageNumber ?: (activePageIndex + 1)}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) GoldLight else GoldDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Right Pane: Active Line Text Editor
                        Surface(
                            color = paperBg,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, paperBorder),
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Editor Transkripsi Baris",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScholarBlue
                                )

                                if (lines.isEmpty()) {
                                    EmptyPageExtractionCard(
                                        pageNumber = activePart?.pageNumber ?: (activePageIndex + 1),
                                        isDarkMode = isDarkMode,
                                        onRunExtraction = onRunAutoSegmentation
                                    )
                                } else {
                                    lines.forEach { line ->
                                        val isSelected = line.id == selectedLineId
                                        val lineText = transcriptionMap[line.id]?.text ?: ""

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) wordBlue.copy(alpha = 0.15f) else (if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant))
                                                .border(
                                                    width = if (isSelected) 1.dp else 0.5.dp,
                                                    color = if (isSelected) GoldPrimary else paperBorder,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .clickable { onLineSelected(line.id) }
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) GoldPrimary else (if (isDarkMode) DarkBorder else ParchmentBorder)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${line.orderIndex}",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else (if (isDarkMode) DarkText else ParchmentText)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            BasicTextField(
                                                value = lineText,
                                                onValueChange = { onTextChange(line.id, it, selectedLayerId) },
                                                modifier = Modifier.weight(1f).testTag("line_editor_${line.id}"),
                                                textStyle = TextStyle(
                                                    fontSize = fontSizePt.sp,
                                                    fontFamily = selectedTypeface.fontFamily,
                                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                                    textAlign = textAlign,
                                                    textDirection = if (textAlign == TextAlign.Right) TextDirection.Rtl else TextDirection.Ltr,
                                                    color = actualTextColor
                                                ),
                                                cursorBrush = SolidColor(GoldPrimary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 7. Microsoft Word / Folia Status Bar (Bottom Office Strip with Auto-Hide) ---
        AnimatedVisibility(
            visible = effectiveBarsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = wordBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("writer_status_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Page, Words, Language
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hal. ${activePageIndex + 1} dari $pageCount", fontSize = 9.5.sp, color = Color.White)
                        Text("•", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("$wordCount kata", fontSize = 9.5.sp, color = Color.White)
                        Text("•", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("Arab / Jawi", fontSize = 9.5.sp, color = Color.White)
                    }

                    // Right: Zoom Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${zoomPercent.toInt()}%", fontSize = 9.5.sp, color = Color.White, fontWeight = FontWeight.Bold)

                        IconButton(
                            onClick = {
                                triggerInteraction()
                                zoomPercent = (zoomPercent - 10f).coerceIn(50f, 200f)
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Perkecil", tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                        IconButton(
                            onClick = {
                                triggerInteraction()
                                zoomPercent = (zoomPercent + 10f).coerceIn(50f, 200f)
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Perbesar", tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
        }

        // --- 8. Floating "Alat" Reveal Pill (Shown only when bars are auto-hidden) ---
        AnimatedVisibility(
            visible = !effectiveBarsVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
        ) {
            Surface(
                color = (if (isDarkMode) DarkSurface else Color.White).copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ribbonBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        triggerInteraction()
                    }
                    .testTag("writer_fullscreen_reveal_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tampilkan Alat",
                        tint = wordBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Alat",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) DarkText else Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}

/**
 * Interlinear Line Card: displays the actual manuscript handwriting line crop
 * directly paired above the transcription text input field!
 */
@Composable
private fun InterlinearLineCard(
    line: LineSegmentEntity,
    text: String,
    isSelected: Boolean,
    imageResName: String?,
    imageUri: String?,
    selectedTypeface: TypefaceOption,
    fontSizePt: Float,
    isBold: Boolean,
    isItalic: Boolean,
    textAlign: TextAlign,
    actualTextColor: Color,
    isDarkMode: Boolean,
    onLineSelected: () -> Unit,
    onTextChange: (String) -> Unit,
    onOpenLineEdit: () -> Unit
) {
    val context = LocalContext.current
    val snippetBitmap = remember(line.id, line.partId, imageResName, imageUri) {
        ManuscriptSnippetHelper.cropLineSnippet(context, line, imageResName, imageUri)
    }

    val cardBg = if (isSelected) {
        if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF)
    } else {
        if (isDarkMode) DarkSurfaceVariant else Color(0xFFF8FAFC)
    }

    val borderColor = if (isSelected) ScholarBlue else (if (isDarkMode) DarkBorder else Color(0xFFE2E8F0))

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLineSelected() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Line Number, Typology, Confidence & Boundary Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ScholarBlue else (if (isDarkMode) ScholarBlueDark else Color(0xFF334155))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${line.orderIndex}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (line.typology) {
                                    "Matan" -> Color(0xFFDC2626).copy(alpha = 0.15f)
                                    "Syarah" -> Color(0xFFD97706).copy(alpha = 0.15f)
                                    "Marginalia" -> Color(0xFF7C3AED).copy(alpha = 0.15f)
                                    else -> ScholarBlue.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = line.typology,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (line.typology) {
                                "Matan" -> Color(0xFFDC2626)
                                "Syarah" -> Color(0xFFD97706)
                                "Marginalia" -> Color(0xFF7C3AED)
                                else -> ScholarBlue
                            }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${(line.confidence * 100).toInt()}% ONNX",
                        fontSize = 8.5.sp,
                        color = if (isDarkMode) DarkMuted else Color(0xFF475569)
                    )

                    IconButton(
                        onClick = onOpenLineEdit,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Edit Batas Baris",
                            tint = ScholarBlue,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // Middle: Original Manuscript Line Handwriting Snippet
            if (snippetBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = snippetBitmap.asImageBitmap(),
                        contentDescription = "Potongan Baris Manuskrip Asli #${line.orderIndex}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom: Editable Line Transcription Field
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDarkMode) DarkSurface else Color.White)
                    .border(0.5.dp, if (isSelected) ScholarBlue else (if (isDarkMode) DarkBorder else Color(0xFFCBD5E1)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("interlinear_line_input_${line.id}"),
                textStyle = TextStyle(
                    fontSize = fontSizePt.sp,
                    lineHeight = (fontSizePt * 1.5f).sp,
                    fontFamily = selectedTypeface.fontFamily,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    textAlign = textAlign,
                    textDirection = if (textAlign == TextAlign.Right) TextDirection.Rtl else TextDirection.Ltr,
                    color = actualTextColor
                ),
                cursorBrush = SolidColor(ScholarBlue),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Ketik transkripsi baris #${line.orderIndex} di sini...",
                            fontSize = fontSizePt.sp,
                            fontFamily = selectedTypeface.fontFamily,
                            textAlign = textAlign,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    inner()
                }
            )
        }
    }
}

/**
 * Interactive card shown when a new / empty page sheet is opened
 */
@Composable
private fun EmptyPageExtractionCard(
    pageNumber: Int,
    isDarkMode: Boolean,
    onRunExtraction: () -> Unit
) {
    Surface(
        color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFEF3C7),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkMode) GoldLight.copy(alpha = 0.5f) else Color(0xFFF59E0B)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isDarkMode) GoldLight else GoldDark, modifier = Modifier.size(16.dp))
                Text(
                    text = "Lembar $pageNumber Belum Memiliki Segmen Baris",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) GoldLight else GoldDark
                )
            }

            Text(
                text = "Jalankan model PP-OCRv5 & Muharaf HTR untuk mendeteksi baris dan mengisi transkripsi dari citra naskah lembar ini.",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = if (isDarkMode) DarkText else Color(0xFF78350F)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onRunExtraction,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Deteksi Baris & HTR Lembar $pageNumber", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun WordMiniButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDarkMode: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant)
            .border(0.5.dp, if (isDarkMode) DarkBorder else ParchmentBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDarkMode) DarkText else ParchmentText
        )
    }
}

@Composable
private fun WordToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkMode: Boolean = false,
    onClick: () -> Unit
) {
    val activeColor = if (isDarkMode) ScholarBlueDark else ScholarBlue
    val unselectedBg = if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant
    val unselectedBorder = if (isDarkMode) DarkBorder else ParchmentBorder

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSelected) activeColor else unselectedBg)
            .border(0.5.dp, if (isSelected) GoldPrimary else unselectedBorder, RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else (if (isDarkMode) DarkText else ParchmentText),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun WordVerticalDivider(isDarkMode: Boolean = false) {
    Box(
        modifier = Modifier
            .height(18.dp)
            .width(1.dp)
            .background(if (isDarkMode) DarkBorder else ParchmentBorder)
    )
}

@Composable
private fun WordInteractiveRuler(
    leftIndentCm: Float,
    rightIndentCm: Float,
    firstLineIndentCm: Float,
    isDarkMode: Boolean = false,
    onLeftIndentChanged: (Float) -> Unit,
    onRightIndentChanged: (Float) -> Unit,
    onFirstLineIndentChanged: (Float) -> Unit
) {
    val rulerBg = if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant
    val rulerBorder = if (isDarkMode) DarkBorder else ParchmentBorder
    val rulerText = if (isDarkMode) DarkMuted else ParchmentMuted

    Surface(
        color = rulerBg,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .border(width = 0.5.dp, color = rulerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (cm in 0..16) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(if (cm % 5 == 0) 8.dp else 4.dp)
                            .background(rulerText)
                    )
                    if (cm % 2 == 0 && cm != 0) {
                        Text(
                            text = "$cm",
                            fontSize = 7.5.sp,
                            color = rulerText,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
