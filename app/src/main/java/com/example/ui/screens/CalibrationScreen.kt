package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ColumnConfig
import com.example.engine.ReceiptOcrEngine
import com.example.ui.components.OcrCalibrationCanvas
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val bitmap by viewModel.activeReceiptBitmap.collectAsStateWithLifecycle()
    val ocrResult by viewModel.activeOcrResult.collectAsStateWithLifecycle()
    val columns by viewModel.activeColumns.collectAsStateWithLifecycle()
    val selectedColumn by viewModel.selectedCalibrationColumn.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val autoColumns = remember(columns) { columns.filter { it.isAuto && it.isEnabled } }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Local temporary coordinates while dragging/tapping
    var currentNormX by remember(selectedColumn) { mutableFloatStateOf(selectedColumn?.normalizedX ?: 0.1f) }
    var currentNormY by remember(selectedColumn) { mutableFloatStateOf(selectedColumn?.normalizedY ?: 0.1f) }
    var currentNormW by remember(selectedColumn) { mutableFloatStateOf(selectedColumn?.normalizedWidth ?: 0.8f) }
    var currentNormH by remember(selectedColumn) { mutableFloatStateOf(selectedColumn?.normalizedHeight ?: 0.06f) }

    // Live extracted preview from current bounding box
    var liveExtractedText by remember { mutableStateOf("") }

    LaunchedEffect(currentNormX, currentNormY, currentNormW, currentNormH, selectedColumn, ocrResult) {
        val col = selectedColumn
        val items = ocrResult?.detectedItems
        if (col != null && items != null) {
            val matchingItems = items.filter { item ->
                val itemBox = item.normalizedBox
                val targetRight = currentNormX + currentNormW
                val targetBottom = currentNormY + currentNormH
                // Check intersection or vertical overlap
                (itemBox.left < targetRight && itemBox.right > currentNormX &&
                        itemBox.top < targetBottom && itemBox.bottom > currentNormY)
            }.sortedWith(compareBy<com.example.engine.DetectedTextItem> { it.normalizedBox.top }.thenBy { it.normalizedBox.left })

            val rawCombined = matchingItems.joinToString(" ") { it.text }
            liveExtractedText = ReceiptOcrEngine.applyFunctionAndCharRules(rawCombined, col)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Kalibrasi Koordinat OCR",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Pilih kolom dan tentukan area baca pada gambar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("calibration_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Column Selection Chips (Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in autoColumns) {
                    val isSelected = selectedColumn?.id == col.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.selectColumnForCalibration(col)
                            currentNormX = col.normalizedX
                            currentNormY = col.normalizedY
                            currentNormW = col.normalizedWidth
                            currentNormH = col.normalizedHeight
                        },
                        label = { Text(col.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Interactive Image & Canvas Area
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    OcrCalibrationCanvas(
                        bitmap = bitmap!!,
                        detectedItems = ocrResult?.detectedItems ?: emptyList(),
                        selectedColumn = selectedColumn?.copy(
                            normalizedX = currentNormX,
                            normalizedY = currentNormY,
                            normalizedWidth = currentNormW,
                            normalizedHeight = currentNormH
                        ),
                        onBoxChanged = { normX, normY, normW, normH ->
                            currentNormX = normX
                            currentNormY = normY
                            currentNormW = normW
                            currentNormH = normH
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Belum ada gambar struk yang dimuat.")
                }
            }

            // Bottom Panel: Live Extraction & Save Action
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kolom: ${selectedColumn?.name ?: "-"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Fungsi: ${selectedColumn?.columnFunction ?: "FREE_TEXT"}",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Live Result Display
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Hasil Pembacaan Area:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (liveExtractedText.isBlank()) "(Tidak ada teks terbaca di kotak ini)" else liveExtractedText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (liveExtractedText.isBlank()) Color.Gray else Color(0xFF16A34A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "X: ${(currentNormX * 100).toInt()}% Y: ${(currentNormY * 100).toInt()}% W: ${(currentNormW * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                viewModel.saveCalibrationBox(currentNormX, currentNormY, currentNormW, currentNormH)
                            },
                            modifier = Modifier.testTag("save_calibration_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Koordinat")
                        }
                    }
                }
            }
        }
    }
}
