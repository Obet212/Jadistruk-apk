package com.example.data.ocr

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrTextLine
import com.example.data.model.TargetRecipientField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCategoryDetectionTest {

  @Test
  fun testBankRegistryMatching() {
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

    val jagoMatch = BankRegistry.matchBank("BANK JAGO DIGITAL")
    assertNotNull(jagoMatch)
    assertEquals("Bank Jago", jagoMatch.officialName)

    val seabankMatch = BankRegistry.matchBank("SEABANK INDONESIA")
    assertNotNull(seabankMatch)
    assertEquals("SeaBank", seabankMatch.officialName)

    val randomTextMatch = BankRegistry.matchBank("BANK MAKAN SIANG HARI INI")
    assertFalse(randomTextMatch.isConfident)

    val interfaceMatch = BankRegistry.matchBank("TRANSFER BERHASIL")
    assertFalse(interfaceMatch.isConfident)
  }

  @Test
  fun testDateCalendarValidation() {
    // 1. Format DD/MM/YYYY
    val (d1, err1) = FieldValidator.sanitizeDate("28/10/2026")
    assertEquals("28/10/2026", d1)
    assertNull(err1)

    // 2. Format DD-MM-YYYY
    val (d2, err2) = FieldValidator.sanitizeDate("Tanggal: 28-10-2026")
    assertEquals("28/10/2026", d2)
    assertNull(err2)

    // 3. Format DD.MM.YYYY
    val (d3, err3) = FieldValidator.sanitizeDate("Tanggal Transaksi: 28.10.2026")
    assertEquals("28/10/2026", d3)
    assertNull(err3)

    // 4. Format YYYY-MM-DD (ISO) dikonversi ke standar DD/MM/YYYY
    val (d4, err4) = FieldValidator.sanitizeDate("2026-10-28")
    assertEquals("28/10/2026", d4)
    assertNull(err4)

    // 5. Format DD MMM YYYY
    val (d5, err5) = FieldValidator.sanitizeDate("28 Okt 2026")
    assertEquals("28/10/2026", d5)
    assertNull(err5)

    // 6. Validasi Kalender: 31 April tidak ada (April hanya 30 hari)
    val (dInvalidApr, errInvalidApr) = FieldValidator.sanitizeDate("31/04/2026")
    assertEquals("", dInvalidApr)
    assertNotNull(errInvalidApr)

    // 7. Validasi Kabisat: 29 Februari 2024 (Kabisat = Valid), 29 Februari 2023 (Bukan Kabisat = Tidak Valid)
    val (dLeapValid, errLeapValid) = FieldValidator.sanitizeDate("29/02/2024")
    assertEquals("29/02/2024", dLeapValid)
    assertNull(errLeapValid)

    val (dLeapInvalid, errLeapInvalid) = FieldValidator.sanitizeDate("29/02/2023")
    assertEquals("", dLeapInvalid)
    assertNotNull(errLeapInvalid)

    // 8. Penolakan Tanggal Lahir & Kedaluwarsa
    val (dBirth, errBirth) = FieldValidator.sanitizeDate("Tanggal Lahir: 15/05/1990")
    assertEquals("", dBirth)
    assertNotNull(errBirth)

    val (dExp, errExp) = FieldValidator.sanitizeDate("Valid Thru / Exp: 12/28")
    assertEquals("", dExp)
    assertNotNull(errExp)
  }

  @Test
  fun testTime24HourValidation() {
    // 1. Format HH:MM:SS
    val (t1, err1) = FieldValidator.sanitizeTime("15:33:00")
    assertEquals("15:33:00", t1)
    assertNull(err1)

    // 2. Format HH:MM
    val (t2, err2) = FieldValidator.sanitizeTime("Waktu: 15:33")
    assertEquals("15:33", t2)
    assertNull(err2)

    // 3. Dengan suffix WIB/WITA/WIT
    val (t3, err3) = FieldValidator.sanitizeTime("Jam Transaksi 15:33:00 WIB")
    assertEquals("15:33:00", t3)
    assertNull(err3)

    // 4. Validasi batas 24 jam (jam > 23 ditolak)
    val (tInvalidHour, errInvalidHour) = FieldValidator.sanitizeTime("25:30")
    assertEquals("", tInvalidHour)
    assertNotNull(errInvalidHour)

    // 5. Validasi menit (menit > 59 ditolak)
    val (tInvalidMin, errInvalidMin) = FieldValidator.sanitizeTime("14:75")
    assertEquals("", tInvalidMin)
    assertNotNull(errInvalidMin)
  }

  @Test
  fun testReferenceNumberValidation() {
    // 1. Kode transaksi panjang > 15 karakter dengan nol di depan
    val (ref1, err1) = FieldValidator.sanitizeRefNumber("REF: 002026102843500282289238639")
    assertEquals("002026102843500282289238639", ref1)
    assertNull(err1)

    // 2. Serial number alfanumerik
    val (ref2, err2) = FieldValidator.sanitizeRefNumber("Kode Transaksi: TRX-2026-ABCD-9988")
    assertEquals("TRX-2026-ABCD-9988", ref2)
    assertNull(err2)

    // 3. Menolak jika nilai sama persis dengan nomor rekening
    val (refDup, errDup) = FieldValidator.sanitizeRefNumber("001601120412504", candidateAccount = "001601120412504")
    assertEquals("", refDup)
    assertNotNull(errDup)

    // 4. Menolak jika berupa nominal uang
    val (refNom, errNom) = FieldValidator.sanitizeRefNumber("Rp 240.000")
    assertEquals("", refNom)
    assertNotNull(errNom)
  }

  @Test
  fun testAccountNumberStrictValidation() {
    // 1. Rekening 10–15 digit dengan nol di depan terjaga
    val (acc10, err10) = FieldValidator.sanitizeAccount("001601120412504")
    assertEquals("001601120412504", acc10)
    assertTrue(acc10.startsWith("00"))
    assertNull(err10)

    val (acc15, err15) = FieldValidator.sanitizeAccount("123456789012345")
    assertEquals("123456789012345", acc15)
    assertNull(err15)

    // 2. Nomor rekening > 15 digit TIDAK BOLEH dipotong otomatis, tapi ditandai error
    val (accLong, errLong) = FieldValidator.sanitizeAccount("12345678901234567890")
    assertEquals("", accLong)
    assertNotNull(errLong)
    assertTrue(errLong?.contains("melebihi batas 15 digit") == true)

    // 3. Kurang dari 10 digit ditolak
    val (accShort, errShort) = FieldValidator.sanitizeAccount("12345678")
    assertEquals("", accShort)
    assertNotNull(errShort)

    // 4. Nilai nominal tidak boleh dianggap rekening
    val (accMoney, errMoney) = FieldValidator.sanitizeAccount("Rp 500.000")
    assertEquals("", accMoney)
    assertNotNull(errMoney)
  }

  @Test
  fun testRecipientValidation() {
    // 1. Membersihkan label "Kepada:", "Penerima:", dll
    val (name1, err1) = FieldValidator.sanitizeRecipientName("Kepada: LANANG SUTRISNO")
    assertEquals("LANANG SUTRISNO", name1)
    assertNull(err1)

    val (name2, err2) = FieldValidator.sanitizeRecipientName("Penerima: PT. SUMBER MAKMUR JAYA & CO")
    assertEquals("PT. SUMBER MAKMUR JAYA & CO", name2)
    assertNull(err2)

    // 2. Menolak nama pengirim
    val (nameSender, errSender) = FieldValidator.sanitizeRecipientName("Nama Pengirim: BUDI SANTOSO")
    assertEquals("", nameSender)
    assertNotNull(errSender)

    // 3. Menolak nama bank sebagai nama penerima
    val (nameBank, errBank) = FieldValidator.sanitizeRecipientName("BANK CENTRAL ASIA")
    assertEquals("", nameBank)
    assertNotNull(errBank)

    // 4. Menolak angka murni
    val (nameDigits, errDigits) = FieldValidator.sanitizeRecipientName("123456789")
    assertEquals("", nameDigits)
    assertNotNull(errDigits)
  }

  @Test
  fun testNominalRupiahValidation() {
    // 1. Berbagai variasi format nominal
    val (nom1, err1) = FieldValidator.parseNominal("Rp 20.000")
    assertEquals(20000L, nom1)
    assertNull(err1)

    val (nom2, err2) = FieldValidator.parseNominal("Rp20.000,00")
    assertEquals(20000L, nom2)
    assertNull(err2)

    val (nom3, err3) = FieldValidator.parseNominal("IDR 20,000")
    assertEquals(20000L, nom3)
    assertNull(err3)

    val (nom4, err4) = FieldValidator.parseNominal("240000")
    assertEquals(240000L, nom4)
    assertNull(err4)

    // 2. Menolak teks saldo atau biaya admin
    val (nomSaldo, errSaldo) = FieldValidator.parseNominal("Sisa Saldo Rp 500.000")
    assertEquals(0L, nomSaldo)
    assertNotNull(errSaldo)

    val (nomAdmin, errAdmin) = FieldValidator.parseNominal("Biaya Admin Rp 2.500")
    assertEquals(0L, nomAdmin)
    assertNotNull(errAdmin)
  }

  @Test
  fun testCrossValidationPreventAccountReferenceSwap() {
    val rawText = """
      TRFBT CELL
      BUKTI TRANSFER
      TANGGAL: 28/10/2026
      WAKTU: 15:33:00
      REF: 2026102843500282289238639
      KEPADA:
      PENERIMA: LANANG SUTRISNO
      BANK: BRI
      NO REKENING: 001601120412504
      NOMINAL: Rp 240.000
      ADMIN: Rp 0
      TOTAL: Rp 240.000
    """.trimIndent()

    val receipt = ReceiptParser.parse(rawText)

    // Tanggal & Waktu
    assertEquals("28/10/2026", receipt.displayDate)
    assertEquals("15:33:00", receipt.displayTime)

    // Referensi vs Rekening (tidak tertukar)
    assertEquals("2026102843500282289238639", receipt.refNumber)
    assertEquals("001601120412504", receipt.recipientAccount)

    // Bank & Penerima
    assertEquals("Bank BRI", receipt.recipientBank)
    assertEquals("LANANG SUTRISNO", receipt.recipientName)

    // Nominal
    assertEquals(240000L, receipt.amount)
    assertEquals(0L, receipt.adminFee)
    assertEquals(240000L, receipt.total)
  }

  @Test
  fun testSingleAreaMultipleCategoriesExtraction() {
    val dummyBox = OcrBoundingBox(0, 0, 100, 20, 0f, 0f, 1f, 1f)
    val lines = listOf(
      OcrTextLine("BANK BCA", dummyBox),
      OcrTextLine("NAMA PENERIMA", dummyBox),
      OcrTextLine("BUDI SANTOSO", dummyBox),
      OcrTextLine("NO REKENING", dummyBox),
      OcrTextLine("1234567890", dummyBox),
      OcrTextLine("TANGGAL", dummyBox),
      OcrTextLine("18/10/2026", dummyBox),
      OcrTextLine("WAKTU", dummyBox),
      OcrTextLine("14:32:00", dummyBox),
      OcrTextLine("NOMINAL", dummyBox),
      OcrTextLine("Rp150.000", dummyBox)
    )

    val extraction = MultiCategoryExtractor.extractFromLines(lines)

    assertEquals("BCA", extraction.bank?.value)
    assertEquals("BUDI SANTOSO", extraction.penerima?.value)
    assertEquals("1234567890", extraction.rekening?.value)
    assertEquals("18/10/2026", extraction.tanggal?.value)
    assertEquals("14:32:00", extraction.waktu?.value)
    assertEquals(150000L, extraction.nominal?.value)
  }

  @Test
  fun testAdminFeeTierRules() {
    val defaultRules = com.example.data.model.AdminFeeRule.defaultRules()

    assertEquals(3000L, com.example.data.model.AdminFeeRule.calculateAdminFee(10000L, defaultRules))
    assertEquals(3000L, com.example.data.model.AdminFeeRule.calculateAdminFee(150000L, defaultRules))
    assertEquals(3000L, com.example.data.model.AdminFeeRule.calculateAdminFee(400000L, defaultRules))

    assertEquals(4000L, com.example.data.model.AdminFeeRule.calculateAdminFee(401000L, defaultRules))
    assertEquals(4000L, com.example.data.model.AdminFeeRule.calculateAdminFee(500000L, defaultRules))
    assertEquals(4000L, com.example.data.model.AdminFeeRule.calculateAdminFee(600000L, defaultRules))

    assertEquals(null, com.example.data.model.AdminFeeRule.calculateAdminFee(5000L, defaultRules))
    assertEquals(null, com.example.data.model.AdminFeeRule.calculateAdminFee(800000L, defaultRules))
  }
}
