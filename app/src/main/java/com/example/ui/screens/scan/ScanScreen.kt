package com.example.ui.screens.scan

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Receipt
import com.example.ui.viewmodel.OcrUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
  ocrState: OcrUiState,
  currentReceipt: Receipt,
  currentImageUri: Uri?,
  onProcessImageUri: (Uri) -> Unit,
  onUpdateReceipt: (Receipt) -> Unit,
  onSaveReceipt: (onSaved: (Long) -> Unit) -> Unit,
  onViewPrintPreview: () -> Unit,
  onResetScan: () -> Unit,
  availableTemplates: List<com.example.data.model.OcrTemplate> = emptyList(),
  onApplyTemplate: (com.example.data.model.OcrTemplate?) -> Unit = {},
  printSettings: com.example.data.model.PrintSettings = com.example.data.model.PrintSettings(),
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  var showRawTextSheet by remember { mutableStateOf(false) }
  var showCoordinatesSheet by remember { mutableStateOf(false) }
  var isImageExpanded by remember { mutableStateOf(false) }
  var showValidationDialog by remember { mutableStateOf(false) }
  var selectedTemplateId by remember { mutableStateOf<Long?>(null) }

  val validationReport = remember(currentReceipt) {
    FieldValidator.validateReceipt(currentReceipt)
  }

  val popularBanks = remember {
    listOf(
      "BCA", "Bank Mandiri", "Bank BRI", "Bank BNI", "Bank BSI",
      "CIMB Niaga", "SeaBank", "Bank Jago", "DANA", "GoPay", "OVO", "ShopeePay"
    )
  }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        onProcessImageUri(uri)
      }
    }
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Baca Bukti Transfer",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Mesin OCR On-Device Google ML Kit",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          if (currentImageUri != null || ocrState !is OcrUiState.Idle) {
            IconButton(
              onClick = onResetScan,
              modifier = Modifier.testTag("btn_reset_scan")
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = "Ulangi / Bersihkan")
            }
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
      contentPadding = PaddingValues(vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Image Picker / Preview Section
      item {
        if (currentImageUri == null && ocrState is OcrUiState.Idle) {
          // Empty State Picker
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                photoPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
              .testTag("card_picker_placeholder"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.AddPhotoAlternate,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp)
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = "Pilih Gambar Bukti Transfer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Pilih screenshot bukti transfer dari galeri. ML Kit membaca teks on-device tanpa mengirim gambar ke internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                lineHeight = 18.sp
              )
            }
          }
        } else {
          // Image Preview Card
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Filled.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Bukti Transfer Terpilih",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                OutlinedButton(
                  onClick = {
                    photoPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                  modifier = Modifier.testTag("btn_change_photo")
                ) {
                  Text("Ganti Gambar", style = MaterialTheme.typography.labelSmall)
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Display Image Clearly
              if (currentImageUri != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isImageExpanded) 360.dp else 180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { isImageExpanded = !isImageExpanded }
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                  contentAlignment = Alignment.Center
                ) {
                  AsyncImage(
                    model = currentImageUri,
                    contentDescription = "Bukti Transfer",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (isImageExpanded) ContentScale.Fit else ContentScale.Crop
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Image metadata & OCR coordinate stats
                val ocr = currentReceipt.ocrResult
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = if (ocr != null && ocr.imageWidth > 0) {
                      "Ukuran: ${ocr.imageWidth}×${ocr.imageHeight} px • ${ocr.lines.size} baris teks"
                    } else {
                      "Ketuk gambar untuk memperbesar"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  if (ocr != null && ocr.lines.isNotEmpty()) {
                    TextButton(
                      onClick = { showCoordinatesSheet = true },
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                      modifier = Modifier.testTag("btn_view_coordinates")
                    ) {
                      Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Posisi Bounding Box", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 2. OCR Loading State
      if (ocrState is OcrUiState.Processing) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = ocrState.progressMessage,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "100% on-device Google ML Kit (bebas kuota & privasi aman)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
              )
            }
          }
        }
      }

      // 3. OCR Error State
      if (ocrState is OcrUiState.Error) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Pemberitahuan Gambar / OCR",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }
              Text(
                text = ocrState.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 6.dp),
                lineHeight = 18.sp
              )
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = {
                  photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
              ) {
                Text("Pilih Gambar Lain dari Galeri")
              }
            }
          }
        }
      }

      // 4. Extracted Data Form: 7 Target Recipient Columns
      if (ocrState is OcrUiState.Success || currentReceipt.rawOcrText.isNotBlank()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "7 Kolom Data Penerima",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Khusus data penerima transfer (Waktu, Bank, Rek, Nama, Nominal, Admin, Total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                if (currentReceipt.rawOcrText.isNotBlank()) {
                  Spacer(modifier = Modifier.width(8.dp))
                  TextButton(
                    onClick = { showRawTextSheet = true },
                    modifier = Modifier.testTag("btn_view_raw_ocr")
                  ) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Teks Asli", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }

              Spacer(modifier = Modifier.height(14.dp))

              // Manual Template Selector
              if (availableTemplates.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Template Ekstraksi:",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = if (selectedTemplateId == null) "Deteksi Otomatis" else "Template Manual",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                  ) {
                    item {
                      val isAuto = selectedTemplateId == null
                      SuggestionChip(
                        onClick = {
                          selectedTemplateId = null
                          onApplyTemplate(null)
                        },
                        label = { Text("Otomatis (ML Kit)") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                          containerColor = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                      )
                    }
                    items(availableTemplates) { tpl ->
                      val isSelected = selectedTemplateId == tpl.id
                      SuggestionChip(
                        onClick = {
                          selectedTemplateId = tpl.id
                          onApplyTemplate(tpl)
                        },
                        label = { Text(tpl.bankName) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                          containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                      )
                    }
                  }
                }
              }

              if (currentReceipt.reviewFlags.isNotEmpty() || currentReceipt.candidateConflicts.isNotEmpty()) {
                Card(
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                  ),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        "Catatan Deteksi Multi-Kategori",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                      )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    currentReceipt.reviewFlags.forEach { flag ->
                      Text(
                        "• $flag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                      )
                    }
                    if (currentReceipt.candidateConflicts.isNotEmpty()) {
                      Spacer(modifier = Modifier.height(6.dp))
                      currentReceipt.candidateConflicts.forEach { (field, candidates) ->
                        Text(
                          "Pilihan alternatif untuk ${field.fieldName}:",
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.SemiBold,
                          color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        LazyRow(
                          horizontalArrangement = Arrangement.spacedBy(6.dp),
                          modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                          items(candidates) { cand ->
                            AssistChip(
                              onClick = {
                                when (field) {
                                  TargetRecipientField.WAKTU -> onUpdateReceipt(currentReceipt.copy(transactionDate = cand))
                                  TargetRecipientField.BANK -> onUpdateReceipt(currentReceipt.copy(recipientBank = cand))
                                  TargetRecipientField.REKENING -> onUpdateReceipt(currentReceipt.copy(recipientAccount = cand))
                                  TargetRecipientField.PENERIMA -> onUpdateReceipt(currentReceipt.copy(recipientName = cand))
                                  TargetRecipientField.NOMINAL -> onUpdateReceipt(currentReceipt.copy(amount = cand.toLongOrNull() ?: currentReceipt.amount))
                                  else -> {}
                                }
                              },
                              label = { Text(cand, style = MaterialTheme.typography.labelSmall) }
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              // 1. WAKTU
              val waktuWarning = validationReport.warnings[TargetRecipientField.WAKTU]
              OutlinedTextField(
                value = currentReceipt.transactionDate,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(transactionDate = it)) },
                label = { Text("1. WAKTU (Tanggal & Jam)") },
                supportingText = {
                  if (waktuWarning != null) {
                    Text(waktuWarning, color = MaterialTheme.colorScheme.tertiary)
                  } else if (currentReceipt.transactionDate.isBlank()) {
                    Text("Waktu transaksi belum terdeteksi. Silakan isi manual jika perlu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_transaction_date"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 2. BANK
              val bankError = validationReport.errors[TargetRecipientField.BANK]
              val bankWarning = validationReport.warnings[TargetRecipientField.BANK]
              val bankMatch = remember(currentReceipt.recipientBank) {
                BankRegistry.matchBank(currentReceipt.recipientBank)
              }

              Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                  value = currentReceipt.recipientBank,
                  onValueChange = { onUpdateReceipt(currentReceipt.copy(recipientBank = it)) },
                  label = { Text("2. BANK (Tujuan Penerima)") },
                  isError = bankError != null,
                  supportingText = {
                    if (bankError != null) {
                      Text(bankError, color = MaterialTheme.colorScheme.error)
                    } else if (bankWarning != null) {
                      Text(bankWarning, color = MaterialTheme.colorScheme.tertiary)
                    } else if (bankMatch.isConfident) {
                      Text("✓ Bank Resmi: ${bankMatch.officialName}", color = MaterialTheme.colorScheme.primary)
                    }
                  },
                  leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                  trailingIcon = {
                    if (bankMatch.isConfident) {
                      Icon(Icons.Filled.Done, contentDescription = "Bank Resmi Terverifikasi", tint = MaterialTheme.colorScheme.primary)
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_recipient_bank"),
                  singleLine = true,
                  shape = RoundedCornerShape(10.dp)
                )

                // Quick Popular Bank Chips
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                  items(popularBanks) { bankName ->
                    val isSelected = currentReceipt.recipientBank.equals(bankName, ignoreCase = true)
                    SuggestionChip(
                      onClick = { onUpdateReceipt(currentReceipt.copy(recipientBank = bankName)) },
                      label = { Text(bankName, style = MaterialTheme.typography.labelSmall) },
                      colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                      )
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              // 3. REKENING
              val rekWarning = validationReport.warnings[TargetRecipientField.REKENING]
              OutlinedTextField(
                value = currentReceipt.recipientAccount,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(recipientAccount = it)) },
                label = { Text("3. REKENING (No. Rek / HP Penerima)") },
                supportingText = {
                  if (rekWarning != null) {
                    Text(rekWarning, color = MaterialTheme.colorScheme.tertiary)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_recipient_account"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 4. PENERIMA
              val nameError = validationReport.errors[TargetRecipientField.PENERIMA]
              val nameWarning = validationReport.warnings[TargetRecipientField.PENERIMA]
              OutlinedTextField(
                value = currentReceipt.recipientName,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(recipientName = it)) },
                label = { Text("4. PENERIMA (Nama Lengkap)") },
                isError = nameError != null,
                supportingText = {
                  if (nameError != null) {
                    Text(nameError, color = MaterialTheme.colorScheme.error)
                  } else if (nameWarning != null) {
                    Text(nameWarning, color = MaterialTheme.colorScheme.tertiary)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_recipient_name"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 5. NOMINAL
              val nominalError = validationReport.errors[TargetRecipientField.NOMINAL]
              val matchingAdminRule = remember(currentReceipt.amount, printSettings.adminFeeRules) {
                com.example.data.model.AdminFeeRule.findMatchingRule(currentReceipt.amount, printSettings.adminFeeRules)
              }

              OutlinedTextField(
                value = if (currentReceipt.amount > 0) currentReceipt.amount.toString() else "",
                onValueChange = { input ->
                  val clean = input.filter { it.isDigit() }
                  val amt = clean.toLongOrNull() ?: 0L
                  val autoFee = if (printSettings.autoApplyAdminFee && amt > 0L) {
                    com.example.data.model.AdminFeeRule.calculateAdminFee(amt, printSettings.adminFeeRules)
                  } else null
                  val newAdmin = autoFee ?: currentReceipt.adminFee
                  onUpdateReceipt(currentReceipt.copy(amount = amt, adminFee = newAdmin))
                },
                label = { Text("5. NOMINAL (Rp)") },
                isError = nominalError != null,
                supportingText = {
                  if (nominalError != null) {
                    Text(nominalError, color = MaterialTheme.colorScheme.error)
                  } else if (currentReceipt.amount > 0) {
                    Text("Terbaca: ${currentReceipt.formattedAmount}", color = MaterialTheme.colorScheme.primary)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_recipient_amount"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 6. ADMIN (Otomatis sesuai pengaturan rentang nominal / diisi manual)
              Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                  value = if (currentReceipt.adminFee > 0) currentReceipt.adminFee.toString() else "",
                  onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    val fee = clean.toLongOrNull() ?: 0L
                    onUpdateReceipt(currentReceipt.copy(adminFee = fee))
                  },
                  label = { Text("6. ADMIN (Biaya Admin)") },
                  supportingText = {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      if (matchingAdminRule != null && currentReceipt.adminFee == matchingAdminRule.adminFee) {
                        Text(
                          "✓ Otomatis dari Pengaturan Admin: ${currentReceipt.formattedAdminFee} (${matchingAdminRule.formattedRange})",
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.Medium,
                          softWrap = false
                        )
                      } else if (currentReceipt.adminFee > 0) {
                        Text(
                          "Admin: ${currentReceipt.formattedAdminFee} (Dapat diubah manual)",
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          softWrap = false
                        )
                      } else {
                        Text(
                          "Otomatis mengikuti rentang nominal di Pengaturan, atau isi manual",
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          softWrap = false
                        )
                      }
                    }
                  },
                  leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_admin_fee"),
                  singleLine = true,
                  shape = RoundedCornerShape(10.dp)
                )

                // Quick Admin Preset Chips
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                  val adminPresets = buildList {
                    if (matchingAdminRule != null) {
                      add(Pair("Aturan: ${matchingAdminRule.formattedFee}", matchingAdminRule.adminFee))
                    }
                    add(Pair("Rp 0 (Gratis)", 0L))
                    add(Pair("Rp 3.000", 3000L))
                    add(Pair("Rp 4.000", 4000L))
                    add(Pair("Rp 2.500 (BI-FAST)", 2500L))
                    add(Pair("Rp 6.500 (Online)", 6500L))
                  }.distinctBy { it.second }

                  items(adminPresets) { (label, fee) ->
                    val isSelected = currentReceipt.adminFee == fee
                    SuggestionChip(
                      onClick = { onUpdateReceipt(currentReceipt.copy(adminFee = fee)) },
                      label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                      colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                      )
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(14.dp))

              // 7. TOTAL (Dihitung Otomatis: NOMINAL + ADMIN)
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Calculate,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                      modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                    ) {
                      Text(
                        text = "7. TOTAL (Otomatis)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        softWrap = false
                      )
                      Text(
                        text = "Nominal (${currentReceipt.formattedAmount}) + Admin (${currentReceipt.formattedAdminFee})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        softWrap = false
                      )
                    }
                  }

                  Spacer(modifier = Modifier.width(10.dp))

                  Text(
                    text = currentReceipt.formattedTotal,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                  )
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Validation Summary Notice
              if (!validationReport.isValidForPrint) {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    verticalAlignment = Alignment.Top
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
                        text = "Lengkapi data wajib penerima sebelum cetak/simpan:",
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
              } else {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                      text = "Data penerima terverifikasi & siap dicetak ke struk",
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Primary Action Buttons
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                // Simpan ke Riwayat
                Button(
                  onClick = {
                    if (!validationReport.isValidForPrint) {
                      showValidationDialog = true
                    } else {
                      onSaveReceipt {
                        Toast.makeText(context, "Struk penerima berhasil disimpan!", Toast.LENGTH_SHORT).show()
                      }
                    }
                  },
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_save_receipt"),
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                  Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Simpan", maxLines = 1)
                }

                // Cetak / Pratinjau Struk
                Button(
                  onClick = {
                    if (!validationReport.isValidForPrint) {
                      showValidationDialog = true
                    } else {
                      onSaveReceipt {
                        onViewPrintPreview()
                      }
                    }
                  },
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_print_receipt"),
                  shape = RoundedCornerShape(10.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                  Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Format Struk", maxLines = 1)
                }
              }
            }
          }
        }
      }
    }
  }

  // Bottom Sheet 1: Bounding Box Coordinates Inspector (Verification for Tahap 3 preparation)
  if (showCoordinatesSheet && currentReceipt.ocrResult != null) {
    val ocr = currentReceipt.ocrResult!!
    ModalBottomSheet(
      onDismissRequest = { showCoordinatesSheet = false },
      sheetState = rememberModalBottomSheetState()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Koordinat Bounding Box Teks",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Total ${ocr.lines.size} baris teks terdeteksi dengan koordinat piksel & normalisasi",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          IconButton(
            onClick = {
              val coordsText = ocr.lines.joinToString("\n") { line ->
                val b = line.boundingBox
                if (b != null) {
                  "[X:${b.left}, Y:${b.top}, W:${b.width}, H:${b.height} | N: ${String.format("%.2f", b.normalizedLeft)}, ${String.format("%.2f", b.normalizedTop)}] -> ${line.text}"
                } else {
                  "[-] ${line.text}"
                }
              }
              clipboardManager.setText(AnnotatedString(coordsText))
              Toast.makeText(context, "Daftar koordinat disalin", Toast.LENGTH_SHORT).show()
            }
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Koordinat")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          LazyColumn(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            itemsIndexed(ocr.lines) { index, line ->
              val box = line.boundingBox
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                  .padding(10.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "#${index + 1}: ${line.text}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }

                if (box != null) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Piksel: Left ${box.left}, Top ${box.top}, W ${box.width}, H ${box.height}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = "Normalisasi (0..1): X ${String.format("%.3f", box.normalizedLeft)}, Y ${String.format("%.3f", box.normalizedTop)} (W: ${String.format("%.3f", box.normalizedWidth)}, H: ${String.format("%.3f", box.normalizedHeight)})",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  // Bottom Sheet 2: Raw OCR Text Inspector (Bisa menyalin teks satu-satu di tiap baris & salin semua)
  if (showRawTextSheet && currentReceipt.rawOcrText.isNotBlank()) {
    val rawLines = remember(currentReceipt.rawOcrText) {
      currentReceipt.rawOcrText.lines().filter { it.isNotBlank() }
    }

    ModalBottomSheet(
      onDismissRequest = { showRawTextSheet = false },
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
              text = "Teks Mentah Hasil OCR",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Total ${rawLines.size} baris teks terbaca. Ketuk ikon salin pada baris untuk menyalin teks satu-satu.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Tombol Salin Semua Teks
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(currentReceipt.rawOcrText))
              Toast.makeText(context, "Seluruh teks OCR disalin ke papan klip", Toast.LENGTH_SHORT).show()
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Salin Semua", fontSize = 12.sp, maxLines = 1)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Daftar baris OCR dengan tombol salin per baris
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          itemsIndexed(rawLines) { index, lineText ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  clipboardManager.setText(AnnotatedString(lineText))
                  Toast.makeText(context, "Baris ${index + 1} disalin: \"$lineText\"", Toast.LENGTH_SHORT).show()
                },
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              ),
              border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                // Nomor baris
                Box(
                  modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Isi teks baris mendatar tanpa terbungkus
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                ) {
                  Text(
                    text = lineText,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false
                  )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tombol salin baris ini
                IconButton(
                  onClick = {
                    clipboardManager.setText(AnnotatedString(lineText))
                    Toast.makeText(context, "Baris ${index + 1} disalin: \"$lineText\"", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.size(34.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Salin Baris ${index + 1}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  // Validation Blocking Alert Dialog
  if (showValidationDialog) {
    AlertDialog(
      onDismissRequest = { showValidationDialog = false },
      icon = {
        Icon(
          Icons.Filled.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp)
        )
      },
      title = {
        Text(
          text = "Data Belum Lengkap",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Sebelum struk dicetak atau disimpan, pastikan seluruh data wajib penerima terisi dengan benar:",
            style = MaterialTheme.typography.bodyMedium
          )
          validationReport.errors.values.forEach { err ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "• $err",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showValidationDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Text("Lengkapi Sekarang")
        }
      }
    )
  }
}
