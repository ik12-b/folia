package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.drawBehind
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DocumentInfoDialog
import com.example.ui.components.ExportDialog
import com.example.ui.components.GlyphQADialog
import com.example.ui.components.KeyboardShortcutsDialog
import com.example.ui.components.LayerManagerDialog
import com.example.ui.components.LineEditorModal
import com.example.ui.components.ModelManagerDialog
import com.example.ui.components.TextAlignmentDialog
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMuted
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkText
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
import com.example.ui.viewmodel.FoliaViewModel
import com.example.ui.viewmodel.ManuscriptProgress
import com.example.ui.viewmodel.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(
    viewModel: FoliaViewModel,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val document by viewModel.currentDocument.collectAsStateWithLifecycle()
    val metadata by viewModel.currentMetadata.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    val activePageIndex by viewModel.activePageIndex.collectAsStateWithLifecycle()
    val activePart by viewModel.activePart.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val selectedLayerId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val transcriptions by viewModel.transcriptions.collectAsStateWithLifecycle()
    val selectedLineId by viewModel.selectedLineId.collectAsStateWithLifecycle()
    val canvasToolMode by viewModel.canvasToolMode.collectAsStateWithLifecycle()
    val selectedTypology by viewModel.selectedTypology.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val manuscriptProgress by viewModel.manuscriptProgress.collectAsStateWithLifecycle()

    // System-wide Dark Mode State
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    val showAlignmentDialog by viewModel.showAlignmentDialog.collectAsStateWithLifecycle()
    val showGlyphQADialog by viewModel.showGlyphQADialog.collectAsStateWithLifecycle()
    val showExportDialog by viewModel.showExportDialog.collectAsStateWithLifecycle()
    val showModelManagerDialog by viewModel.showModelManagerDialog.collectAsStateWithLifecycle()
    val showDocInfoDialog by viewModel.showDocInfoDialog.collectAsStateWithLifecycle()
    val showLayerManagerDialog by viewModel.showLayerManagerDialog.collectAsStateWithLifecycle()
    val showPpOcrConfigDialog by viewModel.showPpOcrConfigDialog.collectAsStateWithLifecycle()
    val ppOcrConfig by viewModel.ppOcrConfig.collectAsStateWithLifecycle()
    val showLineEditModal by viewModel.showLineEditModal.collectAsStateWithLifecycle()
    val editingLineId by viewModel.editingLineId.collectAsStateWithLifecycle()

    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val alignmentResult by viewModel.alignmentResult.collectAsStateWithLifecycle()
    val exportedContent by viewModel.exportedContent.collectAsStateWithLifecycle()
    val exportedFormat by viewModel.exportedFormat.collectAsStateWithLifecycle()
    val exportedPdfResult by viewModel.exportedPdfResult.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Horizontal Pager for Seamless Left/Right Swiping between Manuscript Viewer (0) & Rich Text Editor (1)
    val workspacePagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    // Overflow menu & dialog states
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showKeyboardShortcutsDialog by remember { mutableStateOf(false) }

    // Full-screen and auto-hiding bars state across the entire workspace
    var isBarsVisible by remember { mutableStateOf(true) }
    var isBarsPinned by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val autoHideDelayMs = 3500L

    fun triggerWorkspaceInteraction() {
        isBarsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(isBarsVisible, isBarsPinned, lastInteractionTime, showMenuDropdown) {
        if (isBarsVisible && !isBarsPinned && !showMenuDropdown) {
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = (autoHideDelayMs - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            if (!showMenuDropdown) {
                isBarsVisible = false
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Delete confirmation dialog
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Hapus Lembar Halaman Ini?") },
                    text = {
                        Text("Lembar ke-${activePart?.pageNumber ?: (activePageIndex + 1)} beserta semua segmen garis dan transkripsinya akan dihapus secara permanen.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                viewModel.deleteCurrentPage()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("Hapus", color = Color.White)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            // Dialogs
            if (showKeyboardShortcutsDialog) {
                KeyboardShortcutsDialog(
                    onDismiss = { showKeyboardShortcutsDialog = false }
                )
            }

            if (showAlignmentDialog) {
                val curText = lines.joinToString("\n") { l ->
                    transcriptions.find { it.lineId == l.id }?.text ?: ""
                }
                TextAlignmentDialog(
                    currentTranscriptions = curText,
                    alignmentResult = alignmentResult,
                    onRunAlignment = { viewModel.runTextAlignment(it) },
                    onDismiss = { viewModel.setShowAlignmentDialog(false) }
                )
            }

            if (showGlyphQADialog) {
                GlyphQADialog(
                    transcriptions = transcriptions,
                    onDismiss = { viewModel.setShowGlyphQADialog(false) }
                )
            }

            if (showExportDialog) {
                ExportDialog(
                    content = exportedContent ?: "",
                    currentFormat = exportedFormat,
                    pdfResult = exportedPdfResult,
                    onFormatChange = { viewModel.exportDocument(it) },
                    onDismiss = { viewModel.setShowExportDialog(false) }
                )
            }

            if (showModelManagerDialog) {
                ModelManagerDialog(
                    models = allModels,
                    onConfigurePpOcr = {
                        viewModel.setShowModelManagerDialog(false)
                        viewModel.setShowPpOcrConfigDialog(true)
                    },
                    onActivateModel = { model -> viewModel.setActiveModel(model) },
                    onImportModel = { resolver, uri, type -> viewModel.importOcrModelFromUri(resolver, uri, type) },
                    onDismiss = { viewModel.setShowModelManagerDialog(false) }
                )
            }

            if (showPpOcrConfigDialog) {
                com.example.ui.components.PpOcrConfigDialog(
                    currentConfig = ppOcrConfig,
                    onApplyAndRun = { cfg ->
                        viewModel.runAutoSegmentation(cfg)
                        viewModel.setShowPpOcrConfigDialog(false)
                    },
                    onDismiss = { viewModel.setShowPpOcrConfigDialog(false) }
                )
            }

            if (showDocInfoDialog) {
                DocumentInfoDialog(
                    metadata = metadata,
                    onSave = { author, date, copier, repo, shelfmark, notes ->
                        viewModel.updateMetadata(author, date, copier, repo, shelfmark, notes)
                    },
                    onDismiss = { viewModel.setShowDocInfoDialog(false) }
                )
            }

            if (showLayerManagerDialog) {
                LayerManagerDialog(
                    layers = layers,
                    selectedLayerId = selectedLayerId,
                    onSelectLayer = { viewModel.selectLayer(it) },
                    onCreateLayer = { name, copySourceId -> viewModel.addNewLayer(name, copySourceId) },
                    onDeleteLayer = { viewModel.deleteLayer(it) },
                    onDismiss = { viewModel.setShowLayerManagerDialog(false) }
                )
            }

            // Line Editor Modal - Scholarly Manual Boundary Adjustment
            if (showLineEditModal) {
                val currentLineToEdit = lines.find { it.id == editingLineId } ?: lines.firstOrNull()
                if (currentLineToEdit != null) {
                    val lineTranscription = transcriptions.find { it.lineId == currentLineToEdit.id }?.text
                    LineEditorModal(
                        line = currentLineToEdit,
                        allLines = lines,
                        imageResName = activePart?.imageResName,
                        imageUri = activePart?.imageUri,
                        transcriptionText = lineTranscription,
                        onSaveBoundaries = { lineId, baselineJson, maskJson, typology ->
                            viewModel.saveLineBoundaries(lineId, baselineJson, maskJson, typology)
                        },
                        onSelectLine = { targetId ->
                            viewModel.setEditingLineId(targetId)
                        },
                        onDismiss = { viewModel.closeLineEditModal() }
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isCtrl = keyEvent.isCtrlPressed
                    val isAlt = keyEvent.isAltPressed

                    when {
                        // 1. Swipe / Tab Switching Shortcuts (Ctrl+1 = Naskah Asli, Ctrl+2 = Transkripsi, Ctrl+M = Toggle)
                        (isCtrl || isAlt) && keyEvent.key == Key.One -> {
                            coroutineScope.launch {
                                workspacePagerState.animateScrollToPage(0)
                            }
                            true
                        }
                        (isCtrl || isAlt) && keyEvent.key == Key.Two -> {
                            coroutineScope.launch {
                                workspacePagerState.animateScrollToPage(1)
                            }
                            true
                        }
                        (isCtrl || isAlt) && keyEvent.key == Key.M -> {
                            coroutineScope.launch {
                                val targetPage = if (workspacePagerState.currentPage == 0) 1 else 0
                                workspacePagerState.animateScrollToPage(targetPage)
                            }
                            true
                        }
                        // 2. Line Navigation (Previous): Alt+Up, Ctrl+Up, LeftBracket [
                        ((isAlt || isCtrl) && keyEvent.key == Key.DirectionUp) || keyEvent.key == Key.LeftBracket -> {
                            if (lines.isNotEmpty()) {
                                val curIdx = lines.indexOfFirst { it.id == selectedLineId }
                                if (curIdx > 0) {
                                    viewModel.selectLine(lines[curIdx - 1].id)
                                } else if (curIdx == -1) {
                                    viewModel.selectLine(lines.last().id)
                                }
                            }
                            true
                        }
                        // Line Navigation (Next): Alt+Down, Ctrl+Down, RightBracket ]
                        ((isAlt || isCtrl) && keyEvent.key == Key.DirectionDown) || keyEvent.key == Key.RightBracket -> {
                            if (lines.isNotEmpty()) {
                                val curIdx = lines.indexOfFirst { it.id == selectedLineId }
                                if (curIdx in 0 until lines.size - 1) {
                                    viewModel.selectLine(lines[curIdx + 1].id)
                                } else if (curIdx == -1) {
                                    viewModel.selectLine(lines.first().id)
                                }
                            }
                            true
                        }
                        // 3. Page Navigation: PageUp or Alt+Left
                        keyEvent.key == Key.PageUp || (isAlt && keyEvent.key == Key.DirectionLeft) -> {
                            if (activePageIndex > 0) {
                                viewModel.selectPage(activePageIndex - 1)
                            }
                            true
                        }
                        // Page Navigation: PageDown or Alt+Right
                        keyEvent.key == Key.PageDown || (isAlt && keyEvent.key == Key.DirectionRight) -> {
                            if (activePageIndex < parts.size - 1) {
                                viewModel.selectPage(activePageIndex + 1)
                            }
                            true
                        }
                        // 4. Scholarly Actions: Ctrl+E -> Ekspor PDF
                        isCtrl && keyEvent.key == Key.E -> {
                            viewModel.exportDocument("PDF_OVERLAY")
                            true
                        }
                        // Line Detection PP-OCRv5: Ctrl+D
                        isCtrl && keyEvent.key == Key.D -> {
                            viewModel.runAutoSegmentation()
                            true
                        }
                        // Line Boundary Editor Modal: Ctrl+B or Alt+B
                        (isCtrl || isAlt) && keyEvent.key == Key.B -> {
                            viewModel.openLineEditModal()
                            true
                        }
                        // Reorder Lines Scholarly (Urutan Baca Filologi): Ctrl+R or Alt+R
                        (isCtrl || isAlt) && keyEvent.key == Key.R -> {
                            viewModel.reorderLinesScholarly()
                            true
                        }
                        // Layer Manager: Ctrl+L
                        isCtrl && keyEvent.key == Key.L -> {
                            viewModel.setShowLayerManagerDialog(true)
                            true
                        }
                        // Document Info: Ctrl+I
                        isCtrl && keyEvent.key == Key.I -> {
                            viewModel.setShowDocInfoDialog(true)
                            true
                        }
                        // Keyboard Shortcuts Guide: F1 or Ctrl+/
                        keyEvent.key == Key.F1 || (isCtrl && keyEvent.key == Key.Slash) -> {
                            showKeyboardShortcutsDialog = true
                            true
                        }
                        // Escape: Clear line selection
                        keyEvent.key == Key.Escape -> {
                            if (selectedLineId != null) {
                                viewModel.selectLine(-1L)
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("workspace_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isDarkTheme) DarkBg else ParchmentBg)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        triggerWorkspaceInteraction()
                        waitForUpOrCancellation()
                    }
                }
        ) {
            // --- Ultra-Compact Slim Top Bar (Single Row, 38dp) & Progress Tracker with Auto-Hide ---
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = if (isDarkTheme) DarkSurface else Color.White,
                        tonalElevation = 2.dp,
                        shadowElevation = if (isDarkTheme) 0.dp else 1.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isDarkTheme) DarkBorder else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Raised from 38dp to 48dp -- Android's documented
                        // minimum touch target size. At 38dp, the icon
                        // buttons inside (dark mode toggle, pin bars,
                        // overflow menu) had to be squeezed to 28dp each,
                        // well under half the recommended size, making
                        // them genuinely hard to tap accurately on a real
                        // phone screen with a finger (as opposed to a
                        // mouse cursor in a preview/emulator).
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // Vertical padding added so the tap target for
                        // this frequently-used back navigation reaches
                        // closer to Android's 48dp minimum -- previously
                        // it had no padding at all, so the tappable area
                        // was only as tall as the 16dp icon/text content.
                        modifier = Modifier
                            .clickable { onBackToLibrary() }
                            .padding(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = if (isDarkTheme) DarkMuted else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = document?.title ?: "folia",
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) DarkText else Color(0xFF0F172A),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    // Center: 2-Way Tab Switcher (📄 Naskah Asli | ✍️ Editor Transkripsi)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDarkTheme) DarkSurfaceVariant else Color(0xFFEFF6FF))
                            .border(
                                0.5.dp,
                                if (isDarkTheme) DarkBorder else Color(0xFFDBEAFE),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Tab: Naskah Asli (Page 0)
                            val isViewerActive = workspacePagerState.currentPage == 0
                            val viewerBg by animateColorAsState(
                                targetValue = if (isViewerActive) (if (isDarkTheme) ScholarBlueDark else ScholarBlue) else Color.Transparent,
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                label = "viewer_tab_bg"
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(viewerBg)
                                    .clickable {
                                        keyboardController?.hide()
                                        coroutineScope.launch {
                                            workspacePagerState.animateScrollToPage(0)
                                        }
                                    }
                                    // Vertical padding raised from 4dp to
                                    // 12dp: with the 13dp icon and 11sp
                                    // text inside, the old padding gave a
                                    // total tap height of ~24dp for a
                                    // frequently-used tab switcher --
                                    // roughly half Android's documented
                                    // 48dp minimum touch target, easy to
                                    // mis-tap on a real phone screen.
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (isViewerActive) Color.White else (if (isDarkTheme) DarkMuted else Color(0xFF475569)),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Naskah Asli",
                                    fontSize = 11.sp,
                                    fontWeight = if (isViewerActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isViewerActive) Color.White else (if (isDarkTheme) DarkMuted else Color(0xFF475569))
                                )
                            }

                            // 2. Tab: Editor Transkripsi (Page 1)
                            val isWriterActive = workspacePagerState.currentPage == 1
                            val writerBg by animateColorAsState(
                                targetValue = if (isWriterActive) (if (isDarkTheme) ScholarBlueDark else ScholarBlue) else Color.Transparent,
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                label = "writer_tab_bg"
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(writerBg)
                                    .clickable {
                                        coroutineScope.launch {
                                            workspacePagerState.animateScrollToPage(1)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = if (isWriterActive) Color.White else (if (isDarkTheme) DarkMuted else Color(0xFF475569)),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Transkripsi",
                                    fontSize = 11.sp,
                                    fontWeight = if (isWriterActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isWriterActive) Color.White else (if (isDarkTheme) DarkMuted else Color(0xFF475569))
                                )
                            }
                        }
                    }

                    // Right: Actions (Dark Mode Toggle & Overflow Menu)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.size(44.dp).testTag("dark_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme) "Beralih ke Mode Terang" else "Beralih ke Mode Gelap",
                                tint = if (isDarkTheme) GoldLight else ScholarBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                triggerWorkspaceInteraction()
                                isBarsPinned = !isBarsPinned
                            },
                            modifier = Modifier.size(44.dp).testTag("workspace_pin_bars_button")
                        ) {
                            Icon(
                                imageVector = if (isBarsPinned) Icons.Default.PushPin else Icons.Default.Fullscreen,
                                contentDescription = if (isBarsPinned) "Sematkan Bilah" else "Layar Penuh Otomatis",
                                tint = if (isBarsPinned) GoldPrimary else (if (isDarkTheme) DarkMuted else Color(0xFF64748B)),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenuDropdown = true },
                                modifier = Modifier.size(44.dp).testTag("workspace_overflow_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu Opsi Workspace",
                                    tint = if (isDarkTheme) DarkMuted else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false },
                                modifier = Modifier.background(if (isDarkTheme) DarkSurface else Color.White)
                            ) {
                                // Deteksi Baris and Editor Batas Baris are
                                // intentionally NOT duplicated here -- they
                                // already have dedicated, always-visible
                                // buttons in the manuscript viewer's own
                                // toolbar (see ViewerStudioView). Having the
                                // same action reachable from two different
                                // menus with no visual indication of which
                                // is "the" way to do it was confusing users
                                // trying to find the core OCR action; this
                                // menu is now reserved for actions that
                                // genuinely have no other entry point.
                                DropdownMenuItem(
                                    text = { Text("Urutkan Urutan Baca (Filologi)", fontSize = 12.sp, color = if (isDarkTheme) DarkText else Color(0xFF0F172A)) },
                                    leadingIcon = { Icon(Icons.Default.AutoGraph, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.reorderLinesScholarly()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Kelola Layer Transkripsi", fontSize = 12.sp, color = if (isDarkTheme) DarkText else Color(0xFF0F172A)) },
                                    leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = if (isDarkTheme) DarkMuted else Color(0xFF64748B), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.setShowLayerManagerDialog(true)
                                    }
                                )
                                HorizontalDivider(color = if (isDarkTheme) DarkBorder else Color(0xFFE2E8F0))
                                DropdownMenuItem(
                                    text = { Text("Informasi Manuskrip", fontSize = 12.sp, color = if (isDarkTheme) DarkText else Color(0xFF0F172A)) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDarkTheme) DarkMuted else Color(0xFF64748B), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.setShowDocInfoDialog(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Model OCR & HTR On-Device", fontSize = 12.sp, color = if (isDarkTheme) DarkText else Color(0xFF0F172A)) },
                                    leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = if (isDarkTheme) DarkMuted else Color(0xFF64748B), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.setShowModelManagerDialog(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Pintasan Keyboard (Ctrl+/)", fontSize = 12.sp, color = if (isDarkTheme) DarkText else Color(0xFF0F172A)) },
                                    leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null, tint = if (isDarkTheme) DarkMuted else Color(0xFF64748B), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        showKeyboardShortcutsDialog = true
                                    }
                                )
                                HorizontalDivider(color = if (isDarkTheme) DarkBorder else Color(0xFFE2E8F0))
                                DropdownMenuItem(
                                    text = { Text("Hapus Lembar Ini", fontSize = 12.sp, color = Color(0xFFEF4444)) },
                                    onClick = {
                                        showMenuDropdown = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- Manuscript Lines Progress Tracker (Visual Progress Bar) ---
            ManuscriptProgressBar(
                progress = manuscriptProgress,
                isDarkTheme = isDarkTheme
            )
                }
            }

            // --- Main Content: Horizontal Pager for Seamless Left/Right Swiping between Manuscript Viewer & Rich Text Editor ---
            HorizontalPager(
                state = workspacePagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("workspace_horizontal_pager")
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        // Page 0: Naskah Asli (Adobe Acrobat Style PDF & Vector Manuscript Viewer)
                        ViewerStudioView(
                            document = document,
                            parts = parts,
                            activePageIndex = activePageIndex,
                            activePart = activePart,
                            blocks = blocks,
                            lines = lines,
                            selectedLineId = selectedLineId,
                            canvasToolMode = canvasToolMode,
                            selectedTypology = selectedTypology,
                            onSelectPage = { viewModel.selectPage(it) },
                            onAddNewPage = { viewModel.addNewPage(insertAfterCurrent = true) },
                            onDeleteCurrentPage = { showDeleteConfirmDialog = true },
                            onLineSelected = { viewModel.selectLine(it) },
                            onToolModeChanged = { viewModel.setCanvasToolMode(it) },
                            onTypologyChanged = { viewModel.setSelectedTypology(it) },
                            onRunAutoSegmentation = { viewModel.runAutoSegmentation() },
                            onRunAutoTranscription = { viewModel.runAutoTranscription() },
                            onOpenPpOcrConfig = { viewModel.setShowPpOcrConfigDialog(true) },
                            onPolygonCompleted = { viewModel.addManualRegion(it) },
                            onBaselineCompleted = { viewModel.addManualBaseline(it) },
                            onOpenLineEditModal = { viewModel.openLineEditModal(it) },
                            onReorderLinesScholarly = { viewModel.reorderLinesScholarly() },
                            onSwipeToWriterHint = {
                                coroutineScope.launch {
                                    workspacePagerState.animateScrollToPage(1)
                                }
                            },
                            isDarkMode = isDarkTheme,
                            isBarsVisible = isBarsVisible,
                            onUserInteraction = { triggerWorkspaceInteraction() },
                            isBarsPinned = isBarsPinned,
                            onTogglePin = { isBarsPinned = !isBarsPinned },
                            onToggleBars = { isBarsVisible = !isBarsVisible },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> {
                        // Page 1: Transkripsi (Microsoft Word Style Rich Document Editor)
                        WriterView(
                            document = document,
                            parts = parts,
                            activePageIndex = activePageIndex,
                            activePart = activePart,
                            layers = layers,
                            selectedLayerId = selectedLayerId,
                            lines = lines,
                            blocks = blocks,
                            transcriptions = transcriptions,
                            selectedLineId = selectedLineId,
                            isDarkMode = isDarkTheme,
                            isBarsVisible = isBarsVisible,
                            onUserInteraction = { triggerWorkspaceInteraction() },
                            isBarsPinned = isBarsPinned,
                            onTogglePin = { isBarsPinned = !isBarsPinned },
                            onToggleBars = { isBarsVisible = !isBarsVisible },
                            onSelectPage = { viewModel.selectPage(it) },
                            onAddNewPage = { viewModel.addNewPage(insertAfterCurrent = true) },
                            onDeleteCurrentPage = { showDeleteConfirmDialog = true },
                            onLineSelected = { viewModel.selectLine(it) },
                            onTextChange = { lineId, text, layerId -> viewModel.updateLineText(lineId, text, layerId) },
                            onContinuousTextChange = { fullText, layerId -> viewModel.updatePageContinuousText(fullText, layerId) },
                            onCopyTextToLayer = { lineId, text, targetLayerId -> viewModel.copyTextToLayer(lineId, text, targetLayerId) },
                            onUndo = { viewModel.performUndo() },
                            onRedo = { viewModel.performRedo() },
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onOpenLayerManager = { viewModel.setShowLayerManagerDialog(true) },
                            onNormalize = { viewModel.applyNormalizationToActiveLine() },
                            onFixArabicSpacing = { viewModel.fixArabicSpacingForActiveLineOrPage() },
                            onStripTashkeel = { viewModel.stripTashkeelFromActiveLine() },
                            onRunAutoSegmentation = { viewModel.runAutoSegmentation() },
                            onOpenLineEditModal = { viewModel.openLineEditModal(it) },
                            onOpenAlignment = { viewModel.setShowAlignmentDialog(true) },
                            onOpenGlyphQA = { viewModel.setShowGlyphQADialog(true) },
                            onOpenExport = { viewModel.exportDocument("PDF_OVERLAY") },
                            onOpenDocInfo = { viewModel.setShowDocInfoDialog(true) },
                            onDeleteSelectedLine = { viewModel.deleteSelectedLine() },
                            onSwipeToViewerHint = {
                                coroutineScope.launch {
                                    workspacePagerState.animateScrollToPage(0)
                                }
                            },
                            onBackToLibrary = onBackToLibrary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ManuscriptProgressBar(
    progress: ManuscriptProgress,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedOverall by animateFloatAsState(
        targetValue = progress.overallPercentage,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "manuscript_progress_anim"
    )

    val progressPercentInt = (progress.overallPercentage * 100f).toInt()
    val isComplete = progress.totalLines > 0 && progress.completedLines == progress.totalLines

    // No own Surface elevation/border here on purpose: this bar sits
    // directly beneath the nav Row's Surface above it, and the two used
    // to each draw their own border + shadow, reading as two stacked
    // boxes rather than one continuous top bar. A shared background with
    // just a bottom border keeps it visually seamless with the bar above
    // while still separating it from the manuscript canvas below.
    Surface(
        color = if (isDarkTheme) DarkSurface else Color.White,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 0.5.dp.toPx()
                drawLine(
                    color = if (isDarkTheme) DarkBorder else Color(0xFFE2E8F0),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth
                )
            }
            .testTag("manuscript_progress_bar_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Icon + Label + Completed lines count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.AutoGraph,
                        contentDescription = "Status Progres Transkripsi",
                        tint = if (isComplete) Color(0xFF10B981) else (if (isDarkTheme) GoldLight else GoldDark),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Progres Manuskrip:",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkTheme) DarkText else Color(0xFF0F172A)
                    )
                    Text(
                        text = if (progress.totalLines > 0) {
                            "${progress.completedLines}/${progress.totalLines} Baris Selesai"
                        } else {
                            "0 Baris (Jalankan Deteksi Baris)"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isComplete) Color(0xFF10B981) else if (isDarkTheme) ScholarBlueLight else ScholarBlue
                    )
                }

                // Right: Page specific mini info & Percentage badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (progress.pageTotalLines > 0) {
                            "Hal. ini: ${progress.pageCompletedLines}/${progress.pageTotalLines}"
                        } else {
                            "Hal. ini: 0 Baris"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) DarkMuted else Color(0xFF64748B)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isComplete) Color(0xFF059669).copy(alpha = 0.2f)
                                else if (isDarkTheme) DarkSurfaceVariant
                                else Color(0xFFEFF6FF)
                            )
                            .border(
                                0.5.dp,
                                if (isDarkTheme) DarkBorder else Color(0xFFDBEAFE),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = if (progress.pageTotalLines == 0) "Kosong" else "$progressPercentInt%",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isComplete) Color(0xFF10B981) else (if (isDarkTheme) GoldLight else GoldDark)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Visual Progress Track (Multi-color responsive gradient bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDarkTheme) DarkBorder else Color(0xFFE2E8F0))
            ) {
                if (animatedOverall > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedOverall.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isComplete) {
                                        listOf(Color(0xFF10B981), Color(0xFF34D399))
                                    } else {
                                        listOf(ScholarBlue, GoldPrimary, ScholarBlueLight)
                                    }
                                )
                            )
                    )
                }
            }
        }
    }
}
