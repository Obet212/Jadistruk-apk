package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.local.SettingsManager
import com.example.data.local.TemplateDao
import com.example.data.local.TemplateEntity
import com.example.data.model.AdminFeeRule
import com.example.data.model.AppTheme
import com.example.data.model.PrintSettings
import com.example.data.model.ReceiptTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Metadata informasi file backup JadiStruk.
 */
data class BackupMetadata(
  val format: String = BackupManager.FORMAT_NAME,
  val version: Int = BackupManager.BACKUP_VERSION,
  val timestamp: Long = System.currentTimeMillis(),
  val formattedDate: String = "",
  val appVersion: String = BuildConfig.VERSION_NAME,
  val storeName: String = "",
  val templateCount: Int = 0
)

/**
 * Data item template di dalam paket backup lengkap dengan gambar yang di-encode Base64.
 */
data class BackupTemplateItem(
  val id: Long = 0L,
  val bankName: String,
  val category: String,
  val detectionKeywords: String,
  val recipientPattern: String,
  val accountPattern: String,
  val amountPattern: String,
  val isBuiltIn: Boolean,
  val areaConfigJson: String,
  val sampleImageBase64: String = ""
)

/**
 * Paket data lengkap backup JadiStruk.
 */
data class BackupPackage(
  val metadata: BackupMetadata,
  val settings: PrintSettings,
  val templates: List<BackupTemplateItem>,
  val rawJson: String = ""
)

/**
 * Pengelola pembuatan, penyimpanan, pembagian, validasi, dan pemulihan data backup (.jadistruk).
 * 100% Offline, aman, dan kompatibel lintas perangkat.
 */
