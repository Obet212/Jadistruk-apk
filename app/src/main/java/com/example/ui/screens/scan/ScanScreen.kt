package com.example.ui.screens.scan

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
  smartReaderResult: com.example.data.smartreader.SmartParsedReceipt? = null,
  onProcessImageUri: (Uri) -> Unit,
  onUpdateReceipt: (Receipt) -> Unit,
  onSwitchSmartReaderSource: (com.example.data.smartreader.TransactionSource) -> Unit = {},
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
  var showOriginalImageDialog by remember { mutableStateOf(false) }
  var showValidationDialog by remember { mutableStateOf(false) }
  var selectedTemplateId by remember { mutableStateOf<Long?>(null) }
  var showSenderAndTransactionDetails by remember { mutableStateOf(true) }

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
              text = "ESA STRUK - Terima Share Bukti",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Penerimaan Intent ACTION_SEND & OCR Presisi",
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
              // ESA STRUK SMART READER HEADER & SOURCE SELECTOR
              if (smartReaderResult != null) {
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                  ),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = Color(smartReaderResult.source.primaryColorHex),
                          modifier = Modifier.padding(end = 8.dp)
                        ) {
                          Text(
                            text = smartReaderResult.source.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                          )
                        }
                        Text(
                          text = "ESA STRUK Smart Reader",
                          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                          color = MaterialTheme.colorScheme.onSurface
                        )
                      }

                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                      ) {
                        Text(
                          text = "Akurasi: ${smartReaderResult.confidenceScore}%",
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                          color = if (smartReaderResult.confidenceScore >= 75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                      text = "Sumber: ${smartReaderResult.source.displayName} • Ekstraksi modular otomatis",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Source Switcher Chips
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = "Ganti template bank/e-wallet jika salah deteksi:",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      contentPadding = PaddingValues(horizontal = 1.dp)
                    ) {
                      items(com.example.data.smartreader.TransactionSource.entries) { src ->
                        val isCurrent = smartReaderResult.source == src
                        SuggestionChip(
                          onClick = { onSwitchSmartReaderSource(src) },
                          label = {
                            Text(
                              src.displayName,
                              style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                              )
                            )
                          },
                          colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                          ),
                          border = BorderStroke(
                            1.dp,
                            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                          )
                        )
                      }
                    }

                    // Warning / Missing fields notes
                    val allNotices = (smartReaderResult.missingFields + smartReaderResult.reviewWarnings).distinct()
                    if (allNotices.isNotEmpty()) {
                      Spacer(modifier = Modifier.height(8.dp))
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                          allNotices.forEach { notice ->
                            Text(
                              text = "• $notice",
                              style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                              color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Formulir Hasil Pembacaan Transaksi",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Dapat diedit langsung, salin teks per kolom, dan diverifikasi sebelum cetak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Baris Tombol Aksi Cepat: Lihat Asli, Pindai Ulang, Teks Asli
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (currentImageUri != null) {
                  OutlinedButton(
                    onClick = { showOriginalImageDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_view_original_image")
                  ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lihat Gambar Asli", style = MaterialTheme.typography.labelSmall)
                  }
                }

                OutlinedButton(
                  onClick = {
                    photoPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.testTag("btn_rescan_image")
                ) {
                  Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Pindai Ulang", style = MaterialTheme.typography.labelSmall)
                }

                if (currentReceipt.rawOcrText.isNotBlank()) {
                  TextButton(
                    onClick = { showRawTextSheet = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_view_raw_ocr")
                  ) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Teks Mentah OCR", style = MaterialTheme.typography.labelSmall)
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
                                  TargetRecipientField.TANGGAL -> onUpdateReceipt(currentReceipt.copy(transactionDate = cand))
                                  TargetRecipientField.WAKTU -> onUpdateReceipt(currentReceipt.copy(transactionTime = cand))
                                  TargetRecipientField.REFERENSI -> onUpdateReceipt(currentReceipt.copy(refNumber = cand))
                                  TargetRecipientField.BANK -> onUpdateReceipt(currentReceipt.copy(recipientBank = cand))
                                  TargetRecipientField.REKENING -> onUpdateReceipt(currentReceipt.copy(recipientAccount = cand))
                                  TargetRecipientField.PENERIMA -> onUpdateReceipt(currentReceipt.copy(recipientName = cand))
                                  TargetRecipientField.NOMINAL -> onUpdateReceipt(currentReceipt.copy(amount = cand.toLongOrNull() ?: currentReceipt.amount))
                                  TargetRecipientField.ADMIN -> onUpdateReceipt(currentReceipt.copy(adminFee = cand.toLongOrNull() ?: currentReceipt.adminFee))
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

              Spacer(modifier = Modifier.height(10.dp))

              // 1. TANGGAL (DD/MM/YYYY)
              val tanggalWarning = validationReport.warnings[TargetRecipientField.TANGGAL]
              val tanggalError = validationReport.errors[TargetRecipientField.TANGGAL]
              val (sanitizedDate, dateErr) = remember(currentReceipt.displayDate) {
                FieldValidator.sanitizeDate(currentReceipt.displayDate)
              }

              OutlinedTextField(
                value = currentReceipt.displayDate,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(transactionDate = it)) },
                label = { Text("1. Tanggal Transaksi (DD/MM/YYYY)") },
                isError = tanggalError != null,
                supportingText = {
                  if (tanggalError != null) {
                    Text(tanggalError, color = MaterialTheme.colorScheme.error)
                  } else if (tanggalWarning != null) {
                    Text(tanggalWarning, color = MaterialTheme.colorScheme.tertiary)
                  } else if (sanitizedDate.isNotBlank()) {
                    Text("✓ Tanggal kalender valid: $sanitizedDate", color = MaterialTheme.colorScheme.primary)
                  } else {
                    Text("Format standar: DD/MM/YYYY (contoh: 28/10/2026)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                trailingIcon = {
                  if (sanitizedDate.isNotBlank() && dateErr == null) {
                    Icon(Icons.Filled.Done, contentDescription = "Valid", tint = MaterialTheme.colorScheme.primary)
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_transaction_date"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 2. WAKTU (Format 24 Jam)
              val waktuWarning = validationReport.warnings[TargetRecipientField.WAKTU]
              val waktuError = validationReport.errors[TargetRecipientField.WAKTU]
              val (sanitizedTime, timeErr) = remember(currentReceipt.displayTime) {
                FieldValidator.sanitizeTime(currentReceipt.displayTime)
              }

              OutlinedTextField(
                value = currentReceipt.displayTime,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(transactionTime = it)) },
                label = { Text("2. Waktu Transaksi (24 Jam: HH:MM:SS)") },
                isError = waktuError != null,
                supportingText = {
                  if (waktuError != null) {
                    Text(waktuError, color = MaterialTheme.colorScheme.error)
                  } else if (waktuWarning != null) {
                    Text(waktuWarning, color = MaterialTheme.colorScheme.tertiary)
                  } else if (sanitizedTime.isNotBlank()) {
                    Text("✓ Waktu 24 jam valid: $sanitizedTime", color = MaterialTheme.colorScheme.primary)
                  } else {
                    Text("Format 24 jam: HH:MM:SS atau HH:MM (contoh: 15:33:00)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                trailingIcon = {
                  if (sanitizedTime.isNotBlank() && timeErr == null) {
                    Icon(Icons.Filled.Done, contentDescription = "Valid", tint = MaterialTheme.colorScheme.primary)
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_transaction_time"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 3. REFERENSI / SERIAL NUMBER / KODE TRANSAKSI
              val refWarning = validationReport.warnings[TargetRecipientField.REFERENSI]
              val refError = validationReport.errors[TargetRecipientField.REFERENSI]

              OutlinedTextField(
                value = currentReceipt.refNumber,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(refNumber = it)) },
                label = { Text("3. Referensi / Serial Number / Kode Transaksi") },
                isError = refError != null,
                supportingText = {
                  if (refError != null) {
                    Text(refError, color = MaterialTheme.colorScheme.error)
                  } else if (refWarning != null) {
                    Text(refWarning, color = MaterialTheme.colorScheme.tertiary)
                  } else if (currentReceipt.refNumber.isNotBlank()) {
                    Text("✓ Kode referensi asli (${currentReceipt.refNumber.length} karakter, nol depan terjaga)", color = MaterialTheme.colorScheme.primary)
                  } else {
                    Text("Kode referensi / SN transaksi (prioritas > 15 digit)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                trailingIcon = {
                  if (currentReceipt.refNumber.isNotBlank()) {
                    IconButton(
                      onClick = {
                        clipboardManager.setText(AnnotatedString(currentReceipt.refNumber))
                        Toast.makeText(context, "No. Referensi disalin ke papan klip", Toast.LENGTH_SHORT).show()
                      }
                    ) {
                      Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Referensi", modifier = Modifier.size(18.dp))
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_ref_number"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 4. BANK TUJUAN
              val bankError = validationReport.errors[TargetRecipientField.BANK]
              val bankWarning = validationReport.warnings[TargetRecipientField.BANK]
              val bankMatch = remember(currentReceipt.recipientBank) {
                BankRegistry.matchBank(currentReceipt.recipientBank)
              }

              Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                  value = currentReceipt.recipientBank,
                  onValueChange = { onUpdateReceipt(currentReceipt.copy(recipientBank = it)) },
                  label = { Text("4. Bank Tujuan Penerima") },
                  isError = bankError != null,
                  supportingText = {
                    if (bankError != null) {
                      Text(bankError, color = MaterialTheme.colorScheme.error)
                    } else if (bankWarning != null) {
                      Text(bankWarning, color = MaterialTheme.colorScheme.tertiary)
                    } else if (bankMatch.isConfident) {
                      Text("✓ Bank Resmi Terverifikasi: ${bankMatch.officialName}", color = MaterialTheme.colorScheme.primary)
                    } else {
                      Text("Nama bank atau dompet digital penerima transfer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  },
                  leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                  trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      if (currentReceipt.recipientBank.isNotBlank()) {
                        IconButton(
                          onClick = {
                            clipboardManager.setText(AnnotatedString(currentReceipt.recipientBank))
                            Toast.makeText(context, "Bank Tujuan disalin ke papan klip", Toast.LENGTH_SHORT).show()
                          }
                        ) {
                          Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Bank", modifier = Modifier.size(18.dp))
                        }
                      }
                      if (bankMatch.isConfident) {
                        Icon(Icons.Filled.Done, contentDescription = "Bank Resmi Terverifikasi", tint = MaterialTheme.colorScheme.primary)
                      }
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_recipient_bank"),
                  singleLine = true,
                  shape = RoundedCornerShape(10.dp)
                )

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

              // 5. NOMOR REKENING (Maksimal 15 Digit)
              val rekError = validationReport.errors[TargetRecipientField.REKENING]
              val rekWarning = validationReport.warnings[TargetRecipientField.REKENING]
              val isTooLong = currentReceipt.recipientAccount.length > 15

              OutlinedTextField(
                value = currentReceipt.recipientAccount,
                onValueChange = { input ->
                  val digits = input.filter { it.isDigit() }
                  onUpdateReceipt(currentReceipt.copy(recipientAccount = digits))
                },
                label = { Text("5. Nomor Rekening (Maksimal 15 Digit)") },
                isError = isTooLong || rekError != null,
                supportingText = {
                  if (isTooLong) {
                    Text(
                      "⚠️ Nomor rekening melebihi batas 15 digit (${currentReceipt.recipientAccount.length} digit). Periksa apakah tertukar dengan No. Referensi.",
                      color = MaterialTheme.colorScheme.error
                    )
                  } else if (rekError != null) {
                    Text(rekError, color = MaterialTheme.colorScheme.error)
                  } else if (rekWarning != null) {
                    Text(rekWarning, color = MaterialTheme.colorScheme.tertiary)
                  } else if (currentReceipt.recipientAccount.isNotBlank()) {
                    Text("✓ Nomor rekening valid (${currentReceipt.recipientAccount.length}/15 digit, nol depan terjaga)", color = MaterialTheme.colorScheme.primary)
                  } else {
                    Text("Hanya angka 0–9, maksimal 15 digit (tidak boleh dipotong otomatis jika melebihi)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                trailingIcon = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentReceipt.recipientAccount.isNotBlank()) {
                      IconButton(
                        onClick = {
                          clipboardManager.setText(AnnotatedString(currentReceipt.recipientAccount))
                          Toast.makeText(context, "Nomor Rekening disalin ke papan klip", Toast.LENGTH_SHORT).show()
                        }
                      ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Rekening", modifier = Modifier.size(18.dp))
                      }
                    }
                    if (currentReceipt.recipientAccount.isNotBlank() && !isTooLong) {
                      Icon(Icons.Filled.Done, contentDescription = "Valid", tint = MaterialTheme.colorScheme.primary)
                    }
                  }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_recipient_account"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 6. NAMA PENERIMA
              val nameError = validationReport.errors[TargetRecipientField.PENERIMA]
              val nameWarning = validationReport.warnings[TargetRecipientField.PENERIMA]

              OutlinedTextField(
                value = currentReceipt.recipientName,
                onValueChange = { onUpdateReceipt(currentReceipt.copy(recipientName = it)) },
                label = { Text("6. Nama Penerima Transfer") },
                isError = nameError != null,
                supportingText = {
                  if (nameError != null) {
                    Text(nameError, color = MaterialTheme.colorScheme.error)
                  } else if (nameWarning != null) {
                    Text(nameWarning, color = MaterialTheme.colorScheme.tertiary)
                  } else if (currentReceipt.recipientName.isNotBlank()) {
                    Text("✓ Nama penerima terverifikasi (label & data pengirim dihapus)", color = MaterialTheme.colorScheme.primary)
                  } else {
                    Text("Nama lengkap penerima (label 'Kepada:' dibersihkan)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                trailingIcon = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentReceipt.recipientName.isNotBlank()) {
                      IconButton(
                        onClick = {
                          clipboardManager.setText(AnnotatedString(currentReceipt.recipientName))
                          Toast.makeText(context, "Nama Penerima disalin ke papan klip", Toast.LENGTH_SHORT).show()
                        }
                      ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Salin Nama", modifier = Modifier.size(18.dp))
                      }
                    }
                    if (currentReceipt.recipientName.isNotBlank() && nameError == null) {
                      Icon(Icons.Filled.Done, contentDescription = "Valid", tint = MaterialTheme.colorScheme.primary)
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("input_recipient_name"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              // 7. NOMINAL TRANSFER
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
                label = { Text("7. Nominal Transfer (Rp)") },
                isError = nominalError != null,
                supportingText = {
                  if (nominalError != null) {
                    Text(nominalError, color = MaterialTheme.colorScheme.error)
                  } else if (currentReceipt.amount > 0) {
                    Text("✓ Nominal transfer terdeteksi: ${currentReceipt.formattedAmount}", color = MaterialTheme.colorScheme.primary)
                  } else {
                    Text("Jumlah uang transfer (bukan saldo atau biaya admin)", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

              // DETAIL TRANSAKSI & PENGIRIM (Status, Jenis Transaksi, Pengirim, Catatan)
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { showSenderAndTransactionDetails = !showSenderAndTransactionDetails },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "Detail Transaksi & Pengirim",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                    }
                    IconButton(
                      onClick = { showSenderAndTransactionDetails = !showSenderAndTransactionDetails },
                      modifier = Modifier.size(28.dp)
                    ) {
                      Icon(
                        if (showSenderAndTransactionDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                  }

                  AnimatedVisibility(visible = showSenderAndTransactionDetails) {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                      verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      // Status Transaksi
                      OutlinedTextField(
                        value = currentReceipt.transactionStatus,
                        onValueChange = { onUpdateReceipt(currentReceipt.copy(transactionStatus = it)) },
                        label = { Text("Status Transaksi") },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                        trailingIcon = {
                          if (currentReceipt.transactionStatus.isNotBlank()) {
                            IconButton(onClick = {
                              clipboardManager.setText(AnnotatedString(currentReceipt.transactionStatus))
                              Toast.makeText(context, "Status Transaksi disalin", Toast.LENGTH_SHORT).show()
                            }) {
                              Icon(Icons.Filled.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(18.dp))
                            }
                          }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_transaction_status"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                      )

                      // Jenis Transaksi
                      OutlinedTextField(
                        value = currentReceipt.transactionType,
                        onValueChange = { onUpdateReceipt(currentReceipt.copy(transactionType = it)) },
                        label = { Text("Jenis Transaksi") },
                        leadingIcon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                        trailingIcon = {
                          if (currentReceipt.transactionType.isNotBlank()) {
                            IconButton(onClick = {
                              clipboardManager.setText(AnnotatedString(currentReceipt.transactionType))
                              Toast.makeText(context, "Jenis Transaksi disalin", Toast.LENGTH_SHORT).show()
                            }) {
                              Icon(Icons.Filled.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(18.dp))
                            }
                          }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_transaction_type"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                      )

                      // Nama Pengirim
                      OutlinedTextField(
                        value = currentReceipt.senderName,
                        onValueChange = { onUpdateReceipt(currentReceipt.copy(senderName = it)) },
                        label = { Text("Nama Pengirim") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        trailingIcon = {
                          if (currentReceipt.senderName.isNotBlank()) {
                            IconButton(onClick = {
                              clipboardManager.setText(AnnotatedString(currentReceipt.senderName))
                              Toast.makeText(context, "Nama Pengirim disalin", Toast.LENGTH_SHORT).show()
                            }) {
                              Icon(Icons.Filled.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(18.dp))
                            }
                          }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_sender_name"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                      )

                      // Bank & Rekening Pengirim
                      val senderAccDisplay = when {
                        currentReceipt.senderBank.isNotBlank() && currentReceipt.senderAccount.isNotBlank() ->
                          "${currentReceipt.senderBank} - ${currentReceipt.senderAccount}"
                        currentReceipt.senderBank.isNotBlank() -> currentReceipt.senderBank
                        currentReceipt.senderAccount.isNotBlank() -> currentReceipt.senderAccount
                        else -> ""
                      }
                      OutlinedTextField(
                        value = senderAccDisplay,
                        onValueChange = { input ->
                          val parts = input.split("-")
                          if (parts.size >= 2) {
                            onUpdateReceipt(currentReceipt.copy(senderBank = parts[0].trim(), senderAccount = parts.drop(1).joinToString("-").trim()))
                          } else {
                            onUpdateReceipt(currentReceipt.copy(senderAccount = input.trim()))
                          }
                        },
                        label = { Text("Bank & Rekening Pengirim") },
                        leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                        trailingIcon = {
                          if (senderAccDisplay.isNotBlank()) {
                            IconButton(onClick = {
                              clipboardManager.setText(AnnotatedString(senderAccDisplay))
                              Toast.makeText(context, "Rekening Pengirim disalin", Toast.LENGTH_SHORT).show()
                            }) {
                              Icon(Icons.Filled.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(18.dp))
                            }
                          }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_sender_account"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                      )

                      // Catatan
                      OutlinedTextField(
                        value = currentReceipt.notes,
                        onValueChange = { onUpdateReceipt(currentReceipt.copy(notes = it)) },
                        label = { Text("Catatan / Keterangan") },
                        leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_notes"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                      )
                    }
                  }
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
                  Text("Cetak", maxLines = 1)
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

  // Dialog Lihat Gambar Asli (Penuh & Jelas)
  if (showOriginalImageDialog && currentImageUri != null) {
    AlertDialog(
      onDismissRequest = { showOriginalImageDialog = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Gambar Bukti Transfer Asli",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          IconButton(onClick = { showOriginalImageDialog = false }) {
            Icon(Icons.Filled.Close, contentDescription = "Tutup")
          }
        }
      },
      text = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          AsyncImage(
            model = currentImageUri,
            contentDescription = "Gambar Bukti Transfer Asli Penuh",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showOriginalImageDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Text("Tutup")
        }
      }
    )
  }
}
