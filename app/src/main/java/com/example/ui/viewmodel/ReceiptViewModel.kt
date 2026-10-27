package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ReceiptEntity
import com.example.data.local.SettingsManager
import com.example.data.local.TemplateEntity
import com.example.data.model.OcrTemplate
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import com.example.data.ocr.MlKitTextRecognizer
import com.example.data.ocr.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OcrUiState {
  data object Idle : OcrUiState
  data class Processing(val progressMessage: String) : OcrUiState
  data class Success(val receipt: Receipt, val imageUri: Uri? = null) : OcrUiState
  data class Error(val errorMessage: String) : OcrUiState
}

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

  private val database = AppDatabase.getDatabase(application, viewModelScope)
  private val receiptDao = database.receiptDao()
  private val templateDao = database.templateDao()
  private val settingsManager = SettingsManager(application)
  val textRecognizer = MlKitTextRecognizer(application)
  val thermalPrinter = com.example.data.printer.BluetoothThermalPrinter(application)

  val settings: StateFlow<PrintSettings> = settingsManager.settings

  val savedReceipts: StateFlow<List<Receipt>> = receiptDao.getAllReceipts()
    .map { entities -> entities.map { it.toModel() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val ocrTemplates: StateFlow<List<OcrTemplate>> = templateDao.getAllTemplates()
    .map { entities -> entities.map { it.toModel() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val receiptCount: StateFlow<Int> = receiptDao.getReceiptCount()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val totalAmountSum: StateFlow<Long> = receiptDao.getTotalAmountSum()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

  private val _ocrState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
  val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()

  private val _currentReceipt = MutableStateFlow(Receipt())
  val currentReceipt: StateFlow<Receipt> = _currentReceipt.asStateFlow()

  private val _currentImageUri = MutableStateFlow<Uri?>(null)
  val currentImageUri: StateFlow<Uri?> = _currentImageUri.asStateFlow()

  fun processImageUri(uri: Uri) {
    viewModelScope.launch {
      _currentImageUri.value = uri
      _ocrState.value = OcrUiState.Processing("Membaca teks dari bukti transfer...")
      try {
        val ocrResult = withContext(Dispatchers.IO) {
          textRecognizer.processImageUri(uri)
        }

        if (ocrResult.isEmpty) {
          _ocrState.value = OcrUiState.Error("Tidak ada teks yang terdeteksi pada gambar. Pastikan gambar bukti transfer jelas, pencahayaan cukup, dan tidak buram.")
          return@launch
        }

        _ocrState.value = OcrUiState.Processing("Menyimpan teks & koordinat bounding box...")
        val templates = ocrTemplates.value
        val parsed = withContext(Dispatchers.Default) {
          ReceiptParser.parseOcrResult(ocrResult, templates)
        }

        val finalReceipt = if (settings.value.autoApplyAdminFee && parsed.amount > 0L) {
          val autoFee = com.example.data.model.AdminFeeRule.calculateAdminFee(parsed.amount, settings.value.adminFeeRules)
          if (autoFee != null) parsed.copy(adminFee = autoFee) else parsed
        } else {
          parsed
        }

        _currentReceipt.value = finalReceipt
        _ocrState.value = OcrUiState.Success(finalReceipt, uri)
      } catch (e: Exception) {
        val userFriendlyMessage = e.localizedMessage ?: "Terjadi kesalahan saat memproses gambar dengan ML Kit."
        _ocrState.value = OcrUiState.Error(userFriendlyMessage)
      }
    }
  }

  fun updateCurrentReceipt(receipt: Receipt) {
    _currentReceipt.value = receipt
    if (_ocrState.value is OcrUiState.Success) {
      _ocrState.value = OcrUiState.Success(receipt, _currentImageUri.value)
    }
  }

  fun applyTemplate(template: OcrTemplate?) {
    val ocr = _currentReceipt.value.ocrResult ?: return
    viewModelScope.launch(Dispatchers.Default) {
      val parsed = if (template != null) {
        ReceiptParser.parseOcrResult(ocr, listOf(template))
      } else {
        ReceiptParser.parseOcrResult(ocr, ocrTemplates.value)
      }
      val previousAdmin = _currentReceipt.value.adminFee
      val updated = parsed.copy(
        adminFee = if (parsed.adminFee > 0) parsed.adminFee else previousAdmin
      )
      _currentReceipt.value = updated
      if (_ocrState.value is OcrUiState.Success) {
        _ocrState.value = OcrUiState.Success(updated, _currentImageUri.value)
      }
    }
  }

  fun saveCurrentReceipt(onSaved: (Long) -> Unit = {}) {
    val receiptToSave = _currentReceipt.value
    viewModelScope.launch(Dispatchers.IO) {
      val id = receiptDao.insertReceipt(ReceiptEntity.fromModel(receiptToSave))
      _currentReceipt.value = receiptToSave.copy(id = id)
      withContext(Dispatchers.Main) {
        onSaved(id)
      }
    }
  }

  fun deleteReceipt(receiptId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      receiptDao.deleteReceiptById(receiptId)
    }
  }

  fun saveTemplate(template: OcrTemplate) {
    viewModelScope.launch(Dispatchers.IO) {
      templateDao.insertTemplate(TemplateEntity.fromModel(template))
    }
  }

  fun deleteTemplate(template: OcrTemplate) {
    viewModelScope.launch(Dispatchers.IO) {
      templateDao.deleteTemplate(TemplateEntity.fromModel(template))
    }
  }

  fun updateSettings(newSettings: PrintSettings) {
    settingsManager.updateSettings(newSettings)
  }

  fun resetScanState() {
    _ocrState.value = OcrUiState.Idle
    _currentReceipt.value = Receipt()
    _currentImageUri.value = null
  }

  fun selectReceiptForDetail(receipt: Receipt) {
    _currentReceipt.value = receipt
  }

  val printerConnectionState = thermalPrinter.connectionState

  fun getPairedPrinters(): List<com.example.data.printer.BluetoothPrinterDevice> {
    return thermalPrinter.getPairedPrinters()
  }

  fun setSelectedPrinter(device: com.example.data.printer.BluetoothPrinterDevice) {
    val current = settings.value
    updateSettings(
      current.copy(
        selectedPrinterAddress = device.address,
        selectedPrinterName = device.name
      )
    )
  }

  fun getDiagnosticStatus(): com.example.data.printer.BluetoothDiagnosticStatus {
    return thermalPrinter.getDiagnosticStatus()
  }

  fun isBluetoothSupported(): Boolean = thermalPrinter.isBluetoothSupported()

  fun isBluetoothEnabled(): Boolean = thermalPrinter.isBluetoothEnabled()

  fun hasBluetoothPermission(): Boolean = thermalPrinter.hasBluetoothPermission()

  fun testPrint(onResult: (com.example.data.printer.PrintResult) -> Unit) {
    viewModelScope.launch {
      val res = thermalPrinter.testPrint(settings.value)
      onResult(res)
    }
  }

  fun connectPrinter(deviceAddress: String, deviceName: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val res = thermalPrinter.connect(deviceAddress, deviceName)
      onResult(res.isSuccess)
    }
  }

  fun disconnectPrinter() {
    thermalPrinter.disconnect()
  }

  fun printReceipt(receipt: Receipt, onResult: (com.example.data.printer.PrintResult) -> Unit) {
    viewModelScope.launch {
      val res = thermalPrinter.printReceipt(receipt, settings.value)
      onResult(res)
    }
  }

  override fun onCleared() {
    super.onCleared()
    textRecognizer.close()
  }
}