class BackupManager(
  private val context: Context,
  private val settingsManager: SettingsManager,
  private val templateDao: TemplateDao
) {

  companion object {
    private const val TAG = "BackupManager"
    const val FORMAT_NAME = "JADISTRUK_BACKUP"
    const val BACKUP_VERSION = 1
    const val FILE_EXTENSION = ".jadistruk"
  }

  /**
   * Mengumpulkan seluruh pengaturan dan template (termasuk gambar) menjadi payload JSON terstruktur.
   */
  suspend fun createBackupPayload(): BackupPackage = withContext(Dispatchers.IO) {
    val currentSettings = settingsManager.settings.value
    val templatesList = templateDao.getAllTemplatesList()

    val backupTemplates = templatesList.map { entity ->
      var imageBase64 = ""
      if (entity.sampleImageUri.isNotBlank()) {
        try {
          val uri = Uri.parse(entity.sampleImageUri)
          val bytes = if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) file.readBytes() else null
          } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          }
          if (bytes != null && bytes.isNotEmpty()) {
            imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
          }
        } catch (e: Exception) {
          Log.e(TAG, "Gagal membaca gambar template '${entity.bankName}': ${e.message}")
        }
      }

      BackupTemplateItem(
        id = entity.id,
        bankName = entity.bankName,
        category = entity.category,
        detectionKeywords = entity.detectionKeywords,
        recipientPattern = entity.recipientPattern,
        accountPattern = entity.accountPattern,
        amountPattern = entity.amountPattern,
        isBuiltIn = entity.isBuiltIn,
        areaConfigJson = entity.areaConfigJson,
        sampleImageBase64 = imageBase64
      )
    }

    val now = System.currentTimeMillis()
    val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(now))

    val root = JSONObject().apply {
      put("format", FORMAT_NAME)
      put("version", BACKUP_VERSION)
      put("timestamp", now)
      put("formattedDate", formattedDate)
      put("appVersion", BuildConfig.VERSION_NAME)
      put("storeName", currentSettings.storeName)
      put("templateCount", backupTemplates.size)

      // 1. Data Pengaturan & Identitas Toko
      val settingsJson = JSONObject().apply {
        put("storeName", currentSettings.storeName)
        put("storeAddress", currentSettings.storeAddress)
        put("storePhone", currentSettings.storePhone)
        put("footerMessage", currentSettings.footerMessage)
        put("paperWidthMm", currentSettings.paperWidthMm)
        put("showDate", currentSettings.showDate)
        put("showRefNumber", currentSettings.showRefNumber)
        put("autoAddHeader", currentSettings.autoAddHeader)
        put("autoApplyAdminFee", currentSettings.autoApplyAdminFee)
        put("adminFeeRulesJson", AdminFeeRule.rulesToJson(currentSettings.adminFeeRules))
        put("selectedPrinterAddress", currentSettings.selectedPrinterAddress)
        put("selectedPrinterName", currentSettings.selectedPrinterName)
        put("autoCutPaper", currentSettings.autoCutPaper)
        put("printFeedLines", currentSettings.printFeedLines)
        put("receiptThemeId", currentSettings.receiptTheme.id)
        put("appThemeId", currentSettings.appTheme.id)
      }
      put("settings", settingsJson)

      // 2. Data Seluruh Template & Gambar
      val templatesArray = JSONArray()
      backupTemplates.forEach { t ->
        val tJson = JSONObject().apply {
          put("id", t.id)
          put("bankName", t.bankName)
          put("category", t.category)
          put("detectionKeywords", t.detectionKeywords)
          put("recipientPattern", t.recipientPattern)
          put("accountPattern", t.accountPattern)
          put("amountPattern", t.amountPattern)
          put("isBuiltIn", t.isBuiltIn)
          put("areaConfigJson", t.areaConfigJson)
          put("sampleImageBase64", t.sampleImageBase64)
        }
        templatesArray.put(tJson)
      }
      put("templates", templatesArray)
    }

    val rawJson = root.toString(2)
    val metadata = BackupMetadata(
      format = FORMAT_NAME,
      version = BACKUP_VERSION,
      timestamp = now,
      formattedDate = formattedDate,
      appVersion = BuildConfig.VERSION_NAME,
      storeName = currentSettings.storeName,
      templateCount = backupTemplates.size
    )

    BackupPackage(
      metadata = metadata,
      settings = currentSettings,
      templates = backupTemplates,
      rawJson = rawJson
    )
  }

  /**
   * Membuat file lokal ".jadistruk" sementara di cache untuk dibagikan melalui Share Sheet.
   */
  suspend fun createCacheBackupFile(payloadJson: String): File = withContext(Dispatchers.IO) {
    val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }

    // Hapus file backup lama di folder cache agar hemat ruang
    backupDir.listFiles()?.forEach { oldFile ->
      if (oldFile.name.endsWith(FILE_EXTENSION) && oldFile.lastModified() < System.currentTimeMillis() - 86400000L) {
        oldFile.delete()
      }
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val backupFile = File(backupDir, "backup_jadistruk_$timestamp$FILE_EXTENSION")
    backupFile.writeText(payloadJson, Charsets.UTF_8)
    backupFile
  }

  /**
   * Menulis isi backup ke Uri tujuan yang dipilih pengguna melalui Storage Access Framework (SAF).
   */
  suspend fun writeBackupToDestination(destinationUri: Uri, payloadJson: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
      output.write(payloadJson.toByteArray(Charsets.UTF_8))
      output.flush()
    }
  }

  /**
   * Membuka Android Share Sheet untuk berbagi file ".jadistruk" ke WhatsApp, Bluetooth, Telegram, dll.
   */
  fun shareBackupFile(backupFile: File) {
    try {
      val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        backupFile
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        putExtra(Intent.EXTRA_SUBJECT, "Backup JadiStruk - ${backupFile.name}")
        putExtra(Intent.EXTRA_TEXT, "File backup konfigurasi dan template aplikasi JadiStruk.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(shareIntent, "Bagikan File Backup JadiStruk").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
    } catch (e: Exception) {
      Log.e(TAG, "Gagal membagikan file backup: ${e.message}")
    }
  }

  /**
   * Mengurai string JSON mentah menjadi BackupPackage terstruktur.
   */
  fun parseJsonToPackage(content: String): BackupPackage {
    val root = JSONObject(content)
    val format = root.optString("format", "")
    val version = root.optInt("version", 1)
    val settingsObj = root.optJSONObject("settings") ?: JSONObject()
    val templatesArray = root.optJSONArray("templates") ?: JSONArray()

    val rulesJson = settingsObj.optString("adminFeeRulesJson", "")
    val adminRules = if (rulesJson.isNotBlank()) {
      AdminFeeRule.rulesFromJson(rulesJson)
    } else {
      AdminFeeRule.defaultRules()
    }

    val restoredSettings = PrintSettings(
      storeName = settingsObj.optString("storeName", "JADISTRUK POS"),
      storeAddress = settingsObj.optString("storeAddress", ""),
      storePhone = settingsObj.optString("storePhone", ""),
      footerMessage = settingsObj.optString("footerMessage", "Terima kasih! Bukti transfer telah diverifikasi."),
      paperWidthMm = settingsObj.optInt("paperWidthMm", 58),
      showDate = settingsObj.optBoolean("showDate", true),
      showRefNumber = settingsObj.optBoolean("showRefNumber", false),
      autoAddHeader = settingsObj.optBoolean("autoAddHeader", true),
      autoApplyAdminFee = settingsObj.optBoolean("autoApplyAdminFee", true),
      adminFeeRules = adminRules,
      selectedPrinterAddress = settingsObj.optString("selectedPrinterAddress", ""),
      selectedPrinterName = settingsObj.optString("selectedPrinterName", ""),
      autoCutPaper = settingsObj.optBoolean("autoCutPaper", true),
      printFeedLines = settingsObj.optInt("printFeedLines", 3),
      receiptTheme = ReceiptTheme.fromId(settingsObj.optString("receiptThemeId", "profesional")),
      appTheme = AppTheme.fromId(settingsObj.optString("appThemeId", "ocean_blue"))
    )

    val templateItems = mutableListOf<BackupTemplateItem>()
    for (i in 0 until templatesArray.length()) {
      val tObj = templatesArray.getJSONObject(i)
      templateItems.add(
        BackupTemplateItem(
          id = tObj.optLong("id", 0L),
          bankName = tObj.optString("bankName", "Template ${i + 1}"),
          category = tObj.optString("category", "Bank Transfer"),
          detectionKeywords = tObj.optString("detectionKeywords", ""),
          recipientPattern = tObj.optString("recipientPattern", ""),
          accountPattern = tObj.optString("accountPattern", ""),
          amountPattern = tObj.optString("amountPattern", ""),
          isBuiltIn = tObj.optBoolean("isBuiltIn", false),
          areaConfigJson = tObj.optString("areaConfigJson", ""),
          sampleImageBase64 = tObj.optString("sampleImageBase64", "")
        )
      )
    }

    val timestamp = root.optLong("timestamp", System.currentTimeMillis())
    val formattedDate = root.optString(
      "formattedDate",
      SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    )
    val appVersion = root.optString("appVersion", "2.2")

    val metadata = BackupMetadata(
      format = format,
      version = version,
      timestamp = timestamp,
      formattedDate = formattedDate,
      appVersion = appVersion,
      storeName = restoredSettings.storeName,
      templateCount = templateItems.size
    )

    return BackupPackage(
      metadata = metadata,
      settings = restoredSettings,
      templates = templateItems,
      rawJson = content
    )
  }

  /**
   * Membaca dan memvalidasi file ".jadistruk" yang dipilih pengguna sebelum proses pemulihan.
   * Menolak jika file rusak, tidak lengkap, atau bukan format resmi JadiStruk.
   */
  suspend fun validateAndParseBackup(sourceUri: Uri): Result<BackupPackage> = withContext(Dispatchers.IO) {
    try {
      val content = context.contentResolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)?.use {
        it.readText()
      } ?: return@withContext Result.failure(Exception("File backup kosong atau tidak dapat diakses."))

      if (content.isBlank()) {
        return@withContext Result.failure(Exception("File backup tidak memiliki isi (kosong)."))
      }

      val root = try {
        JSONObject(content)
      } catch (e: Exception) {
        return@withContext Result.failure(Exception("Format file rusak atau bukan struktur JSON yang sah."))
      }

      val format = root.optString("format", "")
      if (format != FORMAT_NAME) {
        return@withContext Result.failure(Exception("File ini bukan file backup resmi JadiStruk (format: $format)."))
      }

      val version = root.optInt("version", 0)
      if (version <= 0) {
        return@withContext Result.failure(Exception("Versi format backup tidak valid ($version)."))
      }

      Result.success(parseJsonToPackage(content))
    } catch (e: Exception) {
      Log.e(TAG, "Gagal memvalidasi backup: ${e.message}", e)
      Result.failure(Exception("Gagal membaca file: ${e.localizedMessage ?: e.message}"))
    }
  }

  /**
   * Menjalankan pemulihan penuh dari paket backup yang telah divalidasi.
   * - Menulis ulang file gambar template ke internal storage.
   * - Memperbarui tabel database template secara atomik.
   * - Mengembalikan pengaturan identitas toko, tema, dan preferensi aplikasi.
   */
  suspend fun restoreFromBackup(backupPackage: BackupPackage): Result<Int> = withContext(Dispatchers.IO) {
    try {
      val imageDir = File(context.filesDir, "template_images").apply { mkdirs() }

      val entitiesToInsert = backupPackage.templates.mapIndexed { index, item ->
        var restoredImageUri = ""
        if (item.sampleImageBase64.isNotBlank()) {
          try {
            val imageBytes = Base64.decode(item.sampleImageBase64, Base64.DEFAULT)
            if (imageBytes != null && imageBytes.isNotEmpty()) {
              val destFile = File(
                imageDir,
                "tpl_restored_${System.currentTimeMillis()}_${index}_${UUID.randomUUID().toString().take(6)}.jpg"
              )
              FileOutputStream(destFile).use { fos ->
                fos.write(imageBytes)
                fos.flush()
              }
              restoredImageUri = Uri.fromFile(destFile).toString()
            }
          } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan file gambar template '${item.bankName}': ${e.message}")
          }
        }

        TemplateEntity(
          id = 0L, // Primary key otomatis di database baru
          bankName = item.bankName,
          category = item.category,
          detectionKeywords = item.detectionKeywords,
          recipientPattern = item.recipientPattern,
          accountPattern = item.accountPattern,
          amountPattern = item.amountPattern,
          isBuiltIn = item.isBuiltIn,
          areaConfigJson = item.areaConfigJson,
          sampleImageUri = restoredImageUri
        )
      }

      // 1. Perbarui tabel database template secara menyeluruh
      templateDao.deleteAllTemplates()
      if (entitiesToInsert.isNotEmpty()) {
        templateDao.insertOrReplaceTemplates(entitiesToInsert)
      }

      // 2. Terapkan seluruh pengaturan toko, tema, dan preferensi
      settingsManager.updateSettings(backupPackage.settings)

      Result.success(entitiesToInsert.size)
    } catch (e: Exception) {
      Log.e(TAG, "Gagal mengeksekusi pemulihan data: ${e.message}", e)
      Result.failure(Exception("Pemulihan data gagal: ${e.localizedMessage ?: e.message}"))
    }
  }
}
