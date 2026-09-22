package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ModelManagerDialog
import com.example.ui.theme.ConfHigh
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMuted
import com.example.ui.theme.DarkText
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ParchmentBg
import com.example.ui.theme.ParchmentBorder
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.ParchmentSurface
import com.example.ui.theme.ParchmentText
import com.example.ui.theme.ScholarBlue
import com.example.ui.theme.ScholarBlueContainer
import com.example.ui.viewmodel.FoliaViewModel
import com.example.ui.viewmodel.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: FoliaViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMode = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDark
    }

    val showModelManagerDialog by viewModel.showModelManagerDialog.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var autoNormalizeHamzah by remember { mutableStateOf(true) }
    var autoStripTashkeelInSearch by remember { mutableStateOf(true) }
    var defaultExportFormat by remember { mutableStateOf("PAGE_XML") }
    var defaultReadingDirection by remember { mutableStateOf("RTL") }
    var showBaselineHandles by remember { mutableStateOf(true) }

    // Surfaces the result of model import/activation (and any other
    // repository action that sets statusMessage) as a Snackbar. Without
    // this, importing or activating a model from the Model Manager opened
    // from Settings gave the user no feedback at all -- success and
    // failure looked identical (nothing visibly happened either way),
    // which is what made "import/switch model doesn't work" hard to tell
    // apart from "it worked silently". WorkspaceScreen already had this;
    // it just wasn't wired up here or in HomeScreen, the two other places
    // ModelManagerDialog can be opened from.
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Header Banner ---
            Surface(
                color = if (isDarkMode) DarkBg else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = if (isDarkMode) 0.dp else 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDarkMode) GoldPrimary.copy(alpha = 0.25f) else GoldContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (isDarkMode) GoldLight else GoldDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Pengaturan Proyek & Tampilan",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) DarkText else ScholarBlue
                        )
                        Text(
                            text = "Mode Gelap, HTR On-Device, Normalisasi & Ekspor",
                            fontSize = 11.sp,
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Section 1: System-Wide Dark Mode & Eye Comfort ---
                SettingsCard(title = "Tema & Mode Gelap (Sesi Transkripsi Malam)", icon = Icons.Default.DarkMode) {
                    Text(
                        text = "Sesuaikan kontras tampilan untuk kenyamanan mata saat transkripsi naskah larut malam:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val themeOptions = listOf(
                        Triple(ThemeMode.DARK, "Mode Gelap (Sesi Malam)", "Direkomendasikan untuk mereduksi ketegangan mata saat transkripsi lembar naskah"),
                        Triple(ThemeMode.LIGHT, "Mode Terang (Klasik)", "Palet perkamen dan kertas putih bersih untuk penggunaan di ruangan terang"),
                        Triple(ThemeMode.SYSTEM, "Ikuti Sistem Android", "Otomatis menyesuaikan dengan jadwal mode gelap perangkat Anda")
                    )

                    themeOptions.forEach { (mode, title, desc) ->
                        val isSelected = themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ScholarBlueContainer.copy(alpha = 0.6f) else Color.Transparent)
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setThemeMode(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ScholarBlue,
                                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) ScholarBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // --- Section 2: HTR & OCR Models ---
                SettingsCard(title = "Engine HTR & Inferensi On-Device", icon = Icons.Default.Memory) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "KitabHTR-Tiny (CTC Model)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Model lokal 18.4 MB untuk aksara Arab klasik & Pegon",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ConfHigh.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = "Aktif • Offline",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ConfHigh
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.setShowModelManagerDialog(true) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("manage_models_btn")
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = GoldDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kelola Model & Unduh Alternatif", color = GoldDark, fontSize = 13.sp)
                    }
                }

                // --- Section 3: Normalization & Linguistic Rules ---
                SettingsCard(title = "Aturan Normalisasi Aksara Arab", icon = Icons.Default.TextFields) {
                    SettingToggleRow(
                        title = "Normalisasi Otomatis Hamzah & Alif",
                        subtitle = "Menyeragamkan أ / إ / آ menjadi ا saat pemrosesan HTR",
                        checked = autoNormalizeHamzah,
                        onCheckedChange = { autoNormalizeHamzah = it }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = "Abaikan Harakat / Tashkeel pada Pencarian",
                        subtitle = "Mempermudah pencarian kata kunci tanpa memerlukan harakat persis",
                        checked = autoStripTashkeelInSearch,
                        onCheckedChange = { autoStripTashkeelInSearch = it }
                    )
                }

                // --- Section 4: Scholarly Export Defaults ---
                SettingsCard(title = "Format Ekspor Standar", icon = Icons.Default.FileDownload) {
                    Text(
                        text = "Pilih format default untuk ekspor naskah & dataset training:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "PAGE_XML" to "PAGE-XML 2019 (Standar Riset Filologi Eropa)",
                        "ALTO_XML" to "ALTO-XML v4 (Standar Perpustakaan Digital / OCR-D)",
                        "PLAIN_TXT" to "Teks Polos (.txt) dengan Penanda Tipologi",
                        "KAGGLE_JSON" to "Kaggle / PyTorch CRNN Training JSON"
                    ).forEach { (formatKey, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { defaultExportFormat = formatKey }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultExportFormat == formatKey,
                                onClick = { defaultExportFormat = formatKey },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ScholarBlue,
                                    unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label, 
                                fontSize = 12.5.sp, 
                                fontWeight = if (defaultExportFormat == formatKey) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // --- Section 5: Manuscript Canvas Preferences ---
                SettingsCard(title = "Kanvas & Visualisasi Vektor", icon = Icons.Default.Palette) {
                    SettingToggleRow(
                        title = "Tampilkan Titik Handle Garis Baseline",
                        subtitle = "Menampilkan lingkaran interaktif di ujung awal/akhir garis",
                        checked = showBaselineHandles,
                        onCheckedChange = { showBaselineHandles = it }
                    )
                }

                // --- Section 6: About Folia ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tentang folia v1.2",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Platform transkripsi dan segmentasi naskah manuskrip berbasis on-device HTR yang dirancang untuk preservasi filologi Islam dan manuskrip nusantara.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
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
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GoldPrimary
            )
        )
    }
}
