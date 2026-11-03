package com.example.data.backup

import com.example.data.model.AdminFeeRule
import com.example.data.model.AppTheme
import com.example.data.model.PrintSettings
import com.example.data.model.ReceiptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BackupManagerTest {

  @Test
  fun testBackupConstants() {
    assertEquals("JADISTRUK_BACKUP", BackupManager.FORMAT_NAME)
    assertEquals(1, BackupManager.BACKUP_VERSION)
    assertEquals(".jadistruk", BackupManager.FILE_EXTENSION)
  }

  @Test
  fun testBackupPackageModelIntegrity() {
    val settings = PrintSettings(
      storeName = "TOKO SERBA ADA",
      storeAddress = "Jl. Merdeka No. 45",
      storePhone = "08123456789",
      footerMessage = "Terima kasih banyak atas kunjungan Anda!",
      paperWidthMm = 80,
      showDate = true,
      showRefNumber = true,
      autoAddHeader = true,
      autoApplyAdminFee = true,
      adminFeeRules = listOf(
        AdminFeeRule(minNominal = 0, maxNominal = 100000, adminFee = 2000),
        AdminFeeRule(minNominal = 100001, maxNominal = 1000000, adminFee = 3500)
      ),
      selectedPrinterAddress = "AA:BB:CC:DD:EE:FF",
      selectedPrinterName = "POS-80",
      autoCutPaper = true,
      printFeedLines = 4,
      receiptTheme = ReceiptTheme.MODERN,
      appTheme = AppTheme.EMERALD_GREEN
    )

    val dummyImageBytes = "JADISTRUK_DUMMY_IMAGE_DATA".toByteArray(Charsets.UTF_8)
    val base64Img = Base64.getEncoder().encodeToString(dummyImageBytes)

    val templates = listOf(
      BackupTemplateItem(
        id = 1L,
        bankName = "Bank Mandiri Test",
        category = "Bank Transfer",
        detectionKeywords = "MANDIRI,BERHASIL",
        recipientPattern = "",
        accountPattern = "",
        amountPattern = "",
        isBuiltIn = false,
        areaConfigJson = "{\"areas\":[{\"name\":\"nominal\"}]}",
        sampleImageBase64 = base64Img
      ),
      BackupTemplateItem(
        id = 2L,
        bankName = "BCA Test",
        category = "Bank Transfer",
        detectionKeywords = "BCA,TRANSFER",
        recipientPattern = "",
        accountPattern = "",
        amountPattern = "",
        isBuiltIn = true,
        areaConfigJson = "{}",
        sampleImageBase64 = ""
      )
    )

    val metadata = BackupMetadata(
      format = BackupManager.FORMAT_NAME,
      version = BackupManager.BACKUP_VERSION,
      timestamp = 1730000000000L,
      formattedDate = "02 Nov 2026, 16:30",
      appVersion = "1.7",
      storeName = settings.storeName,
      templateCount = templates.size
    )

    val backupPackage = BackupPackage(
      metadata = metadata,
      settings = settings,
      templates = templates,
      rawJson = "{}"
    )

    assertEquals(BackupManager.FORMAT_NAME, backupPackage.metadata.format)
    assertEquals(1, backupPackage.metadata.version)
    assertEquals("TOKO SERBA ADA", backupPackage.metadata.storeName)
    assertEquals(2, backupPackage.metadata.templateCount)
    assertEquals(2, backupPackage.templates.size)

    assertEquals("Bank Mandiri Test", backupPackage.templates[0].bankName)
    assertEquals(base64Img, backupPackage.templates[0].sampleImageBase64)
    assertFalse(backupPackage.templates[0].isBuiltIn)

    assertEquals("BCA Test", backupPackage.templates[1].bankName)
    assertTrue(backupPackage.templates[1].isBuiltIn)
    assertEquals("", backupPackage.templates[1].sampleImageBase64)

    // Base64 decode validation
    val decoded = Base64.getDecoder().decode(backupPackage.templates[0].sampleImageBase64)
    assertEquals("JADISTRUK_DUMMY_IMAGE_DATA", String(decoded, Charsets.UTF_8))
  }
}
