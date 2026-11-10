package com.example.ui.screens.detail

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import com.example.data.model.ReceiptTheme
import com.example.data.ocr.FieldValidator
import com.example.data.printer.BluetoothPrinterDevice
import com.example.data.printer.PrintResult
import com.example.data.printer.PrinterConnectionState
import com.example.data.printer.ReceiptFormatter
import com.example.data.printer.ReceiptPdfAndPrintHelper
import com.example.ui.theme.ReceiptBorder
import com.example.ui.theme.ReceiptPaperBg
import com.example.ui.theme.ReceiptText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
  receipt: Receipt,
  settings: PrintSettings,
  onBack: () -> Unit,
  onPrintBluetooth: (Receipt, (PrintResult) -> Unit) -> Unit = { _, _ -> },
  onTestPrint: ((PrintResult) -> Unit) -> Unit = {},
  onSaveSettings: (PrintSettings) -> Unit = {},
  getPairedPrinters: () -> List<BluetoothPrinterDevice> = { emptyList() },
  printerConnectionState: StateFlow<PrinterConnectionState>? = null,
  isBluetoothSupported: () -> Boolean = { true },
  isBluetoothEnabled: () -> Boolean = { true },
  hasBluetoothPermission: () -> Boolean = { true },
  onUpdateReceipt: (Receipt) -> Unit = {},
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()
  var previewPaperWidth by remember { mutableIntStateOf(settings.paperWidthMm) }
  var selectedTheme by remember(settings.receiptTheme) { mutableStateOf(settings.receiptTheme) }

  // Printing state
  var isPrinting by remember { mutableStateOf(false) }
  var isTestingPrint by remember { mutableStateOf(false) }
  var printErrorMessage by remember { mutableStateOf<String?>(null) }
  var showPrinterPickerSheet by remember { mutableStateOf(false) }
  var showPermissionDialog by remember { mutableStateOf(false) }
  var showBluetoothDisabledDialog by remember { mutableStateOf(false) }
  var pairedPrintersList by remember { mutableStateOf<List<BluetoothPrinterDevice>>(emptyList()) }
  var showEditReceiptDialog by remember { mutableStateOf(false) }
  var editRecipientName by remember(receipt.recipientName) { mutableStateOf(receipt.recipientName) }
  var editRecipientBank by remember(receipt.recipientBank) { mutableStateOf(receipt.recipientBank) }
  var editRecipientAccount by remember(receipt.recipientAccount) { mutableStateOf(receipt.recipientAccount) }
  var editAmountStr by remember(receipt.amount) { mutableStateOf(receipt.amount.toString()) }
  var editAdminFeeStr by remember(receipt.adminFee) { mutableStateOf(receipt.adminFee.toString()) }
  var editDate by remember(receipt.transactionDate) { mutableStateOf(receipt.transactionDate) }
  var editTime by remember(receipt.transactionTime) { mutableStateOf(receipt.transactionTime) }
  var editRef by remember(receipt.refNumber) { mutableStateOf(receipt.refNumber) }
  var editNotes by remember(receipt.notes) { mutableStateOf(receipt.notes) }

  val activeConnectionState = printerConnectionState?.collectAsState()?.value
    ?: PrinterConnectionState.Unselected

  val validationReport = remember(receipt) {
    FieldValidator.validateReceipt(receipt)
  }

  BackHandler {
    onBack()
  }

  val activeSettings = settings.copy(
    paperWidthMm = previewPaperWidth,
    receiptTheme = selectedTheme
  )
  val formattedReceiptText = remember(receipt, activeSettings) {
    ReceiptFormatter.formatText(receipt, activeSettings)
  }

  // Multi-permission launcher for modern & legacy Android
  val multiplePermissionsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissionsMap ->
    val allGranted = permissionsMap.values.all { it }
    if (allGranted) {
      if (!isBluetoothEnabled()) {
        showBluetoothDisabledDialog = true
      } else {
        val printers = getPairedPrinters()
        pairedPrintersList = printers
        if (printers.isEmpty() || settings.selectedPrinterAddress.isBlank()) {
          showPrinterPickerSheet = true
        } else {
          // Siap cetak langsung
          isPrinting = true
          onPrintBluetooth(receipt) { result ->
            isPrinting = false
            when (result) {
              is PrintResult.Success -> {
                Toast.makeText(context, "✓ Berhasil mencetak ke printer thermal!", Toast.LENGTH_SHORT).show()
              }
              is PrintResult.Error -> {
                printErrorMessage = result.message
              }
            }
          }
        }
      }
    } else {
      showPermissionDialog = true
    }
  }

  fun checkAndRequestPermissionsThen(onReady: () -> Unit) {
    if (!isBluetoothSupported()) {
      Toast.makeText(context, "Perangkat tidak memiliki Bluetooth. Gunakan cetak sistem / bagikan gambar.", Toast.LENGTH_LONG).show()
      return
    }

    val neededPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_SCAN
      )
    } else {
      listOf(
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN
      )
    }

    val missing = neededPermissions.filter {
      ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missing.isNotEmpty()) {
      multiplePermissionsLauncher.launch(missing.toTypedArray())
    } else if (!isBluetoothEnabled()) {
      showBluetoothDisabledDialog = true
    } else {
      onReady()
    }
  }

  fun executePrintFlow() {
    checkAndRequestPermissionsThen {
      val printers = getPairedPrinters()
      pairedPrintersList = printers

      if (settings.selectedPrinterAddress.isBlank() || printers.none { it.address.equals(settings.selectedPrinterAddress, ignoreCase = true) }) {
        showPrinterPickerSheet = true
        return@checkAndRequestPermissionsThen
      }

      onSaveSettings(activeSettings)
      isPrinting = true
      onPrintBluetooth(receipt) { result ->
        isPrinting = false
        when (result) {
          is PrintResult.Success -> {
            Toast.makeText(
              context,
              "✓ Struk berhasil dicetak ke ${settings.selectedPrinterName.ifBlank { "printer thermal" }}!",
              Toast.LENGTH_SHORT
            ).show()
          }
          is PrintResult.Error -> {
            printErrorMessage = result.message
          }
        }
      }
    }
  }

  fun executeTestPrintFlow() {
    checkAndRequestPermissionsThen {
      val printers = getPairedPrinters()
      pairedPrintersList = printers

      if (settings.selectedPrinterAddress.isBlank() || printers.none { it.address.equals(settings.selectedPrinterAddress, ignoreCase = true) }) {
        showPrinterPickerSheet = true
        return@checkAndRequestPermissionsThen
      }

      isTestingPrint = true
      onTestPrint { result ->
        isTestingPrint = false
        when (result) {
          is PrintResult.Success -> {
            Toast.makeText(context, "✓ Tes cetak berhasil! Printer siap digunakan.", Toast.LENGTH_LONG).show()
          }
          is PrintResult.Error -> {
            printErrorMessage = "Tes Cetak Gagal: ${result.message}"
          }
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Pratinjau Struk Cetak",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Struk #${receipt.id} • ${if (receipt.recipientBank.isNotBlank()) receipt.recipientBank else "Transfer"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
          }
        },
        actions = {
          IconButton(
            onClick = {
              ReceiptPdfAndPrintHelper.exportAndShareJpg(context, receipt, activeSettings)
            }
          ) {
            Icon(Icons.Filled.Share, contentDescription = "Bagikan Gambar")
          }
          IconButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(formattedReceiptText))
              Toast.makeText(context, "Format struk disalin ke papan klip", Toast.LENGTH_SHORT).show()
            }
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Struk")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Transaction Safety Banner (Keamanan Transaksi: Data tersimpan aman)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "✓ Transaksi Telah Tersimpan di Database",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Cetak ulang tidak akan menggandakan transaksi atau mengubah nomor struk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // 2. PENGATURAN PILIHAN TEMPLATE STRUK (3 TEMA)
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_theme_selector_card"),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Palette,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Pilihan Template Struk",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Pilih tema tampilan & format sebelum mencetak",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
              ) {
                Text(
                  text = selectedTheme.displayName,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Tombol Pilihan Tema (1. Standard, 2. Minimalis, 3. Modern)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              ReceiptTheme.entries.forEach { theme ->
                val isSelected = selectedTheme == theme
                Surface(
                  modifier = Modifier
                    .weight(1f)
                    .clickable {
                      selectedTheme = theme
                      val updated = activeSettings.copy(receiptTheme = theme)
                      onSaveSettings(updated)
                    }
                    .testTag("theme_chip_${theme.id}"),
                  shape = RoundedCornerShape(20.dp),
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.surface
                  },
                  border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.outlineVariant
                    }
                  ),
                  shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 10.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = theme.displayName,
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                      } else {
                        MaterialTheme.colorScheme.onSurface
                      },
                      maxLines = 1,
                      softWrap = false
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Keterangan Tema Terpilih
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "${selectedTheme.tagLine}: ${selectedTheme.description}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pengaturan Lebar Kertas
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Lebar Kertas Thermal:",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                maxLines = 1
              )
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                FilterChip(
                  selected = previewPaperWidth == 58,
                  onClick = {
                    previewPaperWidth = 58
                    onSaveSettings(activeSettings.copy(paperWidthMm = 58))
                  },
                  label = { Text("58 mm (32 kolom)", softWrap = false, maxLines = 1) }
                )
                FilterChip(
                  selected = previewPaperWidth == 88 || previewPaperWidth == 80,
                  onClick = {
                    previewPaperWidth = 88
                    onSaveSettings(activeSettings.copy(paperWidthMm = 88))
                  },
                  label = { Text("88 mm (48 kolom)", softWrap = false, maxLines = 1) }
                )
              }
            }
          }
        }
      }

      // 3. Validation Notice Banner if any field is invalid
      if (!validationReport.isValidForPrint) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Perhatian Data Struk:",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                validationReport.errors.values.forEach { err ->
                  Text(
                    text = "• $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                  )
                }
              }
            }
          }
        }
      }

      // 4. Simulated Thermal Paper Card dengan Header Pratinjau
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState()),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Pratinjau Hasil Cetak",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                maxLines = 1
              )
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
              ) {
                Text(
                  text = "${selectedTheme.displayName} • ${if (previewPaperWidth == 88 || previewPaperWidth == 80) "88mm" else "58mm"}",
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  softWrap = false,
                  maxLines = 1
                )
              }
            }

            IconButton(
              onClick = {
                clipboardManager.setText(AnnotatedString(formattedReceiptText))
                Toast.makeText(context, "Teks struk berhasil disalin!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .size(40.dp)
                .testTag("btn_copy_receipt")
            ) {
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Salin Struk",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }
          }

          // Banner instruksi ketuk untuk edit langsung
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp)
              .clickable { showEditReceiptDialog = true }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Ketuk pratinjau untuk edit langsung",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSecondaryContainer
              )
            }
          }

          // Tampilan Kertas Thermal 100% Identik Hasil Cetak Asli (dapat diketuk untuk edit)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showEditReceiptDialog = true }
          ) {
            ThermalReceiptPreview(
              receipt = receipt,
              settings = activeSettings,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("thermal_paper_card")
            )
          }
        }
      }

      // 5. Panel Pengelola Printer Bluetooth & Status Koneksi
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (settings.selectedPrinterAddress.isNotBlank()) {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
          ),
          border = BorderStroke(
            1.dp,
            if (settings.selectedPrinterAddress.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // Header baris status
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .background(
                      if (settings.selectedPrinterAddress.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                      CircleShape
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = if (settings.selectedPrinterAddress.isNotBlank()) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (settings.selectedPrinterAddress.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = if (settings.selectedPrinterName.isNotBlank()) settings.selectedPrinterName else "Belum Memilih Printer",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                  )
                  Text(
                    text = if (settings.selectedPrinterAddress.isNotBlank()) settings.selectedPrinterAddress else "Ketuk 'Pilih' untuk menentukan printer default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                  )
                }
              }

              TextButton(
                onClick = {
                  checkAndRequestPermissionsThen {
                    pairedPrintersList = getPairedPrinters()
                    showPrinterPickerSheet = true
                  }
                }
              ) {
                Text(if (settings.selectedPrinterAddress.isNotBlank()) "Ganti" else "Pilih")
              }
            }

            // Indikator Status Operasi Real-Time
            if (activeConnectionState is PrinterConnectionState.Connecting ||
                activeConnectionState is PrinterConnectionState.Printing ||
                activeConnectionState is PrinterConnectionState.Error ||
                activeConnectionState is PrinterConnectionState.Success) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (activeConnectionState) {
                  is PrinterConnectionState.Connecting, is PrinterConnectionState.Printing -> MaterialTheme.colorScheme.primaryContainer
                  is PrinterConnectionState.Success -> MaterialTheme.colorScheme.secondaryContainer
                  is PrinterConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                  else -> MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (activeConnectionState is PrinterConnectionState.Connecting || activeConnectionState is PrinterConnectionState.Printing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                  }
                  Text(
                    text = when (activeConnectionState) {
                      is PrinterConnectionState.Connecting -> "Menghubungkan ke ${activeConnectionState.deviceName} (timeout 8s)..."
                      is PrinterConnectionState.Printing -> "Mengirim instruksi ESC/POS ke ${activeConnectionState.deviceName}..."
                      is PrinterConnectionState.Success -> "✓ ${activeConnectionState.message}"
                      is PrinterConnectionState.Error -> "Gagal: ${activeConnectionState.message}"
                      else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tombol Cetak Utama & Tes Cetak
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { executePrintFlow() },
                enabled = !isPrinting && !isTestingPrint,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("btn_print_bluetooth"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
              ) {
                if (isPrinting) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Mencetak...", fontSize = 13.sp)
                } else {
                  Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Cetak Struk", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
              }

              OutlinedButton(
                onClick = { executeTestPrintFlow() },
                enabled = !isPrinting && !isTestingPrint && settings.selectedPrinterAddress.isNotBlank(),
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(10.dp)
              ) {
                if (isTestingPrint) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Text("Tes Cetak", fontSize = 13.sp)
                }
              }
            }
          }
        }
      }

      // 6. Panel Alternatif Cetak (Sistem Android Print Framework & Ekspor PDF)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "Alternatif Cetak & Berbagi Struk:",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Gunakan opsi ini jika Bluetooth sedang tidak tersedia, printer belum siap, atau ingin mencetak lewat driver printer Android / PDF.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Tombol Cetak via Dialog Sistem Android
              OutlinedButton(
                onClick = {
                  ReceiptPdfAndPrintHelper.printViaSystemDialog(context, receipt, activeSettings)
                },
                modifier = Modifier
                  .weight(1f)
                  .height(46.dp)
                  .testTag("btn_system_print_dialog"),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print Sistem", fontSize = 12.sp, maxLines = 1)
              }

              // Tombol Bagikan Gambar JPG
              Button(
                onClick = {
                  ReceiptPdfAndPrintHelper.exportAndShareJpg(context, receipt, activeSettings)
                },
                modifier = Modifier
                  .weight(1f)
                  .height(46.dp)
                  .testTag("btn_share_image"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagikan Gambar", fontSize = 12.sp, maxLines = 1)
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tombol Salin Teks Format Thermal
            OutlinedButton(
              onClick = {
                clipboardManager.setText(AnnotatedString(formattedReceiptText))
                Toast.makeText(context, "Teks format struk berhasil disalin!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Salin Teks Format Thermal Monospace", fontSize = 12.sp)
            }
          }
        }
      }

      // 7. Catatan Teknis & Bantuan
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
              Icons.Filled.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Perintah ESC/POS menggunakan port Bluetooth RFCOMM SPP standar dengan deteksi timeout 8 detik dan fallback refleksi otomatis, kompatibel dengan mayoritas printer kasir 58mm & 80mm.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 16.sp
            )
          }
        }
      }
    }
  }

  // BOTTOM SHEET: PILIH PRINTER BLUETOOTH DENGAN DIAGNOSIS
  if (showPrinterPickerSheet) {
    ModalBottomSheet(
      onDismissRequest = { showPrinterPickerSheet = false },
      sheetState = rememberModalBottomSheetState()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Pilih Printer Thermal Bluetooth",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Daftar perangkat printer yang sudah berpasangan (paired):",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(
            onClick = {
              pairedPrintersList = getPairedPrinters()
              Toast.makeText(context, "Daftar printer disegarkan", Toast.LENGTH_SHORT).show()
            }
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Segarkan")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (pairedPrintersList.isEmpty()) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Tidak Ada Printer Paired Terdeteksi",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Pastikan printer menyala, Bluetooth HP aktif, dan printer sudah dipasangkan di Pengaturan Bluetooth HP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = {
                    try {
                      context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: Exception) {
                      Toast.makeText(context, "Buka menu Bluetooth HP Anda secara manual", Toast.LENGTH_SHORT).show()
                    }
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Buka Bluetooth HP")
                }

                OutlinedButton(
                  onClick = {
                    pairedPrintersList = getPairedPrinters()
                  },
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Text("Coba Lagi")
                }
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(pairedPrintersList) { device ->
              val isSelected = device.address.equals(settings.selectedPrinterAddress, ignoreCase = true)
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    onSaveSettings(
                      settings.copy(
                        selectedPrinterAddress = device.address,
                        selectedPrinterName = device.name
                      )
                    )
                    showPrinterPickerSheet = false
                    Toast.makeText(context, "Printer terpilih: ${device.name}", Toast.LENGTH_SHORT).show()
                  },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  }
                )
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .background(
                          if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                          CircleShape
                        ),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = "Terpilih",
                      tint = MaterialTheme.colorScheme.primary
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

  // DIALOG PERIZINAN BLUETOOTH DITOLAK
  if (showPermissionDialog) {
    AlertDialog(
      onDismissRequest = { showPermissionDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(32.dp)
        )
      },
      title = { Text("Izin Bluetooth Diperlukan") },
      text = {
        Text(
          "Untuk dapat mendeteksi dan mengirim data ke printer thermal kasir, Android membutuhkan izin koneksi Bluetooth. Jika tidak ingin menggunakan Bluetooth, Anda tetap dapat mencetak struk lewat menu 'Print Sistem' atau 'Bagikan Gambar'.",
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showPermissionDialog = false
            try {
              val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
              context.startActivity(intent)
            } catch (e: Exception) {
              Toast.makeText(context, "Buka Pengaturan Aplikasi di HP Anda", Toast.LENGTH_SHORT).show()
            }
          }
        ) {
          Text("Buka Pengaturan Izin")
        }
      },
      dismissButton = {
        TextButton(onClick = { showPermissionDialog = false }) {
          Text("Gunakan Cetak PDF")
        }
      }
    )
  }

  // DIALOG BLUETOOTH NONAKTIF
  if (showBluetoothDisabledDialog) {
    AlertDialog(
      onDismissRequest = { showBluetoothDisabledDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Filled.BluetoothDisabled,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = { Text("Bluetooth Sedang Nonaktif") },
      text = {
        Text(
          "Bluetooth pada HP Anda sedang dimatikan. Silakan aktifkan Bluetooth agar dapat terhubung ke printer thermal.",
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showBluetoothDisabledDialog = false
            try {
              context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: Exception) {
              try {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
              } catch (_: Exception) {}
            }
          }
        ) {
          Text("Aktifkan Bluetooth")
        }
      },
      dismissButton = {
        TextButton(onClick = { showBluetoothDisabledDialog = false }) {
          Text("Tutup")
        }
      }
    )
  }

  // DIALOG ERROR CETAK DENGAN RECOVERY ACTION
  printErrorMessage?.let { errMsg ->
    AlertDialog(
      onDismissRequest = { printErrorMessage = null },
      icon = {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(32.dp)
        )
      },
      title = { Text("Gagal Terhubung ke Printer") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(errMsg, style = MaterialTheme.typography.bodyMedium)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Solusi:\n1. Pastikan printer thermal menyala dan lampu indikator aktif.\n2. Coba matikan dan hidupkan kembali printer thermal.\n3. Periksa apakah printer sedang dipakai oleh aplikasi lain.\n4. Atau gunakan opsi alternatif 'Print Sistem / PDF'.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            printErrorMessage = null
            executePrintFlow()
          }
        ) {
          Text("Coba Lagi")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            printErrorMessage = null
            ReceiptPdfAndPrintHelper.printViaSystemDialog(context, receipt, activeSettings)
          }
        ) {
          Text("Cetak via Sistem / PDF")
        }
      }
    )
  }

  // POPUP EDIT LANGSUNG PREVIEW STRUK (KOLOM MANUAL & OTOMATIS)
  if (showEditReceiptDialog) {
    AlertDialog(
      onDismissRequest = { showEditReceiptDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text("Edit Data Struk", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
          item {
            OutlinedTextField(
              value = editRecipientName,
              onValueChange = { editRecipientName = it },
              label = { Text("Nama Penerima (Kolom Otomatis)") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editRecipientBank,
              onValueChange = { editRecipientBank = it },
              label = { Text("Bank / Dompet Digital") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editRecipientAccount,
              onValueChange = { editRecipientAccount = it },
              label = { Text("Nomor Rekening / HP (Pertahankan 0)") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editAmountStr,
              onValueChange = { editAmountStr = it.filter { ch -> ch.isDigit() } },
              label = { Text("Nominal Transfer (Rp)") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editAdminFeeStr,
              onValueChange = { editAdminFeeStr = it.filter { ch -> ch.isDigit() } },
              label = { Text("Biaya Admin (Manual)") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editDate,
              onValueChange = { editDate = it },
              label = { Text("Tanggal Transaksi") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editTime,
              onValueChange = { editTime = it },
              label = { Text("Waktu Transaksi") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editRef,
              onValueChange = { editRef = it },
              label = { Text("No. Referensi / Transaksi") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              singleLine = true
            )
            OutlinedTextField(
              value = editNotes,
              onValueChange = { editNotes = it },
              label = { Text("Keterangan / Catatan") },
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val amountNum = editAmountStr.toLongOrNull() ?: receipt.amount
            val adminNum = editAdminFeeStr.toLongOrNull() ?: receipt.adminFee
            val updated = receipt.copy(
              recipientName = editRecipientName.trim(),
              recipientBank = editRecipientBank.trim(),
              recipientAccount = editRecipientAccount.trim(),
              amount = amountNum,
              adminFee = adminNum,
              transactionDate = editDate.trim(),
              transactionTime = editTime.trim(),
              refNumber = editRef.trim(),
              notes = editNotes.trim()
            )
            onUpdateReceipt(updated)
            showEditReceiptDialog = false
            Toast.makeText(context, "Data struk berhasil diperbarui!", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Simpan")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditReceiptDialog = false }) {
          Text("Batal")
        }
      }
    )
  }
}
