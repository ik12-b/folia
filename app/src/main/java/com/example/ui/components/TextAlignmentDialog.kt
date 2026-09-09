package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.TextAlignmentEngine
import com.example.ui.theme.ConfHigh
import com.example.ui.theme.ConfLow
import com.example.ui.theme.ConfMedium
import com.example.ui.theme.GoldPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextAlignmentDialog(
    currentTranscriptions: String,
    alignmentResult: TextAlignmentEngine.AlignmentResult?,
    onRunAlignment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var referenceInput by remember {
        mutableStateOf(
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\nالْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ، وَبِهِ نَسْتَعِينُ عَلَى أُمُورِ الدُّنْيَا وَالدِّينِ\nقَالَ الشَّيْخُ الإِمَامُ أَبُو شُجَاعٍ أَحْمَدُ بْنُ الْحُسَيْنِ"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("text_alignment_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CompareArrows, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Penyelarasan Teks Referensi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = "Lakukan forced alignment (Needleman-Wunsch) antara hasil transkripsi dan edisi cetak referensi (Shamela/OpenITI).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Input reference
                OutlinedTextField(
                    value = referenceInput,
                    onValueChange = { referenceInput = it },
                    label = { Text("Teks Edisi Referensi (Witness)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onRunAlignment(referenceInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_alignment_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Jalankan Alignment & Hitung Diff")
                }

                if (alignmentResult != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Alignment Summary
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Kecocokan: %.1f%%".format(alignmentResult.similarityScore),
                                    fontWeight = FontWeight.Bold,
                                    color = if (alignmentResult.similarityScore > 85f) ConfHigh else ConfMedium
                                )
                                Text(
                                    text = "${alignmentResult.matches} Cocok | ${alignmentResult.substitutions} Beda",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Visualisasi Token Diff:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                alignmentResult.tokens.take(120).forEach { token ->
                                    val bg = when (token.type) {
                                        TextAlignmentEngine.DiffType.MATCH -> ConfHigh.copy(alpha = 0.15f)
                                        TextAlignmentEngine.DiffType.SUBSTITUTION -> ConfMedium.copy(alpha = 0.25f)
                                        TextAlignmentEngine.DiffType.INSERTION -> Color.Blue.copy(alpha = 0.2f)
                                        TextAlignmentEngine.DiffType.DELETION -> ConfLow.copy(alpha = 0.25f)
                                    }
                                    val textColor = when (token.type) {
                                        TextAlignmentEngine.DiffType.MATCH -> MaterialTheme.colorScheme.onSurface
                                        TextAlignmentEngine.DiffType.SUBSTITUTION -> ConfMedium
                                        TextAlignmentEngine.DiffType.INSERTION -> Color.Blue
                                        TextAlignmentEngine.DiffType.DELETION -> ConfLow
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(bg)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (token.ocrChar.isNotEmpty()) token.ocrChar else token.refChar,
                                            fontSize = 13.sp,
                                            color = textColor,
                                            fontWeight = if (token.type == TextAlignmentEngine.DiffType.MATCH) FontWeight.Normal else FontWeight.Bold
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
}
