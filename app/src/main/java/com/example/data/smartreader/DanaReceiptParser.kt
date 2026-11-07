package com.example.data.smartreader

import com.example.data.model.OcrResult
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import java.util.Locale

/**
 * Parser khusus untuk bukti transaksi dompet digital DANA ("Kirim Uang", "DANA Protection").
 * Mengekstrak penerima, bank tujuan, nomor HP/rekening, nominal bayar, biaya admin,
 * ID transaksi, serta tanggal dan waktu secara presisi.
 */
class DanaReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.DANA

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = "Akun DANA"
    var recipientName = ""
    var recipientBank = "DANA"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    var notes = ""
    var transactionType = "Kirim Uang DANA"
    val transactionStatus = "Berhasil"

    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      // 1. Penerima & Bank Tujuan pada DANA
      // Pola: "Kirim Uang ke [Nama]", "Penerima: [Nama]", atau baris setelah "Tujuan"
      if (lower.startsWith("kirim uang ke") || lower.startsWith("transfer ke")) {
        val inline = line.replace(Regex("(?i)^(?:kirim\\s*uang\\s*ke|transfer\\s*ke)[:\\s]+"), "").trim()
        if (inline.isNotBlank()) {
          val (cleanName, _) = FieldValidator.sanitizeRecipientName(inline)
          if (cleanName.isNotBlank()) recipientName = cleanName
        }
      } else if (lower == "penerima" || lower.startsWith("penerima:") || lower == "nama tujuan") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) recipientName = cleanName
      }

      // Deteksi Bank Tujuan jika ditransfer ke rekening bank (bukan sesama DANA)
      val bankMatch = BankRegistry.matchBank(line)
      if (bankMatch.isConfident && !lower.contains("sumber dana")) {
        recipientBank = bankMatch.officialName
        transactionType = "Kirim Uang ke ${bankMatch.officialName}"
      }

      // 2. Nomor Rekening atau Nomor HP Tujuan (08xx)
      // DANA sering menampilkan nomor HP "0812xxxx" atau nomor rekening bank
      val digitsOnly = line.filter { it.isDigit() }
      if (recipientAccount.isBlank()) {
        if (digitsOnly.length in 10..15) {
          // Cek apakah label di atasnya rekening / akun
          val prevLine = lines.getOrNull(i - 1)?.lowercase(Locale.ROOT) ?: ""
          if (prevLine.contains("rekening") || prevLine.contains("nomor") || prevLine.contains("akun") || digitsOnly.startsWith("08")) {
            recipientAccount = digitsOnly
            detectedFields.add(TargetRecipientField.REKENING)
          }
        }
      }

      // 3. Nominal Transaksi
      // DANA: "Total Pembayaran", "Jumlah Transfer", "Total Bayar"
      if (lower.contains("total pembayaran") || lower.contains("jumlah transfer") || lower.contains("total bayar") || lower == "nominal") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      // 4. Biaya Admin
      // DANA: "Biaya Transaksi", "Bebas Biaya", "Biaya Transfer"
      if (lower.contains("biaya transaksi") || lower.contains("biaya transfer") || lower.contains("biaya admin")) {
        if (lower.contains("bebas biaya") || lower.contains("gratis") || lower.contains("rp 0") || lower.contains("rp0")) {
          adminFee = 0L
          detectedFields.add(TargetRecipientField.ADMIN)
        } else {
          val inline = extractInline(line)
          val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
          val (parsedFee, _) = FieldValidator.parseNominal(candidate)
          if (parsedFee >= 0L) {
            adminFee = parsedFee
            detectedFields.add(TargetRecipientField.ADMIN)
          }
        }
      }

      // 5. Tanggal & Waktu Transaksi
      if (lower.contains("tanggal") || lower.contains("waktu") || lower.contains("tgl")) {
        val (d, _) = FieldValidator.sanitizeDate(line)
        if (d.isNotBlank()) {
          transactionDate = d
          detectedFields.add(TargetRecipientField.TANGGAL)
        }
        val (t, _) = FieldValidator.sanitizeTime(line)
        if (t.isNotBlank()) {
          transactionTime = t
          detectedFields.add(TargetRecipientField.WAKTU)
        }
        // Jika pada baris berikutnya
        if (transactionDate.isBlank() && i + 1 < lines.size) {
          val next = lines[i + 1]
          val (dNext, _) = FieldValidator.sanitizeDate(next)
          if (dNext.isNotBlank()) {
            transactionDate = dNext
            detectedFields.add(TargetRecipientField.TANGGAL)
          }
          val (tNext, _) = FieldValidator.sanitizeTime(next)
          if (tNext.isNotBlank()) {
            transactionTime = tNext
            detectedFields.add(TargetRecipientField.WAKTU)
          }
        }
      }

      // 6. ID Transaksi (Nomor Referensi DANA >= 16 digit murni angka)
      if (lower.contains("id transaksi") || lower.contains("nomor transaksi") || lower.contains("no. transaksi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }

      // 7. Catatan
      if (lower.contains("catatan") || lower.contains("berita")) {
        val inline = extractInline(line)
        if (inline.isNotBlank() && inline != "-") notes = inline
      }
    }

    // Fallback jika nominal belum terdeteksi (cari angka Rp terbesar yang bukan saldo)
    if (amount == 0L) {
      for (line in lines) {
        if (line.contains("Rp", ignoreCase = true) && !line.contains("saldo", ignoreCase = true)) {
          val (parsed, _) = FieldValidator.parseNominal(line)
          if (parsed > 0L) {
            amount = parsed
            detectedFields.add(TargetRecipientField.NOMINAL)
            break
          }
        }
      }
    }

    // Validasi kelengkapan
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi otomatis")
    if (amount == 0L) missingFields.add("Nominal transfer belum ditemukan")
    if (refNumber.isBlank()) reviewWarnings.add("ID transaksi DANA belum terbaca atau kurang dari 16 digit")

    val score = calculateConfidence(detectedFields)

    return SmartParsedReceipt(
      source = TransactionSource.DANA,
      senderName = senderName,
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
      confidenceScore = score,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  private fun extractInline(line: String): String {
    return if (line.contains(":")) line.substringAfter(":").trim()
    else if (line.contains("=")) line.substringAfter("=").trim()
    else ""
  }

  private fun calculateConfidence(fields: Set<TargetRecipientField>): Int {
    var score = 50
    if (fields.contains(TargetRecipientField.NOMINAL)) score += 15
    if (fields.contains(TargetRecipientField.PENERIMA)) score += 15
    if (fields.contains(TargetRecipientField.REKENING)) score += 10
    if (fields.contains(TargetRecipientField.REFERENSI)) score += 10
    return score.coerceIn(50, 99)
  }
}
