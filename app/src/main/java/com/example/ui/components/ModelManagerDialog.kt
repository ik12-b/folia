package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.OcrModelEntity
import com.example.ui.theme.ConfHigh
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ScholarBlue

@Composable
fun ModelManagerDialog(
    models: List<OcrModelEntity>,
    onConfigurePpOcr: () -> Unit = {},
    onActivateModel: (OcrModelEntity) -> Unit = {},
    onImportModel: (android.content.ContentResolver, Uri, type: String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Which type ("Segmentation" / "Transcription CTC") the next file-picker
    // result should be imported as. Set right before launching the picker.
    var pendingImportType by remember { mutableStateOf("Transcription CTC") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportModel(context.contentResolver, uri, pendingImportType)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("model_manager_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Model OCR & HTR On-Device",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = "Model inferensi lokal (.onnx) dioptimalkan untuk CPU seluler tanpa koneksi internet. Impor model kustom lalu jadikan aktif untuk menggantikan model bawaan.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Import buttons, one per model type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingImportType = "Segmentation"
                            filePickerLauncher.launch("*/*")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("btn_import_det_model")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Impor Model Deteksi", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingImportType = "Transcription CTC"
                            filePickerLauncher.launch("*/*")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("btn_import_rec_model")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Impor Model OCR", fontSize = 11.sp)
                    }
                }
                Text(
                    text = "Pilih berkas .onnx dari penyimpanan perangkat. Untuk model OCR dengan alfabet berbeda, sertakan berkas \"<namamodel>_keys.txt\" di folder yang sama.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    // A LazyColumn inside a Dialog's wrap-content Column
                    // has no height constraint to measure against by
                    // default, which is a well-known Compose pitfall --
                    // depending on how the surrounding constraints
                    // resolve, it can end up rendering only its first
                    // item (or none) instead of becoming scrollable, which
                    // is exactly what the "only one model card visible"
                    // bug report showed (3 models are seeded in the
                    // database -- see FoliaDatabase.populateInitialKitabs
                    // -- but only "KitabHTR-Tiny" was rendering). Giving
                    // it an explicit bounded height makes it scroll
                    // properly within the dialog instead.
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(models, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onConfigurePpOcr = onConfigurePpOcr,
                            onActivateModel = onActivateModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: OcrModelEntity,
    onConfigurePpOcr: () -> Unit,
    onActivateModel: (OcrModelEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isActive) {
                ConfHigh.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (model.isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (model.isActive) "Aktif" else "Tidak aktif",
                        tint = if (model.isActive) ConfHigh else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = model.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (model.type == "Segmentation") ScholarBlue.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (model.type == "Segmentation") "Deteksi Baris (Line Det)" else "Pengenalan Teks (HTR)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (model.type == "Segmentation") ScholarBlue else GoldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (model.type == "Segmentation") "Tujuan: Menemukan koordinat garis & baseline teks manuskrip (PPLCNetV3 + DBNet)." else "Tujuan: Membaca dan mentranskripsi rasm teks dari irisan baris (CRNN / CTC).",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Arsitektur: ${model.architecture}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Sumber: ${model.huggingFaceRepo} • ${"%.1f".format(model.fileSizeMb)} MB",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.isActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ConfHigh,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp)
                        )
                        Text(text = "Sedang Digunakan", fontSize = 12.sp, color = ConfHigh, fontWeight = FontWeight.Medium)
                    }
                } else if (!model.isInstalled) {
                    // Listed as a known model but its file isn't bundled/
                    // imported on this device -- showing an enabled
                    // "Jadikan Aktif" button here previously let the user
                    // tap it with no real effect (see
                    // FoliaDatabase.populateInitialKitabs for the KitabHTR-
                    // Tiny case this was written for): the button appeared
                    // to succeed but silently activated a different,
                    // already-installed model instead of this one.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp)
                        )
                        Text(
                            text = "Belum Diunduh — impor berkas .onnx untuk memakai model ini",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = { onActivateModel(model) },
                        colors = ButtonDefaults.buttonColors(containerColor = ScholarBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_activate_model_${model.id}")
                    ) {
                        Text("Jadikan Aktif", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (model.type == "Segmentation" && model.isActive) {
                    OutlinedButton(
                        onClick = onConfigurePpOcr,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Parameter Deteksi Baris", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
