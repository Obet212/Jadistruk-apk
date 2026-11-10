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
  val remoteConfigManager = com.example.data.remote.RemoteConfigManager(application)
  val backupManager = com.example.data.backup.BackupManager(application, settingsManager, templateDao)
  val deviceIdentityManager = com.example.data.security.DeviceIdentityManager(application)
  val autoBackupSyncManager = com.example.data.backup.AutoBackupSyncManager(application, backupManager, deviceIdentityManager)

  val deviceId: String get() = deviceIdentityManager.getOrCreateDeviceId()
  val syncStatus: StateFlow<com.example.data.backup.CloudSyncStatus> = autoBackupSyncManager.syncStatus

  val settings: StateFlow<PrintSettings> = settingsManager.settings

  private val _appUpdateStatus = MutableStateFlow<com.example.data.remote.AppUpdateStatus>(
    com.example.data.remote.AppUpdateStatus.Checking
  )
  val appUpdateStatus: StateFlow<com.example.data.remote.AppUpdateStatus> = _appUpdateStatus.asStateFlow()

  private val _isUpdateFailureDismissed = MutableStateFlow(false)
  val isUpdateFailureDismissed: StateFlow<Boolean> = _isUpdateFailureDismissed.asStateFlow()

  init {
    checkForAppUpdate()
    initStartupBackupAndSync()
  }

  private fun initStartupBackupAndSync() {
    viewModelScope.launch {
      autoBackupSyncManager.checkAndRestoreOnStartup()
    }
  }

  fun triggerAutoBackup(reason: String = "manual") {
    viewModelScope.launch {
      autoBackupSyncManager.performAutoBackup(reason)
    }
  }

  fun regenerateDeviceId(): String {
    val newId = deviceIdentityManager.regenerateDeviceId()
    triggerAutoBackup("re-identified")
    return newId
  }

  fun checkForAppUpdate() {
    viewModelScope.launch {
      _appUpdateStatus.value = com.example.data.remote.AppUpdateStatus.Checking
      _isUpdateFailureDismissed.value = false
      val status = remoteConfigManager.checkAppVersion()
      _appUpdateStatus.value = status
    }
  }

  fun dismissUpdateFailure() {
    _isUpdateFailureDismissed.value = true
  }

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

  private val _smartReaderResult = MutableStateFlow<com.example.data.smartreader.SmartParsedReceipt?>(null)
  val smartReaderResult: StateFlow<com.example.data.smartreader.SmartParsedReceipt?> = _smartReaderResult.asStateFlow()

  private val _sharedImageUri = MutableStateFlow<Uri?>(null)
  val sharedImageUri: StateFlow<Uri?> = _sharedImageUri.asStateFlow()

  fun onSharedImageReceived(uri: Uri) {
    _sharedImageUri.value = uri
    processImageUri(uri)
  }

  fun clearSharedImage() {
    _sharedImageUri.value = null
  }

  fun processImageUri(uri: Uri) {
    viewModelScope.launch {
      _currentImageUri.value = uri
      _ocrState.value = OcrUiState.Processing("OBETSTRUK Smart Reader: Membaca teks on-device...")
      try {
        val ocrResult = withContext(Dispatchers.IO) {
          textRecognizer.processImageUri(uri)
        }

        if (ocrResult.isEmpty) {
          _smartReaderResult.value = null
          _ocrState.value = OcrUiState.Error("Tidak ada teks yang terdeteksi pada gambar. Pastikan gambar bukti transfer jelas, pencahayaan cukup, dan tidak buram.")
          return@launch
        }

        _ocrState.value = OcrUiState.Processing("Mendeteksi sumber transaksi & template khusus...")
        val templates = ocrTemplates.value

        // Execute Smart Reader Engine (Source detection + dedicated parser + cross validation)
        val smartParsed = withContext(Dispatchers.Default) {
          com.example.data.smartreader.SmartReaderEngine.process(ocrResult)
        }
        _smartReaderResult.value = smartParsed

        val parsed = withContext(Dispatchers.Default) {
          if (smartParsed.source != com.example.data.smartreader.TransactionSource.GENERIC) {
            smartParsed.toReceipt(receiptId = _currentReceipt.value.id).copy(ocrResult = ocrResult)
          } else {
            ReceiptParser.parseOcrResult(ocrResult, templates)
          }
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
        _smartReaderResult.value = null
        _ocrState.value = OcrUiState.Error(userFriendlyMessage)
      }
    }
  }

  fun switchSmartReaderSource(source: com.example.data.smartreader.TransactionSource) {
    val ocr = _currentReceipt.value.ocrResult ?: return
    viewModelScope.launch(Dispatchers.Default) {
      val reParsedSmart = com.example.data.smartreader.SmartReaderEngine.parseWithSource(ocr, source)
      _smartReaderResult.value = reParsedSmart

      val updatedReceipt = reParsedSmart.toReceipt(receiptId = _currentReceipt.value.id).copy(ocrResult = ocr)
      val autoFee = if (settings.value.autoApplyAdminFee && updatedReceipt.amount > 0L) {
        com.example.data.model.AdminFeeRule.calculateAdminFee(updatedReceipt.amount, settings.value.adminFeeRules)
      } else null
      val finalReceipt = if (autoFee != null) updatedReceipt.copy(adminFee = autoFee) else updatedReceipt

      _currentReceipt.value = finalReceipt
      if (_ocrState.value is OcrUiState.Success) {
        _ocrState.value = OcrUiState.Success(finalReceipt, _currentImageUri.value)
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
      autoBackupSyncManager.performAutoBackup("template_saved")
    }
  }

  fun deleteTemplate(template: OcrTemplate) {
    viewModelScope.launch(Dispatchers.IO) {
      templateDao.deleteTemplate(TemplateEntity.fromModel(template))
      autoBackupSyncManager.performAutoBackup("template_deleted")
    }
  }

  fun updateSettings(newSettings: PrintSettings) {
    settingsManager.updateSettings(newSettings)
    viewModelScope.launch(Dispatchers.IO) {
      autoBackupSyncManager.performAutoBackup("settings_updated")
    }
  }

  fun resetScanState() {
    _ocrState.value = OcrUiState.Idle
    _currentReceipt.value = Receipt()
    _currentImageUri.value = null
    _smartReaderResult.value = null
    _sharedImageUri.value = null
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

  suspend fun createBackupPayload(): com.example.data.backup.BackupPackage {
    return backupManager.createBackupPayload()
  }

  suspend fun createCacheBackupFile(payloadJson: String): java.io.File {
    return backupManager.createCacheBackupFile(payloadJson)
  }

  suspend fun writeBackupToDestination(uri: Uri, payloadJson: String) {
    backupManager.writeBackupToDestination(uri, payloadJson)
  }

  fun shareBackupFile(file: java.io.File) {
    backupManager.shareBackupFile(file)
  }

  suspend fun validateAndParseBackup(uri: Uri): Result<com.example.data.backup.BackupPackage> {
    return backupManager.validateAndParseBackup(uri)
  }

  suspend fun restoreFromBackup(backupPackage: com.example.data.backup.BackupPackage): Result<Int> {
    return backupManager.restoreFromBackup(backupPackage)
  }

  override fun onCleared() {
    super.onCleared()
    textRecognizer.close()
  }
}
