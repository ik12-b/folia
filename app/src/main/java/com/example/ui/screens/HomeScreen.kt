package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DocumentEntity
import com.example.ui.components.ModelManagerDialog
import com.example.ui.components.NewDocumentDialog
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkText
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ScholarBlue
import com.example.ui.theme.ScholarBlueContainer
import com.example.ui.viewmodel.FoliaViewModel
import com.example.ui.viewmodel.ThemeMode

@Composable
fun HomeScreen(
    viewModel: FoliaViewModel,
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDark
    }

    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val showNewDocDialog by viewModel.showNewDocDialog.collectAsStateWithLifecycle()
    val showModelManagerDialog by viewModel.showModelManagerDialog.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedScriptFilter by remember { mutableStateOf("Semua") }
    var isGridView by remember { mutableStateOf(false) }

    val filteredDocs = remember(documents, searchQuery, selectedScriptFilter) {
        documents.filter { doc ->
            val matchesQuery = searchQuery.isBlank() || doc.title.contains(searchQuery, ignoreCase = true)
            val matchesScript = selectedScriptFilter == "Semua" || doc.mainScript.contains(selectedScriptFilter, ignoreCase = true)
            matchesQuery && matchesScript
        }
    }

    // See SettingsScreen for why this is needed: without it, importing or
    // activating a model from the Model Manager opened here (via the
    // "KitabHTR" shortcut on this screen) gave no visible feedback either
    // way.
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.setShowNewDocDialog(true) },
                containerColor = GoldPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_document_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Manuskrip")
            }
        },
        modifier = modifier.fillMaxSize().testTag("home_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // --- Header Banner ---
            Surface(
                color = if (isDarkTheme) DarkBg else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = if (isDarkTheme) 0.dp else 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkTheme) GoldPrimary.copy(alpha = 0.25f) else GoldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) GoldLight else GoldDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "folia",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDarkTheme) DarkText else ScholarBlue,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Galeri & Koleksi Naskah",
                                    fontSize = 11.sp,
                                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                                )
                            }
                        }

                        // Model Manager Shortcut Button
                        OutlinedButton(
                            onClick = { viewModel.setShowModelManagerDialog(true) },
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDarkTheme) Color(0xFF334155) else ScholarBlue.copy(alpha = 0.3f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFEFF6FF)
                            ),
                            modifier = Modifier.testTag("models_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = if (isDarkTheme) GoldLight else ScholarBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "KitabHTR", 
                                fontSize = 12.sp, 
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkTheme) DarkText else ScholarBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Specs Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            HomeStatItem(title = "Koleksi Naskah", value = "${documents.size}", isDarkTheme = isDarkTheme)
                            HomeStatItem(title = "Engine HTR", value = "KitabHTR-Tiny", isDarkTheme = isDarkTheme)
                            HomeStatItem(title = "Penyimpanan", value = "Room Offline", isDarkTheme = isDarkTheme)
                        }
                    }
                }
            }

            // --- Search, Filter & View Mode Controls ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari naskah atau kitab...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ScholarBlue) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = ScholarBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Script Filter Chips & View Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Semua", "Arabic", "Jawi", "Latin").forEach { script ->
                            val isSelected = selectedScriptFilter == script
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedScriptFilter = script },
                                label = { 
                                    Text(
                                        script, 
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ) 
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ScholarBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) ScholarBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    // Toggle List vs Grid
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Ganti Tampilan",
                            tint = ScholarBlue
                        )
                    }
                }
            }

            // --- Document Content Area (List vs Grid) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (filteredDocs.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 30.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Tidak Ditemukan" else "Belum Ada Manuskrip",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gunakan tombol + untuk menambahkan naskah baru atau muat preset Kitab Kuning.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else if (isGridView) {
                    // Visual Folio Gallery Grid View
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentGridCard(
                                document = doc,
                                onClick = { onOpenDocument(doc.id) },
                                onDelete = { viewModel.deleteDocument(doc.id) }
                            )
                        }
                    }
                } else {
                    // Structured List View
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentListCard(
                                document = doc,
                                onClick = { onOpenDocument(doc.id) },
                                onDelete = { viewModel.deleteDocument(doc.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showNewDocDialog) {
            NewDocumentDialog(
                onCreate = { title, script, preset ->
                    viewModel.createNewDocument(title, script, preset)
                },
                onDismiss = { viewModel.setShowNewDocDialog(false) }
            )
        }

        if (showModelManagerDialog) {
            ModelManagerDialog(
                models = allModels,
                onActivateModel = { model -> viewModel.setActiveModel(model) },
                onImportModel = { resolver, uri, type -> viewModel.importOcrModelFromUri(resolver, uri, type) },
                onDismiss = { viewModel.setShowModelManagerDialog(false) }
            )
        }
    }
}

@Composable
private fun HomeStatItem(title: String, value: String, isDarkTheme: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title, 
            fontSize = 10.sp, 
            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value, 
            fontSize = 13.sp, 
            fontWeight = FontWeight.Bold, 
            color = if (isDarkTheme) GoldLight else ScholarBlue
        )
    }
}

@Composable
private fun DocumentListCard(
    document: DocumentEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("document_card_${document.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = GoldDark,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = document.title,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ScholarBlueContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = document.mainScript,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScholarBlue
                            )
                        }
                        Text(
                            text = "Arah: ${document.readDirection}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus Dokumen",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun DocumentGridCard(
    document: DocumentEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("document_grid_card_${document.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Simulated Folio Preview Thumbnail Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = GoldPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = document.title,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ScholarBlueContainer)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = document.mainScript,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ScholarBlue
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Dokumen",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
