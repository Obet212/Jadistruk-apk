package com.example.data.smartreader

import com.example.data.model.OcrResult
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import java.util.Locale

/**
 * Parser khusus untuk bukti transaksi Livin' by Mandiri (Bank Mandiri).
 * Menangani tata letak: "Resi Transfer", "Rekening Sumber", "Tujuan Transfer",
 * "Waktu Selesai", dan "Nomor Resi / Referensi".
 */
class LivinReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.LIVIN

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = ""
    var recipientName = ""
    var recipientBank = "MANDIRI"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    var notes = ""
    val transactionType = "Resi Transfer Mandiri"
    val transactionStatus = "Berhasil"

    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      // 1. Rekening Sumber / Pengirim
      if (lower.contains("rekening sumber") || lower.contains("dari rekening")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.isNotBlank()) senderName = candidate
      }

      // 2. Penerima
      if (lower == "tujuan transfer" || lower == "nama penerima" || lower.startsWith("nama penerima:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      // 3. Bank Tujuan
      if (lower.contains("bank tujuan") || lower.startsWith("ke bank")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val match = BankRegistry.matchBank(candidate)
        if (match.isConfident) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      } else {
        val match = BankRegistry.matchBank(line)
        if (match.isConfident && !lower.contains("sumber") && !lower.contains("mandiri")) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      }

      // 4. Nomor Rekening Tujuan (10–15 digit)
      if (lower.contains("nomor rekening") || lower.contains("no. rekening") || lower == "rekening tujuan") {
        if (!lower.contains("sumber")) {
          val inline = extractInline(line)
          val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
          val (cleanAcc, _) = FieldValidator.sanitizeAccount(candidate, refNumber)
          if (cleanAcc.isNotBlank()) {
            recipientAccount = cleanAcc
            detectedFields.add(TargetRecipientField.REKENING)
          }
        }
      }

      // 5. Nominal Transfer
      if (lower.contains("nominal transfer") || lower.contains("jumlah transfer") || lower == "nominal") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      // 6. Biaya Transfer / Admin
      if (lower.contains("biaya transfer") || lower.contains("biaya transaksi") || lower.contains("biaya admin")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      // 7. Waktu Selesai / Tanggal
      if (lower.contains("waktu selesai") || lower.contains("tanggal") || lower.contains("waktu transaksi")) {
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

      // 8. Nomor Resi / Referensi
      if (lower.contains("nomor resi") || lower.contains("no. resi") || lower.contains("nomor referensi") || lower.contains("no. referensi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }

      // 9. Catatan
      if (lower.contains("catatan") || lower.contains("keterangan")) {
        val inline = extractInline(line)
        if (inline.isNotBlank() && inline != "-") notes = inline
      }
    }

    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (recipientAccount.isBlank()) missingFields.add("Nomor rekening tujuan belum terbaca")
    if (amount == 0L) missingFields.add("Nominal transfer belum ditemukan")
    if (refNumber.isBlank()) reviewWarnings.add("Nomor resi/referensi Mandiri belum terbaca atau di bawah 16 digit")

    val score = calculateConfidence(detectedFields)

    return SmartParsedReceipt(
      source = TransactionSource.LIVIN,
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
