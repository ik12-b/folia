package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentPartEntity
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ScholarBlue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceBottomNavBar(
    parts: List<DocumentPartEntity>,
    activePageIndex: Int,
    onSelectPage: (Int) -> Unit,
    onAddNewPage: () -> Unit,
    onDeleteCurrentPage: () -> Unit,
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showQuickJumpDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Automatically scroll to make active page visible in the chip row
    LaunchedEffect(activePageIndex) {
        if (parts.isNotEmpty() && activePageIndex in parts.indices) {
            listState.animateScrollToItem(activePageIndex)
        }
    }

    val barBg = if (isDarkMode) Color(0xFF131D2D) else Color(0xFFFFFFFF)
    val badgeBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF)
    val badgeBorder = if (isDarkMode) Color(0xFF334155) else Color(0xFFBFDBFE)
    val chipInactiveBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val chipInactiveText = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
    val iconTint = if (isDarkMode) Color.White else Color(0xFF0F172A)

    Surface(
        color = barBg,
        tonalElevation = 4.dp,
        shadowElevation = if (isDarkMode) 0.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("workspace_bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- Left: Stepper & Quick Jump Badge ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Prev Page Button
                IconButton(
                    onClick = { if (activePageIndex > 0) onSelectPage(activePageIndex - 1) },
                    enabled = activePageIndex > 0,
                    modifier = Modifier.size(28.dp).testTag("prev_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Halaman Sebelumnya",
                        tint = if (activePageIndex > 0) iconTint else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Interactive Current Page Badge (Click to open Quick Jump Grid)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
                        .clickable { showQuickJumpDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("page_indicator_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFF38BDF8) else ScholarBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Hal ${activePageIndex + 1} / ${parts.size.coerceAtLeast(1)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else ScholarBlue
                        )
                    }
                }

                // Next Page Button
                IconButton(
                    onClick = { if (activePageIndex < parts.size - 1) onSelectPage(activePageIndex + 1) },
                    enabled = activePageIndex < parts.size - 1,
                    modifier = Modifier.size(28.dp).testTag("next_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Halaman Selanjutnya",
                        tint = if (activePageIndex < parts.size - 1) iconTint else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // --- Center: Horizontal Quick Jump Chips Bar ---
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                itemsIndexed(parts) { idx, part ->
                    val isCurrent = idx == activePageIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isCurrent) ScholarBlue else chipInactiveBg)
                            .clickable { onSelectPage(idx) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("page_chip_${idx + 1}")
                    ) {
                        Text(
                            text = "${part.pageNumber}",
                            fontSize = 10.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) Color.White else chipInactiveText
                        )
                    }
                }
            }

            // --- Right: Quick Page Management Actions (+ / Delete) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Add Blank Page Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ScholarBlue)
                        .clickable { onAddNewPage() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("quick_add_page_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Lembar",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Lembar",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Delete Page Button (if parts > 1)
                if (parts.size > 1) {
                    IconButton(
                        onClick = onDeleteCurrentPage,
                        modifier = Modifier.size(26.dp).testTag("quick_delete_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Halaman",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }

    // --- Quick Jump Modal Grid & Slider Dialog ---
    if (showQuickJumpDialog) {
        val dialogBg = if (isDarkMode) Color(0xFF1E293B) else Color.White
        val dialogText = if (isDarkMode) Color.White else Color(0xFF0F172A)
        val dialogGridBg = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)

        AlertDialog(
            onDismissRequest = { showQuickJumpDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FindInPage,
                        contentDescription = null,
                        tint = if (isDarkMode) Color(0xFF38BDF8) else ScholarBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Lompat ke Halaman Naskah",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = dialogText
                    )
                }
            },
            text = {
                var sliderVal by remember { mutableFloatStateOf((activePageIndex + 1).toFloat()) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pilih nomor lembar untuk berpindah seketika:",
                        fontSize = 12.sp,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )

                    // Visual Page Grid / Chips
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(dialogGridBg)
                            .border(1.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            parts.forEachIndexed { idx, part ->
                                val isSelected = idx == activePageIndex
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) ScholarBlue
                                            else (if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF))
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) (if (isDarkMode) Color(0xFF38BDF8) else ScholarBlue)
                                            else (if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            onSelectPage(idx)
                                            showQuickJumpDialog = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Hal ${part.pageNumber}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else (if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF1E293B))
                                    )
                                }
                            }
                        }
                    }

                    // Slider for Fast Scrubbing (if more than 2 pages)
                    if (parts.size > 2) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hal 1", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(
                                    "Terpilih: Hal ${sliderVal.toInt()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color(0xFF38BDF8) else ScholarBlue
                                )
                                Text("Hal ${parts.size}", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                            Slider(
                                value = sliderVal,
                                onValueChange = { sliderVal = it },
                                onValueChangeFinished = {
                                    val targetIdx = (sliderVal.toInt() - 1).coerceIn(0, parts.size - 1)
                                    onSelectPage(targetIdx)
                                },
                                valueRange = 1f..parts.size.toFloat(),
                                steps = (parts.size - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = if (isDarkMode) Color(0xFF38BDF8) else ScholarBlue,
                                    activeTrackColor = ScholarBlue,
                                    inactiveTrackColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { showQuickJumpDialog = false },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1)
                    )
                ) {
                    Text("Tutup", color = if (isDarkMode) Color.White else Color(0xFF0F172A), fontSize = 12.sp)
                }
            },
            containerColor = dialogBg
        )
    }
}
