package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.GoldPrimary

@Composable
fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("keyboard_shortcuts_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pintasan Keyboard Fisik",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = "Dukungan navigasi & aksi cepat menggunakan keyboard fisik / Bluetooth:",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShortcutSectionHeader("Navigasi Mode & Halaman")
                    ShortcutItem(keys = listOf("Ctrl", "1"), description = "Beralih ke Mode Baca (Viewer)")
                    ShortcutItem(keys = listOf("Ctrl", "2"), description = "Beralih ke Mode Tulis (PDF Writer)")
                    ShortcutItem(keys = listOf("Ctrl", "M"), description = "Toggle Mode Baca ↔ Mode Tulis")
                    ShortcutItem(keys = listOf("PageUp", "Alt + ←"), description = "Pindah ke Lembar Halaman Sebelumnya")
                    ShortcutItem(keys = listOf("PageDown", "Alt + →"), description = "Pindah ke Lembar Halaman Berikutnya")

                    Spacer(modifier = Modifier.height(6.dp))
                    ShortcutSectionHeader("Navigasi Baris & Teks")
                    ShortcutItem(keys = listOf("Alt + ↑", "Ctrl + ↑", "["), description = "Pilih Baris Sebelumnya")
                    ShortcutItem(keys = listOf("Alt + ↓", "Ctrl + ↓", "]"), description = "Pilih Baris Berikutnya")
                    ShortcutItem(keys = listOf("Escape"), description = "Hapus Pilihan Baris / Tutup Dialog")

                    Spacer(modifier = Modifier.height(6.dp))
                    ShortcutSectionHeader("Alur Kerja & Ekspor Naskah")
                    ShortcutItem(keys = listOf("Ctrl", "E"), description = "Ekspor PDF Dokumen Naskah / XML")
                    ShortcutItem(keys = listOf("Ctrl", "D"), description = "Jalankan Deteksi Baris PP-OCRv5")
                    ShortcutItem(keys = listOf("Ctrl", "R"), description = "Urutkan Baris Sesuai Urutan Baca Filologi")
                    ShortcutItem(keys = listOf("Ctrl", "B"), description = "Buka Editor Batas Baris (Line Boundary Modal)")
                    ShortcutItem(keys = listOf("Ctrl", "L"), description = "Buka Pengelola Layer Transkripsi")
                    ShortcutItem(keys = listOf("Ctrl", "I"), description = "Buka Info & Metadata Naskah")
                    ShortcutItem(keys = listOf("F1", "Ctrl + /"), description = "Tampilkan Panduan Pintasan Ini")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Mengerti", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShortcutSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = GoldPrimary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ShortcutItem(
    keys: List<String>,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = description,
            fontSize = 11.5.sp,
            color = Color(0xFFE2E8F0),
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { key ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}
