package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import com.example.data.printer.BluetoothPrinterDevice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.rememberCoroutineScope
import com.example.BuildConfig
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupPackage
import com.example.data.model.AdminFeeRule
import com.example.data.model.AppTheme
import com.example.data.model.PrintSettings
import com.example.data.model.ReceiptTheme
import com.example.data.remote.AppUpdateStatus
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import com.example.data.backup.CloudSyncStatus
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  currentSettings: PrintSettings,
  onSaveSettings: (PrintSettings) -> Unit,
  getPairedPrinters: () -> List<BluetoothPrinterDevice> = { emptyList() },
  onTestPrint: ((com.example.data.printer.PrintResult) -> Unit) -> Unit = {},
  appUpdateStatus: AppUpdateStatus = AppUpdateStatus.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_NAME),
  onCheckForUpdate: () -> Unit = {},
  backupManager: BackupManager? = null,
  deviceId: String = "",
  syncStatus: CloudSyncStatus = CloudSyncStatus.Idle,
  onTriggerAutoBackup: () -> Unit = {},
  onRegenerateDeviceId: () -> Unit = {},
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // Backup & Restore states
  var isBackingUp by remember { mutableStateOf(false) }
  var createdBackupPackage by remember { mutableStateOf<BackupPackage?>(null) }
  var createdBackupFile by remember { mutableStateOf<File?>(null) }
  var showBackupSuccessDialog by remember { mutableStateOf(false) }
  var isRestoring by remember { mutableStateOf(false) }
  var pendingRestorePackage by remember { mutableStateOf<BackupPackage?>(null) }
  var restoreErrorMessage by remember { mutableStateOf<String?>(null) }
  var restoreSuccessMessage by remember { mutableStateOf<String?>(null) }
  var showRegenerateDialog by remember { mutableStateOf(false) }

  val saveDocLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/octet-stream")
  ) { destinationUri ->
    if (destinationUri != null && createdBackupPackage != null) {
      coroutineScope.launch {
        try {
          backupManager?.writeBackupToDestination(destinationUri, createdBackupPackage!!.rawJson)
          Toast.makeText(context, "✓ File backup berhasil disimpan ke penyimpanan HP!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Gagal menyimpan file: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  val openDocLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { sourceUri ->
    if (sourceUri != null && backupManager != null) {
      coroutineScope.launch {
        isRestoring = true
        val result = backupManager.validateAndParseBackup(sourceUri)
        isRestoring = false
        result.onSuccess { pkg ->
          pendingRestorePackage = pkg
        }.onFailure { err ->
          restoreErrorMessage = err.message ?: "Format file tidak valid atau rusak."
        }
      }
    }
  }

  var storeName by remember(currentSettings) { mutableStateOf(currentSettings.storeName) }
  var storeAddress by remember(currentSettings) { mutableStateOf(currentSettings.storeAddress) }
  var storePhone by remember(currentSettings) { mutableStateOf(currentSettings.storePhone) }
  var footerMessage by remember(currentSettings) { mutableStateOf(currentSettings.footerMessage) }
  var paperWidthMm by remember(currentSettings) { mutableIntStateOf(currentSettings.paperWidthMm) }
  var showDate by remember(currentSettings) { mutableStateOf(currentSettings.showDate) }
  var showRefNumber by remember(currentSettings) { mutableStateOf(currentSettings.showRefNumber) }
  var autoAddHeader by remember(currentSettings) { mutableStateOf(currentSettings.autoAddHeader) }
  var receiptTheme by remember(currentSettings) { mutableStateOf(currentSettings.receiptTheme) }
  var appTheme by remember(currentSettings) { mutableStateOf(currentSettings.appTheme) }

  // Admin Fee Tier / Range Rules states
  var autoApplyAdminFee by remember(currentSettings) { mutableStateOf(currentSettings.autoApplyAdminFee) }
  var adminFeeRules by remember(currentSettings) { mutableStateOf(currentSettings.adminFeeRules) }

  // Bluetooth Thermal Printer states
  var selectedPrinterAddress by remember(currentSettings) { mutableStateOf(currentSettings.selectedPrinterAddress) }
  var selectedPrinterName by remember(currentSettings) { mutableStateOf(currentSettings.selectedPrinterName) }
  var autoCutPaper by remember(currentSettings) { mutableStateOf(currentSettings.autoCutPaper) }
  var printFeedLines by remember(currentSettings) { mutableIntStateOf(currentSettings.printFeedLines) }
  var showPrinterPicker by remember { mutableStateOf(false) }

  // Dialog states for Add / Edit Rule
  var showAddDialog by remember { mutableStateOf(false) }
  var ruleToEdit by remember { mutableStateOf<AdminFeeRule?>(null) }
  var ruleToDelete by remember { mutableStateOf<AdminFeeRule?>(null) }

  // Theme dialog state
  var showThemeDialog by remember { mutableStateOf(false) }
  var tempSelectedTheme by remember(showThemeDialog) { mutableStateOf(appTheme) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Pengaturan ESA STRUK",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Kustomisasi cetak struk & aturan biaya admin otomatis",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. PENGATURAN BIAYA ADMIN OTOMATIS (RENTANG NOMINAL)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
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
                  .size(38.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Payments,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Aturan Biaya Admin Otomatis",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Admin terisi otomatis berdasarkan rentang nominal transfer",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Toggle Aktifkan Hitung Admin Otomatis
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                  RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Hitung Admin Otomatis",
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                  text = if (autoApplyAdminFee) "Aktif (sesuai aturan rentang di bawah)" else "Nonaktif (diisi manual)",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (autoApplyAdminFee) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = autoApplyAdminFee,
                onCheckedChange = { autoApplyAdminFee = it }
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Daftar Aturan Rentang Nominal:",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (adminFeeRules.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                  .padding(14.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Belum ada aturan admin. Tambahkan aturan baru di bawah.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                adminFeeRules.forEachIndexed { index, rule ->
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Row(
                          modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Text(
                            text = "Aturan ${index + 1}: ${rule.formattedRange}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = false
                          )
                        }
                        Row(
                          modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Text(
                            text = "Biaya Admin: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false
                          )
                          Text(
                            text = rule.formattedFee,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            softWrap = false
                          )
                        }
                      }

                      Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tombol Ubah (Edit)
                        IconButton(
                          onClick = { ruleToEdit = rule },
                          modifier = Modifier.size(36.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Ubah Aturan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                          )
                        }

                        // Tombol Hapus (Delete)
                        IconButton(
                          onClick = { ruleToDelete = rule },
                          modifier = Modifier.size(36.dp)
                        ) {
                          Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Hapus Aturan",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Tambah Aturan & Kembalikan Default
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
              ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah Aturan", style = MaterialTheme.typography.labelMedium)
              }

              OutlinedButton(
                onClick = {
                  adminFeeRules = AdminFeeRule.defaultRules()
                  Toast.makeText(context, "Aturan admin dikembalikan ke default (10rb-400rb: 3rb, 401rb-600rb: 4rb)", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
              ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Default", style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }

      // 2. Store Header Info
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Identitas Usaha / Header Struk",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = storeName,
              onValueChange = { storeName = it },
              label = { Text("Nama Toko / Loket / Usaha") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_store_name"),
              shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = storeAddress,
              onValueChange = { storeAddress = it },
              label = { Text("Alamat / Keterangan Toko") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_store_address"),
              shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = storePhone,
              onValueChange = { storePhone = it },
              label = { Text("Nomor Kontak / WhatsApp") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_store_phone"),
              shape = RoundedCornerShape(10.dp)
            )
          }
        }
      }

      // 3. Paper Size and Toggles
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Format Kertas & Komponen",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "Pilihan Template Struk:",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              ReceiptTheme.entries.forEach { theme ->
                val isSelected = receiptTheme == theme
                Card(
                  modifier = Modifier
                    .weight(1f)
                    .clickable { receiptTheme = theme }
                    .testTag("settings_theme_card_${theme.id}"),
                  shape = RoundedCornerShape(10.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                  ),
                  border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                  )
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = theme.displayName,
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = when (theme) {
                        ReceiptTheme.PROFESIONAL -> "Standard"
                        ReceiptTheme.SEDERHANA -> "Minimalis"
                        ReceiptTheme.MODERN -> "Modern"
                      },
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "• ${receiptTheme.description}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
              text = "Ukuran Kertas Standar:",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              softWrap = false,
              maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              FilterChip(
                selected = paperWidthMm == 58,
                onClick = { paperWidthMm = 58 },
                label = { Text("58 mm (32 kolom)", softWrap = false, maxLines = 1) }
              )
              FilterChip(
                selected = paperWidthMm == 88 || paperWidthMm == 80,
                onClick = { paperWidthMm = 88 },
                label = { Text("88 mm (48 kolom)", softWrap = false, maxLines = 1) }
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Sertakan Header Toko", style = MaterialTheme.typography.bodyMedium)
              Switch(
                checked = autoAddHeader,
                onCheckedChange = { autoAddHeader = it }
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Tampilkan Waktu Transaksi", style = MaterialTheme.typography.bodyMedium)
              Switch(
                checked = showDate,
                onCheckedChange = { showDate = it }
              )
            }
          }
        }
      }

      // 4. Footer Message
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Pesan Bagian Bawah (Footer)",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = footerMessage,
              onValueChange = { footerMessage = it },
              label = { Text("Ucapan atau Catatan Kaki Struk") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("input_footer_message"),
              maxLines = 3,
              shape = RoundedCornerShape(10.dp)
            )
          }
        }
      }

      // 5. TEMA TAMPILAN APLIKASI (UI THEME)
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              tempSelectedTheme = appTheme
              showThemeDialog = true
            }
            .testTag("btn_theme_setting"),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Tema Tampilan Aplikasi",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Tema aktif: ${appTheme.displayName} • Ketuk untuk ubah",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            // Pratinjau 3 warna tema aktif saat ini
            Row(
              horizontalArrangement = Arrangement.spacedBy((-6).dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .background(appTheme.primaryColor, CircleShape)
                  .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
              )
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .background(appTheme.accentColor, CircleShape)
                  .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
              )
              Box(
                modifier = Modifier
                  .size(22.dp)
                  .background(appTheme.backgroundColor, CircleShape)
                  .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
              )
            }
          }
        }
      }

      // 6. Konfigurasi Printer Thermal Bluetooth
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Print,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Printer Thermal Bluetooth",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Pengaturan koneksi printer ESC/POS paired",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Printer Terpilih
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (selectedPrinterName.isNotBlank()) selectedPrinterName else "Belum memilih printer",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                  )
                  Text(
                    text = if (selectedPrinterAddress.isNotBlank()) selectedPrinterAddress else "Ketuk 'Pilih' untuk memilih printer paired",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                TextButton(
                  onClick = { showPrinterPicker = true }
                ) {
                  Text(if (selectedPrinterAddress.isNotBlank()) "Ganti" else "Pilih")
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Potong Kertas Otomatis
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Potong Kertas Otomatis (Auto Cut)", style = MaterialTheme.typography.bodyMedium)
              Switch(
                checked = autoCutPaper,
                onCheckedChange = { autoCutPaper = it }
              )
            }

            // Jumlah Baris Feed
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Jarak Baris Kertas Setelah Cetak (Feed Lines):",
              style = MaterialTheme.typography.labelMedium,
              softWrap = false,
              maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(1, 2, 3, 4).forEach { lines ->
                FilterChip(
                  selected = printFeedLines == lines,
                  onClick = { printFeedLines = lines },
                  label = { Text("$lines Baris", softWrap = false, maxLines = 1) }
                )
              }
            }

            if (selectedPrinterAddress.isNotBlank()) {
              Spacer(modifier = Modifier.height(14.dp))
              OutlinedButton(
                onClick = {
                  onTestPrint { result ->
                    when (result) {
                      is com.example.data.printer.PrintResult.Success -> {
                        Toast.makeText(context, "✓ Tes cetak berhasil!", Toast.LENGTH_SHORT).show()
                      }
                      is com.example.data.printer.PrintResult.Error -> {
                        Toast.makeText(context, "Tes cetak gagal: ${result.message}", Toast.LENGTH_LONG).show()
                      }
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tes Cetak Printer Terpilih")
              }
            }
          }
        }
      }

      // 7. Backup & Pemulihan Data (.obetstruk)
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("card_backup_restore"),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.SettingsBackupRestore,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Backup & Pemulihan Data",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Ekspor & impor seluruh konfigurasi (.obetstruk)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "Simpan seluruh template struk (termasuk gambar screenshot), identitas toko, tema, dan preferensi printer ke dalam satu file .obetstruk untuk dicadangkan atau dipulihkan ke HP baru.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Tombol SIMPAN BACKUP
              Button(
                onClick = {
                  if (backupManager != null) {
                    coroutineScope.launch {
                      isBackingUp = true
                      try {
                        val pkg = backupManager.createBackupPayload()
                        val cacheFile = backupManager.createCacheBackupFile(pkg.rawJson)
                        createdBackupPackage = pkg
                        createdBackupFile = cacheFile
                        showBackupSuccessDialog = true
                      } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membuat backup: ${e.message}", Toast.LENGTH_LONG).show()
                      } finally {
                        isBackingUp = false
                      }
                    }
                  } else {
                    Toast.makeText(context, "Layanan backup belum siap.", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("btn_simpan_backup"),
                shape = RoundedCornerShape(10.dp),
                enabled = !isBackingUp && !isRestoring
              ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  if (isBackingUp) "Memproses..." else "SIMPAN BACKUP",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
              }

              // Tombol PULIHKAN BACKUP
              OutlinedButton(
                onClick = {
                  if (backupManager != null) {
                    try {
                      openDocLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                    } catch (e: Exception) {
                      Toast.makeText(context, "Gagal membuka pengelola file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                  } else {
                    Toast.makeText(context, "Layanan pemulihan belum siap.", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("btn_pulihkan_backup"),
                shape = RoundedCornerShape(10.dp),
                enabled = !isBackingUp && !isRestoring
              ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  if (isRestoring) "Membaca..." else "PULIHKAN BACKUP",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
              }
            }
          }
        }
      }

      // 8. Keamanan, Identitas Perangkat & Sinkronisasi Otomatis
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("card_security_and_device_identity"),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.Security,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Keamanan & Identitas Perangkat",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Perlindungan layar & pencadangan otomatis (v2.2)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-item 1: Perlindungan Layar (FLAG_SECURE)
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Perlindungan Layar Aktif (FLAG_SECURE)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Screenshot dan rekaman layar dicegah demi melindungi data transaksi sensitif & privasi pelanggan.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-item 2: Identitas Perangkat (Device ID Keystore)
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = "Device ID Unik (Android Keystore)",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "ID perangkat dibuat secara lokal menggunakan hardware Keystore. Digunakan untuk identifikasi data & pemulihan otomatis tanpa nomor IMEI.",
                  style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = if (deviceId.isNotBlank()) deviceId else "Belum dibuat",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                  )
                  IconButton(
                    onClick = {
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      val clip = ClipData.newPlainText("Device ID", deviceId)
                      clipboard.setPrimaryClip(clip)
                      Toast.makeText(context, "Device ID disalin!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ContentCopy,
                      contentDescription = "Salin Device ID",
                      modifier = Modifier.size(16.dp),
                      tint = MaterialTheme.colorScheme.primary
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End
                ) {
                  TextButton(
                    onClick = { showRegenerateDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Regenerasi ID", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-item 3: Sinkronisasi & Auto-Backup
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Pencadangan & Pemulihan Otomatis",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    val statusText = when (syncStatus) {
                      is CloudSyncStatus.Syncing -> "Sedang menyinkronkan data..."
                      is CloudSyncStatus.Success -> (syncStatus as CloudSyncStatus.Success).message
                      is CloudSyncStatus.Error -> (syncStatus as CloudSyncStatus.Error).errorMessage
                      is CloudSyncStatus.Idle -> "Siap. Otomatis mencadangkan saat data diubah dan saat aplikasi diminimize."
                    }
                    Text(
                      text = statusText,
                      style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                      color = if (syncStatus is CloudSyncStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                OutlinedButton(
                  onClick = {
                    onTriggerAutoBackup()
                    Toast.makeText(context, "Memulai pencadangan otomatis...", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(8.dp),
                  enabled = syncStatus !is CloudSyncStatus.Syncing
                ) {
                  Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    if (syncStatus is CloudSyncStatus.Syncing) "Menyinkronkan..." else "Cadangkan Otomatis Sekarang",
                    style = MaterialTheme.typography.labelMedium
                  )
                }
              }
            }
          }
        }
      }

      // 9. Save Button
      item {
        Button(
          onClick = {
            onSaveSettings(
              PrintSettings(
                storeName = storeName.trim(),
                storeAddress = storeAddress.trim(),
                storePhone = storePhone.trim(),
                footerMessage = footerMessage.trim(),
                paperWidthMm = paperWidthMm,
                showDate = showDate,
                showRefNumber = showRefNumber,
                autoAddHeader = autoAddHeader,
                autoApplyAdminFee = autoApplyAdminFee,
                adminFeeRules = adminFeeRules,
                selectedPrinterAddress = selectedPrinterAddress,
                selectedPrinterName = selectedPrinterName,
                autoCutPaper = autoCutPaper,
                printFeedLines = printFeedLines,
                receiptTheme = receiptTheme,
                appTheme = appTheme,
              )
            )
            Toast.makeText(context, "Pengaturan struk & printer berhasil disimpan!", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("btn_save_settings"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Filled.Save, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Simpan Pengaturan", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
      }
    }
  }

  // DIALOG TAMBAH ATURAN ADMIN BARU
  if (showAddDialog) {
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Tambah Aturan Biaya Admin") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Tentukan rentang nominal transfer dan biaya admin yang otomatis diterapkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          OutlinedTextField(
            value = minText,
            onValueChange = { minText = it.filter { c -> c.isDigit() } },
            label = { Text("Nominal Minimal (Rp)") },
            placeholder = { Text("Misal: 10000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = maxText,
            onValueChange = { maxText = it.filter { c -> c.isDigit() } },
            label = { Text("Nominal Maksimal (Rp)") },
            placeholder = { Text("Misal: 400000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = feeText,
            onValueChange = { feeText = it.filter { c -> c.isDigit() } },
            label = { Text("Biaya Admin (Rp)") },
            placeholder = { Text("Misal: 3000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          if (errorMsg != null) {
            Text(
              text = errorMsg!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val min = minText.toLongOrNull() ?: 0L
            val max = maxText.toLongOrNull() ?: 0L
            val fee = feeText.toLongOrNull() ?: 0L

            if (min < 0 || max <= 0 || fee < 0) {
              errorMsg = "Harap masukkan angka nominal yang valid."
              return@Button
            }
            if (max <= min) {
              errorMsg = "Nominal maksimal harus lebih besar dari nominal minimal."
              return@Button
            }

            val newRule = AdminFeeRule(
              minNominal = min,
              maxNominal = max,
              adminFee = fee
            )
            adminFeeRules = (adminFeeRules + newRule).sortedBy { it.minNominal }
            showAddDialog = false
            Toast.makeText(context, "Aturan baru berhasil ditambahkan", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Tambahkan")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Batal")
        }
      }
    )
  }

  // DIALOG UBAH ATURAN ADMIN
  ruleToEdit?.let { targetRule ->
    var minText by remember(targetRule) { mutableStateOf(targetRule.minNominal.toString()) }
    var maxText by remember(targetRule) { mutableStateOf(targetRule.maxNominal.toString()) }
    var feeText by remember(targetRule) { mutableStateOf(targetRule.adminFee.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { ruleToEdit = null },
      title = { Text("Ubah Aturan Biaya Admin") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = minText,
            onValueChange = { minText = it.filter { c -> c.isDigit() } },
            label = { Text("Nominal Minimal (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = maxText,
            onValueChange = { maxText = it.filter { c -> c.isDigit() } },
            label = { Text("Nominal Maksimal (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = feeText,
            onValueChange = { feeText = it.filter { c -> c.isDigit() } },
            label = { Text("Biaya Admin (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          if (errorMsg != null) {
            Text(
              text = errorMsg!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val min = minText.toLongOrNull() ?: 0L
            val max = maxText.toLongOrNull() ?: 0L
            val fee = feeText.toLongOrNull() ?: 0L

            if (min < 0 || max <= 0 || fee < 0) {
              errorMsg = "Harap masukkan angka nominal yang valid."
              return@Button
            }
            if (max <= min) {
              errorMsg = "Nominal maksimal harus lebih besar dari nominal minimal."
              return@Button
            }

            val updatedRule = targetRule.copy(
              minNominal = min,
              maxNominal = max,
              adminFee = fee
            )
            adminFeeRules = adminFeeRules.map { if (it.id == targetRule.id) updatedRule else it }.sortedBy { it.minNominal }
            ruleToEdit = null
            Toast.makeText(context, "Aturan admin berhasil diperbarui", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Simpan Perubahan")
        }
      },
      dismissButton = {
        TextButton(onClick = { ruleToEdit = null }) {
          Text("Batal")
        }
      }
    )
  }

  // DIALOG KONFIRMASI HAPUS ATURAN
  ruleToDelete?.let { targetRule ->
    AlertDialog(
      onDismissRequest = { ruleToDelete = null },
      title = { Text("Hapus Aturan Admin?") },
      text = {
        Text("Apakah Anda yakin ingin menghapus aturan rentang ${targetRule.formattedRange} (Admin ${targetRule.formattedFee})?")
      },
      confirmButton = {
        Button(
          onClick = {
            adminFeeRules = adminFeeRules.filter { it.id != targetRule.id }
            ruleToDelete = null
            Toast.makeText(context, "Aturan admin berhasil dihapus", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Hapus")
        }
      },
      dismissButton = {
        TextButton(onClick = { ruleToDelete = null }) {
          Text("Batal")
        }
      }
    )
  }

  // DIALOG PILIH PRINTER PAIRED
  if (showPrinterPicker) {
    val pairedList = remember { getPairedPrinters() }
    AlertDialog(
      onDismissRequest = { showPrinterPicker = false },
      title = { Text("Pilih Printer Bluetooth Paired") },
      text = {
        if (pairedList.isEmpty()) {
          Text(
            text = "Tidak ada printer Bluetooth yang sedang berpasangan (paired). Silakan pasangkan printer thermal di menu Bluetooth HP terlebih dahulu.",
            style = MaterialTheme.typography.bodyMedium
          )
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pairedList) { device ->
              val isSelected = device.address.equals(selectedPrinterAddress, ignoreCase = true)
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    selectedPrinterAddress = device.address
                    selectedPrinterName = device.name
                    showPrinterPicker = false
                    Toast.makeText(context, "Printer dipilih: ${device.name}", Toast.LENGTH_SHORT).show()
                  },
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Filled.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(device.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showPrinterPicker = false }) {
          Text("Tutup")
        }
      }
    )
  }

  // DIALOG PILIH TEMA TAMPILAN
  if (showThemeDialog) {
    AlertDialog(
      onDismissRequest = {
        tempSelectedTheme = appTheme
        showThemeDialog = false
      },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Pilih Tema Tampilan",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Pilih tema warna aplikasi yang diinginkan. Tekan TERAPKAN untuk menyimpan dan mengaktifkan tema.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(4.dp))
          AppTheme.entries.forEach { theme ->
            val isSelected = tempSelectedTheme == theme
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  tempSelectedTheme = theme
                },
              colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
              ),
              shape = RoundedCornerShape(12.dp),
              border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = { tempSelectedTheme = theme }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = theme.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                  horizontalArrangement = Arrangement.spacedBy((-4).dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .background(theme.primaryColor, CircleShape)
                      .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  )
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .background(theme.accentColor, CircleShape)
                      .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  )
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .background(theme.backgroundColor, CircleShape)
                      .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              tempSelectedTheme = appTheme
              showThemeDialog = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("BATAL")
          }
          Button(
            onClick = {
              appTheme = tempSelectedTheme
              onSaveSettings(
                PrintSettings(
                  storeName = storeName.trim(),
                  storeAddress = storeAddress.trim(),
                  storePhone = storePhone.trim(),
                  footerMessage = footerMessage.trim(),
                  paperWidthMm = paperWidthMm,
                  showDate = showDate,
                  showRefNumber = showRefNumber,
                  autoAddHeader = autoAddHeader,
                  autoApplyAdminFee = autoApplyAdminFee,
                  adminFeeRules = adminFeeRules,
                  selectedPrinterAddress = selectedPrinterAddress,
                  selectedPrinterName = selectedPrinterName,
                  autoCutPaper = autoCutPaper,
                  printFeedLines = printFeedLines,
                  receiptTheme = receiptTheme,
                  appTheme = tempSelectedTheme,
                )
              )
              showThemeDialog = false
              Toast.makeText(context, "Tema berhasil diaktifkan: ${tempSelectedTheme.displayName}", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("TERAPKAN")
          }
        }
      },
      dismissButton = null
    )
  }

  // DIALOG 1: BACKUP BERHASIL DIBUAT (SIMPAN KE HP / BAGIKAN)
  if (showBackupSuccessDialog && createdBackupPackage != null) {
    val pkg = createdBackupPackage!!
    val file = createdBackupFile
    AlertDialog(
      onDismissRequest = { showBackupSuccessDialog = false },
      icon = {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      },
      title = {
        Text("Backup Berhasil Dibuat", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Seluruh data pengaturan, identitas toko, tema, dan template struk (termasuk gambar) telah dikemas ke file .obetstruk:",
            style = MaterialTheme.typography.bodySmall
          )
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("• Berkas: .obetstruk (Versi ${pkg.metadata.version})", style = MaterialTheme.typography.bodySmall)
              Text("• Toko: ${pkg.metadata.storeName}", style = MaterialTheme.typography.bodySmall)
              Text("• Template: ${pkg.metadata.templateCount} template (lengkap dengan data area & gambar)", style = MaterialTheme.typography.bodySmall)
              Text("• Tanggal: ${pkg.metadata.formattedDate}", style = MaterialTheme.typography.bodySmall)
              if (file != null) {
                Text("• Ukuran: ${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Silakan simpan file ke memori HP atau langsung bagikan ke WhatsApp / Bluetooth:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
              saveDocLauncher.launch("backup_obetstruk_$timestamp.obetstruk")
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Simpan ke HP", maxLines = 1)
          }
          Button(
            onClick = {
              if (file != null && backupManager != null) {
                backupManager.shareBackupFile(file)
              }
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Bagikan", maxLines = 1)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showBackupSuccessDialog = false }) {
          Text("Selesai")
        }
      }
    )
  }

  // DIALOG 2: KONFIRMASI PEMULIHAN DATA (PULIHKAN BACKUP)
  if (pendingRestorePackage != null) {
    val pkg = pendingRestorePackage!!
    AlertDialog(
      onDismissRequest = {
        if (!isRestoring) pendingRestorePackage = null
      },
      icon = {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
      },
      title = {
        Text("Konfirmasi Pemulihan Data", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Ditemukan file backup resmi ESA STRUK yang valid:",
            style = MaterialTheme.typography.bodySmall
          )
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("• Toko: ${pkg.metadata.storeName}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
              Text("• Tanggal Backup: ${pkg.metadata.formattedDate}", style = MaterialTheme.typography.bodySmall)
              Text("• Versi Aplikasi: v${pkg.metadata.appVersion}", style = MaterialTheme.typography.bodySmall)
              Text("• Jumlah Template: ${pkg.metadata.templateCount} template (termasuk gambar & area)", style = MaterialTheme.typography.bodySmall)
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "PERINGATAN:\nSeluruh pengaturan toko, tema tampilan, konfigurasi printer, dan template yang ada saat ini akan digantikan dengan data dari file backup ini.",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error
          )
          Text(
            text = "Apakah Anda yakin ingin memulihkan seluruh data?",
            style = MaterialTheme.typography.bodySmall
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (backupManager != null) {
              coroutineScope.launch {
                isRestoring = true
                val res = backupManager.restoreFromBackup(pkg)
                isRestoring = false
                pendingRestorePackage = null
                res.onSuccess { count ->
                  val s = pkg.settings
                  storeName = s.storeName
                  storeAddress = s.storeAddress
                  storePhone = s.storePhone
                  footerMessage = s.footerMessage
                  paperWidthMm = s.paperWidthMm
                  showDate = s.showDate
                  showRefNumber = s.showRefNumber
                  autoAddHeader = s.autoAddHeader
                  autoApplyAdminFee = s.autoApplyAdminFee
                  adminFeeRules = s.adminFeeRules
                  selectedPrinterAddress = s.selectedPrinterAddress
                  selectedPrinterName = s.selectedPrinterName
                  autoCutPaper = s.autoCutPaper
                  printFeedLines = s.printFeedLines
                  receiptTheme = s.receiptTheme
                  appTheme = s.appTheme

                  restoreSuccessMessage = "Data berhasil dipulihkan! Sebanyak $count template struk, gambar, tema tampilan, dan seluruh konfigurasi toko telah dikembalikan seperti semula."
                }.onFailure { err ->
                  restoreErrorMessage = "Gagal memulihkan data: ${err.message}"
                }
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          enabled = !isRestoring
        ) {
          Text(if (isRestoring) "Memulihkan..." else "PULIHKAN SEKARANG")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingRestorePackage = null },
          enabled = !isRestoring
        ) {
          Text("BATAL")
        }
      }
    )
  }

  // DIALOG 3: NOTIFIKASI ERROR PEMULIHAN
  if (restoreErrorMessage != null) {
    AlertDialog(
      onDismissRequest = { restoreErrorMessage = null },
      icon = {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
      },
      title = { Text("Pemulihan Dibatalkan") },
      text = {
        Text(
          text = restoreErrorMessage!!,
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(onClick = { restoreErrorMessage = null }) {
          Text("Mengerti")
        }
      }
    )
  }

  // DIALOG 4: NOTIFIKASI SUKSES PEMULIHAN
  if (restoreSuccessMessage != null) {
    AlertDialog(
      onDismissRequest = { restoreSuccessMessage = null },
      icon = {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      },
      title = { Text("Pemulihan Berhasil!") },
      text = {
        Text(
          text = restoreSuccessMessage!!,
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(onClick = { restoreSuccessMessage = null }) {
          Text("Selesai")
        }
      }
    )
  }

  // DIALOG 5: KONFIRMASI REGENERASI DEVICE ID
  if (showRegenerateDialog) {
    AlertDialog(
      onDismissRequest = { showRegenerateDialog = false },
      icon = {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      },
      title = { Text("Regenerasi Device ID?") },
      text = {
        Text("Apakah Anda yakin ingin membuat Device ID baru? Mekanisme ini dapat digunakan jika aplikasi dipasang ulang atau untuk mengidentifikasi perangkat secara baru. Data backup sebelumnya tetap aman tersimpan di brankas penyimpanan.")
      },
      confirmButton = {
        Button(
          onClick = {
            onRegenerateDeviceId()
            showRegenerateDialog = false
            Toast.makeText(context, "Device ID baru berhasil dibuat & dicadangkan!", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Ya, Regenerasi")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRegenerateDialog = false }) {
          Text("Batal")
        }
      }
    )
  }
}
