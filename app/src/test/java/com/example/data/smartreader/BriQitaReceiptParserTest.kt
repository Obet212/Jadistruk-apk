package com.example.data.smartreader

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriQitaReceiptParserTest {

  /**
   * Helper untuk membuat OcrResult tiruan dengan resolusi asli 720 × 1959 px
   * dan koordinat ternormalisasi sesuai posisi visual gambar referensi Qita.
   */
  private fun createQitaOcrResult(imageWidth: Int = 720, imageHeight: Int = 1959): OcrResult {
    val items = listOf(
      // Header Qita by BRI
      Triple("Qita by BRI", 250, 90),
      // Status
      Triple("Transfer Berhasil", 200, 480),
      // Nominal Utama
      Triple("Rp299.529", 220, 580),
      // Tanggal & Waktu
      Triple("03 November 2026, 09:06:54 WIB", 120, 650),
      // Detail Transaksi
      Triple("Detail Transaksi", 60, 730),
      // Pengirim: Nama
      Triple("ADHI NUGROHO", 150, 810),
      // Pengirim: Bank & Rekening
      Triple("BANK BRI - 6051 **** **** 500", 150, 860),
      // Penerima: Nama
      Triple("PROSPEK MITRA ABADI", 150, 960),
      // Penerima: Bank & Rekening
      Triple("BANK BRI - 0251 0133 3333 301", 150, 1010),
      // Label No. Referensi
      Triple("No. Referensi", 60, 1120),
      // Value No. Referensi
      Triple("244404392528", 450, 1120),
      // Label Jenis Transaksi
      Triple("Jenis Transaksi", 60, 1180),
      // Value Jenis Transaksi
      Triple("Transfer Bank BRI", 430, 1180),
      // Label Catatan
      Triple("Catatan", 60, 1280),
      // Value Catatan
      Triple("-", 500, 1280),
      // Label Nominal
      Triple("Nominal", 60, 1380),
      // Value Nominal
      Triple("Rp299.529", 450, 1380),
      // Label Biaya Admin
      Triple("Biaya Admin", 60, 1440),
      // Value Biaya Admin
      Triple("Rp0", 500, 1440),
      // Footer info
      Triple("PT. Bank Rakyat Indonesia (Persero) Tbk.", 100, 1600),
      Triple("NPWP : 01.001.608.7-093.000", 120, 1660)
    )

    val lines = items.map { (text, leftPx, topPx) ->
      val rightPx = leftPx + (text.length * 15)
      val bottomPx = topPx + 35
      val box = OcrBoundingBox(
        left = leftPx,
        top = topPx,
        right = rightPx,
        bottom = bottomPx,
        normalizedLeft = leftPx.toFloat() / imageWidth,
        normalizedTop = topPx.toFloat() / imageHeight,
        normalizedRight = rightPx.toFloat() / imageWidth,
        normalizedBottom = bottomPx.toFloat() / imageHeight
      )
      OcrTextLine(text = text, boundingBox = box)
    }

    return OcrResult(
      fullText = items.joinToString("\n") { it.first },
      imageWidth = imageWidth,
      imageHeight = imageHeight,
      lines = lines
    )
  }

  @Test
  fun testDetectSourceBriQita() {
    val ocr = createQitaOcrResult()
    val detection = SourceDetector.detectSource(ocr.fullText, ocr.lines.map { it.text })
    assertEquals(TransactionSource.BRI_QITA, detection.source)
    assertTrue("Confidence harus tinggi (>= 75)", detection.confidence >= 75)
  }

  @Test
  fun testParseBriQitaExactFields() {
    val ocr = createQitaOcrResult()
    val parsed = SmartReaderEngine.process(ocr)

    assertEquals(TransactionSource.BRI_QITA, parsed.source)
    assertEquals("Transfer Berhasil", parsed.transactionStatus)
    assertEquals(299529L, parsed.amount)
    assertEquals(0L, parsed.adminFee)
    assertEquals(299529L, parsed.total)

    // Tanggal kalender valid DD/MM/YYYY
    assertEquals("03/11/2026", parsed.transactionDate)
    assertEquals("09:06:54", parsed.transactionTime)

    // Pengirim
    assertEquals("ADHI NUGROHO", parsed.senderName)
    assertEquals("BANK BRI", parsed.senderBank)
    assertEquals("6051 **** **** 500", parsed.senderAccount)

    // Penerima
    assertEquals("PROSPEK MITRA ABADI", parsed.recipientName)
    assertEquals("BANK BRI", parsed.recipientBank)
    // 15 digit rekening tanpa spasi
    assertEquals("025101333333301", parsed.recipientAccount)

    // No. Referensi (12 digit dibaca dari label No. Referensi tanpa penolakan)
    assertEquals("244404392528", parsed.refNumber)

    // Metadata transaksi
    assertEquals("Transfer Bank BRI", parsed.transactionType)
    assertEquals("", parsed.notes) // Catatan "-" disimpan sebagai teks kosong
    assertTrue("Tidak boleh ada missing fields penting", parsed.missingFields.isEmpty())
  }

  @Test
  fun testBriQitaFallbackWithoutCoordinates() {
    // Skenario fallback jika gambar dipotong / koordinat tidak tersedia
    val rawLines = listOf(
      "Qita by BRI",
      "Transfer Berhasil",
      "Rp299.529",
      "03 November 2026, 09:06:54 WIB",
      "Detail Transaksi",
      "ADHI NUGROHO",
      "BANK BRI - 6051 **** **** 500",
      "PROSPEK MITRA ABADI",
      "BANK BRI - 0251 0133 3333 301",
      "No. Referensi : 244404392528",
      "Jenis Transaksi : Transfer Bank BRI",
      "Catatan : -",
      "Nominal : Rp299.529",
      "Biaya Admin : Rp0"
    )

    val ocrWithoutBoxes = OcrResult(
      fullText = rawLines.joinToString("\n"),
      imageWidth = 0,
      imageHeight = 0,
      lines = rawLines.map { OcrTextLine(text = it, boundingBox = null) }
    )

    val parser = BriQitaReceiptParser()
    val parsed = parser.parse(rawLines, ocrWithoutBoxes.fullText, ocrWithoutBoxes)

    assertEquals("Transfer Berhasil", parsed.transactionStatus)
    assertEquals(299529L, parsed.amount)
    assertEquals(0L, parsed.adminFee)
    assertEquals("03/11/2026", parsed.transactionDate)
    assertEquals("09:06:54", parsed.transactionTime)
    assertEquals("ADHI NUGROHO", parsed.senderName)
    assertEquals("BANK BRI", parsed.senderBank)
    assertEquals("PROSPEK MITRA ABADI", parsed.recipientName)
    assertEquals("BANK BRI", parsed.recipientBank)
    assertEquals("025101333333301", parsed.recipientAccount)
    assertEquals("244404392528", parsed.refNumber)
    assertEquals("Transfer Bank BRI", parsed.transactionType)
    assertEquals("", parsed.notes)
  }

  @Test
  fun testBriQitaAccountNumberMax15Digits() {
    val parser = BriQitaReceiptParser()
    val lines = listOf(
      "Qita",
      "Transfer Berhasil",
      "Detail Transaksi",
      "PROSPEK MITRA ABADI",
      "BANK BRI - 0251 0133 3333 301 9999", // 19 digit
      "No. Referensi : 244404392528"
    )
    val ocr = OcrResult(
      fullText = lines.joinToString("\n"),
      lines = lines.map { OcrTextLine(text = it, boundingBox = null) }
    )
    val parsed = parser.parse(lines, ocr.fullText, ocr)
    // Harus dipotong ke 15 digit sesuai aturan perbankan BRI (Rule 6)
    assertEquals(15, parsed.recipientAccount.length)
    assertEquals("025101333333301", parsed.recipientAccount)
  }
}
