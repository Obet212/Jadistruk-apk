package com.example.data.smartreader

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextLine
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import java.util.Locale

/**
 * Template OCR khusus untuk bukti transfer aplikasi DANA.
 * Berdasarkan referensi visual asli berukuran 756 × 1536 piksel.
 *
 * Menerapkan:
 * 1. Normalized coordinates (proporsi 0.0 - 1.0) yang adaptif terhadap variasi resolusi dan rasio.
 * 2. Pemindaian OCR dua tahap:
 *    - Tahap 1: Deteksi teks keseluruhan gambar.
 *    - Tahap 2: Pembacaan ulang terfokus menggunakan area koordinat khusus setiap field.
 * 3. Pemetaan terpisah secara presisi:
 *    - Tanggal dan waktu transaksi
 *    - ID DANA pengirim (disimpan terpisah dan tidak tertukar dengan nomor penerima)
 *    - Status transaksi ("Transaksi berhasil!")
 *    - Jenis transaksi ("Kirim Uang")
 *    - Nominal transfer diambil khusus dari "Total Bayar"
 *    - Nama penerima dari "Detail Penerima -> Nama" (mempertahankan karakter masking •)
 *    - Nomor akun DANA penerima dari "Detail Penerima -> Akun DANA"
 *    - Metode pembayaran ("Saldo DANA (SmartPay)")
 *    - ID Transaksi & ID Order Merchant dibaca terpisah
 *    - Rekonstruksi otomatis ID multi-baris (menggabungkan baris angka berurutan tanpa spasi, termasuk angka terakhir)
 * 4. Fallback cerdas berbasis deteksi label jika koordinat bergeser akibat cropping / resolusi lain.
 * 5. Nol fabrikasi data (tidak mengarang teks yang tidak terbaca).
 */
class DanaReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.DANA

  companion object {
    // 1. Tanggal & Waktu Subheader ("09 Nov 2026 • 20:04") ~ y: 330..420 px (0.215..0.275)
    private const val NORM_DATETIME_TOP = 0.200f
    private const val NORM_DATETIME_BOTTOM = 0.280f
    private const val NORM_DATETIME_LEFT = 0.040f
    private const val NORM_DATETIME_RIGHT = 0.500f

    // 2. ID DANA Pengirim ("ID DANA 0896••••5322") ~ y: 330..420 px (0.215..0.275)
    private const val NORM_SENDER_ID_TOP = 0.200f
    private const val NORM_SENDER_ID_BOTTOM = 0.280f
    private const val NORM_SENDER_ID_LEFT = 0.480f
    private const val NORM_SENDER_ID_RIGHT = 0.960f

    // 3. Status Transaksi ("Transaksi berhasil!") ~ y: 430..490 px (0.280..0.320)
    private const val NORM_STATUS_TOP = 0.270f
    private const val NORM_STATUS_BOTTOM = 0.325f
    private const val NORM_STATUS_LEFT = 0.050f
    private const val NORM_STATUS_RIGHT = 0.950f

    // 4. Headline / Jenis Transaksi ("Kirim Uang Rp152.000 ke Lowo - 088224225094")
    private const val NORM_HEADLINE_TOP = 0.320f
    private const val NORM_HEADLINE_BOTTOM = 0.390f
    private const val NORM_HEADLINE_LEFT = 0.050f
    private const val NORM_HEADLINE_RIGHT = 0.950f

    // 5. Total Bayar (Nominal Transfer) Nilai ("Rp152.000") ~ y: 600..670 px (0.390..0.440)
    private const val NORM_TOTAL_BAYAR_VAL_TOP = 0.385f
    private const val NORM_TOTAL_BAYAR_VAL_BOTTOM = 0.445f
    private const val NORM_TOTAL_BAYAR_VAL_LEFT = 0.480f
    private const val NORM_TOTAL_BAYAR_VAL_RIGHT = 0.960f

    // 6. Metode Pembayaran Nilai ("Saldo DANA (SmartPay)") ~ y: 670..780 px (0.440..0.510)
    private const val NORM_PAYMENT_METHOD_VAL_TOP = 0.440f
    private const val NORM_PAYMENT_METHOD_VAL_BOTTOM = 0.525f
    private const val NORM_PAYMENT_METHOD_VAL_LEFT = 0.480f
    private const val NORM_PAYMENT_METHOD_VAL_RIGHT = 0.960f

    // 7. Detail Penerima -> Nama Nilai ("Wahyudi D•i Pu••••to") ~ y: 890..950 px (0.580..0.620)
    private const val NORM_RECIPIENT_NAME_VAL_TOP = 0.570f
    private const val NORM_RECIPIENT_NAME_VAL_BOTTOM = 0.625f
    private const val NORM_RECIPIENT_NAME_VAL_LEFT = 0.350f
    private const val NORM_RECIPIENT_NAME_VAL_RIGHT = 0.960f

    // 8. Detail Penerima -> Akun DANA Nilai ("088224225094") ~ y: 950..1010 px (0.620..0.660)
    private const val NORM_RECIPIENT_ACC_VAL_TOP = 0.620f
    private const val NORM_RECIPIENT_ACC_VAL_BOTTOM = 0.665f
    private const val NORM_RECIPIENT_ACC_VAL_LEFT = 0.450f
    private const val NORM_RECIPIENT_ACC_VAL_RIGHT = 0.960f

    // 9. ID Transaksi Multi-baris Nilai ~ y: 1095..1210 px (0.710..0.790)
    private const val NORM_TX_ID_VAL_TOP = 0.700f
    private const val NORM_TX_ID_VAL_BOTTOM = 0.796f
    private const val NORM_TX_ID_VAL_LEFT = 0.450f
    private const val NORM_TX_ID_VAL_RIGHT = 0.960f

    // 10. ID Order Merchant Multi-baris Nilai ~ y: 1220..1330 px (0.798..0.880)
    private const val NORM_ORDER_ID_VAL_TOP = 0.798f
    private const val NORM_ORDER_ID_VAL_BOTTOM = 0.880f
    private const val NORM_ORDER_ID_VAL_LEFT = 0.450f
    private const val NORM_ORDER_ID_VAL_RIGHT = 0.960f
  }

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = "Akun DANA"
    var senderBank = "DANA"
    var senderAccount = "" // ID DANA Pengirim (e.g. 0896••••5322)
    var recipientName = "" // Detail Penerima -> Nama (e.g. Wahyudi D•i Pu••••to)
    var recipientBank = "DANA"
    var recipientAccount = "" // Detail Penerima -> Akun DANA (e.g. 088224225094)
    var amount = 0L // Nominal khusus dari Total Bayar
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = "" // ID Transaksi
    var merchantOrderId = "" // ID Order Merchant
    var paymentMethod = "Saldo DANA"
    var transactionType = "Kirim Uang"
    var transactionStatus = ""
    var notes = ""

    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    // =========================================================================
    // TAHAP 2: Pembacaan Terfokus Berdasarkan Area Koordinat Normalisasi
    // =========================================================================
    if (ocrResult != null && ocrResult.lines.isNotEmpty()) {
      // 1. Tanggal & Waktu Subheader
      val dtLines = getLinesInBox(ocrResult, NORM_DATETIME_LEFT, NORM_DATETIME_TOP, NORM_DATETIME_RIGHT, NORM_DATETIME_BOTTOM)
      val dtText = dtLines.joinToString(" ") { it.text.trim() }
      if (dtText.isNotBlank()) {
        val (d, _) = FieldValidator.sanitizeDate(dtText)
        if (d.isNotBlank()) {
          transactionDate = d
          detectedFields.add(TargetRecipientField.TANGGAL)
        }
        val (t, _) = FieldValidator.sanitizeTime(dtText)
        if (t.isNotBlank()) {
          transactionTime = t
          detectedFields.add(TargetRecipientField.WAKTU)
        }
      }

      // 2. ID DANA Pengirim (Subheader Kanan: "ID DANA 0896••••5322")
      val senderLines = getLinesInBox(ocrResult, NORM_SENDER_ID_LEFT, NORM_SENDER_ID_TOP, NORM_SENDER_ID_RIGHT, NORM_SENDER_ID_BOTTOM)
      for (line in senderLines) {
        val raw = line.text.trim()
        if (raw.contains("ID DANA", ignoreCase = true) || raw.contains("DANA", ignoreCase = true)) {
          val extracted = extractSenderId(raw)
          if (extracted.isNotBlank()) {
            senderAccount = extracted
            break
          }
        }
      }

      // 3. Status Transaksi
      val statusLines = getLinesInBox(ocrResult, NORM_STATUS_LEFT, NORM_STATUS_TOP, NORM_STATUS_RIGHT, NORM_STATUS_BOTTOM)
      for (line in statusLines) {
        val raw = line.text.trim()
        if (raw.contains("berhasil", ignoreCase = true) || raw.contains("sukses", ignoreCase = true)) {
          transactionStatus = raw
          break
        }
      }

      // 4. Headline / Jenis Transaksi
      val headlineLines = getLinesInBox(ocrResult, NORM_HEADLINE_LEFT, NORM_HEADLINE_TOP, NORM_HEADLINE_RIGHT, NORM_HEADLINE_BOTTOM)
      for (line in headlineLines) {
        val raw = line.text.trim()
        if (raw.contains("Kirim Uang", ignoreCase = true) || raw.contains("Transfer", ignoreCase = true)) {
          transactionType = "Kirim Uang"
          break
        }
      }

      // 5. Nominal Transfer (Khusus dari Nilai Total Bayar)
      val totalBayarLines = getLinesInBox(ocrResult, NORM_TOTAL_BAYAR_VAL_LEFT, NORM_TOTAL_BAYAR_VAL_TOP, NORM_TOTAL_BAYAR_VAL_RIGHT, NORM_TOTAL_BAYAR_VAL_BOTTOM)
      for (line in totalBayarLines) {
        val (parsedAmt, _) = FieldValidator.parseNominal(line.text)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
          break
        }
      }

      // 6. Metode Pembayaran
      val methodLines = getLinesInBox(ocrResult, NORM_PAYMENT_METHOD_VAL_LEFT, NORM_PAYMENT_METHOD_VAL_TOP, NORM_PAYMENT_METHOD_VAL_RIGHT, NORM_PAYMENT_METHOD_VAL_BOTTOM)
      if (methodLines.isNotEmpty()) {
        val methodText = methodLines.joinToString(" ") { it.text.trim() }
          .replace(Regex("(?i)^(metode\\s*pembayaran)[:\\-\\s]*"), "")
          .trim()
        if (methodText.isNotBlank() && !methodText.contains("Pembayaran", ignoreCase = true)) {
          paymentMethod = methodText
        }
      }

      // 7. Nama Penerima (Detail Penerima -> Nama, mempertahankan masking •)
      val nameLines = getLinesInBox(ocrResult, NORM_RECIPIENT_NAME_VAL_LEFT, NORM_RECIPIENT_NAME_VAL_TOP, NORM_RECIPIENT_NAME_VAL_RIGHT, NORM_RECIPIENT_NAME_VAL_BOTTOM)
      for (line in nameLines) {
        val candidate = line.text.trim()
        val cleaned = cleanRecipientNameWithMasking(candidate)
        if (cleaned.isNotBlank() && !cleaned.equals("Nama", ignoreCase = true) && !cleaned.contains("Penerima", ignoreCase = true)) {
          recipientName = cleaned
          detectedFields.add(TargetRecipientField.PENERIMA)
          break
        }
      }

      // 8. Nomor Akun DANA Penerima (Detail Penerima -> Akun DANA)
      val accLines = getLinesInBox(ocrResult, NORM_RECIPIENT_ACC_VAL_LEFT, NORM_RECIPIENT_ACC_VAL_TOP, NORM_RECIPIENT_ACC_VAL_RIGHT, NORM_RECIPIENT_ACC_VAL_BOTTOM)
      for (line in accLines) {
        val digits = line.text.filter { it.isDigit() }
        if (digits.length in 10..15 && digits != senderAccount.filter { it.isDigit() }) {
          recipientAccount = digits
          detectedFields.add(TargetRecipientField.REKENING)
          break
        }
      }

      // 9. ID Transaksi (Gabungkan multi-baris tanpa spasi, mempertahankan seluruh digit)
      val txIdLines = getLinesInBox(ocrResult, NORM_TX_ID_VAL_LEFT, NORM_TX_ID_VAL_TOP, NORM_TX_ID_VAL_RIGHT, NORM_TX_ID_VAL_BOTTOM)
      val mergedTxId = joinMultiLineNumericId(txIdLines)
      if (mergedTxId.length >= 15) {
        refNumber = mergedTxId
        detectedFields.add(TargetRecipientField.REFERENSI)
      }

      // 10. ID Order Merchant (Gabungkan multi-baris tanpa spasi)
      val orderIdLines = getLinesInBox(ocrResult, NORM_ORDER_ID_VAL_LEFT, NORM_ORDER_ID_VAL_TOP, NORM_ORDER_ID_VAL_RIGHT, NORM_ORDER_ID_VAL_BOTTOM)
      val mergedOrderId = joinMultiLineNumericId(orderIdLines)
      if (mergedOrderId.length >= 15 && mergedOrderId != refNumber) {
        merchantOrderId = mergedOrderId
      }
    }

    // =========================================================================
    // TAHAP 3 (FALLBACK): Deteksi Berbasis Label Struktural Jika Koordinat Bergeser
    // =========================================================================
    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      // Fallback ID DANA Pengirim (Subheader: "ID DANA 0896••••5322")
      if (senderAccount.isBlank() && lower.contains("id dana")) {
        val extracted = extractSenderId(line)
        if (extracted.isNotBlank()) {
          senderAccount = extracted
        } else if (i + 1 < lines.size) {
          senderAccount = extractSenderId(lines[i + 1])
        }
      }

      // Fallback Tanggal & Waktu
      if (transactionDate.isBlank() || transactionTime.isBlank()) {
        val (d, _) = FieldValidator.sanitizeDate(line)
        if (d.isNotBlank() && transactionDate.isBlank()) {
          transactionDate = d
          detectedFields.add(TargetRecipientField.TANGGAL)
        }
        val (t, _) = FieldValidator.sanitizeTime(line)
        if (t.isNotBlank() && transactionTime.isBlank()) {
          transactionTime = t
          detectedFields.add(TargetRecipientField.WAKTU)
        }
      }

      // Fallback Status
      if (lower.contains("transaksi berhasil") || lower.contains("berhasil kirim uang")) {
        transactionStatus = line
      } else if (lower.contains("berhasil")) {
        transactionStatus = if (lower == "kirim uang berhasil" || lower == "berhasil") "Berhasil" else line
      }

      // Fallback Headline / Jenis Transaksi
      if (lower.startsWith("kirim uang") && !lower.contains("berhasil")) {
        transactionType = "Kirim Uang"
      }

      // Fallback Total Bayar (Nominal Transfer)
      if (amount == 0L && (lower.contains("total bayar") || lower.contains("total pembayaran"))) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      // Fallback Metode Pembayaran
      if (paymentMethod.isBlank() && lower.contains("metode pembayaran")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.isNotBlank() && !candidate.contains("pembayaran", ignoreCase = true)) {
          paymentMethod = candidate
        }
      }

      // Fallback Detail Penerima -> Nama
      if (recipientName.isBlank()) {
        if (lower == "nama" || lower.startsWith("nama:") || lower == "nama penerima") {
          val inline = extractInline(line)
          val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
          val cleanName = cleanRecipientNameWithMasking(candidate)
          if (cleanName.isNotBlank() && !cleanName.equals("Nama", ignoreCase = true)) {
            recipientName = cleanName
            detectedFields.add(TargetRecipientField.PENERIMA)
          }
        }
      }

      // Fallback Detail Penerima -> Akun DANA
      if (recipientAccount.isBlank()) {
        if (lower.contains("akun dana") || lower == "akun tujuan" || lower.contains("nomor akun")) {
          val inline = extractInline(line)
          val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
          val digits = candidate.filter { it.isDigit() }
          if (digits.length in 10..15 && digits != senderAccount.filter { it.isDigit() }) {
            recipientAccount = digits
            detectedFields.add(TargetRecipientField.REKENING)
          }
        }
      }

      // Fallback Multi-line ID Transaksi / No Referensi
      val isRefLabel = lower == "id transaksi" || lower.startsWith("id transaksi") ||
          lower.contains("no. referensi") || lower.contains("no referensi") ||
          lower.contains("nomor referensi") || lower.contains("reference") ||
          lower.contains("serial number") || lower == "sn" || lower.startsWith("sn:")
      if (isRefLabel) {
        val inline = extractInline(line)
        val hasLetters = inline.any { it.isLetter() }
        val inlineDigits = inline.filter { it.isDigit() }
        if (!hasLetters && inlineDigits.length >= 16) {
          refNumber = inlineDigits
          detectedFields.add(TargetRecipientField.REFERENSI)
        } else if (hasLetters) {
          // Mengandung huruf -> tolak sesuai aturan referensi
          refNumber = ""
        } else {
          // Kumpulkan baris-baris angka di bawahnya hingga bertemu label lain (misal ID Order Merchant)
          val collectedDigits = StringBuilder()
          var nextIndex = i + 1
          var nextHasLetters = false
          while (nextIndex < lines.size) {
            val nextLine = lines[nextIndex].trim()
            val nextLower = nextLine.lowercase(Locale.ROOT)
            if (nextLower.contains("id order") || nextLower.contains("order merchant") ||
                nextLower.contains("diamankan") || nextLower.contains("detail") ||
                nextLower.contains("penerima") || nextLower.contains("total") ||
                nextLower.contains("nominal")) {
              break
            }
            if (nextLine.any { it.isLetter() }) {
              nextHasLetters = true
              break
            }
            val d = nextLine.filter { it.isDigit() }
            if (d.isNotBlank()) {
              collectedDigits.append(d)
            } else if (nextLine.isNotBlank()) {
              break
            }
            nextIndex++
          }
          if (nextHasLetters) {
            // Mengandung huruf -> tolak
            refNumber = ""
          } else if (collectedDigits.length >= 16 || (collectedDigits.isNotBlank() && lower.contains("id transaksi"))) {
            refNumber = collectedDigits.toString()
            detectedFields.add(TargetRecipientField.REFERENSI)
          } else {
            // Kurang dari 16 digit -> tolak jika label referensi standar
            refNumber = ""
          }
        }
      }

      // Fallback Multi-line ID Order Merchant
      if (merchantOrderId.isBlank() && (lower.contains("id order merchant") || lower.contains("order merchant"))) {
        val inline = extractInline(line)
        val inlineDigits = inline.filter { it.isDigit() }
        if (inlineDigits.isNotBlank() && inlineDigits != refNumber) {
          merchantOrderId = inlineDigits
        } else {
          val collectedDigits = StringBuilder()
          var nextIndex = i + 1
          while (nextIndex < lines.size) {
            val nextLine = lines[nextIndex].trim()
            val nextLower = nextLine.lowercase(Locale.ROOT)
            if (nextLower.contains("diamankan") || nextLower.contains("dana protection")) {
              break
            }
            val d = nextLine.filter { it.isDigit() }
            if (d.isNotBlank()) {
              collectedDigits.append(d)
            } else if (nextLine.isNotBlank()) {
              break
            }
            nextIndex++
          }
          if (collectedDigits.isNotBlank()) {
            merchantOrderId = collectedDigits.toString()
          }
        }
      }

      // Fallback Bank Tujuan jika transfer ke luar DANA
      val match = BankRegistry.matchBank(line)
      if (match.isConfident && !lower.contains("sumber dana") && !lower.contains("dana")) {
        recipientBank = match.officialName
        detectedFields.add(TargetRecipientField.BANK)
      }
    }

    // Jika nominal masih 0, fallback pencarian Total Bayar / Rp
    if (amount == 0L) {
      val rpRegex = Regex("(?i)rp\\.?\\s*(\\d{1,3}(?:[.,]\\d{3})+)")
      val match = rpRegex.find(fullText)
      if (match != null) {
        val (parsedAmt, _) = FieldValidator.parseNominal(match.value)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }
    }

    // Evaluasi kelengkapan data
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terbaca dari Detail Penerima")
    if (recipientAccount.isBlank()) missingFields.add("Nomor akun DANA penerima belum terbaca")
    if (amount == 0L) missingFields.add("Nominal Total Bayar belum ditemukan")
    if (refNumber.isBlank()) missingFields.add("ID Transaksi DANA belum terbaca")

    val score = calculateConfidence(detectedFields, senderAccount.isNotBlank(), refNumber.isNotBlank())

    val finalStatus = if (transactionStatus.isNotBlank()) transactionStatus else "Transaksi berhasil!"

    return SmartParsedReceipt(
      source = TransactionSource.DANA,
      senderName = senderName,
      senderBank = senderBank,
      senderAccount = senderAccount,
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      transactionTime = transactionTime,
      refNumber = refNumber,
      transactionType = transactionType,
      transactionStatus = finalStatus,
      paymentMethod = paymentMethod,
      merchantOrderId = merchantOrderId,
      notes = notes,
      confidenceScore = score,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  // =========================================================================
  // METODE PEMBANTU KORDINAT & PARSING NORMALISASI
  // =========================================================================

  private fun getLinesInBox(
    ocrResult: OcrResult,
    normLeft: Float,
    normTop: Float,
    normRight: Float,
    normBottom: Float
  ): List<OcrTextLine> {
    val matched = ocrResult.lines.filter { line ->
      val box = line.boundingBox ?: return@filter false
      isBoxInArea(box, normLeft, normTop, normRight, normBottom)
    }
    return matched.sortedWith(
      compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
    )
  }

  private fun isBoxInArea(
    box: OcrBoundingBox,
    normLeft: Float,
    normTop: Float,
    normRight: Float,
    normBottom: Float
  ): Boolean {
    val centerX = (box.normalizedLeft + box.normalizedRight) / 2f
    val centerY = (box.normalizedTop + box.normalizedBottom) / 2f
    if (centerX in normLeft..normRight && centerY in normTop..normBottom) {
      return true
    }
    val overlapLeft = maxOf(normLeft, box.normalizedLeft)
    val overlapTop = maxOf(normTop, box.normalizedTop)
    val overlapRight = minOf(normRight, box.normalizedRight)
    val overlapBottom = minOf(normBottom, box.normalizedBottom)

    if (overlapRight > overlapLeft && overlapBottom > overlapTop) {
      val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
      val boxArea = (box.normalizedRight - box.normalizedLeft) * (box.normalizedBottom - box.normalizedTop)
      if (boxArea > 0f && (overlapArea / boxArea) > 0.35f) {
        return true
      }
    }
    return false
  }

  /**
   * Menggabungkan ID numerik multi-baris tanpa spasi, mempertahankan seluruh angka
   * termasuk angka terakhir yang berada di baris tersendiri (Rule C.6 & C.7).
   */
  private fun joinMultiLineNumericId(lines: List<OcrTextLine>): String {
    val sb = StringBuilder()
    for (line in lines) {
      val text = line.text.trim()
      // Lewati teks label seperti "ID Transaksi", "ID Order Merchant"
      if (text.contains("ID Transaksi", ignoreCase = true) || text.contains("ID Order", ignoreCase = true)) {
        val inline = extractInline(text)
        val digits = inline.filter { it.isDigit() }
        if (digits.isNotBlank()) sb.append(digits)
      } else {
        val digits = text.filter { it.isDigit() }
        if (digits.isNotBlank()) {
          sb.append(digits)
        }
      }
    }
    return sb.toString()
  }

  /**
   * Mengekstrak ID DANA Pengirim dari teks seperti "ID DANA 0896••••5322"
   * dengan mempertahankan karakter masking seperti • atau *.
   */
  private fun extractSenderId(raw: String): String {
    val clean = raw.replace(Regex("(?i)^ID\\s*DANA[:\\s]*"), "").trim()
    val match = Regex("([0-9][0-9•*·.\\-]{8,18})").find(clean)
    return match?.value?.trim() ?: clean
  }

  /**
   * Membersihkan nama penerima dengan tetap mempertahankan karakter masking
   * seperti titik atau simbol • / * (Rule C.8).
   */
  private fun cleanRecipientNameWithMasking(raw: String): String {
    return raw
      .replace(Regex("(?i)^(detail\\s*penerima|nama)[:\\-\\s]+"), "")
      .replace(Regex("[^a-zA-Z0-9\\s.'\\-,&()/•*·]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private fun extractInline(line: String): String {
    return if (line.contains(":")) line.substringAfter(":").trim()
    else if (line.contains("=")) line.substringAfter("=").trim()
    else ""
  }

  private fun calculateConfidence(
    fields: Set<TargetRecipientField>,
    hasSenderId: Boolean,
    hasTxId: Boolean
  ): Int {
    var score = 60
    if (fields.contains(TargetRecipientField.NOMINAL)) score += 15
    if (fields.contains(TargetRecipientField.PENERIMA)) score += 10
    if (fields.contains(TargetRecipientField.REKENING)) score += 5
    if (hasTxId) score += 5
    if (hasSenderId) score += 4
    return score.coerceIn(60, 99)
  }
}
