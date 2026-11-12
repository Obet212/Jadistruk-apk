package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CharRule
import com.example.data.model.ColumnConfig
import com.example.data.model.ColumnFunction
import com.example.data.model.ColumnType
import com.example.data.model.Template
import com.example.data.model.TextAlignment
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCalibration: () -> Unit
) {
    val template by viewModel.activeTemplate.collectAsStateWithLifecycle()
    val columns by viewModel.activeColumns.collectAsStateWithLifecycle()
    val activeSourceApp by viewModel.activeSourceApp.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    var showEditTemplateDialog by remember { mutableStateOf(false) }
    var editingColumnConfig by remember { mutableStateOf<ColumnConfig?>(null) }
    var showAddColumnDialog by remember { mutableStateOf(false) }

    val sortedColumns = remember(columns) { columns.sortedBy { it.orderIndex } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pengaturan Template",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${activeSourceApp?.appName ?: "Umum"} - ${template?.templateName ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("template_editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditTemplateDialog = true },
                        modifier = Modifier.testTag("edit_template_header_footer_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Ubah Header / Footer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddColumnDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_column_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Kolom")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Template Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = template?.templateName ?: "Template",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Kertas ${template?.paperWidthMm ?: 58}mm",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Header: ${template?.headerTitle ?: ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!template?.headerSubtitle.isNullOrBlank()) {
                            Text(
                                text = "Sub-header: ${template?.headerSubtitle ?: ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Kolom Struk (${sortedColumns.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Manual (Kiri) | Auto (Kanan)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (sortedColumns.isEmpty()) {
                item {
                    Text("Belum ada kolom. Ketuk tombol '+' untuk menambah kolom.")
                }
            } else {
                items(sortedColumns) { col ->
                    ColumnConfigCard(
                        column = col,
                        onToggle = { viewModel.toggleColumnEnabled(col) },
                        onEdit = { editingColumnConfig = col },
                        onDelete = { viewModel.deleteColumnConfig(col) },
                        onMoveUp = { viewModel.moveColumnOrder(col, moveUp = true) },
                        onMoveDown = { viewModel.moveColumnOrder(col, moveUp = false) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal Edit Header & Footer Template
    if (showEditTemplateDialog && template != null) {
        var title by remember { mutableStateOf(template!!.headerTitle) }
        var subtitle by remember { mutableStateOf(template!!.headerSubtitle) }
        var footer by remember { mutableStateOf(template!!.footerNote) }
        var paperWidth by remember { mutableIntStateOf(template!!.paperWidthMm) }

        AlertDialog(
            onDismissRequest = { showEditTemplateDialog = false },
            title = { Text("Ubah Header & Footer Template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Judul Struk (Header)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("Sub-judul (Nama Loket/Agen)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = footer,
                        onValueChange = { footer = it },
                        label = { Text("Catatan Bawah (Footer)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Lebar Kertas Thermal:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = paperWidth == 58, onClick = { paperWidth = 58 })
                        Text("58 mm (Standar)", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = paperWidth == 80, onClick = { paperWidth = 80 })
                        Text("80 mm (Lebar)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateTemplateSettings(
                        template!!.copy(
                            headerTitle = title,
                            headerSubtitle = subtitle,
                            footerNote = footer,
                            paperWidthMm = paperWidth
                        )
                    )
                    showEditTemplateDialog = false
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditTemplateDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Modal Tambah / Edit Kolom
    if (showAddColumnDialog && template != null) {
        ColumnEditorDialog(
            initialColumn = null,
            templateId = template!!.id,
            nextOrder = sortedColumns.size + 1,
            onDismiss = { showAddColumnDialog = false },
            onSave = { newCol ->
                viewModel.addColumnToTemplate(template!!.id, newCol)
                showAddColumnDialog = false
            }
        )
    }

    editingColumnConfig?.let { col ->
        ColumnEditorDialog(
            initialColumn = col,
            templateId = col.templateId,
            nextOrder = col.orderIndex,
            onDismiss = { editingColumnConfig = null },
            onSave = { updatedCol ->
                viewModel.updateColumnConfig(updatedCol)
                editingColumnConfig = null
            }
        )
    }
}

@Composable
fun ColumnConfigCard(
    column: ColumnConfig,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("column_item_${column.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (column.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder up/down buttons
            Column {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Pindah Ke Atas", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Pindah Ke Bawah", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = column.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (column.isManual) Color(0xFFE0F2FE) else Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = if (column.isManual) "MANUAL (KIRI)" else "AUTO OCR (KANAN)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (column.isManual) Color(0xFF0369A1) else Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (column.isManual) {
                    Text(
                        text = "Default: ${if (column.manualDefaultText.isBlank()) "(Kosong)" else column.manualDefaultText}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Fungsi: ${column.columnFunction} | Aturan: ${column.charRule}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Kolom", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus Kolom", tint = MaterialTheme.colorScheme.error)
            }

            Switch(
                checked = column.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("toggle_column_${column.id}")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnEditorDialog(
    initialColumn: ColumnConfig?,
    templateId: Long,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onSave: (ColumnConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialColumn?.name ?: "") }
    var isManual by remember { mutableStateOf(initialColumn?.isManual ?: false) }
    var manualDefaultText by remember { mutableStateOf(initialColumn?.manualDefaultText ?: "") }
    var selectedFunction by remember {
        mutableStateOf(
            try { ColumnFunction.valueOf(initialColumn?.columnFunction ?: ColumnFunction.FREE_TEXT.name) }
            catch (_: Exception) { ColumnFunction.FREE_TEXT }
        )
    }
    var selectedCharRule by remember {
        mutableStateOf(
            try { CharRule.valueOf(initialColumn?.charRule ?: CharRule.FREE_CHARS.name) }
            catch (_: Exception) { CharRule.FREE_CHARS }
        )
    }
    var maxLen by remember { mutableIntStateOf(initialColumn?.maxCharLength ?: 0) }

    var functionExpanded by remember { mutableStateOf(false) }
    var charRuleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialColumn == null) "Tambah Kolom Baru" else "Edit Kolom '${initialColumn.name}'")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Kolom") },
                    placeholder = { Text("Contoh: Nomor Rekening, Tanggal, Nominal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("column_name_input")
                )

                Text("Tipe Kolom:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !isManual,
                        onClick = { isManual = false }
                    )
                    Text("Otomatis (OCR - Rata Kanan)", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isManual,
                        onClick = { isManual = true }
                    )
                    Text("Manual (Tetap - Rata Kiri)", fontSize = 13.sp)
                }

                if (isManual) {
                    OutlinedTextField(
                        value = manualDefaultText,
                        onValueChange = { manualDefaultText = it },
                        label = { Text("Isi Teks Manual Default") },
                        placeholder = { Text("Misal: Rp 2.500 atau SUKSES") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Function Dropdown
                    ExposedDropdownMenuBox(
                        expanded = functionExpanded,
                        onExpandedChange = { functionExpanded = !functionExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedFunction.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fungsi Kolom OCR") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = functionExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = functionExpanded,
                            onDismissRequest = { functionExpanded = false }
                        ) {
                            ColumnFunction.values().forEach { func ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(func.displayName, fontWeight = FontWeight.SemiBold)
                                            Text(func.description, fontSize = 10.sp, color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        selectedFunction = func
                                        functionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Char Rule Dropdown
                    ExposedDropdownMenuBox(
                        expanded = charRuleExpanded,
                        onExpandedChange = { charRuleExpanded = !charRuleExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCharRule.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Aturan Karakter") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = charRuleExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = charRuleExpanded,
                            onDismissRequest = { charRuleExpanded = false }
                        ) {
                            CharRule.values().forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.displayName) },
                                    onClick = {
                                        selectedCharRule = rule
                                        charRuleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Max Length
                    OutlinedTextField(
                        value = if (maxLen == 0) "" else maxLen.toString(),
                        onValueChange = { maxLen = it.toIntOrNull() ?: 0 },
                        label = { Text("Batas Panjang Karakter (0 = Tanpa batas)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val col = (initialColumn ?: ColumnConfig(
                            templateId = templateId,
                            name = name.trim(),
                            orderIndex = nextOrder
                        )).copy(
                            name = name.trim(),
                            columnType = if (isManual) ColumnType.MANUAL.name else ColumnType.AUTO.name,
                            manualDefaultText = manualDefaultText.trim(),
                            columnFunction = selectedFunction.name,
                            charRule = selectedCharRule.name,
                            maxCharLength = maxLen,
                            alignment = if (isManual) TextAlignment.LEFT.name else TextAlignment.RIGHT.name
                        )
                        onSave(col)
                    }
                },
                modifier = Modifier.testTag("confirm_column_button")
            ) {
                Text("Simpan Kolom")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
