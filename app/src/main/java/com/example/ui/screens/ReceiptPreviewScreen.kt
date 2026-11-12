package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ColumnConfig
import com.example.ui.components.EditFieldDialog
import com.example.ui.components.ReceiptPaperView
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToTemplateSettings: () -> Unit
) {
    val context = LocalContext.current
    val template by viewModel.activeTemplate.collectAsStateWithLifecycle()
    val columns by viewModel.activeColumns.collectAsStateWithLifecycle()
    val columnValues by viewModel.activeColumnValues.collectAsStateWithLifecycle()
    val activeSourceApp by viewModel.activeSourceApp.collectAsStateWithLifecycle()
    val allSourceApps by viewModel.allSourceApps.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val pairedPrinters by viewModel.pairedPrinters.collectAsStateWithLifecycle()
    val selectedPrinter by viewModel.selectedPrinter.collectAsStateWithLifecycle()
    val activeBitmap by viewModel.activeReceiptBitmap.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Direct in-place editing state
    var editingColumn by remember { mutableStateOf<ColumnConfig?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var showAppDropdown by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Preview Struk",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${activeSourceApp?.appName ?: "Umum"} (${template?.paperWidthMm ?: 58}mm)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("preview_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Switch template source app
                    Box {
                        OutlinedButton(
                            onClick = { showAppDropdown = true },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = activeSourceApp?.appName?.take(10) ?: "Pilih App",
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = showAppDropdown,
                            onDismissRequest = { showAppDropdown = false }
                        ) {
                            allSourceApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.appName) },
                                    onClick = {
                                        viewModel.switchActiveSourceApp(app)
                                        showAppDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNavigateToTemplateSettings,
                        modifier = Modifier.testTag("open_template_settings_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Pengaturan Kolom")
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
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            // Action Bar (Scrollable horizontally)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Print System / PDF
                Button(
                    onClick = { viewModel.printReceipt(context, viaBluetooth = false) },
                    modifier = Modifier.testTag("system_print_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cetak (Sistem/PDF)", fontSize = 12.sp)
                }

                // Bluetooth Print
                FilledTonalButton(
                    onClick = {
                        if (selectedPrinter == null && pairedPrinters.isNotEmpty()) {
                            viewModel.selectPrinter(pairedPrinters.first())
                        }
                        viewModel.printReceipt(context, viaBluetooth = true)
                    },
                    modifier = Modifier.testTag("bluetooth_print_button")
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedPrinter != null) "Cetak BT (${selectedPrinter?.name?.take(8)})" else "Cetak BT",
                        fontSize = 12.sp
                    )
                }

                // Share as Image
                OutlinedButton(
                    onClick = { viewModel.shareReceiptAsImage(context) },
                    modifier = Modifier.testTag("share_receipt_image_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan Gambar", fontSize = 12.sp)
                }

                // OCR Calibration
                if (activeBitmap != null) {
                    OutlinedButton(
                        onClick = onNavigateToCalibration,
                        modifier = Modifier.testTag("open_calibration_button")
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kalibrasi OCR", fontSize = 12.sp)
                    }
                }

                // Save to History
                OutlinedButton(
                    onClick = {
                        viewModel.saveCurrentReceiptToHistory {
                            Toast.makeText(context, "Struk tersimpan di Riwayat", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_history_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan", fontSize = 12.sp)
                }
            }

            // Receipt Paper Container (Scrollable)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (template != null) {
                    ReceiptPaperView(
                        template = template!!,
                        columns = columns,
                        columnValues = columnValues,
                        onFieldClick = { column, currentValue ->
                            editingColumn = column
                            editingValue = currentValue
                        }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Tidak ada template aktif.")
                        }
                    }
                }
            }
        }
    }

    // In-place Popup Dialog for Direct Editing
    editingColumn?.let { col ->
        EditFieldDialog(
            column = col,
            currentValue = editingValue,
            onDismiss = { editingColumn = null },
            onSave = { updatedText ->
                viewModel.updateColumnValue(col.id, updatedText)
                editingColumn = null
            }
        )
    }
}
