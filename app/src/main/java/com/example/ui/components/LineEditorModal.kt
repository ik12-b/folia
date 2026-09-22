package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LineSegmentEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMuted
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ScholarBlue
import com.example.ui.theme.ScholarBlueLight
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Handle type identified during touch gestures on the manuscript canvas
 */
private enum class DragHandle {
    NONE,
    CORNER_TL,
    CORNER_TR,
    CORNER_BR,
    CORNER_BL,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    BASELINE_START,
    BASELINE_END,
    WHOLE_BOX
}

/**
 * LineEditorModal - Dedicated Scholarly Manuscript Line Boundary Editor
 *
 * Enables scholars and philologists to visually and tactically adjust
 * the boundaries (DBNet bounding box polygon and baseline vector) of any
 * detected line in a manuscript image with extreme precision:
 * - Real-time draggable corner handles & edge midpoint bars
 * - Baseline tilt and height drag handles
 * - High-precision tactile D-Pad & stepper nudge panel with variable step sizes (0.2%, 0.8%, 2.0%)
 * - Live Line Crop Preview: dynamically extracts and previews the exact cropped image slice
 * - Typology categorization: Matan, Syarah, Marginalia, Heading, Footnote
 * - Sequential line navigation: Next / Previous buttons for smooth review workflows
 */
