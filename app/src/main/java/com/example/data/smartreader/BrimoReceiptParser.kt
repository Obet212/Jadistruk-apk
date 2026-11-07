package com.example.data.smartreader

import com.example.data.model.OcrResult
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import java.util.Locale

/**
 * Parser khusus untuk bukti transaksi mobile banking BRImo (Bank Rakyat Indonesia).
 * Memisahkan secara presisi antara Rekening Sumber (Pengirim) vs Rekening Tujuan (Penerima),
 * Nominal Transfer vs Biaya Admin, serta Nomor Referensi Transaksi.
 */
class BrimoReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.BRIMO

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = ""
    var recipientName = ""
    var recipientBank = "BRI"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    var notes = ""
    val transactionType = "Transfer BRImo"
    val transactionStatus = "Berhasil"

    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      // 1. Rekening Sumber / Pengirim
      if (lower.contains("sumber dana") || lower.contains("rekening sumber") || lower.contains("dari rekening")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.isNotBlank()) {
          senderName = candidate
        }
      }

      // 2. Penerima & Bank Tujuan
      // BRImo: "Penerima", "Tujuan Transfer", "Nama Tujuan", "Ke Rekening"
      if (lower == "penerima" || lower.startsWith("penerima:") || lower.contains("tujuan transfer") || lower == "nama tujuan") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      // Deteksi Bank Tujuan
      if (lower.contains("bank tujuan") || lower.startsWith("ke bank")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val match = BankRegistry.matchBank(candidate)
        if (match.isConfident) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      } else {
        // Cek nama bank jika baris berisi nama bank resmi selain BRI
        val match = BankRegistry.matchBank(line)
        if (match.isConfident && !lower.contains("sumber") && !lower.contains("bri")) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      }

      // 3. Nomor Rekening Tujuan (10–15 digit)
      if (lower.contains("nomor rekening") || lower.contains("no. rekening") || lower.contains("no rekening") || lower == "no. rek") {
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

      // 4. Nominal Transfer (jangan tertukar dengan admin)
      if (lower.contains("nominal transfer") || lower.contains("jumlah transfer") || lower == "nominal" || lower == "jumlah") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      // 5. Biaya Admin
      if (lower.contains("biaya admin") || lower.contains("biaya transfer") || lower == "admin") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      // 6. Tanggal & Waktu Transaksi
      if (lower.contains("waktu transaksi") || lower.contains("tanggal") || lower.contains("tgl")) {
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

      // 7. Nomor Referensi (Aturan ketat v2.1: angka murni >= 16 digit)
      if (lower.contains("nomor referensi") || lower.contains("no. referensi") || lower.contains("no referensi") || lower.contains("ref:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }

      // 8. Catatan
      if (lower.contains("catatan") || lower.contains("keterangan")) {
        val inline = extractInline(line)
        if (inline.isNotBlank() && inline != "-") notes = inline
      }
    }

    // Validasi kelengkapan
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (recipientAccount.isBlank()) missingFields.add("Nomor rekening tujuan belum terbaca")
    if (amount == 0L) missingFields.add("Nominal transfer belum ditemukan")
    if (refNumber.isBlank()) reviewWarnings.add("Nomor referensi BRImo belum terbaca atau di bawah 16 digit")

    val score = calculateConfidence(detectedFields)

    return SmartParsedReceipt(
      source = TransactionSource.BRIMO,
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
