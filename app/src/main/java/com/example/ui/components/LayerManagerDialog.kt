package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.TranscriptionLayerEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkText
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ParchmentBg
import com.example.ui.theme.ParchmentBorder
import com.example.ui.theme.ParchmentSurface
import com.example.ui.theme.ParchmentText
import com.example.ui.theme.ScholarBlue
import com.example.ui.theme.ScholarBlueLight

@Composable
fun LayerManagerDialog(
    layers: List<TranscriptionLayerEntity>,
    selectedLayerId: Long?,
    onSelectLayer: (Long) -> Unit,
    onCreateLayer: (name: String, copyFromLayerId: Long?) -> Unit,
    onDeleteLayer: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }
    var newLayerName by remember { mutableStateOf("") }
    var copySourceLayerId by remember { mutableStateOf<Long?>(selectedLayerId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = GoldDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kelola Layer Transkripsi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ParchmentText)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("layer_manager_dialog")
            ) {
                Text(
                    text = "Gunakan layer untuk memisahkan hasil mentah (Raw HTR), pembacaan diplomatik (Diplomatic), dan edisi kritis yang dinormalisasi (Normalized).",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!isAddingNew) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(layers) { layer ->
                            val isSelected = layer.id == selectedLayerId
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) GoldPrimary.copy(alpha = 0.12f) else ParchmentSurface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectLayer(layer.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = GoldDark, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Column {
                                            Text(
                                                text = layer.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = if (isSelected) GoldDark else ParchmentText
                                            )
                                            if (layer.isDefault) {
                                                Text("Layer Utama", fontSize = 10.sp, color = Color.Gray)
                                            } else if (layer.isGroundTruth) {
                                                Text("Ground Truth", fontSize = 10.sp, color = ScholarBlue)
                                            }
                                        }
                                    }

                                    if (!layer.isDefault && layers.size > 1) {
                                        IconButton(
                                            onClick = { onDeleteLayer(layer.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Layer",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { isAddingNew = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = GoldDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Buat Layer Baru", color = GoldDark, fontSize = 12.sp)
                    }
                } else {
                    // Form Create New Layer
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newLayerName,
                            onValueChange = { newLayerName = it },
                            label = { Text("Nama Layer Baru", fontSize = 12.sp) },
                            placeholder = { Text("misal: Terjemahan, Syarah Pegon, Normalized v2") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = ParchmentBorder
                            )
                        )

                        Text("Salin konten dari layer aktif:", fontSize = 11.sp, color = Color.Gray)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            layers.forEach { l ->
                                val isChosen = copySourceLayerId == l.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isChosen) ScholarBlue else ParchmentSurface)
                                        .clickable { copySourceLayerId = l.id }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = l.name,
                                        fontSize = 10.sp,
                                        color = if (isChosen) Color.White else ParchmentText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isAddingNew = false }) {
                                Text("Batal", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (newLayerName.isNotBlank()) {
                                        onCreateLayer(newLayerName.trim(), copySourceLayerId)
                                        isAddingNew = false
                                        newLayerName = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                            ) {
                                Text("Simpan Layer", color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isAddingNew) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = GoldDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = ParchmentBg,
        shape = RoundedCornerShape(16.dp)
    )
}
