package com.example.data.smartreader

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReaderEngineTest {

  private fun createOcrResult(lines: List<String>): OcrResult {
    val ocrLines = lines.mapIndexed { index, text ->
      val top = index * 40
      val bottom = top + 30
      val box = OcrBoundingBox(
        left = 10,
        top = top,
        right = 310,
        bottom = bottom,
        normalizedLeft = 0.025f,
        normalizedTop = top.toFloat() / (lines.size * 40 + 50),
        normalizedRight = 0.775f,
        normalizedBottom = bottom.toFloat() / (lines.size * 40 + 50)
      )
      OcrTextLine(
        text = text,
        boundingBox = box
      )
    }
    return OcrResult(
      fullText = lines.joinToString("\n"),
      lines = ocrLines,
      imageWidth = 400,
      imageHeight = lines.size * 40 + 50
    )
  }

  @Test
  fun testDetectSourceDana() {
    val ocr = createOcrResult(
      listOf(
        "DANA",
        "Kirim Uang Berhasil",
        "Total Pembayaran",
        "Rp 150.000",
        "ID Transaksi",
        "20261028123456789012",
        "Penerima",
        "Budi Santoso",
        "Bank BCA",
        "1234567890",
        "28 Okt 2026 14:30:15"
      )
    )
    val parsed = SmartReaderEngine.process(ocr)
    assertEquals(TransactionSource.DANA, parsed.source)
    assertEquals(150000L, parsed.amount)
    assertEquals("20261028123456789012", parsed.refNumber)
    assertEquals("Budi Santoso", parsed.recipientName)
    assertEquals("BCA", parsed.recipientBank)
    assertEquals("1234567890", parsed.recipientAccount)
    assertEquals("Berhasil", parsed.transactionStatus)
  }

  @Test
  fun testDetectSourceBrimo() {
    val ocr = createOcrResult(
      listOf(
        "BRImo",
        "Transaksi Berhasil",
        "Nomor Referensi",
        "20261028987654321098",
        "Sumber Dana",
        "Ahmad Fauzi",
        "Bank Tujuan",
        "Bank Mandiri",
        "Nomor Rekening",
        "0987654321",
        "Nama Penerima",
        "Siti Rahma",
        "Nominal Transfer",
        "Rp 500.000",
        "Biaya Admin",
        "Rp 2.500",
        "Waktu Transaksi",
        "28/10/2026 09:15:00 WIB"
      )
    )
    val parsed = SmartReaderEngine.process(ocr)
    assertEquals(TransactionSource.BRIMO, parsed.source)
    assertEquals(500000L, parsed.amount)
    assertEquals(2500L, parsed.adminFee)
    assertEquals("20261028987654321098", parsed.refNumber)
    assertEquals("Siti Rahma", parsed.recipientName)
    assertEquals("Bank Mandiri", parsed.recipientBank)
    assertEquals("0987654321", parsed.recipientAccount)
    assertEquals("Berhasil", parsed.transactionStatus)
  }

  @Test
  fun testDetectSourceBca() {
    val ocr = createOcrResult(
      listOf(
        "m-Transfer",
        "BERHASIL",
        "TANGGAL : 28/10/2026",
        "JAM : 13:45:20",
        "DARI : JOHN DOE",
        "KE : 8765432109",
        "BCA",
        "JANE SMITH",
        "JUMLAH : Rp 250.000,00",
        "NO. REFERENSI : 20261028112233445566"
      )
    )
    val parsed = SmartReaderEngine.process(ocr)
    assertEquals(TransactionSource.BCA, parsed.source)
    assertEquals(250000L, parsed.amount)
    assertEquals("20261028112233445566", parsed.refNumber)
    assertEquals("JANE SMITH", parsed.recipientName)
    assertEquals("8765432109", parsed.recipientAccount)
  }

  @Test
  fun testDetectSourceLivinMandiri() {
    val ocr = createOcrResult(
      listOf(
        "Livin' by Mandiri",
        "Transfer Berhasil",
        "Detail Transaksi",
        "Penerima",
        "DEWI LESTARI",
        "Bank BRI",
        "112233445566778",
        "Nominal",
        "Rp 1.000.000",
        "Biaya Transaksi",
        "Rp 2.500",
        "Total",
        "Rp 1.002.500",
        "No. Referensi",
        "20261028556677889900",
        "Tanggal",
        "28 Okt 2026 16:20 WIB"
      )
    )
    val parsed = SmartReaderEngine.process(ocr)
    assertEquals(TransactionSource.LIVIN, parsed.source)
    assertEquals(1000000L, parsed.amount)
    assertEquals(2500L, parsed.adminFee)
    assertEquals("20261028556677889900", parsed.refNumber)
    assertEquals("DEWI LESTARI", parsed.recipientName)
    assertEquals("Bank BRI", parsed.recipientBank)
    assertEquals("112233445566778", parsed.recipientAccount)
  }

  @Test
  fun testStrictNumericRefNumberAtLeast16Digits() {
    // Letters should be rejected, non-16 digits rejected
    val ocrWithShortRef = createOcrResult(
      listOf(
        "DANA",
        "Berhasil",
        "No Referensi",
        "123456789", // only 9 digits, should be rejected
        "Total",
        "Rp 50.000"
      )
    )
    val parsedShort = SmartReaderEngine.process(ocrWithShortRef)
    assertEquals("", parsedShort.refNumber)

    val ocrWithAlphaRef = createOcrResult(
      listOf(
        "DANA",
        "Berhasil",
        "No Referensi",
        "ABC123456789012345", // contains letters, should be rejected
        "Total",
        "Rp 50.000"
      )
    )
    val parsedAlpha = SmartReaderEngine.process(ocrWithAlphaRef)
    assertEquals("", parsedAlpha.refNumber)
  }
}
