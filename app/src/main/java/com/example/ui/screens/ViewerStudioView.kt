package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.data.model.BlockRegionEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentPartEntity
import com.example.data.model.LineSegmentEntity
import com.example.ui.components.SegmentCanvas
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
import com.example.ui.viewmodel.CanvasToolMode

/**
 * ViewerStudioView - Authentic Adobe Acrobat / PDF Reader Style Viewer
 * Displays manuscript folios as pure PDF document sheets with full PDF controls,
 * page thumbnails drawer, zoom level presets, rotation, and vector layer annotations.
 */
@Composable
fun ViewerStudioView(
    document: DocumentEntity?,
    parts: List<DocumentPartEntity>,
    activePageIndex: Int,
    activePart: DocumentPartEntity?,
    blocks: List<BlockRegionEntity>,
    lines: List<LineSegmentEntity>,
    selectedLineId: Long?,
    canvasToolMode: CanvasToolMode,
    selectedTypology: String,
    onSelectPage: (Int) -> Unit,
    onAddNewPage: () -> Unit = {},
    onDeleteCurrentPage: () -> Unit = {},
    onLineSelected: (Long) -> Unit,
    onToolModeChanged: (CanvasToolMode) -> Unit,
    onTypologyChanged: (String) -> Unit,
    onRunAutoSegmentation: () -> Unit,
    onRunAutoTranscription: () -> Unit,
    onOpenPpOcrConfig: () -> Unit = {},
    onPolygonCompleted: (String) -> Unit,
    onBaselineCompleted: (String) -> Unit,
    onOpenLineEditModal: (Long) -> Unit = {},
    onReorderLinesScholarly: () -> Unit = {},
    onSwipeToWriterHint: () -> Unit,
    isDarkMode: Boolean = false,
    isBarsVisible: Boolean = true,
    onUserInteraction: () -> Unit = {},
    isBarsPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onToggleBars: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Dynamic Theme Colors for Viewer Studio
    val canvasBg = if (isDarkMode) DarkBg else ParchmentBg
    val toolbarBg = if (isDarkMode) DarkSurface else Color.White
    val toolbarBorder = if (isDarkMode) DarkBorder else Color(0xFFE2E8F0)
    val pillBg = if (isDarkMode) DarkSurfaceVariant else Color(0xFFF1F5F9)
    val pillBorder = if (isDarkMode) DarkBorder else Color(0xFFCBD5E1)
    val pillText = if (isDarkMode) DarkText else Color(0xFF0F172A)
    val pillIconTint = if (isDarkMode) DarkMuted else Color(0xFF64748B)
    val hudBg = if (isDarkMode) DarkSurface.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
    val hudBorder = if (isDarkMode) DarkBorder else Color(0xFFCBD5E1)
    val hudText = if (isDarkMode) DarkText else Color(0xFF0F172A)

    // Full-screen and auto-hiding bars state
    var internalBarsVisible by remember { mutableStateOf(true) }
    var internalBarsPinned by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val autoHideDelayMs = 3500L

    val effectiveBarsVisible = isBarsVisible && internalBarsVisible
    val effectiveBarsPinned = isBarsPinned || internalBarsPinned

    // Zoom scale state & rotation
    val pageZoomScales = remember { mutableStateMapOf<Long, Float>() }
    var scale by remember { mutableFloatStateOf(1.0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var showVectorOverlay by remember { mutableStateOf(true) }
    var showThumbnailsSidebar by remember { mutableStateOf(false) }
    var showZoomDropdown by remember { mutableStateOf(false) }

    fun triggerInteraction() {
        internalBarsVisible = true
        lastInteractionTime = System.currentTimeMillis()
        onUserInteraction()
    }

    // Auto-hide countdown effect: hides bars after inactivity unless pinned or menu is open
    LaunchedEffect(effectiveBarsVisible, effectiveBarsPinned, lastInteractionTime, showZoomDropdown, showThumbnailsSidebar) {
        if (effectiveBarsVisible && !effectiveBarsPinned) {
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = (autoHideDelayMs - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            if (!showZoomDropdown && !showThumbnailsSidebar) {
                internalBarsVisible = false
            }
        }
    }

    // Restore saved zoom scale for active page
    LaunchedEffect(activePart?.id) {
        val partId = activePart?.id ?: return@LaunchedEffect
        scale = pageZoomScales[partId] ?: 1.0f
    }

    // Save scale for active page
    LaunchedEffect(scale) {
        activePart?.id?.let { partId ->
            pageZoomScales[partId] = scale
        }
    }

    val pageCount = parts.size.coerceAtLeast(1)
    val folioPagerState = rememberPagerState(
        initialPage = activePageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    // Sync from external page selection
    LaunchedEffect(activePageIndex) {
        if (activePageIndex in 0 until pageCount && folioPagerState.currentPage != activePageIndex) {
            folioPagerState.animateScrollToPage(activePageIndex)
        }
    }

    // Sync from vertical roll gesture
    LaunchedEffect(folioPagerState) {
        snapshotFlow { folioPagerState.currentPage }.collect { page ->
            if (page != activePageIndex && page in parts.indices) {
                onSelectPage(page)
            }
        }
    }

    val zoomLevels = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)

    // Responsive padding that smoothly animates when bars appear or hide
    val topBarPadding by animateDpAsState(
        targetValue = if (effectiveBarsVisible) 48.dp else 4.dp,
        animationSpec = tween(durationMillis = 280),
        label = "viewer_top_padding"
    )
    val bottomBarPadding by animateDpAsState(
        targetValue = if (effectiveBarsVisible) 44.dp else 4.dp,
        animationSpec = tween(durationMillis = 280),
        label = "viewer_bottom_padding"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    triggerInteraction()
                    waitForUpOrCancellation()
                }
            }
            .testTag("viewer_studio_screen")
    ) {
        // --- 1. Authentic PDF Viewer Top Header Bar (Overlaid at Top with Auto-Hide) ---
        AnimatedVisibility(
            visible = effectiveBarsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = toolbarBg,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = toolbarBorder)
                    .testTag("viewer_top_header_bar")
            ) {
            val toolbarScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pinned, always-visible pair: these are the two core
                // actions of the entire app (detect lines, then transcribe
                // them) and must never be scrollable out of view the way
                // the other, lower-priority tools in the row below can be.
                // Sized and colored a step more prominently than the
                // scrolling chips (11sp vs 10sp, more padding) to reflect
                // that these are the primary actions on this screen, not
                // peers of "rotate" or "zoom".
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(GoldPrimary.copy(alpha = 0.22f))
                        .border(width = 0.75.dp, color = GoldPrimary.copy(alpha = 0.55f), shape = RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onRunAutoSegmentation() }
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deteksi Baris", fontSize = 11.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = onOpenPpOcrConfig,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Pengaturan Deteksi Baris PP-OCRv5",
                            tint = GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.22f))
                        .border(width = 0.75.dp, color = Color(0xFF10B981).copy(alpha = 0.55f), shape = RoundedCornerShape(5.dp))
                        .clickable { onRunAutoTranscription() }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Translate, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transkripsi", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }

                VerticalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp).height(24.dp),
                    color = toolbarBorder
                )

            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(toolbarScrollState)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // PDF Document Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                        .border(width = 0.5.dp, color = pillBorder, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF File",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = (document?.title ?: "Manuskrip").take(16) + ".pdf",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillText
                    )
                }

                // Sidebar Thumbnails Drawer Toggle
                IconButton(
                    onClick = { showThumbnailsSidebar = !showThumbnailsSidebar },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showThumbnailsSidebar) ScholarBlue else pillBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewSidebar,
                        contentDescription = "Buka Panel Thumbnail",
                        tint = if (showThumbnailsSidebar) Color.White else pillIconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Page Navigation Bar: [ < ] 1 / 2 [ > ]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                        .border(width = 0.5.dp, color = pillBorder, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (activePageIndex > 0) onSelectPage(activePageIndex - 1)
                        },
                        enabled = activePageIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Halaman Sebelumnya",
                            tint = if (activePageIndex > 0) pillIconTint else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "${activePageIndex + 1} / $pageCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillText,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = {
                            if (activePageIndex < pageCount - 1) onSelectPage(activePageIndex + 1)
                        },
                        enabled = activePageIndex < pageCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Halaman Berikutnya",
                            tint = if (activePageIndex < pageCount - 1) pillIconTint else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Zoom Level Controls: [ - ] 100% [ + ]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                        .border(width = 0.5.dp, color = pillBorder, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceIn(0.5f, 4.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = pillIconTint, modifier = Modifier.size(16.dp))
                    }

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0))
                                .clickable { showZoomDropdown = true }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScholarBlue
                            )
                        }

                        DropdownMenu(
                            expanded = showZoomDropdown,
                            onDismissRequest = { showZoomDropdown = false },
                            modifier = Modifier.background(toolbarBg)
                        ) {
                            zoomLevels.forEach { z ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${(z * 100).toInt()}% ${if (scale == z) "✓" else ""}",
                                            fontSize = 11.sp,
                                            color = if (scale == z) ScholarBlue else pillText
                                        )
                                    },
                                    onClick = {
                                        scale = z
                                        showZoomDropdown = false
                                    }
                                )
                            }
                            HorizontalDivider(color = toolbarBorder)
                            DropdownMenuItem(
                                text = { Text("Sesuaikan Lebar (Fit)", fontSize = 11.sp, color = pillText) },
                                onClick = {
                                    scale = 1.0f
                                    showZoomDropdown = false
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceIn(0.5f, 4.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = pillIconTint, modifier = Modifier.size(16.dp))
                    }
                }

                // Rotate Document Tool
                IconButton(
                    onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                        .border(width = 0.5.dp, color = pillBorder, shape = RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Putar 90°",
                        tint = pillIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Toggle AI Annotation Overlay vs Pure PDF
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showVectorOverlay) ScholarBlue else pillBg)
                        .border(width = 0.5.dp, color = if (showVectorOverlay) ScholarBlue else pillBorder, shape = RoundedCornerShape(4.dp))
                        .clickable { showVectorOverlay = !showVectorOverlay }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showVectorOverlay) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (showVectorOverlay) Color.White else pillIconTint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showVectorOverlay) "Anotasi Aktif" else "PDF Murni",
                        fontSize = 11.sp,
                        color = if (showVectorOverlay) Color.White else pillText,
                        fontWeight = FontWeight.Bold
                    )
                }

                // AI Segment & HTR triggers were moved out of this scrolling
                // row -- see the fixed pinned pair placed before the Box
                // below. They're the single most important actions in this
                // screen (this IS the OCR app), so they can't be allowed to
                // scroll off-screen along with the less critical tools.

                // Tool mode chips
                PdfToolChip(
                    icon = Icons.Default.TouchApp,
                    label = "Pilih",
                    isSelected = canvasToolMode == CanvasToolMode.SELECT_LINE,
                    isDarkMode = isDarkMode,
                    onClick = { onToolModeChanged(CanvasToolMode.SELECT_LINE) }
                )
                PdfToolChip(
                    icon = Icons.Default.CropSquare,
                    label = "+Region",
                    isSelected = canvasToolMode == CanvasToolMode.DRAW_REGION_POLYGON,
                    isDarkMode = isDarkMode,
                    onClick = { onToolModeChanged(CanvasToolMode.DRAW_REGION_POLYGON) }
                )
                PdfToolChip(
                    icon = Icons.Default.Gesture,
                    label = "+Baseline",
                    isSelected = canvasToolMode == CanvasToolMode.DRAW_BASELINE,
                    isDarkMode = isDarkMode,
                    onClick = { onToolModeChanged(CanvasToolMode.DRAW_BASELINE) }
                )

                // Dedicated Line Boundary Editor Modal Trigger Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ScholarBlue.copy(alpha = 0.18f))
                        .border(width = 0.5.dp, color = ScholarBlue.copy(alpha = 0.35f), shape = RoundedCornerShape(4.dp))
                        .clickable {
                            val targetId = selectedLineId ?: lines.firstOrNull()?.id
                            if (targetId != null) onOpenLineEditModal(targetId)
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = "Editor Batas Baris",
                        tint = ScholarBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Edit Batas",
                        fontSize = 10.sp,
                        color = ScholarBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Scholarly Reading Order Sort Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.18f))
                        .border(width = 0.5.dp, color = Color(0xFF8B5CF6).copy(alpha = 0.35f), shape = RoundedCornerShape(4.dp))
                        .clickable { onReorderLinesScholarly() }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Urutan Baca Filologi",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Urutan Filologi",
                        fontSize = 10.sp,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Fullscreen / Pin Toggle Button
                IconButton(
                    onClick = {
                        triggerInteraction()
                        internalBarsPinned = !internalBarsPinned
                        onTogglePin()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (effectiveBarsPinned) GoldPrimary.copy(alpha = 0.25f) else pillBg)
                        .border(0.5.dp, if (effectiveBarsPinned) GoldPrimary else pillBorder, RoundedCornerShape(4.dp))
                        .testTag("viewer_pin_bars_button")
                ) {
                    Icon(
                        imageVector = if (effectiveBarsPinned) Icons.Default.PushPin else Icons.Default.Fullscreen,
                        contentDescription = if (effectiveBarsPinned) "Bilah Disematkan" else "Mode Layar Penuh Otomatis",
                        tint = if (effectiveBarsPinned) GoldPrimary else pillIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Manual Hide / Fullscreen button
                IconButton(
                    onClick = {
                        internalBarsVisible = false
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                        .border(0.5.dp, pillBorder, RoundedCornerShape(4.dp))
                        .testTag("viewer_hide_bars_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Sembunyikan Bilah Alat",
                        tint = pillIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                }

                // Right-edge fade + chevron hint: the scrolling section of
                // the toolbar (everything except the pinned Deteksi/
                // Transkripsi pair) still has more controls than fit most
                // phone widths, but a plain horizontalScroll has no visual
                // affordance -- users had no way to know there were more
                // tools (including "+Baseline" and "Edit Batas") sitting
                // off-screen to the right. Only shown when there's actually
                // more content to scroll to, so it disappears once the user
                // has scrolled to the end.
                val canScrollRight = toolbarScrollState.maxValue > 0 &&
                    toolbarScrollState.value < toolbarScrollState.maxValue
                androidx.compose.animation.AnimatedVisibility(
                    visible = canScrollRight,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(toolbarBg.copy(alpha = 0f), toolbarBg)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Lebih banyak alat di sebelah kanan",
                            tint = pillIconTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            }
        }
    }

        // --- 2. Main PDF Workspace Area with Optional Thumbnails Drawer (Full-Screen Canvas) ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarPadding, bottom = bottomBarPadding)
        ) {
            // PDF Page Thumbnails Sidebar (Like Adobe Acrobat left panel)
            AnimatedVisibility(
                visible = showThumbnailsSidebar,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Surface(
                    color = toolbarBg,
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .border(width = 0.5.dp, color = toolbarBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "Halaman PDF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569),
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(parts) { idx, part ->
                                val isSelected = idx == activePageIndex
                                val bitmap = remember(part.imageResName, part.imageUri) {
                                    loadPdfThumbnail(context, part.imageResName, part.imageUri)
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) ScholarBlue.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) ScholarBlue else toolbarBorder,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onSelectPage(idx) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(if (isDarkMode) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Thumbnail Halaman ${idx + 1}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ScholarBlue else pillText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Central PDF Document Canvas Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(canvasBg)
            ) {
                VerticalPager(
                    state = folioPagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotationAngle)
                ) { pageIdx ->
                    val partForPage = parts.getOrNull(pageIdx)
                    val isCurrentPage = pageIdx == activePageIndex

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // PDF Page Container Sheet (Pure White Paper in Light Mode with crisp border & shadow)
                        Surface(
                            color = if (isDarkMode) DarkSurface else Color.White,
                            shape = RoundedCornerShape(2.dp),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(elevation = 10.dp, shape = RoundedCornerShape(2.dp))
                                .border(width = 1.dp, color = if (isDarkMode) DarkBorder else Color(0xFFE2E8F0), shape = RoundedCornerShape(2.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SegmentCanvas(
                                    imageResName = partForPage?.imageResName,
                                    blocks = if (isCurrentPage) blocks else emptyList(),
                                    lines = if (isCurrentPage) lines else emptyList(),
                                    selectedLineId = if (isCurrentPage) selectedLineId else null,
                                    canvasToolMode = canvasToolMode,
                                    showOverlay = showVectorOverlay,
                                    scale = scale,
                                    onScaleChange = { scale = it },
                                    onLineSelected = onLineSelected,
                                    onPolygonCompleted = onPolygonCompleted,
                                    onBaselineCompleted = onBaselineCompleted,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Realistic PDF Page Footer Indicator
                                Surface(
                                    color = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.85f) else Color(0xFFFFFFFF).copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(topEnd = 6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, toolbarBorder),
                                    modifier = Modifier.align(Alignment.BottomStart)
                                ) {
                                    Text(
                                        text = "PDF Hal. ${partForPage?.pageNumber ?: (pageIdx + 1)} / $pageCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pillText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Authentic PDF Viewer Bottom Bar (Overlaid at Bottom with Auto-Hide) ---
        AnimatedVisibility(
            visible = effectiveBarsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = toolbarBg.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(width = 0.5.dp, color = toolbarBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("viewer_bottom_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Stepper & Page Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        IconButton(
                            onClick = {
                                triggerInteraction()
                                if (activePageIndex > 0) onSelectPage(activePageIndex - 1)
                            },
                            enabled = activePageIndex > 0,
                            modifier = Modifier.size(28.dp).testTag("viewer_bottom_prev_page")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Halaman Sebelumnya",
                                tint = if (activePageIndex > 0) pillIconTint else Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Surface(
                            color = pillBg,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, pillBorder)
                        ) {
                            Text(
                                text = "PDF Hal. ${activePart?.pageNumber ?: (activePageIndex + 1)} / $pageCount",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = pillText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                triggerInteraction()
                                if (activePageIndex < pageCount - 1) onSelectPage(activePageIndex + 1)
                            },
                            enabled = activePageIndex < pageCount - 1,
                            modifier = Modifier.size(28.dp).testTag("viewer_bottom_next_page")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Halaman Berikutnya",
                                tint = if (activePageIndex < pageCount - 1) pillIconTint else Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Center: Selected Line Quick Action or Mode Status
                    val selectedLine = lines.firstOrNull { it.id == selectedLineId }
                    if (selectedLine != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF))
                                .border(0.5.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Baris #${selectedLine.orderIndex} (${selectedLine.typology})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) GoldPrimary else ScholarBlue
                            )
                            Button(
                                onClick = {
                                    triggerInteraction()
                                    onOpenLineEditModal(selectedLine.id)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(24.dp).testTag("viewer_edit_batas_bottom_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Edit Batas", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    } else {
                        Text(
                            text = when (canvasToolMode) {
                                CanvasToolMode.SELECT_LINE -> "Sentuh layar untuk sembunyikan/tampilkan bilah"
                                CanvasToolMode.DRAW_REGION_POLYGON -> "Mode Gambar Region"
                                CanvasToolMode.DRAW_BASELINE -> "Mode Gambar Baseline"
                                CanvasToolMode.VIEW_PAN -> "Mode Geser & Zoom"
                                else -> "Sentuh layar untuk sembunyikan/tampilkan bilah"
                            },
                            fontSize = 10.5.sp,
                            color = if (isDarkMode) DarkMuted else Color(0xFF64748B)
                        )
                    }

                    // Right: Zoom Controls & Fullscreen Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        IconButton(
                            onClick = {
                                triggerInteraction()
                                scale = (scale - 0.25f).coerceIn(0.5f, 4.0f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Perkecil", tint = pillIconTint, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScholarBlue
                        )
                        IconButton(
                            onClick = {
                                triggerInteraction()
                                scale = (scale + 0.25f).coerceIn(0.5f, 4.0f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Perbesar", tint = pillIconTint, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                triggerInteraction()
                                scale = 1.0f; rotationAngle = 0f
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = pillIconTint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // --- 4. Floating "Alat" Reveal Pill (Shown only when bars are auto-hidden) ---
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
                border = androidx.compose.foundation.BorderStroke(0.5.dp, toolbarBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        triggerInteraction()
                    }
                    .testTag("viewer_fullscreen_reveal_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tampilkan Alat",
                        tint = ScholarBlue,
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

@Composable
private fun PdfToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkMode: Boolean = false,
    onClick: () -> Unit
) {
    val unselectedBg = if (isDarkMode) DarkSurfaceVariant else ParchmentSurfaceVariant
    val unselectedBorder = if (isDarkMode) DarkBorder else ParchmentBorder
    val unselectedText = if (isDarkMode) DarkMuted else ParchmentMuted
    val activeColor = if (isDarkMode) ScholarBlueDark else ScholarBlue

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) activeColor else unselectedBg)
            .border(
                width = 0.5.dp,
                color = if (isSelected) activeColor else unselectedBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else unselectedText,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else unselectedText
        )
    }
}

private fun loadPdfThumbnail(context: Context, imageResName: String?, imageUri: String?): android.graphics.Bitmap? {
    return try {
        if (!imageUri.isNullOrEmpty()) {
            val uri = android.net.Uri.parse(imageUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } else {
            val resName = when (imageResName) {
                "manuscript_p2" -> "manuscript_p2"
                else -> "manuscript_p1"
            }
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeResource(context.resources, resId, opts)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}
