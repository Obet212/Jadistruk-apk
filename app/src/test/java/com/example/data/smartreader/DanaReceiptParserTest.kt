package com.example.data.smartreader

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanaReceiptParserTest {

  /**
   * Helper untuk membuat OcrResult tiruan dengan resolusi asli 756 × 1536 px
   * dan koordinat ternormalisasi sesuai posisi visual gambar referensi DANA.
   */
  private fun createDanaOcrResult(imageWidth: Int = 756, imageHeight: Int = 1536): OcrResult {
    val items = listOf(
      // Logo DANA
      Triple("DANA", 300, 150),
      // Subheader: Tanggal & Waktu
      Triple("09 Nov 2026 • 20:04", 60, 360),
      // Subheader: ID DANA Pengirim (tersamar)
      Triple("ID DANA 0896••••5322", 420, 360),
      // Status Transaksi
      Triple("Transaksi berhasil!", 120, 460),
      // Headline / Jenis Transaksi
      Triple("Kirim Uang Rp152.000 ke Lowo -", 80, 520),
      Triple("088224225094", 80, 555),
      // Section Total Bayar
      Triple("Total Bayar", 80, 630),
      Triple("Rp152.000", 480, 630),
      // Section Metode Pembayaran
      Triple("Metode Pembayaran", 80, 720),
      Triple("Saldo DANA", 480, 720),
      Triple("(SmartPay)", 480, 755),
      // Section Detail Penerima
      Triple("Detail Penerima", 80, 850),
      Triple("Nama", 80, 920),
      Triple("Wahyudi D•i Pu••••to", 380, 920),
      Triple("Akun DANA", 80, 980),
      Triple("088224225094", 450, 980),
      // Section Detail Transaksi
      Triple("Detail Transaksi", 80, 1060),
      Triple("ID Transaksi", 80, 1120),
      // Multi-line ID Transaksi (3 baris angka dengan baris terakhir digit tunggal '7')
      Triple("202611091012141001", 450, 1120),
      Triple("010016611231076101", 450, 1155),
      Triple("7", 650, 1190),
      // ID Order Merchant (3 baris angka dengan baris terakhir digit tunggal '6')
      Triple("ID Order Merchant", 80, 1230),
      Triple("202611091012141001", 450, 1230),
      Triple("010016611231076101", 450, 1265),
      Triple("6", 650, 1300),
      // Footer
      Triple("Diamankan oleh DANA PROTECTION", 200, 1420)
    )

    val lines = items.map { (text, leftPx, topPx) ->
      val rightPx = leftPx + (text.length * 14)
      val bottomPx = topPx + 32
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
  fun testDetectSourceDana() {
    val ocr = createDanaOcrResult()
    val detection = SourceDetector.detectSource(ocr.fullText, ocr.lines.map { it.text })
    assertEquals(TransactionSource.DANA, detection.source)
    assertTrue("Confidence harus tinggi (>= 75)", detection.confidence >= 75)
  }

  @Test
  fun testParseDanaExactFieldsFromNormalizedCoordinates() {
    val ocr = createDanaOcrResult()
    val parsed = SmartReaderEngine.process(ocr)

    assertEquals(TransactionSource.DANA, parsed.source)
    assertEquals("Transaksi berhasil!", parsed.transactionStatus)
    assertEquals("Kirim Uang", parsed.transactionType)

    // Nominal diambil dari Total Bayar
    assertEquals(152000L, parsed.amount)
    assertEquals(0L, parsed.adminFee)
    assertEquals(152000L, parsed.total)

    // Tanggal & Waktu dari Subheader
    assertEquals("09/11/2026", parsed.transactionDate)
    assertEquals("20:04", parsed.transactionTime)

    // ID DANA Pengirim (tersamar, tidak tertukar dengan nomor penerima)
    assertEquals("0896••••5322", parsed.senderAccount)

    // Detail Penerima: Nama & Akun DANA (mempertahankan karakter masking •)
    assertEquals("Wahyudi D•i Pu••••to", parsed.recipientName)
    assertEquals("088224225094", parsed.recipientAccount)
    assertEquals("DANA", parsed.recipientBank)

    // Pastikan ID DANA Pengirim != Nomor Akun Penerima (Rule C.4)
    assertNotEquals(parsed.senderAccount, parsed.recipientAccount)

    // Metode Pembayaran
    assertTrue("Metode pembayaran harus berisi Saldo DANA", parsed.paymentMethod.contains("Saldo DANA"))

    // ID Transaksi & ID Order Merchant dibaca terpisah (Rule C.5)
    // Multi-baris digabung tanpa spasi, termasuk angka terakhir '7' dan '6' (Rule C.6 & C.7)
    assertEquals("2026110910121410010100166112310761017", parsed.refNumber)
    assertEquals("2026110910121410010100166112310761016", parsed.merchantOrderId)
    assertNotEquals(parsed.refNumber, parsed.merchantOrderId)
  }

  @Test
  fun testDanaFallbackWithoutCoordinates() {
    // Skenario fallback struktural jika koordinat tidak tersedia / crop
    val rawLines = listOf(
      "DANA",
      "09 Nov 2026 • 20:04",
      "ID DANA 0896••••5322",
      "Transaksi berhasil!",
      "Kirim Uang Rp152.000 ke Lowo -",
      "088224225094",
      "Total Bayar",
      "Rp152.000",
      "Metode Pembayaran",
      "Saldo DANA (SmartPay)",
      "Detail Penerima",
      "Nama",
      "Wahyudi D•i Pu••••to",
      "Akun DANA",
      "088224225094",
      "Detail Transaksi",
      "ID Transaksi",
      "202611091012141001",
      "010016611231076101",
      "7",
      "ID Order Merchant",
      "202611091012141001",
      "010016611231076101",
      "6",
      "Diamankan oleh DANA PROTECTION"
    )

    val ocrWithoutBoxes = OcrResult(
      fullText = rawLines.joinToString("\n"),
      imageWidth = 0,
      imageHeight = 0,
      lines = rawLines.map { OcrTextLine(text = it, boundingBox = null) }
    )

    val parser = DanaReceiptParser()
    val parsed = parser.parse(rawLines, ocrWithoutBoxes.fullText, ocrWithoutBoxes)

    assertEquals("Transaksi berhasil!", parsed.transactionStatus)
    assertEquals(152000L, parsed.amount)
    assertEquals("09/11/2026", parsed.transactionDate)
    assertEquals("20:04", parsed.transactionTime)
    assertEquals("0896••••5322", parsed.senderAccount)
    assertEquals("Wahyudi D•i Pu••••to", parsed.recipientName)
    assertEquals("088224225094", parsed.recipientAccount)
    assertEquals("2026110910121410010100166112310761017", parsed.refNumber)
    assertEquals("2026110910121410010100166112310761016", parsed.merchantOrderId)
  }

  @Test
  fun testDanaPreservesMaskedBullets() {
    val parser = DanaReceiptParser()
    val lines = listOf(
      "DANA",
      "Detail Penerima",
      "Nama",
      "Siti N•r Pu••••ti",
      "Akun DANA",
      "081234567890",
      "Total Bayar",
      "Rp50.000"
    )
    val ocr = OcrResult(
      fullText = lines.joinToString("\n"),
      lines = lines.map { OcrTextLine(text = it, boundingBox = null) }
    )
    val parsed = parser.parse(lines, ocr.fullText, ocr)
    assertEquals("Siti N•r Pu••••ti", parsed.recipientName)
    assertTrue("Karakter bullet • harus dipertahankan", parsed.recipientName.contains("•"))
  }
}
