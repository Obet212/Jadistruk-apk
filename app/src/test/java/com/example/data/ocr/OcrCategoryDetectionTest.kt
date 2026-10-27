package com.example.data.ocr

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrTextLine
import com.example.data.model.TargetRecipientField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OcrCategoryDetectionTest {

  @Test
  fun testBankRegistryMatching() {
    // 1. Official names and aliases
    val bcaMatch = BankRegistry.matchBank("BANK CENTRAL ASIA")
    assertNotNull(bcaMatch)
    assertEquals("BCA", bcaMatch.officialName)
    assertTrue(bcaMatch.isConfident)

    val mandiriMatch = BankRegistry.matchBank("BMRI LIVIN MANDIRI")
    assertNotNull(mandiriMatch)
    assertEquals("Bank Mandiri", mandiriMatch.officialName)

    val briMatch = BankRegistry.matchBank("PT BANK RAKYAT INDONESIA")
    assertNotNull(briMatch)
    assertEquals("Bank BRI", briMatch.officialName)

    val bsiMatch = BankRegistry.matchBank("BANK SYARIAH INDONESIA (BSI)")
    assertNotNull(bsiMatch)
    assertEquals("Bank BSI", bsiMatch.officialName)

    val bpdMatch = BankRegistry.matchBank("BANK JATENG")
    assertNotNull(bpdMatch)
    assertEquals("Bank Jateng", bpdMatch.officialName)

    val jagoMatch = BankRegistry.matchBank("BANK JAGO DIGITAL")
    assertNotNull(jagoMatch)
    assertEquals("Bank Jago", jagoMatch.officialName)

    val seabankMatch = BankRegistry.matchBank("SEABANK INDONESIA")
    assertNotNull(seabankMatch)
    assertEquals("SeaBank", seabankMatch.officialName)

    // Arbitrary text should NOT be matched as a bank
    val randomTextMatch = BankRegistry.matchBank("BANK MAKAN SIANG HARI INI")
    assertEquals(false, randomTextMatch.isConfident)

    val interfaceMatch = BankRegistry.matchBank("TRANSFER BERHASIL")
    assertEquals(false, interfaceMatch.isConfident)
  }

  @Test
  fun testRecipientValidation() {
    // Valid recipient names with business entity punctuation
    val (name1, err1) = FieldValidator.sanitizeRecipientName("PT. SUMBER MAKMUR JAYA & CO")
    assertEquals("PT. SUMBER MAKMUR JAYA & CO", name1)
    assertEquals(null, err1)

    val (name2, err2) = FieldValidator.sanitizeRecipientName("BUDI SANTOSO")
    assertEquals("BUDI SANTOSO", name2)
    assertEquals(null, err2)

    // Purely numeric or invalid should be rejected
    val (nameInvalid, errInvalid) = FieldValidator.sanitizeRecipientName("123456789")
    assertEquals("", nameInvalid)
    assertNotNull(errInvalid)

    // Sender labels should be filtered
    val (nameLabel, errLabel) = FieldValidator.sanitizeRecipientName("NAMA PENERIMA:")
    assertEquals("", nameLabel)
    assertNotNull(errLabel)
  }

  @Test
  fun testAccountNumberStrictValidation() {
    // 1. Exactly 10 to 15 digits
    val (acc10, err10) = FieldValidator.sanitizeAccount("0123456789")
    assertEquals("0123456789", acc10)
    assertEquals(null, err10)

    val (acc15, err15) = FieldValidator.sanitizeAccount("123456789012345")
    assertEquals("123456789012345", acc15)
    assertEquals(null, err15)

    // Spaces inside OCR should be cleaned safely
    val (accSpaced, errSpaced) = FieldValidator.sanitizeAccount("123 456 7890")
    assertEquals("1234567890", accSpaced)
    assertEquals(null, errSpaced)

    // Less than 10 digits must be rejected and cleared (not padded or guessed)
    val (accShort, errShort) = FieldValidator.sanitizeAccount("123456789")
    assertEquals("", accShort)
    assertNotNull(errShort)

    // More than 15 digits must NOT be cut to fit, but cleared
    val (accLong, errLong) = FieldValidator.sanitizeAccount("12345678901234567890")
    assertEquals("", accLong)
    assertNotNull(errLong)

    // Leading zero must be preserved
    assertTrue(acc10.startsWith("0"))
  }

  @Test
  fun testDateTimeValidation() {
    // Indonesian and standard formats
    val (dt1, err1) = FieldValidator.sanitizeDateTime("18/10/2026 14:32")
    assertEquals("18/10/2026 14:32", dt1)
    assertEquals(null, err1)

    val (dt2, err2) = FieldValidator.sanitizeDateTime("18-10-2026 14:32:10")
    assertEquals("18/10/2026 14:32:10", dt2)
    assertEquals(null, err2)

    val (dt3, err3) = FieldValidator.sanitizeDateTime("18 Okt 2026 14:32")
    assertEquals("18 Okt 2026 14:32", dt3)
    assertEquals(null, err3)

    // Invalid day/month or invalid time must be rejected, not replaced with current time
    val (dtInvalidDay, errInvalidDay) = FieldValidator.sanitizeDateTime("35/10/2026 14:32")
    assertEquals("", dtInvalidDay)
    assertNotNull(errInvalidDay)

    val (dtInvalidHour, errInvalidHour) = FieldValidator.sanitizeDateTime("18/10/2026 29:32")
    assertEquals("", dtInvalidHour)
    assertNotNull(errInvalidHour)
  }

  @Test
  fun testNominalRupiahValidation() {
    // Standard formats
    val (nom1, err1) = FieldValidator.parseNominal("Rp150.000")
    assertEquals(150000L, nom1)
    assertEquals(null, err1)

    val (nom2, err2) = FieldValidator.parseNominal("Rp 150,000")
    assertEquals(150000L, nom2)
    assertEquals(null, err2)

    val (nom3, err3) = FieldValidator.parseNominal("IDR 150,000.00")
    assertEquals(150000L, nom3)
    assertEquals(null, err3)

    val (nom4, err4) = FieldValidator.parseNominal("Rp150.000,00")
    assertEquals(150000L, nom4)
    assertEquals(null, err4)
  }

  @Test
  fun testSingleAreaMultipleCategoriesExtraction() {
    // Test requirement: Satu area dapat menghasilkan beberapa kategori sekaligus
    val dummyBox = OcrBoundingBox(0, 0, 100, 20, 0f, 0f, 1f, 1f)
    val lines = listOf(
      OcrTextLine("BANK BCA", dummyBox),
      OcrTextLine("NAMA PENERIMA", dummyBox),
      OcrTextLine("BUDI SANTOSO", dummyBox),
      OcrTextLine("NO REKENING", dummyBox),
      OcrTextLine("1234567890", dummyBox),
      OcrTextLine("TANGGAL", dummyBox),
      OcrTextLine("18/10/2026 14:32", dummyBox),
      OcrTextLine("NOMINAL", dummyBox),
      OcrTextLine("Rp150.000", dummyBox)
    )

    val extraction = MultiCategoryExtractor.extractFromLines(lines)

    // All categories must be extracted from the single area!
    assertEquals("BCA", extraction.bank?.value)
    assertEquals("BUDI SANTOSO", extraction.penerima?.value)
    assertEquals("1234567890", extraction.rekening?.value)
    assertEquals("18/10/2026 14:32", extraction.waktu?.value)
    assertEquals(150000L, extraction.nominal?.value)
  }

  @Test
  fun testMultipleAreasExtractionAndMerging() {
    val dummyBox = OcrBoundingBox(0, 0, 100, 20, 0f, 0f, 1f, 1f)
    // Area 1 has transaction details
    val area1Lines = listOf(
      OcrTextLine("BANK MANDIRI", dummyBox),
      OcrTextLine("PENERIMA: SITI AMINAH", dummyBox),
      OcrTextLine("REK: 1400012345678", dummyBox)
    )

    // Area 2 has amount and date
    val area2Lines = listOf(
      OcrTextLine("TANGGAL: 19/10/2026 10:15", dummyBox),
      OcrTextLine("JUMLAH Rp500.000", dummyBox)
    )

    val extraction1 = MultiCategoryExtractor.extractFromLines(area1Lines, designatedField = TargetRecipientField.PENERIMA)
    val extraction2 = MultiCategoryExtractor.extractFromLines(area2Lines, designatedField = TargetRecipientField.NOMINAL)

    val merged = MultiCategoryExtractor.mergeExtractions(listOf(extraction1, extraction2))

    assertEquals("Bank Mandiri", merged.bank?.value)
    assertEquals("SITI AMINAH", merged.penerima?.value)
    assertEquals("1400012345678", merged.rekening?.value)
    assertEquals("19/10/2026 10:15", merged.waktu?.value)
    assertEquals(500000L, merged.nominal?.value)
  }

  @Test
  fun testAdminFeeTierRules() {
    val defaultRules = com.example.data.model.AdminFeeRule.defaultRules()

    // 1. Nominal 10rb sampai 400rb -> admin 3rb
    assertEquals(3000L, com.example.data.model.AdminFeeRule.calculateAdminFee(10000L, defaultRules))
    assertEquals(3000L, com.example.data.model.AdminFeeRule.calculateAdminFee(150000L, defaultRules))
    assertEquals(3000L, com.example.data.model.AdminFeeRule.calculateAdminFee(400000L, defaultRules))

    // 2. Nominal 401rb sampai 600rb -> admin 4rb
    assertEquals(4000L, com.example.data.model.AdminFeeRule.calculateAdminFee(401000L, defaultRules))
    assertEquals(4000L, com.example.data.model.AdminFeeRule.calculateAdminFee(500000L, defaultRules))
    assertEquals(4000L, com.example.data.model.AdminFeeRule.calculateAdminFee(600000L, defaultRules))

    // 3. Di luar rentang default -> null
    assertEquals(null, com.example.data.model.AdminFeeRule.calculateAdminFee(5000L, defaultRules))
    assertEquals(null, com.example.data.model.AdminFeeRule.calculateAdminFee(800000L, defaultRules))

    // 4. Pengujian tambah aturan baru (dapat ditambahkan)
    val customRules = defaultRules + com.example.data.model.AdminFeeRule(
      minNominal = 601000L,
      maxNominal = 1000000L,
      adminFee = 5000L
    )
    assertEquals(5000L, com.example.data.model.AdminFeeRule.calculateAdminFee(750000L, customRules))

    // 5. Pengujian ubah aturan (dapat diubah)
    val modifiedRules = customRules.map {
      if (it.minNominal == 10000L) it.copy(adminFee = 3500L) else it
    }
    assertEquals(3500L, com.example.data.model.AdminFeeRule.calculateAdminFee(10000L, modifiedRules))

    // 6. Pengujian hapus aturan (dapat dihapus)
    val deletedRules = modifiedRules.filter { it.minNominal != 10000L }
    assertEquals(null, com.example.data.model.AdminFeeRule.calculateAdminFee(10000L, deletedRules))

    // 7. Pengujian serialisasi JSON
    val json = com.example.data.model.AdminFeeRule.rulesToJson(defaultRules)
    val restored = com.example.data.model.AdminFeeRule.rulesFromJson(json)
    assertEquals(defaultRules.size, restored.size)
    assertEquals(defaultRules[0].adminFee, restored[0].adminFee)
    assertEquals(defaultRules[1].adminFee, restored[1].adminFee)
  }
}