@Composable
fun LineEditorModal(
    line: LineSegmentEntity,
    allLines: List<LineSegmentEntity>,
    imageResName: String?,
    imageUri: String? = null,
    transcriptionText: String? = null,
    onSaveBoundaries: (lineId: Long, baselineJson: String, maskPolygonJson: String, typology: String) -> Unit,
    onSelectLine: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Load original manuscript image bitmap safely
    val manuscriptBitmap = remember(imageResName, imageUri) {
        loadBitmapSafely(context, imageResName, imageUri)
    }

    // Parse initial coordinates
    val (origTL, origTR, origBR, origBL) = remember(line.id, line.maskPolygonJson, line.baselinePointsJson) {
        extractBoxCoordinates(line.maskPolygonJson, line.baselinePointsJson)
    }

    val (origBStart, origBEnd) = remember(line.id, line.baselinePointsJson, origTL, origTR, origBR, origBL) {
        extractBaselineCoordinates(line.baselinePointsJson, origTL, origTR, origBR, origBL)
    }

    // Active editable coordinate states
    var pTL by remember(line.id) { mutableStateOf(origTL) }
    var pTR by remember(line.id) { mutableStateOf(origTR) }
    var pBR by remember(line.id) { mutableStateOf(origBR) }
    var pBL by remember(line.id) { mutableStateOf(origBL) }

    var bStart by remember(line.id) { mutableStateOf(origBStart) }
    var bEnd by remember(line.id) { mutableStateOf(origBEnd) }

    var typology by remember(line.id) { mutableStateOf(line.typology) }

    // Active handle being dragged
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    // Viewport mode: Line Focus Zoom (zooms into the line) vs Full Page
    var isLineFocusMode by remember { mutableStateOf(true) }

    // Stepper Nudge step size: 0 = Fine (0.2%), 1 = Normal (0.8%), 2 = Coarse (2.0%)
    var nudgeStepIndex by remember { mutableStateOf(1) }
    val stepSizes = listOf(0.002f, 0.008f, 0.020f)
    val currentStep = stepSizes[nudgeStepIndex]

    // Line switcher dropdown state
    var showLineDropdown by remember { mutableStateOf(false) }

    // Check if user made unsaved changes
    val hasChanges = remember(pTL, pTR, pBR, pBL, bStart, bEnd, typology) {
        pTL != origTL || pTR != origTR || pBR != origBR || pBL != origBL ||
                bStart != origBStart || bEnd != origBEnd || typology != line.typology
    }

    // Current line index and position
    val currentLineIndex = allLines.indexOfFirst { it.id == line.id }
    val totalLines = allLines.size
    val hasPrevLine = currentLineIndex > 0
    val hasNextLine = currentLineIndex in 0 until totalLines - 1

    // Generate real-time cropped image slice of the line
    val croppedBitmap = remember(manuscriptBitmap, pTL, pTR, pBR, pBL) {
        if (manuscriptBitmap == null || manuscriptBitmap.isRecycled) null
        else {
            try {
                val minX = listOf(pTL.x, pTR.x, pBR.x, pBL.x).minOrNull() ?: 0f
                val maxX = listOf(pTL.x, pTR.x, pBR.x, pBL.x).maxOrNull() ?: 1f
                val minY = listOf(pTL.y, pTR.y, pBR.y, pBL.y).minOrNull() ?: 0f
                val maxY = listOf(pTL.y, pTR.y, pBR.y, pBL.y).maxOrNull() ?: 1f

                val bmpW = manuscriptBitmap.width
                val bmpH = manuscriptBitmap.height

                val leftPx = (minX * bmpW).toInt().coerceIn(0, bmpW - 2)
                val topPx = (minY * bmpH).toInt().coerceIn(0, bmpH - 2)
                val rightPx = (maxX * bmpW).toInt().coerceIn(leftPx + 1, bmpW)
                val bottomPx = (maxY * bmpH).toInt().coerceIn(topPx + 1, bmpH)

                val widthPx = (rightPx - leftPx).coerceAtLeast(1)
                val heightPx = (bottomPx - topPx).coerceAtLeast(1)

                Bitmap.createBitmap(manuscriptBitmap, leftPx, topPx, widthPx, heightPx)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun saveCurrent() {
        val maskJson = "%.4f,%.4f;%.4f,%.4f;%.4f,%.4f;%.4f,%.4f".format(
            Locale.US,
            pTL.x, pTL.y,
            pTR.x, pTR.y,
            pBR.x, pBR.y,
            pBL.x, pBL.y
        )
        val baselineJson = "%.4f,%.4f;%.4f,%.4f".format(
            Locale.US,
            bStart.x, bStart.y,
            bEnd.x, bEnd.y
        )
        onSaveBoundaries(line.id, baselineJson, maskJson, typology)
    }

    fun resetToOriginal() {
        pTL = origTL
        pTR = origTR
        pBR = origBR
        pBL = origBL
        bStart = origBStart
        bEnd = origBEnd
        typology = line.typology
    }

    var autoAlignMessage by remember { mutableStateOf<String?>(null) }

    fun rotateLineByDelta(
        deltaDeg: Float,
        bmpW: Int = manuscriptBitmap?.width ?: 1000,
        bmpH: Int = manuscriptBitmap?.height ?: 1000
    ) {
        val centerNormX = (pTL.x + pTR.x + pBR.x + pBL.x) / 4f
        val centerNormY = (pTL.y + pTR.y + pBR.y + pBL.y) / 4f
        val centerPixX = centerNormX * bmpW
        val centerPixY = centerNormY * bmpH

        val rad = Math.toRadians(deltaDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()

        fun rotatePoint(pt: Offset): Offset {
            val px = pt.x * bmpW - centerPixX
            val py = pt.y * bmpH - centerPixY
            val nx = centerPixX + (px * cosR - py * sinR)
            val ny = centerPixY + (px * sinR + py * cosR)
            return Offset((nx / bmpW).coerceIn(0f, 1f), (ny / bmpH).coerceIn(0f, 1f))
        }

        pTL = rotatePoint(pTL)
        pTR = rotatePoint(pTR)
        pBR = rotatePoint(pBR)
        pBL = rotatePoint(pBL)
        bStart = rotatePoint(bStart)
        bEnd = rotatePoint(bEnd)
    }

    fun autoAlignDominantSkew() {
        val bmp = manuscriptBitmap ?: return
        val bmpW = bmp.width
        val bmpH = bmp.height
        if (bmpW <= 10 || bmpH <= 10) return

        val minX = minOf(pTL.x, pTR.x, pBR.x, pBL.x).coerceIn(0f, 1f)
        val maxX = maxOf(pTL.x, pTR.x, pBR.x, pBL.x).coerceIn(0f, 1f)
        val minY = minOf(pTL.y, pTR.y, pBR.y, pBL.y).coerceIn(0f, 1f)
        val maxY = maxOf(pTL.y, pTR.y, pBR.y, pBL.y).coerceIn(0f, 1f)

        val cropLeft = (minX * bmpW).toInt().coerceIn(0, bmpW - 1)
        val cropTop = (minY * bmpH).toInt().coerceIn(0, bmpH - 1)
        val cropWidth = ((maxX - minX) * bmpW).toInt().coerceIn(4, bmpW - cropLeft)
        val cropHeight = ((maxY - minY) * bmpH).toInt().coerceIn(4, bmpH - cropTop)

        val dominantAngleDeg = calculateDominantSkewAngle(bmp, cropLeft, cropTop, cropWidth, cropHeight)

        // Current angle of the line's top edge in degrees (Euclidean pixel coordinates)
        val currentDx = (pTR.x - pTL.x) * bmpW
        val currentDy = (pTR.y - pTL.y) * bmpH
        val currentAngleDeg = Math.toDegrees(kotlin.math.atan2(currentDy.toDouble(), currentDx.toDouble())).toFloat()

        val deltaAngleDeg = dominantAngleDeg - currentAngleDeg
        rotateLineByDelta(deltaAngleDeg, bmpW, bmpH)

        val angleFormatted = String.format(Locale.US, "%+.1f°", dominantAngleDeg)
        autoAlignMessage = "Auto-aligned: $angleFormatted"
    }

    // Modal Dialog Container with Wide Responsive Layout
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 10.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .testTag("line_editor_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // --- 1. Top Header Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Editor Batas Baris Manuskrip",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (hasChanges) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFE11D48))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Ada Perubahan",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Geser titik sudut, tepi garis, atau gunakan tombol D-Pad untuk menyetel batas teks secara presisi",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Line Stepper Navigator & Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Previous line button
                        IconButton(
                            onClick = {
                                if (hasPrevLine) {
                                    if (hasChanges) saveCurrent()
                                    onSelectLine(allLines[currentLineIndex - 1].id)
                                }
                            },
                            enabled = hasPrevLine,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasPrevLine) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.4f))
                                .testTag("line_editor_prev_line_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Baris Sebelumnya",
                                tint = if (hasPrevLine) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Line Selector Dropdown Button
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ScholarBlue.copy(alpha = 0.25f))
                                    .clickable { showLineDropdown = true }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Baris ${line.orderIndex} / $totalLines",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ScholarBlueLight
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = ScholarBlueLight,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showLineDropdown,
                                onDismissRequest = { showLineDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E293B))
                                    .height(26.dp)
                            ) {
                                allLines.forEachIndexed { idx, l ->
                                    val isCurrent = l.id == line.id
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Baris #${l.orderIndex} (${l.typology}) ${if (isCurrent) "✓" else ""}",
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) GoldPrimary else Color.White
                                            )
                                        },
                                        onClick = {
                                            showLineDropdown = false
                                            if (l.id != line.id) {
                                                if (hasChanges) saveCurrent()
                                                onSelectLine(l.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Next line button
                        IconButton(
                            onClick = {
                                if (hasNextLine) {
                                    if (hasChanges) saveCurrent()
                                    onSelectLine(allLines[currentLineIndex + 1].id)
                                }
                            },
                            enabled = hasNextLine,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasNextLine) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.4f))
                                .testTag("line_editor_next_line_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Baris Berikutnya",
                                tint = if (hasNextLine) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("line_editor_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // --- 2. Typology Selector Chips Row ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tipologi:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    listOf("Matan", "Syarah", "Marginalia", "Heading", "Footnote").forEach { typo ->
                        val isSelected = typology.equals(typo, ignoreCase = true)
                        val chipColor = getTypologyColor(typo)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) chipColor else Color(0xFF1E293B))
                                .clickable { typology = typo }
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) chipColor else Color(0xFF334155),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = typo,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Auto-Align Skew Angle Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldPrimary.copy(alpha = 0.20f))
                            .border(1.dp, GoldPrimary, RoundedCornerShape(6.dp))
                            .clickable { autoAlignDominantSkew() }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("line_editor_auto_align_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Auto-Align Kemiringan",
                            tint = GoldPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Auto-Align",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }

                    if (autoAlignMessage != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = autoAlignMessage ?: "",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Line Focus Toggle Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLineFocusMode) ScholarBlue else Color(0xFF1E293B))
                            .clickable { isLineFocusMode = !isLineFocusMode }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLineFocusMode) Icons.Default.ZoomIn else Icons.Default.FitScreen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isLineFocusMode) "Fokus Baris (Zoom)" else "Halaman Utuh",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // --- 3. Main Split Content Area: Left Interactive Canvas, Right Nudge Panel & Live Crop ---
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    val isWideScreen = maxWidth > 680.dp

                    if (isWideScreen) {
                        // Desktop / Tablet Landscape: Canvas on Left, Controls & Preview on Right
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF020617))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            ) {
                                InteractiveBoundaryCanvas(
                                    bitmap = manuscriptBitmap,
                                    pTL = pTL,
                                    pTR = pTR,
                                    pBR = pBR,
                                    pBL = pBL,
                                    bStart = bStart,
                                    bEnd = bEnd,
                                    typology = typology,
                                    isLineFocusMode = isLineFocusMode,
                                    activeHandle = activeHandle,
                                    onActiveHandleChange = { activeHandle = it },
                                    onUpdateCoords = { nTL, nTR, nBR, nBL, nBS, nBE ->
                                        pTL = nTL
                                        pTR = nTR
                                        pBR = nBR
                                        pBL = nBL
                                        bStart = nBS
                                        bEnd = nBE
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LiveLineCropCard(
                                    croppedBitmap = croppedBitmap,
                                    transcriptionText = transcriptionText,
                                    typology = typology,
                                    pTL = pTL,
                                    pBR = pBR
                                )

                                TactileNudgePanel(
                                    nudgeStepIndex = nudgeStepIndex,
                                    onStepIndexChange = { nudgeStepIndex = it },
                                    onNudge = { dx, dy, target ->
                                        applyNudge(
                                            dx = dx * currentStep,
                                            dy = dy * currentStep,
                                            target = target,
                                            pTL = pTL, pTR = pTR, pBR = pBR, pBL = pBL,
                                            bStart = bStart, bEnd = bEnd,
                                            onUpdate = { nTL, nTR, nBR, nBL, nBS, nBE ->
                                                pTL = nTL
                                                pTR = nTR
                                                pBR = nBR
                                                pBL = nBL
                                                bStart = nBS
                                                bEnd = nBE
                                            }
                                        )
                                    },
                                    onAutoAlign = { autoAlignDominantSkew() },
                                    onRotate = { deg -> rotateLineByDelta(deg) }
                                )
                            }
                        }
                    } else {
                        // Phone Portrait: Stacked with Canvas taking upper half, controls lower half
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(.dp))
                                    .background(Color(0xFF020617))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            ) {
                                InteractiveBoundaryCanvas(
                                    bitmap = manuscriptBitmap,
                                    pTL = pTL,
                                    pTR = pTR,
                                    pBR = pBR,
                                    pBL = pBL,
                                    bStart = bStart,
                                    bEnd = bEnd,
                                    typology = typology,
                                    isLineFocusMode = isLineFocusMode,
                                    activeHandle = activeHandle,
                                    onActiveHandleChange = { activeHandle = it },
                                    onUpdateCoords = { nTL, nTR, nBR, nBL, nBS, nBE ->
                                        pTL = nTL
                                        pTR = nTR
                                        pBR = nBR
                                        pBL = nBL
                                        bStart = nBS
                                        bEnd = nBE
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LiveLineCropCard(
                                    croppedBitmap = croppedBitmap,
                                    transcriptionText = transcriptionText,
                                    typology = typology,
                                    pTL = pTL,
                                    pBR = pBR
                                )

                                TactileNudgePanel(
                                    nudgeStepIndex = nudgeStepIndex,
                                    onStepIndexChange = { nudgeStepIndex = it },
                                    onNudge = { dx, dy, target ->
                                        applyNudge(
                                            dx = dx * currentStep,
                                            dy = dy * currentStep,
                                            target = target,
                                            pTL = pTL, pTR = pTR, pBR = pBR, pBL = pBL,
                                            bStart = bStart, bEnd = bEnd,
                                            onUpdate = { nTL, nTR, nBR, nBL, nBS, nBE ->
                                                pTL = nTL
                                                pTR = nTR
                                                pBR = nBR
                                                pBL = nBL
                                                bStart = nBS
                                                bEnd = nBE
                                            }
                                        )
                                    },
                                    onAutoAlign = { autoAlignDominantSkew() },
                                    onRotate = { deg -> rotateLineByDelta(deg) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // --- 4. Bottom Actions Footer ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val compactPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

                    // Reset to original boundaries
                    OutlinedButton(
                        onClick = { resetToOriginal() },
                        enabled = hasChanges,
                        contentPadding = compactPadding,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFCBD5E1),
                            disabledContentColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.testTag("line_editor_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Reset ke Asli", fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Cancel / Discard
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                            modifier = Modifier.testTag("line_editor_cancel_button")
                        ) {
                            Text("Batal", fontSize = 12.sp)
                        }

                        // Save & Next Line (sequential review)
                        if (hasNextLine) {
                            Button(
                                onClick = {
                                    saveCurrent()
                                    onSelectLine(allLines[currentLineIndex + 1].id)
                                },
                                contentPadding = compactPadding,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = ScholarBlueLight
                                ),
                                modifier = Modifier.testTag("line_editor_save_and_next_button")
                            ) {
                                Text("Simpan & Lanjut", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }

                        // Save and finish
                        Button(
                            onClick = {
                                saveCurrent()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            modifier = Modifier.testTag("line_editor_save_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Simpan Batas", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Manuscript Canvas with drag handles for all 4 corners,
 * 4 edge midpoints, and baseline vector endpoints.
 */
@Composable
private fun InteractiveBoundaryCanvas(
    bitmap: Bitmap?,
    pTL: Offset,
    pTR: Offset,
    pBR: Offset,
    pBL: Offset,
    bStart: Offset,
    bEnd: Offset,
    typology: String,
    isLineFocusMode: Boolean,
    activeHandle: DragHandle,
    onActiveHandleChange: (DragHandle) -> Unit,
    onUpdateCoords: (pTL: Offset, pTR: Offset, pBR: Offset, pBL: Offset, bStart: Offset, bEnd: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val typoColor = getTypologyColor(typology)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        val imageW = bitmap?.width?.toFloat() ?: 1200f
        val imageH = bitmap?.height?.toFloat() ?: 1600f
        val aspect = imageW / imageH

        // Bounds of current line in normalized coordinates
        val minX = listOf(pTL.x, pTR.x, pBR.x, pBL.x).minOrNull() ?: 0.2f
        val maxX = listOf(pTL.x, pTR.x, pBR.x, pBL.x).maxOrNull() ?: 0.8f
        val minY = listOf(pTL.y, pTR.y, pBR.y, pBL.y).minOrNull() ?: 0.3f
        val maxY = listOf(pTL.y, pTR.y, pBR.y, pBL.y).maxOrNull() ?: 0.4f

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val lineW = (maxX - minX).coerceAtLeast(0.05f)
        val lineH = (maxY - minY).coerceAtLeast(0.02f)

        // Calculate scale and offset
        // If in line-focus mode, zoom in ~2.8x centered around the line
        val zoomScale = if (isLineFocusMode) {
            val fitX = 0.85f / lineW
            val fitY = 0.55f / lineH
            min(fitX, fitY).coerceIn(1.6f, 4.5f)
        } else {
            1.0f
        }

        val baseDrawW = if (containerW / containerH > aspect) containerH * aspect else containerW
        val baseDrawH = if (containerW / containerH > aspect) containerH else containerW / aspect

        val drawW = baseDrawW * zoomScale
        val drawH = baseDrawH * zoomScale

        // Canvas pan offset to center on line
        val panOffsetX = if (isLineFocusMode) {
            (containerW / 2f) - (centerX * drawW)
        } else {
            (containerW - drawW) / 2f
        }

        val panOffsetY = if (isLineFocusMode) {
            (containerH / 2f) - (centerY * drawH)
        } else {
            (containerH - drawH) / 2f
        }

        fun normToScreen(norm: Offset): Offset {
            return Offset(
                x = panOffsetX + (norm.x * drawW),
                y = panOffsetY + (norm.y * drawH)
            )
        }

        fun screenToNorm(screen: Offset): Offset {
            return Offset(
                x = ((screen.x - panOffsetX) / drawW).coerceIn(0f, 1f),
                y = ((screen.y - panOffsetY) / drawH).coerceIn(0f, 1f)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pTL, pTR, pBR, pBL, bStart, bEnd, zoomScale, panOffsetX, panOffsetY) {
                    val handleRadiusThreshold = 36.dp.toPx()

                    detectDragGestures(
                        onDragStart = { startPos ->
                            val sTL = normToScreen(pTL)
                            val sTR = normToScreen(pTR)
                            val sBR = normToScreen(pBR)
                            val sBL = normToScreen(pBL)
                            val sBStart = normToScreen(bStart)
                            val sBEnd = normToScreen(bEnd)

                            val sTopMid = (sTL + sTR) / 2f
                            val sBotMid = (sBL + sBR) / 2f
                            val sLeftMid = (sTL + sBL) / 2f
                            val sRightMid = (sTR + sBR) / 2f

                            val handles = listOf(
                                DragHandle.CORNER_TL to (startPos - sTL).getDistance(),
                                DragHandle.CORNER_TR to (startPos - sTR).getDistance(),
                                DragHandle.CORNER_BR to (startPos - sBR).getDistance(),
                                DragHandle.CORNER_BL to (startPos - sBL).getDistance(),
                                DragHandle.BASELINE_START to (startPos - sBStart).getDistance(),
                                DragHandle.BASELINE_END to (startPos - sBEnd).getDistance(),
                                DragHandle.EDGE_TOP to (startPos - sTopMid).getDistance(),
                                DragHandle.EDGE_BOTTOM to (startPos - sBotMid).getDistance(),
                                DragHandle.EDGE_LEFT to (startPos - sLeftMid).getDistance(),
                                DragHandle.EDGE_RIGHT to (startPos - sRightMid).getDistance()
                            )

                            val closest = handles.minByOrNull { it.second }
                            if (closest != null && closest.second <= handleRadiusThreshold) {
                                onActiveHandleChange(closest.first)
                            } else {
                                // Check if touch is inside the box for whole-box dragging
                                val nPos = screenToNorm(startPos)
                                if (nPos.x in minX..maxX && nPos.y in minY..maxY) {
                                    onActiveHandleChange(DragHandle.WHOLE_BOX)
                                } else {
                                    onActiveHandleChange(DragHandle.NONE)
                                }
                            }
                        },
                        onDragEnd = {
                            onActiveHandleChange(DragHandle.NONE)
                        },
                        onDragCancel = {
                            onActiveHandleChange(DragHandle.NONE)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dxNorm = dragAmount.x / drawW
                            val dyNorm = dragAmount.y / drawH

                            when (activeHandle) {
                                DragHandle.CORNER_TL -> {
                                    val newTL = Offset((pTL.x + dxNorm).coerceIn(0f, 1f), (pTL.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(newTL, pTR, pBR, pBL, bStart, bEnd)
                                }
                                DragHandle.CORNER_TR -> {
                                    val newTR = Offset((pTR.x + dxNorm).coerceIn(0f, 1f), (pTR.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(pTL, newTR, pBR, pBL, bStart, bEnd)
                                }
                                DragHandle.CORNER_BR -> {
                                    val newBR = Offset((pBR.x + dxNorm).coerceIn(0f, 1f), (pBR.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(pTL, pTR, newBR, pBL, bStart, bEnd)
                                }
                                DragHandle.CORNER_BL -> {
                                    val newBL = Offset((pBL.x + dxNorm).coerceIn(0f, 1f), (pBL.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(pTL, pTR, pBR, newBL, bStart, bEnd)
                                }
                                DragHandle.EDGE_TOP -> {
                                    val newTL = Offset(pTL.x, (pTL.y + dyNorm).coerceIn(0f, 1f))
                                    val newTR = Offset(pTR.x, (pTR.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(newTL, newTR, pBR, pBL, bStart, bEnd)
                                }
                                DragHandle.EDGE_BOTTOM -> {
                                    val newBL = Offset(pBL.x, (pBL.y + dyNorm).coerceIn(0f, 1f))
                                    val newBR = Offset(pBR.x, (pBR.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(pTL, pTR, newBR, newBL, bStart, bEnd)
                                }
                                DragHandle.EDGE_LEFT -> {
                                    val newTL = Offset((pTL.x + dxNorm).coerceIn(0f, 1f), pTL.y)
                                    val newBL = Offset((pBL.x + dxNorm).coerceIn(0f, 1f), pBL.y)
                                    onUpdateCoords(newTL, pTR, pBR, newBL, bStart, bEnd)
                                }
                                DragHandle.EDGE_RIGHT -> {
                                    val newTR = Offset((pTR.x + dxNorm).coerceIn(0f, 1f), pTR.y)
                                    val newBR = Offset((pBR.x + dxNorm).coerceIn(0f, 1f), pBR.y)
                                    onUpdateCoords(pTL, newTR, newBR, pBL, bStart, bEnd)
                                }
                                DragHandle.BASELINE_START -> {
                                    val newBS = Offset((bStart.x + dxNorm).coerceIn(0f, 1f), (bStart.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(pTL, pTR, pBR, pBL, newBS, bEnd)
                                }
                                DragHandle.BASELINE_END -> {
                                    val newBE = Offset((bEnd.x + dxNorm).coerceIn(0f, 1f), (bEnd.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(pTL, pTR, pBR, pBL, bStart, newBE)
                                }
                                DragHandle.WHOLE_BOX -> {
                                    val newTL = Offset((pTL.x + dxNorm).coerceIn(0f, 1f), (pTL.y + dyNorm).coerceIn(0f, 1f))
                                    val newTR = Offset((pTR.x + dxNorm).coerceIn(0f, 1f), (pTR.y + dyNorm).coerceIn(0f, 1f))
                                    val newBR = Offset((pBR.x + dxNorm).coerceIn(0f, 1f), (pBR.y + dyNorm).coerceIn(0f, 1f))
                                    val newBL = Offset((pBL.x + dxNorm).coerceIn(0f, 1f), (pBL.y + dyNorm).coerceIn(0f, 1f))
                                    val newBS = Offset((bStart.x + dxNorm).coerceIn(0f, 1f), (bStart.y + dyNorm).coerceIn(0f, 1f))
                                    val newBE = Offset((bEnd.x + dxNorm).coerceIn(0f, 1f), (bEnd.y + dyNorm).coerceIn(0f, 1f))
                                    onUpdateCoords(newTL, newTR, newBR, newBL, newBS, newBE)
                                }
                                else -> {}
                            }
                        }
                    )
                }
        ) {
            // 1. Draw Manuscript Page Base Image
            if (bitmap != null && !bitmap.isRecycled) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(panOffsetX.toInt(), panOffsetY.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
                )
            } else {
                drawRect(
                    color = Color(0xFFF6EBD9),
                    topLeft = Offset(panOffsetX, panOffsetY),
                    size = Size(drawW, drawH)
                )
            }

            // 2. Soft semi-transparent mask outside page
            val sTL = normToScreen(pTL)
            val sTR = normToScreen(pTR)
            val sBR = normToScreen(pBR)
            val sBL = normToScreen(pBL)
            val sBStart = normToScreen(bStart)
            val sBEnd = normToScreen(bEnd)

            val boxPath = Path().apply {
                moveTo(sTL.x, sTL.y)
                lineTo(sTR.x, sTR.y)
                lineTo(sBR.x, sBR.y)
                lineTo(sBL.x, sBL.y)
                close()
            }

            // Draw translucent polygon interior fill
            drawPath(
                path = boxPath,
                color = typoColor.copy(alpha = 0.22f),
                style = Fill
            )

            // Draw bounding box polygon border
            drawPath(
                path = boxPath,
                color = typoColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Draw Baseline Vector (Vibrant Gold Line)
            drawLine(
                color = GoldPrimary,
                start = sBStart,
                end = sBEnd,
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
            )

            // 4. Draw Edge Midpoint Handles (Horizontal/Vertical Pill Bars)
            val topMid = (sTL + sTR) / 2f
            val botMid = (sBL + sBR) / 2f
            val leftMid = (sTL + sBL) / 2f
            val rightMid = (sTR + sBR) / 2f

            listOf(
                topMid to (activeHandle == DragHandle.EDGE_TOP),
                botMid to (activeHandle == DragHandle.EDGE_BOTTOM),
                leftMid to (activeHandle == DragHandle.EDGE_LEFT),
                rightMid to (activeHandle == DragHandle.EDGE_RIGHT)
            ).forEach { (pos, isActive) ->
                drawCircle(
                    color = if (isActive) GoldPrimary else Color.White,
                    radius = if (isActive) 7.dp.toPx() else 5.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = typoColor,
                    radius = if (isActive) 4.5.dp.toPx() else 3.dp.toPx(),
                    center = pos
                )
            }

            // 5. Draw Corner Vertex Handles (Large prominent draggable circles)
            listOf(
                sTL to (activeHandle == DragHandle.CORNER_TL),
                sTR to (activeHandle == DragHandle.CORNER_TR),
                sBR to (activeHandle == DragHandle.CORNER_BR),
                sBL to (activeHandle == DragHandle.CORNER_BL)
            ).forEach { (pos, isActive) ->
                // Outer ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    radius = if (isActive) 12.dp.toPx() else 9.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = if (isActive) GoldPrimary else typoColor,
                    radius = if (isActive) 10.dp.toPx() else 7.5.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isActive) 4.5.dp.toPx() else 3.dp.toPx(),
                    center = pos
                )
            }

            // 6. Draw Baseline Endpoint Handles (Golden Diamonds / Circles)
            listOf(
                sBStart to (activeHandle == DragHandle.BASELINE_START),
                sBEnd to (activeHandle == DragHandle.BASELINE_END)
            ).forEach { (pos, isActive) ->
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = if (isActive) 11.dp.toPx() else 8.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = GoldPrimary,
                    radius = if (isActive) 9.dp.toPx() else 6.5.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = Color.Black,
                    radius = if (isActive) 3.5.dp.toPx() else 2.5.dp.toPx(),
                    center = pos
                )
            }
        }
    }
}

/**
 * Live Line Crop Card - Displays the dynamically extracted manuscript line slice
 * with the corresponding transcription text for scholarly verification.
 */
@Composable
private fun LiveLineCropCard(
    croppedBitmap: Bitmap?,
    transcriptionText: String?,
    typology: String,
    pTL: Offset,
    pBR: Offset,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("line_editor_crop_card")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pratinjau Potongan Baris (Live Crop)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                // Coordinate dimension readout
                val widthPct = (abs(pBR.x - pTL.x) * 100).toInt()
                val heightPct = (abs(pBR.y - pTL.y) * 100).toInt()
                Text(
                    text = "L: $widthPct% | T: $heightPct%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ScholarBlueLight
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cropped Image Canvas Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (croppedBitmap != null && !croppedBitmap.isRecycled) {
                    Image(
                        bitmap = croppedBitmap.asImageBitmap(),
                        contentDescription = "Potongan Baris Manuskrip",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "Memuat potongan citra baris...",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Arabic Transcription Text Display (if available)
            if (!transcriptionText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transcriptionText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldPrimary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = androidx.compose.ui.text.TextStyle(
                            textDirection = TextDirection.Rtl
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Tactile Nudge / Stepper D-Pad Panel
 *
 * Provides physical directional buttons to nudge and expand/shrink line boundaries
 * with 0.1% / 0.5% / 1.5% micro-precision increments without fingers covering the text.
 */
@Composable
private fun TactileNudgePanel(
    nudgeStepIndex: Int,
    onStepIndexChange: (Int) -> Unit,
    onNudge: (dx: Float, dy: Float, target: String) -> Unit,
    onAutoAlign: () -> Unit = {},
    onRotate: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("line_editor_nudge_panel")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header & Step Size Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Penyesuaian Presisi (Nudge)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                // Step Size Selector Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Halus", "Normal", "Kasar").forEachIndexed { idx, label ->
                        val isSelected = idx == nudgeStepIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) ScholarBlue else Color.Transparent)
                                .clickable { onStepIndexChange(idx) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Boundary Border Expansion / Contraction Steppers
            Text(
                text = "Perluas / Sempitkan Batas Sisi:",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top border: Expand Up / Shrink Down
                NudgePairButton(
                    label = "Atas",
                    onExpand = { onNudge(0f, -1f, "TOP") },
                    onShrink = { onNudge(0f, 1f, "TOP") },
                    modifier = Modifier.weight(1f)
                )

                // Bottom border: Expand Down / Shrink Up
                NudgePairButton(
                    label = "Bawah",
                    onExpand = { onNudge(0f, 1f, "BOTTOM") },
                    onShrink = { onNudge(0f, -1f, "BOTTOM") },
                    modifier = Modifier.weight(1f)
                )

                // Left border: Expand Left / Shrink Right
                NudgePairButton(
                    label = "Kiri",
                    onExpand = { onNudge(-1f, 0f, "LEFT") },
                    onShrink = { onNudge(1f, 0f, "LEFT") },
                    modifier = Modifier.weight(1f)
                )

                // Right border: Expand Right / Shrink Left
                NudgePairButton(
                    label = "Kanan",
                    onExpand = { onNudge(1f, 0f, "RIGHT") },
                    onShrink = { onNudge(-1f, 0f, "RIGHT") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // D-Pad for Geser Seluruh Kotak & Posisi Baseline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Shift Whole Box D-Pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "Geser Kotak",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    // D-Pad Up
                    MiniDpadButton(
                        icon = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Geser Kotak ke Atas",
                        onClick = { onNudge(0f, -1f, "WHOLE") }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // D-Pad Left
                        MiniDpadButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Geser Kotak ke Kiri",
                            onClick = { onNudge(-1f, 0f, "WHOLE") }
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenWith,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // D-Pad Right
                        MiniDpadButton(
                            icon = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Geser Kotak ke Kanan",
                            onClick = { onNudge(1f, 0f, "WHOLE") }
                        )
                    }

                    // D-Pad Down
                    MiniDpadButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Geser Kotak ke Bawah",
                        onClick = { onNudge(0f, 1f, "WHOLE") }
                    )
                }

                // Right: Baseline Position Steppers
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "Posisi Baseline",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoldPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onNudge(0f, -1f, "BASELINE") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Baseline Naik", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Naik", fontSize = 10.sp, color = GoldPrimary)
                        }

                        Button(
                            onClick = { onNudge(0f, 1f, "BASELINE") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Baseline Turun", tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Turun", fontSize = 10.sp, color = GoldPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF334155), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Orientasi & Kemiringan (Dominant Skew & Fine Rotate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAutoAlign,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary.copy(alpha = 0.22f),
                        contentColor = GoldPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("line_editor_nudge_auto_align_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Align", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Putar:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                    IconButton(
                        onClick = { onRotate(-1f) },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Putar Kiri 1 Derajat",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    IconButton(
                        onClick = { onRotate(1f) },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Putar Kanan 1 Derajat",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgePairButton(
    label: String,
    onExpand: () -> Unit,
    onShrink: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .clickable { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .clickable { onShrink() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF43F5E))
            }
        }
    }
}

@Composable
private fun MiniDpadButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0F172A))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Coordinate calculation helpers
 */
private fun applyNudge(
    dx: Float,
    dy: Float,
    target: String,
    pTL: Offset,
    pTR: Offset,
    pBR: Offset,
    pBL: Offset,
    bStart: Offset,
    bEnd: Offset,
    onUpdate: (Offset, Offset, Offset, Offset, Offset, Offset) -> Unit
) {
    when (target) {
        "TOP" -> {
            val nTL = Offset(pTL.x, (pTL.y + dy).coerceIn(0f, 1f))
            val nTR = Offset(pTR.x, (pTR.y + dy).coerceIn(0f, 1f))
            onUpdate(nTL, nTR, pBR, pBL, bStart, bEnd)
        }
        "BOTTOM" -> {
            val nBL = Offset(pBL.x, (pBL.y + dy).coerceIn(0f, 1f))
            val nBR = Offset(pBR.x, (pBR.y + dy).coerceIn(0f, 1f))
            onUpdate(pTL, pTR, nBR, nBL, bStart, bEnd)
        }
        "LEFT" -> {
            val nTL = Offset((pTL.x + dx).coerceIn(0f, 1f), pTL.y)
            val nBL = Offset((pBL.x + dx).coerceIn(0f, 1f), pBL.y)
            onUpdate(nTL, pTR, pBR, nBL, bStart, bEnd)
        }
        "RIGHT" -> {
            val nTR = Offset((pTR.x + dx).coerceIn(0f, 1f), pTR.y)
            val nBR = Offset((pBR.x + dx).coerceIn(0f, 1f), pBR.y)
            onUpdate(pTL, nTR, nBR, pBL, bStart, bEnd)
        }
        "WHOLE" -> {
            val nTL = Offset((pTL.x + dx).coerceIn(0f, 1f), (pTL.y + dy).coerceIn(0f, 1f))
            val nTR = Offset((pTR.x + dx).coerceIn(0f, 1f), (pTR.y + dy).coerceIn(0f, 1f))
            val nBR = Offset((pBR.x + dx).coerceIn(0f, 1f), (pBR.y + dy).coerceIn(0f, 1f))
            val nBL = Offset((pBL.x + dx).coerceIn(0f, 1f), (pBL.y + dy).coerceIn(0f, 1f))
            val nBS = Offset((bStart.x + dx).coerceIn(0f, 1f), (bStart.y + dy).coerceIn(0f, 1f))
            val nBE = Offset((bEnd.x + dx).coerceIn(0f, 1f), (bEnd.y + dy).coerceIn(0f, 1f))
            onUpdate(nTL, nTR, nBR, nBL, nBS, nBE)
        }
        "BASELINE" -> {
            val nBS = Offset(bStart.x, (bStart.y + dy).coerceIn(0f, 1f))
            val nBE = Offset(bEnd.x, (bEnd.y + dy).coerceIn(0f, 1f))
            onUpdate(pTL, pTR, pBR, pBL, nBS, nBE)
        }
    }
}

private fun extractBoxCoordinates(
    maskPolygonJson: String,
    baselinePointsJson: String
): List<Offset> {
    val pts = parsePointList(maskPolygonJson)
    if (pts.size >= 4) {
        return pts.take(4)
    }

    val bPts = parsePointList(baselinePointsJson)
    if (bPts.size >= 2) {
        val minX = bPts.minOf { it.x }
        val maxX = bPts.maxOf { it.x }
        val avgY = (bPts[0].y + bPts[1].y) / 2f
        val topY = (avgY - 0.035f).coerceAtLeast(0.01f)
        val bottomY = (avgY + 0.015f).coerceAtMost(0.99f)
        return listOf(
            Offset(minX, topY),
            Offset(maxX, topY),
            Offset(maxX, bottomY),
            Offset(minX, bottomY)
        )
    }

    return listOf(
        Offset(0.15f, 0.30f),
        Offset(0.85f, 0.30f),
        Offset(0.85f, 0.35f),
        Offset(0.15f, 0.35f)
    )
}

private fun extractBaselineCoordinates(
    baselinePointsJson: String,
    pTL: Offset,
    pTR: Offset,
    pBR: Offset,
    pBL: Offset
): Pair<Offset, Offset> {
    val bPts = parsePointList(baselinePointsJson)
    if (bPts.size >= 2) {
        return Pair(bPts.first(), bPts.last())
    }

    val minX = listOf(pTL.x, pBL.x).minOrNull() ?: 0.2f
    val maxX = listOf(pTR.x, pBR.x).maxOrNull() ?: 0.8f
    val bottomY = listOf(pBL.y, pBR.y).average().toFloat()
    val baselineY = (bottomY - 0.005f).coerceAtLeast(0.01f)

    return Pair(Offset(minX, baselineY), Offset(maxX, baselineY))
}

private fun parsePointList(pointsJson: String): List<Offset> {
    if (pointsJson.isBlank()) return emptyList()
    return try {
        pointsJson.split(";").mapNotNull { pairStr ->
            val parts = pairStr.split(",")
            if (parts.size == 2) {
                val x = parts[0].trim().toFloatOrNull()
                val y = parts[1].trim().toFloatOrNull()
                if (x != null && y != null) Offset(x, y) else null
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun getTypologyColor(typology: String): Color {
    return when (typology.lowercase()) {
        "heading", "عنوان" -> Color(0xFFE11D48) // Vibrant Crimson
        "matan", "متن" -> Color(0xFF2563EB) // Royal Blue
        "syarah", "شرح" -> Color(0xFFD97706) // Amber Gold
        "marginalia", "حاشية", "هامش" -> Color(0xFF9333EA) // Vivid Violet
        "footnote", "حاشية سفلية" -> Color(0xFF0D9488) // Teal
        else -> Color(0xFF2563EB)
    }
}

private fun loadBitmapSafely(context: Context, imageResName: String?, imageUri: String?): Bitmap? {
    return try {
        if (!imageUri.isNullOrEmpty()) {
            val uri = android.net.Uri.parse(imageUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } else {
            val resName = when (imageResName) {
                "manuscript_p2" -> "manuscript_p2"
                else -> "manuscript_p1"
            }
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                BitmapFactory.decodeResource(context.resources, resId)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Calculates dominant skew angle (in degrees, horizontal = 0.0°) of manuscript ink
 * within the specified crop area using radon/projection variance maximization.
 */
fun calculateDominantSkewAngle(
    bitmap: Bitmap,
    cropLeft: Int,
    cropTop: Int,
    cropWidth: Int,
    cropHeight: Int
): Float {
    if (cropWidth < 8 || cropHeight < 8) return 0f

    val safeLeft = cropLeft.coerceIn(0, bitmap.width - 1)
    val safeTop = cropTop.coerceIn(0, bitmap.height - 1)
    val safeWidth = cropWidth.coerceIn(4, bitmap.width - safeLeft)
    val safeHeight = cropHeight.coerceIn(4, bitmap.height - safeTop)

    val pixels = IntArray(safeWidth * safeHeight)
    try {
        bitmap.getPixels(pixels, 0, safeWidth, safeLeft, safeTop, safeWidth, safeHeight)
    } catch (e: Exception) {
        return 0f
    }

    var sumLum = 0.0
    val lumArray = FloatArray(pixels.size)
    for (i in pixels.indices) {
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val lum = 0.299f * r + 0.587f * g + 0.114f * b
        lumArray[i] = lum
        sumLum += lum
    }
    val meanLum = (sumLum / pixels.size).toFloat()
    val inkThreshold = (meanLum * 0.86f).coerceAtMost(170f)

    val centerX = safeWidth / 2.0f
    val centerY = safeHeight / 2.0f

    val inkPoints = mutableListOf<Pair<Float, Float>>()
    for (y in 0 until safeHeight) {
        val rowOffset = y * safeWidth
        for (x in 0 until safeWidth) {
            if (lumArray[rowOffset + x] < inkThreshold) {
                inkPoints.add(Pair(x - centerX, y - centerY))
            }
        }
    }

    if (inkPoints.size < 12) return 0f

    // Coarse candidate angles: from -50° to +50° with 1° steps, plus vertical 90°
    val coarseAngles = mutableListOf<Double>()
    for (a in -50..50) coarseAngles.add(a.toDouble())
    coarseAngles.add(90.0)

    var bestAngle = 0.0
    var maxVariance = -1.0

    for (deg in coarseAngles) {
        val rad = Math.toRadians(deg)
        val sinA = kotlin.math.sin(rad).toFloat()
        val cosA = kotlin.math.cos(rad).toFloat()

        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for ((rx, ry) in inkPoints) {
            val v = -rx * sinA + ry * cosA
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }

        val rangeV = maxV - minV
        if (rangeV < 4f) continue

        val binSize = 2.0f
        val numBins = ((rangeV / binSize).toInt() + 1).coerceIn(4, 300)
        val hist = DoubleArray(numBins)

        for ((rx, ry) in inkPoints) {
            val v = -rx * sinA + ry * cosA
            val bIdx = ((v - minV) / binSize).toInt().coerceIn(0, numBins - 1)
            hist[bIdx] += 1.0
        }

        var sum = 0.0
        for (h in hist) sum += h
        val mean = sum / numBins
        var variance = 0.0
        for (h in hist) variance += (h - mean) * (h - mean)
        variance /= numBins

        if (variance > maxVariance) {
            maxVariance = variance
            bestAngle = deg
        }
    }

    // Fine refinement ±1.5° with 0.25° step
    var fineBestAngle = bestAngle
    var fineMaxVariance = maxVariance
    val fineStep = 0.25
    var fDeg = bestAngle - 1.5
    while (fDeg <= bestAngle + 1.5) {
        val rad = Math.toRadians(fDeg)
        val sinA = kotlin.math.sin(rad).toFloat()
        val cosA = kotlin.math.cos(rad).toFloat()

        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for ((rx, ry) in inkPoints) {
            val v = -rx * sinA + ry * cosA
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }

        val rangeV = maxV - minV
        if (rangeV >= 4f) {
            val binSize = 2.0f
            val numBins = ((rangeV / binSize).toInt() + 1).coerceIn(4, 300)
            val hist = DoubleArray(numBins)

            for ((rx, ry) in inkPoints) {
                val v = -rx * sinA + ry * cosA
                val bIdx = ((v - minV) / binSize).toInt().coerceIn(0, numBins - 1)
                hist[bIdx] += 1.0
            }

            var sum = 0.0
            for (h in hist) sum += h
            val mean = sum / numBins
            var variance = 0.0
            for (h in hist) variance += (h - mean) * (h - mean)
            variance /= numBins

            if (variance > fineMaxVariance) {
                fineMaxVariance = variance
                fineBestAngle = fDeg
            }
        }
        fDeg += fineStep
    }

    return fineBestAngle.toFloat()
}
