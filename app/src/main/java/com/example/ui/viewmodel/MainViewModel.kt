package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ColumnConfig
import com.example.data.model.ColumnFunction
import com.example.data.model.ColumnType
import com.example.data.model.ReceiptHistory
import com.example.data.model.SourceApp
import com.example.data.model.Template
import com.example.data.model.TextAlignment
import com.example.data.repository.ReceiptRepository
import com.example.engine.EscPosCharacterNormalizer
import com.example.engine.OcrExtractionResult
import com.example.engine.ReceiptOcrEngine
import com.example.engine.ThermalPrintHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReceiptRepository

    val allSourceApps: StateFlow<List<SourceApp>>
    val allTemplates: StateFlow<List<Template>>
    val allHistory: StateFlow<List<ReceiptHistory>>

    // Active Processing State
    private val _activeReceiptBitmap = MutableStateFlow<Bitmap?>(null)
    val activeReceiptBitmap: StateFlow<Bitmap?> = _activeReceiptBitmap.asStateFlow()

    private val _activeImageUri = MutableStateFlow<Uri?>(null)
    val activeImageUri: StateFlow<Uri?> = _activeImageUri.asStateFlow()

    private val _activeSourceApp = MutableStateFlow<SourceApp?>(null)
    val activeSourceApp: StateFlow<SourceApp?> = _activeSourceApp.asStateFlow()

    private val _activeTemplate = MutableStateFlow<Template?>(null)
    val activeTemplate: StateFlow<Template?> = _activeTemplate.asStateFlow()

    private val _activeColumns = MutableStateFlow<List<ColumnConfig>>(emptyList())
    val activeColumns: StateFlow<List<ColumnConfig>> = _activeColumns.asStateFlow()

    private val _activeColumnValues = MutableStateFlow<Map<Long, String>>(emptyMap())
    val activeColumnValues: StateFlow<Map<Long, String>> = _activeColumnValues.asStateFlow()

    private val _activeOcrResult = MutableStateFlow<OcrExtractionResult?>(null)
    val activeOcrResult: StateFlow<OcrExtractionResult?> = _activeOcrResult.asStateFlow()

    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr.asStateFlow()

    private val _selectedCalibrationColumn = MutableStateFlow<ColumnConfig?>(null)
    val selectedCalibrationColumn: StateFlow<ColumnConfig?> = _selectedCalibrationColumn.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pairedPrinters = MutableStateFlow<List<ThermalPrintHelper.BluetoothPrinter>>(emptyList())
    val pairedPrinters: StateFlow<List<ThermalPrintHelper.BluetoothPrinter>> = _pairedPrinters.asStateFlow()

    private val _selectedPrinter = MutableStateFlow<ThermalPrintHelper.BluetoothPrinter?>(null)
    val selectedPrinter: StateFlow<ThermalPrintHelper.BluetoothPrinter?> = _selectedPrinter.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = ReceiptRepository(
            database.sourceAppDao(),
            database.templateDao(),
            database.columnConfigDao(),
            database.receiptHistoryDao()
        )

        allSourceApps = repository.allSourceApps.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allTemplates = repository.allTemplates.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allHistory = repository.allHistory.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        refreshBluetoothPrinters()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshBluetoothPrinters() {
        _pairedPrinters.value = ThermalPrintHelper.getPairedBluetoothPrinters(getApplication())
    }

    fun selectPrinter(printer: ThermalPrintHelper.BluetoothPrinter) {
        _selectedPrinter.value = printer
    }

    /**
     * Primary entry point when receiving an image via Android Share Intent or Gallery Picker.
     */
    fun processIncomingImage(uri: Uri, callingPackage: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingOcr.value = true
            _activeImageUri.value = uri

            try {
                val context: Context = getApplication()
                val bitmap = ReceiptOcrEngine.loadBitmapFromUri(context, uri)
                if (bitmap == null) {
                    _statusMessage.value = "Gagal memuat gambar dari share intent."
                    _isProcessingOcr.value = false
                    return@launch
                }
                _activeReceiptBitmap.value = bitmap

                // 1. Identify source app by package name if present
                val sourceApps = repository.allSourceApps.firstOrNull() ?: emptyList()
                var matchedApp: SourceApp? = null

                if (!callingPackage.isNullOrBlank()) {
                    matchedApp = sourceApps.firstOrNull { it.packageName.equals(callingPackage, ignoreCase = true) }
                }

                // If no package name match, look for default/generic app or the first available
                if (matchedApp == null) {
                    matchedApp = sourceApps.firstOrNull { it.packageName == "com.cetakin.generic" }
                        ?: sourceApps.firstOrNull()
                }

                if (matchedApp != null) {
                    loadTemplateAndExtract(matchedApp, bitmap)
                } else {
                    _statusMessage.value = "Belum ada template terdaftar. Silakan buat template."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "Terjadi kesalahan: ${e.localizedMessage}"
            } finally {
                _isProcessingOcr.value = false
            }
        }
    }

    fun switchActiveSourceApp(sourceApp: SourceApp) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = _activeReceiptBitmap.value
            if (bitmap != null) {
                _isProcessingOcr.value = true
                loadTemplateAndExtract(sourceApp, bitmap)
                _isProcessingOcr.value = false
            } else {
                val template = repository.getTemplateForSourceAppSync(sourceApp.id)
                _activeSourceApp.value = sourceApp
                _activeTemplate.value = template
                if (template != null) {
                    val cols = repository.getColumnsForTemplateSync(template.id)
                    _activeColumns.value = cols
                }
            }
        }
    }

    private suspend fun loadTemplateAndExtract(sourceApp: SourceApp, bitmap: Bitmap) {
        _activeSourceApp.value = sourceApp
        val template = repository.getTemplateForSourceAppSync(sourceApp.id)
            ?: createDefaultTemplateForApp(sourceApp)

        _activeTemplate.value = template
        val columns = repository.getColumnsForTemplateSync(template.id)
        _activeColumns.value = columns

        // Run ML Kit OCR
        val ocrResult = ReceiptOcrEngine.processImage(bitmap, columns)
        _activeOcrResult.value = ocrResult
        _activeColumnValues.value = ocrResult.columnValues

        if (columns.isNotEmpty()) {
            _selectedCalibrationColumn.value = columns.firstOrNull { it.isAuto } ?: columns.first()
        }

        _statusMessage.value = "Berhasil mengekstrak struk untuk ${sourceApp.appName}"
    }

    private suspend fun createDefaultTemplateForApp(sourceApp: SourceApp): Template {
        val newTemplate = Template(
            sourceAppId = sourceApp.id,
            templateName = "Template ${sourceApp.appName}",
            headerTitle = "BUKTI TRANSAKSI ${sourceApp.appName.uppercase()}",
            headerSubtitle = "STRUK PEMBAYARAN RESMI"
        )
        val templateId = repository.insertTemplate(newTemplate)
        val defaultCols = listOf(
            ColumnConfig(templateId = templateId, name = "Tanggal", columnType = ColumnType.AUTO.name, orderIndex = 1, columnFunction = ColumnFunction.DATE.name),
            ColumnConfig(templateId = templateId, name = "Waktu", columnType = ColumnType.AUTO.name, orderIndex = 2, columnFunction = ColumnFunction.TIME.name),
            ColumnConfig(templateId = templateId, name = "No. Ref", columnType = ColumnType.AUTO.name, orderIndex = 3, columnFunction = ColumnFunction.REFERENCE.name),
            ColumnConfig(templateId = templateId, name = "Nominal", columnType = ColumnType.AUTO.name, orderIndex = 4, columnFunction = ColumnFunction.NOMINAL.name),
            ColumnConfig(templateId = templateId, name = "Status", columnType = ColumnType.MANUAL.name, orderIndex = 5, manualDefaultText = "BERHASIL", alignment = TextAlignment.LEFT.name)
        )
        repository.insertAllColumns(defaultCols)
        return newTemplate.copy(id = templateId)
    }

    /**
     * In-place tap-to-edit update of a column value.
     */
    fun updateColumnValue(columnId: Long, newValue: String) {
        val current = _activeColumnValues.value.toMutableMap()
        current[columnId] = EscPosCharacterNormalizer.normalizeText(newValue)
        _activeColumnValues.value = current
    }

    /**
     * Select column to calibrate on canvas.
     */
    fun selectColumnForCalibration(col: ColumnConfig) {
        _selectedCalibrationColumn.value = col
    }

    /**
     * Updates coordinates from calibration and saves permanently in Room.
     */
    fun saveCalibrationBox(normX: Float, normY: Float, normW: Float, normH: Float) {
        val col = _selectedCalibrationColumn.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updatedCol = col.copy(
                normalizedX = normX,
                normalizedY = normY,
                normalizedWidth = normW,
                normalizedHeight = normH
            )
            repository.updateColumn(updatedCol)
            _selectedCalibrationColumn.value = updatedCol

            // Re-read columns for template
            val template = _activeTemplate.value
            if (template != null) {
                val cols = repository.getColumnsForTemplateSync(template.id)
                _activeColumns.value = cols

                // Re-extract value for this column if bitmap and OCR detected items exist
                val ocrResult = _activeOcrResult.value
                val bitmap = _activeReceiptBitmap.value
                if (ocrResult != null && bitmap != null) {
                    val newOcrResult = ReceiptOcrEngine.processImage(bitmap, cols)
                    _activeOcrResult.value = newOcrResult
                    _activeColumnValues.value = newOcrResult.columnValues
                }
            }
            _statusMessage.value = "Koordinat ${col.name} berhasil disimpan."
        }
    }

    /**
     * Saves the current active receipt into History.
     */
    fun saveCurrentReceiptToHistory(onSuccess: (Long) -> Unit) {
        val template = _activeTemplate.value ?: return
        val sourceApp = _activeSourceApp.value
        val columnValues = _activeColumnValues.value

        viewModelScope.launch(Dispatchers.IO) {
            val json = JSONObject()
            for ((key, value) in columnValues) {
                json.put(key.toString(), value)
            }

            val history = ReceiptHistory(
                sourceAppName = sourceApp?.appName ?: "Struk",
                templateName = template.templateName,
                imagePath = _activeImageUri.value?.toString(),
                extractedDataJson = json.toString(),
                rawOcrText = _activeOcrResult.value?.rawFullText ?: ""
            )

            val id = repository.insertHistory(history)
            _statusMessage.value = "Struk berhasil disimpan ke Riwayat."
            onSuccess(id)
        }
    }

    fun loadReceiptFromHistory(history: ReceiptHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(history.extractedDataJson)
                val map = mutableMapOf<Long, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key.toLong()] = json.getString(key)
                }

                // Find matching template or default
                val templates = repository.allTemplates.firstOrNull() ?: emptyList()
                val matchedTemplate = templates.firstOrNull { it.templateName == history.templateName }
                    ?: templates.firstOrNull()

                if (matchedTemplate != null) {
                    val cols = repository.getColumnsForTemplateSync(matchedTemplate.id)
                    _activeTemplate.value = matchedTemplate
                    _activeColumns.value = cols
                    _activeColumnValues.value = map

                    val sourceApps = repository.allSourceApps.firstOrNull() ?: emptyList()
                    _activeSourceApp.value = sourceApps.firstOrNull { it.id == matchedTemplate.sourceAppId }

                    // Try to restore bitmap if uri exists
                    if (!history.imagePath.isNullOrBlank()) {
                        try {
                            val uri = Uri.parse(history.imagePath)
                            _activeImageUri.value = uri
                            _activeReceiptBitmap.value = ReceiptOcrEngine.loadBitmapFromUri(getApplication(), uri)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteHistory(history: ReceiptHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHistory(history)
            _statusMessage.value = "Riwayat dihapus."
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _statusMessage.value = "Semua riwayat dibersihkan."
        }
    }

    // Template & Column Management
    fun updateTemplateSettings(template: Template) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTemplate(template)
            _activeTemplate.value = template
            _statusMessage.value = "Pengaturan template diperbarui."
        }
    }

    fun addColumnToTemplate(templateId: Long, column: ColumnConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertColumn(column.copy(templateId = templateId))
            val updated = repository.getColumnsForTemplateSync(templateId)
            _activeColumns.value = updated
            _statusMessage.value = "Kolom '${column.name}' berhasil ditambahkan."
        }
    }

    fun updateColumnConfig(column: ColumnConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateColumn(column)
            val updated = repository.getColumnsForTemplateSync(column.templateId)
            _activeColumns.value = updated
            _statusMessage.value = "Kolom '${column.name}' diperbarui."
        }
    }

    fun deleteColumnConfig(column: ColumnConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteColumn(column)
            val updated = repository.getColumnsForTemplateSync(column.templateId)
            _activeColumns.value = updated
            _statusMessage.value = "Kolom '${column.name}' dihapus."
        }
    }

    fun moveColumnOrder(column: ColumnConfig, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getColumnsForTemplateSync(column.templateId).sortedBy { it.orderIndex }
            val index = list.indexOfFirst { it.id == column.id }
            if (index == -1) return@launch

            val targetIndex = if (moveUp) index - 1 else index + 1
            if (targetIndex in list.indices) {
                val currentItem = list[index]
                val targetItem = list[targetIndex]

                repository.updateColumn(currentItem.copy(orderIndex = targetItem.orderIndex))
                repository.updateColumn(targetItem.copy(orderIndex = currentItem.orderIndex))

                val updated = repository.getColumnsForTemplateSync(column.templateId)
                _activeColumns.value = updated
            }
        }
    }

    fun toggleColumnEnabled(column: ColumnConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateColumn(column.copy(isEnabled = !column.isEnabled))
            val updated = repository.getColumnsForTemplateSync(column.templateId)
            _activeColumns.value = updated
        }
    }

    // Source App Management
    fun addNewSourceApp(packageName: String, appName: String, description: String, iconColorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = SourceApp(
                packageName = packageName.trim(),
                appName = appName.trim(),
                description = description.trim(),
                iconColorHex = iconColorHex
            )
            val appId = repository.insertSourceApp(app)
            val newTemplate = createDefaultTemplateForApp(app.copy(id = appId))
            _statusMessage.value = "Aplikasi sumber '${app.appName}' & template berhasil dibuat."
        }
    }

    fun deleteSourceApp(app: SourceApp) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSourceApp(app)
            _statusMessage.value = "Aplikasi sumber '${app.appName}' dihapus."
        }
    }

    // Printing actions
    fun printReceipt(context: Context, viaBluetooth: Boolean = false) {
        val template = _activeTemplate.value ?: return
        val columns = _activeColumns.value
        val values = _activeColumnValues.value

        if (viaBluetooth) {
            val printer = _selectedPrinter.value
            if (printer == null) {
                _statusMessage.value = "Pilih printer Bluetooth terlebih dahulu di Pengaturan."
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                val bytes = ThermalPrintHelper.generateEscPosBytes(template, columns, values)
                val result = ThermalPrintHelper.printToBluetooth(printer.address, bytes)
                if (result.isSuccess) {
                    _statusMessage.value = "Berhasil mencetak ke ${printer.name}"
                } else {
                    _statusMessage.value = "Gagal mencetak: ${result.exceptionOrNull()?.localizedMessage}"
                }
            }
        } else {
            // Android System Print (PDF / Wi-Fi / Any)
            ThermalPrintHelper.printViaSystemPrint(context, template, columns, values)
        }
    }

    fun shareReceiptAsImage(context: Context) {
        val template = _activeTemplate.value ?: return
        val columns = _activeColumns.value
        val values = _activeColumnValues.value
        val bitmap = ThermalPrintHelper.renderReceiptBitmap(template, columns, values)
        ThermalPrintHelper.shareReceiptImage(context, bitmap)
    }
}
