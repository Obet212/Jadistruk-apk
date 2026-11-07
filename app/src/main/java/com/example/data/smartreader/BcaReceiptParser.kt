package com.example.data.smartreader

import com.example.data.model.OcrResult
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import java.util.Locale

/**
 * Parser khusus untuk bukti transfer BCA Mobile (m-BCA) dan myBCA.
 * Menangani format khas: "TRANSFER ANTAR BANK", "TRANSFER KE REK. BCA",
 * pemisahan baris DARI vs KE, label TGL & JAM, serta NO. REFERENSI.
 */
class BcaReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.BCA

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = ""
    var recipientName = ""
    var recipientBank = "BCA"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    var notes = ""
    var transactionType = "m-Transfer BCA"
    val transactionStatus = "BERHASIL"

    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val upper = line.uppercase(Locale.ROOT)

      // 1. Tipe Transaksi BCA
      if (upper.contains("TRANSFER ANTAR BANK")) {
        transactionType = "Transfer Antar Bank (BCA)"
      } else if (upper.contains("TRANSFER KE REK. BCA") || upper.contains("ANTAR REKENING BCA")) {
        transactionType = "Transfer Antar Rekening BCA"
        recipientBank = "BCA"
      }

      // 2. Pengirim ("DARI:")
      if (upper.startsWith("DARI:") || upper.startsWith("DARI ")) {
        val candidate = line.replace(Regex("(?i)^dari[:\\s]+"), "").trim()
        if (candidate.isNotBlank()) senderName = candidate
      }

      // 3. Penerima & Rekening ("KE:" atau "NAMA PENERIMA:")
      if (upper.startsWith("KE:") || upper.startsWith("KE ") || upper.contains("NAMA PENERIMA")) {
        val inline = line.replace(Regex("(?i)^(?:ke|nama\\s*penerima|nama)[:\\s]+"), "").trim()
        val digits = inline.filter { it.isDigit() }
        if (digits.length >= 8 && digits.length <= 15 && recipientAccount.isBlank()) {
          recipientAccount = digits
          detectedFields.add(TargetRecipientField.REKENING)
        }
        
        // Cek baris berikutnya untuk nama penerima (format m-BCA: KE: 1234567890 \n BANK \n NAMA PENERIMA)
        val candidate = if (inline.any { it.isLetter() } && inline != "BCA") inline else {
          var foundName = ""
          for (offset in 1..3) {
            val nextLine = lines.getOrNull(i + offset)?.trim() ?: break
            val nextUpper = nextLine.uppercase(Locale.ROOT)
            if (nextUpper.contains("JUMLAH") || nextUpper.contains("TANGGAL") || nextUpper.contains("BERITA")) break
            val (cleanCandidate, _) = FieldValidator.sanitizeRecipientName(nextLine)
            if (cleanCandidate.isNotBlank() && cleanCandidate != "BCA" && cleanCandidate.length >= 3) {
              foundName = cleanCandidate
              break
            }
          }
          foundName
        }

        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank() && cleanName != "BCA") {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      // 4. Bank Tujuan
      if (upper.contains("BANK TUJUAN") || upper.startsWith("BANK:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val match = BankRegistry.matchBank(candidate)
        if (match.isConfident) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      } else {
        val match = BankRegistry.matchBank(line)
        if (match.isConfident && !upper.contains("DARI") && !upper.contains("BCA")) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      }

      // 5. Nomor Rekening Tujuan (10–15 digit)
      if (upper.contains("NO. REKENING") || upper.contains("NO REK") || upper.contains("REKENING TUJUAN")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanAcc, _) = FieldValidator.sanitizeAccount(candidate, refNumber)
        if (cleanAcc.isNotBlank()) {
          recipientAccount = cleanAcc
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      // 6. Nominal ("JUMLAH:" / "NOMINAL:")
      if (upper.contains("JUMLAH:") || upper.contains("JUMLAH ") || upper.contains("NOMINAL:") || upper.contains("NOMINAL ")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      // 7. Biaya Admin
      if (upper.contains("BIAYA ADMIN") || upper.contains("BIAYA TRANSFER") || upper.contains("ADMIN:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      // 8. Tanggal & Jam ("TGL:" dan "JAM:")
      if (upper.contains("TGL:") || upper.contains("TANGGAL:") || upper.contains("TANGGAL")) {
        val (d, _) = FieldValidator.sanitizeDate(line)
        if (d.isNotBlank()) {
          transactionDate = d
          detectedFields.add(TargetRecipientField.TANGGAL)
        }
      }
      if (upper.contains("JAM:") || upper.contains("WAKTU:") || upper.contains("JAM ")) {
        val (t, _) = FieldValidator.sanitizeTime(line)
        if (t.isNotBlank()) {
          transactionTime = t
          detectedFields.add(TargetRecipientField.WAKTU)
        }
      }

      // 9. No. Referensi
      if (upper.contains("NO. REFERENSI") || upper.contains("NO REFERENSI") || upper.contains("REFERENSI:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }

      // 10. Berita / Catatan
      if (upper.contains("BERITA:") || upper.contains("BERITA ") || upper.contains("CATATAN:")) {
        val inline = extractInline(line)
        if (inline.isNotBlank() && inline != "-") notes = inline
      }
    }

    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (recipientAccount.isBlank()) missingFields.add("Nomor rekening tujuan belum terbaca")
    if (amount == 0L) missingFields.add("Nominal transfer belum ditemukan")
    if (refNumber.isBlank()) reviewWarnings.add("Nomor referensi BCA belum terbaca atau di bawah 16 digit")

    val score = calculateConfidence(detectedFields)

    return SmartParsedReceipt(
      source = TransactionSource.BCA,
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
    var score = 55
    if (fields.contains(TargetRecipientField.NOMINAL)) score += 15
    if (fields.contains(TargetRecipientField.PENERIMA)) score += 15
    if (fields.contains(TargetRecipientField.REKENING)) score += 10
    if (fields.contains(TargetRecipientField.REFERENSI)) score += 10
    return score.coerceIn(55, 99)
  }
}
