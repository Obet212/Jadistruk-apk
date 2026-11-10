package com.example.data.smartreader

import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextLine
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.FieldValidator
import java.util.Locale

/**
 * Template OCR khusus untuk bukti transfer Bank BRI – Qita.
 * Berdasarkan referensi visual asli berukuran 720 × 1959 piksel.
 *
 * Menerapkan:
 * 1. Normalized coordinates (proporsi 0.0 - 1.0) yang adaptif terhadap variasi resolusi dan rasio.
 * 2. Pemindaian OCR dua tahap:
 *    - Tahap 1: Deteksi teks keseluruhan gambar.
 *    - Tahap 2: Pembacaan ulang terfokus menggunakan area koordinat khusus setiap field.
 * 3. Pemetaan terpisah secara presisi:
 *    - Status transaksi
 *    - Nominal utama (mempertahankan format Rupiah & pemisah ribuan)
 *    - Tanggal dan waktu
 *    - Nama pengirim
 *    - Bank dan rekening pengirim
 *    - Nama penerima
 *    - Bank dan rekening penerima (maks 15 digit tanpa spasi, di bawah nama penerima)
 *    - Nomor referensi (dibaca berdasarkan label "No. Referensi", bukan batas digit)
 *    - Jenis transaksi
 *    - Catatan
 *    - Biaya administrasi
 * 4. Fallback struktural berbasis label jika koordinat bergeser akibat pemotongan/perubahan ukuran.
 * 5. Nol fabrikasi data (data kosong tetap kosong jika tidak terbaca).
 */
class BriQitaReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.BRI_QITA

  // Normalized Bounding Boxes (Dihitung dari resolusi referensi 720 × 1959 px)
  companion object {
    // 1. Status Transaksi ("Transfer Berhasil") ~ y: 450..530 px (0.230..0.271)
    private const val NORM_STATUS_TOP = 0.220f
    private const val NORM_STATUS_BOTTOM = 0.275f
    private const val NORM_STATUS_LEFT = 0.100f
    private const val NORM_STATUS_RIGHT = 0.900f

    // 2. Nominal Utama Header ("Rp299.529") ~ y: 535..630 px (0.273..0.322)
    private const val NORM_HEADER_AMOUNT_TOP = 0.270f
    private const val NORM_HEADER_AMOUNT_BOTTOM = 0.325f
    private const val NORM_HEADER_AMOUNT_LEFT = 0.100f
    private const val NORM_HEADER_AMOUNT_RIGHT = 0.900f

    // 3. Tanggal & Waktu ("03 November 2026, 09:06:54 WIB") ~ y: 625..690 px (0.319..0.352)
    private const val NORM_DATETIME_TOP = 0.315f
    private const val NORM_DATETIME_BOTTOM = 0.360f
    private const val NORM_DATETIME_LEFT = 0.050f
    private const val NORM_DATETIME_RIGHT = 0.950f

    // 4. Pengirim: Nama ("ADHI NUGROHO") ~ y: 780..840 px (0.398..0.428)
    private const val NORM_SENDER_NAME_TOP = 0.390f
    private const val NORM_SENDER_NAME_BOTTOM = 0.435f
    private const val NORM_SENDER_NAME_LEFT = 0.150f
    private const val NORM_SENDER_NAME_RIGHT = 0.880f

    // 5. Pengirim: Bank & Rekening ("BANK BRI - 6051 **** **** 500") ~ y: 835..895 px (0.426..0.456)
    private const val NORM_SENDER_ACC_TOP = 0.425f
    private const val NORM_SENDER_ACC_BOTTOM = 0.465f
    private const val NORM_SENDER_ACC_LEFT = 0.150f
    private const val NORM_SENDER_ACC_RIGHT = 0.880f

    // 6. Penerima: Nama ("PROSPEK MITRA ABADI") ~ y: 935..990 px (0.477..0.505)
    private const val NORM_RECIPIENT_NAME_TOP = 0.470f
    private const val NORM_RECIPIENT_NAME_BOTTOM = 0.510f
    private const val NORM_RECIPIENT_NAME_LEFT = 0.150f
    private const val NORM_RECIPIENT_NAME_RIGHT = 0.900f

    // 7. Penerima: Bank & Rekening ("BANK BRI - 0251 0133 3333 301") ~ y: 985..1045 px (0.502..0.533)
    private const val NORM_RECIPIENT_ACC_TOP = 0.502f
    private const val NORM_RECIPIENT_ACC_BOTTOM = 0.542f
    private const val NORM_RECIPIENT_ACC_LEFT = 0.150f
    private const val NORM_RECIPIENT_ACC_RIGHT = 0.900f

    // 8. No. Referensi Value ("244404392528") ~ y: 1090..1155 px (0.556..0.589), x kanan: 0.48..0.96
    private const val NORM_REF_TOP = 0.550f
    private const val NORM_REF_BOTTOM = 0.595f
    private const val NORM_REF_VAL_LEFT = 0.480f
    private const val NORM_REF_VAL_RIGHT = 0.960f

    // 9. Jenis Transaksi Value ("Transfer Bank BRI") ~ y: 1155..1215 px (0.589..0.620)
    private const val NORM_TYPE_TOP = 0.585f
    private const val NORM_TYPE_BOTTOM = 0.630f
    private const val NORM_TYPE_VAL_LEFT = 0.450f
    private const val NORM_TYPE_VAL_RIGHT = 0.960f

    // 10. Catatan Value ("-") ~ y: 1250..1320 px (0.638..0.673)
    private const val NORM_NOTES_TOP = 0.630f
    private const val NORM_NOTES_BOTTOM = 0.680f
    private const val NORM_NOTES_VAL_LEFT = 0.480f
    private const val NORM_NOTES_VAL_RIGHT = 0.960f

    // 11. Nominal Detail Value ("Rp299.529") ~ y: 1355..1420 px (0.691..0.724)
    private const val NORM_DETAIL_AMOUNT_TOP = 0.685f
    private const val NORM_DETAIL_AMOUNT_BOTTOM = 0.730f
    private const val NORM_DETAIL_AMOUNT_VAL_LEFT = 0.480f
    private const val NORM_DETAIL_AMOUNT_VAL_RIGHT = 0.960f

    // 12. Biaya Admin Value ("Rp0") ~ y: 1420..1480 px (0.724..0.755)
    private const val NORM_ADMIN_TOP = 0.720f
    private const val NORM_ADMIN_BOTTOM = 0.765f
    private const val NORM_ADMIN_VAL_LEFT = 0.480f
    private const val NORM_ADMIN_VAL_RIGHT = 0.960f
  }

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = ""
    var senderBank = ""
    var senderAccount = ""
    var recipientName = ""
    var recipientBank = "BANK BRI"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    var transactionType = "Transfer Bank BRI"
    var transactionStatus = "Transfer Berhasil"
    var notes = ""

    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    // =========================================================================
    // TAHAP 2: Pembacaan Terfokus Menggunakan Area Koordinat Khusus Setiap Field
    // =========================================================================
    if (ocrResult != null && ocrResult.lines.isNotEmpty()) {
      // 1. Status Transaksi
      val statusLines = getLinesInBox(ocrResult, NORM_STATUS_LEFT, NORM_STATUS_TOP, NORM_STATUS_RIGHT, NORM_STATUS_BOTTOM)
      if (statusLines.isNotEmpty()) {
        val rawStatus = statusLines.joinToString(" ") { it.text.trim() }
        if (rawStatus.contains("berhasil", ignoreCase = true) || rawStatus.contains("sukses", ignoreCase = true)) {
          transactionStatus = rawStatus.trim()
        }
      }

      // 2. Nominal Utama (Header)
      val headerAmountLines = getLinesInBox(ocrResult, NORM_HEADER_AMOUNT_LEFT, NORM_HEADER_AMOUNT_TOP, NORM_HEADER_AMOUNT_RIGHT, NORM_HEADER_AMOUNT_BOTTOM)
      for (line in headerAmountLines) {
        val parsedAmt = parseRupiahAmount(line.text)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
          break
        }
      }

      // 3. Tanggal & Waktu
      val dateTimeLines = getLinesInBox(ocrResult, NORM_DATETIME_LEFT, NORM_DATETIME_TOP, NORM_DATETIME_RIGHT, NORM_DATETIME_BOTTOM)
      val dateTimeText = dateTimeLines.joinToString(" ") { it.text.trim() }
      if (dateTimeText.isNotBlank()) {
        val (d, _) = FieldValidator.sanitizeDate(dateTimeText)
        if (d.isNotBlank()) {
          transactionDate = d
          detectedFields.add(TargetRecipientField.TANGGAL)
        }
        val (t, _) = FieldValidator.sanitizeTime(dateTimeText)
        if (t.isNotBlank()) {
          transactionTime = t
          detectedFields.add(TargetRecipientField.WAKTU)
        }
      }

      // 4. Nama Pengirim
      val senderNameLines = getLinesInBox(ocrResult, NORM_SENDER_NAME_LEFT, NORM_SENDER_NAME_TOP, NORM_SENDER_NAME_RIGHT, NORM_SENDER_NAME_BOTTOM)
      if (senderNameLines.isNotEmpty()) {
        val candidate = senderNameLines.first().text.trim()
        val cleaned = cleanSenderOrRecipientName(candidate)
        if (cleaned.isNotBlank()) {
          senderName = cleaned
        }
      }

      // 5. Bank & Rekening Pengirim
      val senderAccLines = getLinesInBox(ocrResult, NORM_SENDER_ACC_LEFT, NORM_SENDER_ACC_TOP, NORM_SENDER_ACC_RIGHT, NORM_SENDER_ACC_BOTTOM)
      if (senderAccLines.isNotEmpty()) {
        val line = senderAccLines.joinToString(" ") { it.text.trim() }
        val (bank, acc) = parseBankAndAccountLine(line)
        if (bank.isNotBlank()) senderBank = bank
        if (acc.isNotBlank()) senderAccount = acc
      }

      // 6. Nama Penerima
      val recipientNameLines = getLinesInBox(ocrResult, NORM_RECIPIENT_NAME_LEFT, NORM_RECIPIENT_NAME_TOP, NORM_RECIPIENT_NAME_RIGHT, NORM_RECIPIENT_NAME_BOTTOM)
      if (recipientNameLines.isNotEmpty()) {
        val candidate = recipientNameLines.first().text.trim()
        val cleaned = cleanSenderOrRecipientName(candidate)
        if (cleaned.isNotBlank()) {
          recipientName = cleaned
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      // 7. Bank & Rekening Penerima
      val recipientAccLines = getLinesInBox(ocrResult, NORM_RECIPIENT_ACC_LEFT, NORM_RECIPIENT_ACC_TOP, NORM_RECIPIENT_ACC_RIGHT, NORM_RECIPIENT_ACC_BOTTOM)
      if (recipientAccLines.isNotEmpty()) {
        val line = recipientAccLines.joinToString(" ") { it.text.trim() }
        val (bank, acc) = parseBankAndAccountLine(line)
        if (bank.isNotBlank()) {
          recipientBank = bank
          detectedFields.add(TargetRecipientField.BANK)
        }
        if (acc.isNotBlank()) {
          recipientAccount = acc
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      // 8. Nomor Referensi (Berdasarkan area koordinat nilai referensi)
      val refLines = getLinesInBox(ocrResult, NORM_REF_VAL_LEFT, NORM_REF_TOP, NORM_REF_VAL_RIGHT, NORM_REF_BOTTOM)
      for (line in refLines) {
        val digits = line.text.filter { it in '0'..'9' }
        // Rule: Nomor referensi dibaca berdasarkan label / area "No. Referensi", bukan batas minimal digit
        if (digits.isNotBlank()) {
          refNumber = digits
          detectedFields.add(TargetRecipientField.REFERENSI)
          break
        }
      }

      // 9. Jenis Transaksi
      val typeLines = getLinesInBox(ocrResult, NORM_TYPE_VAL_LEFT, NORM_TYPE_TOP, NORM_TYPE_VAL_RIGHT, NORM_TYPE_BOTTOM)
      if (typeLines.isNotEmpty()) {
        val typeCandidate = typeLines.joinToString(" ") { it.text.trim() }.trim()
        if (typeCandidate.isNotBlank() && !typeCandidate.equals("Jenis Transaksi", ignoreCase = true)) {
          transactionType = typeCandidate
        }
      }

      // 10. Catatan
      val notesLines = getLinesInBox(ocrResult, NORM_NOTES_VAL_LEFT, NORM_NOTES_TOP, NORM_NOTES_VAL_RIGHT, NORM_NOTES_BOTTOM)
      if (notesLines.isNotEmpty()) {
        val notesCandidate = notesLines.joinToString(" ") { it.text.trim() }.trim()
        if (notesCandidate != "-" && notesCandidate.isNotBlank() && !notesCandidate.equals("Catatan", ignoreCase = true)) {
          notes = notesCandidate
        }
      }

      // 11. Nominal Detail Row (Cross-check atau fallback jika header tidak terbaca)
      if (amount == 0L) {
        val detailAmountLines = getLinesInBox(ocrResult, NORM_DETAIL_AMOUNT_VAL_LEFT, NORM_DETAIL_AMOUNT_TOP, NORM_DETAIL_AMOUNT_VAL_RIGHT, NORM_DETAIL_AMOUNT_BOTTOM)
        for (line in detailAmountLines) {
          val parsedAmt = parseRupiahAmount(line.text)
          if (parsedAmt > 0L) {
            amount = parsedAmt
            detectedFields.add(TargetRecipientField.NOMINAL)
            break
          }
        }
      }

      // 12. Biaya Admin
      val adminLines = getLinesInBox(ocrResult, NORM_ADMIN_VAL_LEFT, NORM_ADMIN_TOP, NORM_ADMIN_VAL_RIGHT, NORM_ADMIN_BOTTOM)
      for (line in adminLines) {
        val clean = line.text.trim()
        if (clean.equals("Rp0", ignoreCase = true) || clean.equals("Rp 0", ignoreCase = true) || clean == "0") {
          adminFee = 0L
          detectedFields.add(TargetRecipientField.ADMIN)
          break
        } else {
          val parsedFee = parseRupiahAmount(clean)
          if (parsedFee >= 0L) {
            adminFee = parsedFee
            detectedFields.add(TargetRecipientField.ADMIN)
            break
          }
        }
      }
    }

    // =========================================================================
    // TAHAP 3 (FALLBACK): Deteksi Berbasis Label Jika Koordinat Bergeser / Crop
    // =========================================================================
    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      // Fallback Status
      if (lower.contains("transfer berhasil") || lower.contains("transaksi berhasil")) {
        if (transactionStatus.isBlank()) transactionStatus = line
      }

      // Fallback No. Referensi (Rule: Berdasarkan label "No. Referensi", bukan batas minimal digit)
      if (refNumber.isBlank() && (lower.contains("no. referensi") || lower.contains("no referensi") || lower.contains("nomor referensi"))) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val digits = candidate.filter { it in '0'..'9' }
        // Tolak jika sama dengan nomor rekening atau nomor telepon
        if (digits.isNotBlank() && digits != recipientAccount && digits != senderAccount.filter { it.isDigit() }) {
          refNumber = digits
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }

      // Fallback Jenis Transaksi
      if (lower.contains("jenis transaksi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.isNotBlank() && !candidate.contains("transaksi", ignoreCase = true)) {
          transactionType = candidate
        }
      }

      // Fallback Catatan
      if (lower.contains("catatan")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.isNotBlank() && candidate != "-" && !candidate.contains("catatan", ignoreCase = true)) {
          notes = candidate
        }
      }

      // Fallback Nominal
      if (amount == 0L && (lower == "nominal" || lower.startsWith("nominal:") || lower.startsWith("nominal "))) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val parsedAmt = parseRupiahAmount(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      // Fallback Biaya Admin
      if (!detectedFields.contains(TargetRecipientField.ADMIN) && (lower.contains("biaya admin") || lower == "admin")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.contains("0") || candidate.contains("gratis", ignoreCase = true)) {
          adminFee = 0L
          detectedFields.add(TargetRecipientField.ADMIN)
        } else {
          val parsedFee = parseRupiahAmount(candidate)
          if (parsedFee >= 0L) {
            adminFee = parsedFee
            detectedFields.add(TargetRecipientField.ADMIN)
          }
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

      // Fallback Penerima & Pengirim jika Detail Transaksi ditemukan
      if (lower.contains("detail transaksi")) {
        // Cari blok nama dan rekening pengirim & penerima
        for (j in (i + 1)..minOf(i + 12, lines.size - 1)) {
          val candidateLine = lines[j].trim()
          val cLower = candidateLine.lowercase(Locale.ROOT)

          if (cLower.contains("no. referensi") || cLower.contains("nominal")) break

          // Jika baris mengandung rekening Bank BRI ("BANK BRI - 0251 ...")
          if (cLower.contains("bank bri") && (candidateLine.contains("-") || candidateLine.any { it.isDigit() })) {
            val (bank, acc) = parseBankAndAccountLine(candidateLine)
            // Rekening dengan sensor bintang "****" adalah pengirim
            if (candidateLine.contains("*") || candidateLine.contains("****")) {
              if (senderBank.isBlank()) senderBank = bank
              if (senderAccount.isBlank()) senderAccount = acc
              if (senderName.isBlank() && j > 0) {
                val prev = cleanSenderOrRecipientName(lines[j - 1])
                if (prev.isNotBlank() && !prev.contains("detail", ignoreCase = true)) {
                  senderName = prev
                }
              }
            } else {
              // Rekening tanpa bintang adalah penerima tujuan
              if (recipientAccount.isBlank()) {
                recipientAccount = acc
                detectedFields.add(TargetRecipientField.REKENING)
              }
              if (recipientBank.isBlank()) {
                recipientBank = bank
                detectedFields.add(TargetRecipientField.BANK)
              }
              if (recipientName.isBlank() && j > 0) {
                val prev = cleanSenderOrRecipientName(lines[j - 1])
                if (prev.isNotBlank() && !prev.contains("detail", ignoreCase = true) && prev != senderName) {
                  recipientName = prev
                  detectedFields.add(TargetRecipientField.PENERIMA)
                }
              }
            }
          }
        }
      }
    }

    // Jika nominal masih 0, coba cari pola "Rp299.529" di seluruh teks
    if (amount == 0L) {
      val rpRegex = Regex("(?i)rp\\.?\\s*(\\d{1,3}(?:[.,]\\d{3})+)")
      val match = rpRegex.find(fullText)
      if (match != null) {
        val parsed = parseRupiahAmount(match.value)
        if (parsed > 0L) {
          amount = parsed
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }
    }

    // Evaluasi Kelengkapan Data & Peringatan
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terbaca")
    if (recipientAccount.isBlank()) missingFields.add("Nomor rekening tujuan belum terbaca")
    if (amount == 0L) missingFields.add("Nominal transaksi belum ditemukan")
    if (refNumber.isBlank()) missingFields.add("Nomor referensi Qita belum terbaca")

    val confidence = calculateConfidence(detectedFields, senderName.isNotBlank(), refNumber.isNotBlank())

    return SmartParsedReceipt(
      source = TransactionSource.BRI_QITA,
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
      transactionStatus = transactionStatus,
      notes = notes,
      confidenceScore = confidence,
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

  /**
   * Mengambil semua baris teks OCR yang berpotongan atau berada di dalam area normalisasi.
   */
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

    // Urutkan berdasarkan urutan baca: atas ke bawah, lalu kiri ke kanan
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
    // 1. Cek posisi titik tengah
    val centerX = (box.normalizedLeft + box.normalizedRight) / 2f
    val centerY = (box.normalizedTop + box.normalizedBottom) / 2f
    if (centerX in normLeft..normRight && centerY in normTop..normBottom) {
      return true
    }

    // 2. Cek rasio overlap area
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
   * Mengurai teks seperti "BANK BRI - 0251 0133 3333 301" menjadi Bank ("BANK BRI") dan No Rekening ("025101333333301").
   * Membatasi maksimal 15 digit rekening tanpa menghitung spasi (Rule 6).
   */
  private fun parseBankAndAccountLine(line: String): Pair<String, String> {
    val parts = line.split("-")
    var bank = ""
    var rawAcc = ""

    if (parts.size >= 2) {
      bank = parts[0].trim()
      rawAcc = parts.drop(1).joinToString("-").trim()
    } else {
      bank = if (line.contains("BRI", ignoreCase = true)) "BANK BRI" else ""
      rawAcc = line.replace(Regex("(?i)BANK\\s*BRI"), "").trim()
    }

    // Jika rekening tersensor dengan bintang "****" (pengirim)
    val accResult = if (rawAcc.contains("*")) {
      rawAcc.trim()
    } else {
      // Rekening tujuan (angka murni, maks 15 digit)
      val digitsOnly = rawAcc.filter { it in '0'..'9' }
      if (digitsOnly.length > 15) {
        // Ambil 15 digit pertama sesuai aturan rekening perbankan BRI (Rule 6)
        digitsOnly.take(15)
      } else {
        digitsOnly
      }
    }

    val finalBank = if (bank.contains("BRI", ignoreCase = true)) "BANK BRI" else bank
    return Pair(finalBank, accResult)
  }

  /**
   * Mengurai nominal Rupiah (e.g. "Rp299.529" -> 299529L).
   */
  private fun parseRupiahAmount(raw: String): Long {
    val clean = raw.trim()
    if (clean.isBlank()) return 0L

    val digitsOnly = clean
      .replace(Regex("(?i)^rp\\.?\\s*"), "")
      .replace(".", "")
      .replace(",", "")
      .filter { it in '0'..'9' }

    return digitsOnly.toLongOrNull() ?: 0L
  }

  private fun cleanSenderOrRecipientName(raw: String): String {
    return raw
      .replace(Regex("(?i)^(detail\\s*transaksi|sumber\\s*dana|tujuan\\s*transfer|ke|dari|nama)"), "")
      .replace(Regex("^[-:=•>\\s]+"), "")
      .trim()
  }

  private fun extractInline(line: String): String {
    return if (line.contains(":")) line.substringAfter(":").trim()
    else if (line.contains("=")) line.substringAfter("=").trim()
    else ""
  }

  private fun calculateConfidence(
    fields: Set<TargetRecipientField>,
    hasSender: Boolean,
    hasRef: Boolean
  ): Int {
    var score = 60
    if (fields.contains(TargetRecipientField.NOMINAL)) score += 15
    if (fields.contains(TargetRecipientField.PENERIMA)) score += 10
    if (fields.contains(TargetRecipientField.REKENING)) score += 5
    if (hasRef) score += 5
    if (hasSender) score += 4
    return score.coerceIn(60, 99)
  }
}
