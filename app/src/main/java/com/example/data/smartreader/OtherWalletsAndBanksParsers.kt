package com.example.data.smartreader

import com.example.data.model.OcrResult
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.BankRegistry
import com.example.data.ocr.FieldValidator
import com.example.data.ocr.MultiCategoryExtractor
import com.example.data.ocr.ReceiptParser
import java.util.Locale

/**
 * Parser khusus untuk transaksi GoPay / Gojek.
 */
class GopayReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.GOPAY

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var recipientName = ""
    var recipientBank = "GoPay"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    var notes = ""
    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      if (lower == "penerima" || lower.startsWith("penerima:") || lower.startsWith("kirim ke")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      val bankMatch = BankRegistry.matchBank(line)
      if (bankMatch.isConfident && !lower.contains("sumber")) {
        recipientBank = bankMatch.officialName
        detectedFields.add(TargetRecipientField.BANK)
      }

      val digits = line.filter { it.isDigit() }
      if (recipientAccount.isBlank() && (digits.length in 10..15 || digits.startsWith("08"))) {
        val prev = lines.getOrNull(i - 1)?.lowercase(Locale.ROOT) ?: ""
        if (prev.contains("rekening") || prev.contains("nomor") || digits.startsWith("08")) {
          recipientAccount = digits
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      if (lower.contains("nominal transfer") || lower.contains("jumlah") || lower == "nominal") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      if (lower.contains("biaya transaksi") || lower.contains("biaya transfer")) {
        if (lower.contains("gratis") || lower.contains("bebas biaya") || lower.contains("rp 0") || lower.contains("rp0")) {
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

      if (lower.contains("waktu transaksi") || lower.contains("tanggal")) {
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
      }

      if (lower.contains("order id") || lower.contains("id transaksi") || lower.contains("nomor referensi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }

      if (lower.contains("catatan")) {
        val inline = extractInline(line)
        if (inline.isNotBlank() && inline != "-") notes = inline
      }
    }

    if (amount == 0L) missingFields.add("Nominal transfer GoPay belum terdeteksi")
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (refNumber.isBlank()) reviewWarnings.add("ID Transaksi GoPay belum terbaca atau di bawah 16 digit")

    return SmartParsedReceipt(
      source = TransactionSource.GOPAY,
      senderName = "Saldo GoPay",
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      transactionTime = transactionTime,
      refNumber = refNumber,
      transactionType = "Transfer GoPay",
      transactionStatus = "Berhasil",
      notes = notes,
      confidenceScore = 80,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  private fun extractInline(line: String): String =
    if (line.contains(":")) line.substringAfter(":").trim() else ""
}

/**
 * Parser khusus untuk dompet digital OVO.
 */
class OvoReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.OVO

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var recipientName = ""
    var recipientBank = "OVO"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      if (lower == "penerima" || lower.startsWith("penerima:") || lower.startsWith("transfer ke")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      val bankMatch = BankRegistry.matchBank(line)
      if (bankMatch.isConfident) {
        recipientBank = bankMatch.officialName
        detectedFields.add(TargetRecipientField.BANK)
      }

      val digits = line.filter { it.isDigit() }
      if (recipientAccount.isBlank() && (digits.length in 10..15 || digits.startsWith("08"))) {
        val prev = lines.getOrNull(i - 1)?.lowercase(Locale.ROOT) ?: ""
        if (prev.contains("rekening") || prev.contains("nomor") || digits.startsWith("08")) {
          recipientAccount = digits
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      if (lower.contains("nominal transfer") || lower.contains("jumlah") || lower == "nominal") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      if (lower.contains("biaya transaksi") || lower.contains("biaya transfer")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      if (lower.contains("tanggal") || lower.contains("waktu")) {
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
      }

      if (lower.contains("nomor referensi") || lower.contains("no. referensi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }
    }

    if (amount == 0L) missingFields.add("Nominal transfer OVO belum terdeteksi")
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (refNumber.isBlank()) reviewWarnings.add("Nomor referensi OVO belum terbaca atau di bawah 16 digit")

    return SmartParsedReceipt(
      source = TransactionSource.OVO,
      senderName = "OVO Cash",
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      transactionTime = transactionTime,
      refNumber = refNumber,
      transactionType = "Transfer OVO",
      transactionStatus = "Berhasil",
      confidenceScore = 80,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  private fun extractInline(line: String): String =
    if (line.contains(":")) line.substringAfter(":").trim() else ""
}

/**
 * Parser khusus untuk dompet digital ShopeePay / SeaBank.
 */
class ShopeePayReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.SHOPEEPAY

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var recipientName = ""
    var recipientBank = "ShopeePay"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      if (lower.contains("transfer ke") || lower == "penerima" || lower.startsWith("penerima:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      val bankMatch = BankRegistry.matchBank(line)
      if (bankMatch.isConfident) {
        recipientBank = bankMatch.officialName
        detectedFields.add(TargetRecipientField.BANK)
      }

      val digits = line.filter { it.isDigit() }
      if (recipientAccount.isBlank() && (digits.length in 10..15 || digits.startsWith("08"))) {
        val prev = lines.getOrNull(i - 1)?.lowercase(Locale.ROOT) ?: ""
        if (prev.contains("rekening") || prev.contains("handphone") || prev.contains("nomor") || digits.startsWith("08")) {
          recipientAccount = digits
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      if (lower.contains("jumlah transfer") || lower.contains("total pembayaran") || lower == "nominal") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      if (lower.contains("biaya penanganan") || lower.contains("biaya admin")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      if (lower.contains("waktu selesai") || lower.contains("waktu pembayaran") || lower.contains("tanggal")) {
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
      }

      if (lower.contains("no. transaksi") || lower.contains("nomor transaksi") || lower.contains("no transaksi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }
    }

    if (amount == 0L) missingFields.add("Jumlah transfer ShopeePay belum terbaca")
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (refNumber.isBlank()) reviewWarnings.add("No. transaksi ShopeePay belum terbaca atau di bawah 16 digit")

    return SmartParsedReceipt(
      source = TransactionSource.SHOPEEPAY,
      senderName = "ShopeePay",
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      transactionTime = transactionTime,
      refNumber = refNumber,
      transactionType = "Transfer ShopeePay",
      transactionStatus = "Berhasil",
      confidenceScore = 80,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  private fun extractInline(line: String): String =
    if (line.contains(":")) line.substringAfter(":").trim() else ""
}

/**
 * Parser khusus untuk mobile banking BNI (Bank Negara Indonesia).
 */
class BniReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.BNI

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var senderName = ""
    var recipientName = ""
    var recipientBank = "BNI"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      if (lower.contains("rekening debet") || lower.contains("rekening sumber")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        if (candidate.isNotBlank()) senderName = candidate
      }

      if (lower == "nama penerima" || lower.startsWith("nama penerima:") || lower == "penerima") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      if (lower.contains("bank tujuan") || lower.startsWith("bank:")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val match = BankRegistry.matchBank(candidate)
        if (match.isConfident) {
          recipientBank = match.officialName
          detectedFields.add(TargetRecipientField.BANK)
        }
      }

      if (lower.contains("rekening tujuan") || lower.contains("no. rekening")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanAcc, _) = FieldValidator.sanitizeAccount(candidate, refNumber)
        if (cleanAcc.isNotBlank()) {
          recipientAccount = cleanAcc
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      if (lower.contains("nominal") || lower.contains("jumlah")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      if (lower.contains("biaya transaksi") || lower.contains("biaya admin")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      if (lower.contains("waktu transaksi") || lower.contains("tanggal")) {
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
      }

      if (lower.contains("nomor referensi") || lower.contains("no. referensi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }
    }

    if (amount == 0L) missingFields.add("Nominal transfer BNI belum ditemukan")
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (refNumber.isBlank()) reviewWarnings.add("Nomor referensi BNI belum terbaca atau di bawah 16 digit")

    return SmartParsedReceipt(
      source = TransactionSource.BNI,
      senderName = senderName,
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      transactionTime = transactionTime,
      refNumber = refNumber,
      transactionType = "Transfer BNI Mobile",
      transactionStatus = "Berhasil",
      confidenceScore = 80,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  private fun extractInline(line: String): String =
    if (line.contains(":")) line.substringAfter(":").trim() else ""
}

/**
 * Parser khusus untuk BSI Mobile (Bank Syariah Indonesia).
 */
class BsiReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.BSI

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    var recipientName = ""
    var recipientBank = "BSI"
    var recipientAccount = ""
    var amount = 0L
    var adminFee = 0L
    var transactionDate = ""
    var transactionTime = ""
    var refNumber = ""
    val detectedFields = mutableSetOf<TargetRecipientField>()
    val missingFields = mutableListOf<String>()
    val reviewWarnings = mutableListOf<String>()

    for (i in lines.indices) {
      val line = lines[i].trim()
      val lower = line.lowercase(Locale.ROOT)

      if (lower.contains("nama penerima") || lower == "penerima") {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanName, _) = FieldValidator.sanitizeRecipientName(candidate)
        if (cleanName.isNotBlank()) {
          recipientName = cleanName
          detectedFields.add(TargetRecipientField.PENERIMA)
        }
      }

      val bankMatch = BankRegistry.matchBank(line)
      if (bankMatch.isConfident) {
        recipientBank = bankMatch.officialName
        detectedFields.add(TargetRecipientField.BANK)
      }

      if (lower.contains("rekening tujuan") || lower.contains("no. rekening")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanAcc, _) = FieldValidator.sanitizeAccount(candidate, refNumber)
        if (cleanAcc.isNotBlank()) {
          recipientAccount = cleanAcc
          detectedFields.add(TargetRecipientField.REKENING)
        }
      }

      if (lower.contains("nominal") || lower.contains("jumlah")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedAmt, _) = FieldValidator.parseNominal(candidate)
        if (parsedAmt > 0L) {
          amount = parsedAmt
          detectedFields.add(TargetRecipientField.NOMINAL)
        }
      }

      if (lower.contains("biaya admin")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (parsedFee, _) = FieldValidator.parseNominal(candidate)
        if (parsedFee >= 0L) {
          adminFee = parsedFee
          detectedFields.add(TargetRecipientField.ADMIN)
        }
      }

      if (lower.contains("tanggal") || lower.contains("waktu")) {
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
      }

      if (lower.contains("nomor referensi") || lower.contains("no. referensi")) {
        val inline = extractInline(line)
        val candidate = if (inline.isNotBlank()) inline else lines.getOrNull(i + 1) ?: ""
        val (cleanRef, _) = FieldValidator.sanitizeRefNumber(candidate, candidateAccount = recipientAccount)
        if (cleanRef.isNotBlank()) {
          refNumber = cleanRef
          detectedFields.add(TargetRecipientField.REFERENSI)
        }
      }
    }

    if (amount == 0L) missingFields.add("Nominal transfer BSI belum ditemukan")
    if (recipientName.isBlank()) missingFields.add("Nama penerima belum terdeteksi")
    if (refNumber.isBlank()) reviewWarnings.add("Nomor referensi BSI belum terbaca atau di bawah 16 digit")

    return SmartParsedReceipt(
      source = TransactionSource.BSI,
      senderName = "BSI Mobile",
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = transactionDate,
      transactionTime = transactionTime,
      refNumber = refNumber,
      transactionType = "Transfer BSI Mobile",
      transactionStatus = "Berhasil",
      confidenceScore = 80,
      detectedFields = detectedFields,
      missingFields = missingFields,
      reviewWarnings = reviewWarnings,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }

  private fun extractInline(line: String): String =
    if (line.contains(":")) line.substringAfter(":").trim() else ""
}

/**
 * Parser umum / fallback cerdas jika bukti transfer bukan dari bank/e-wallet spesifik di atas.
 * Memanfaatkan MultiCategoryExtractor 7-kolom dan ReceiptParser bawaan JADISTRUK.
 */
class GenericSmartReceiptParser : SmartReceiptParser {
  override val source: TransactionSource = TransactionSource.GENERIC

  override fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt {
    val legacyReceipt = ReceiptParser.parse(fullText)
    val extraction = MultiCategoryExtractor.extractFromTextLines(lines)

    val detected = mutableSetOf<TargetRecipientField>()
    if (legacyReceipt.recipientName.isNotBlank()) detected.add(TargetRecipientField.PENERIMA)
    if (legacyReceipt.recipientBank.isNotBlank()) detected.add(TargetRecipientField.BANK)
    if (legacyReceipt.recipientAccount.isNotBlank()) detected.add(TargetRecipientField.REKENING)
    if (legacyReceipt.amount > 0L) detected.add(TargetRecipientField.NOMINAL)
    if (legacyReceipt.transactionDate.isNotBlank()) detected.add(TargetRecipientField.TANGGAL)
    if (legacyReceipt.transactionTime.isNotBlank()) detected.add(TargetRecipientField.WAKTU)
    if (legacyReceipt.refNumber.isNotBlank()) detected.add(TargetRecipientField.REFERENSI)

    val missing = mutableListOf<String>()
    if (legacyReceipt.recipientName.isBlank()) missing.add("Nama penerima belum terdeteksi")
    if (legacyReceipt.recipientBank.isBlank()) missing.add("Bank tujuan belum terdeteksi")
    if (legacyReceipt.recipientAccount.isBlank()) missing.add("Nomor rekening belum ditemukan")
    if (legacyReceipt.amount == 0L) missing.add("Nominal transfer belum ditemukan")
    if (legacyReceipt.refNumber.isBlank()) missing.add("Nomor referensi transaksi belum terbaca (wajib >=16 digit)")

    return SmartParsedReceipt(
      source = TransactionSource.GENERIC,
      senderName = "Pengirim",
      recipientName = legacyReceipt.recipientName,
      recipientBank = legacyReceipt.recipientBank.ifBlank { "Bank Transfer" },
      recipientAccount = legacyReceipt.recipientAccount,
      amount = legacyReceipt.amount,
      adminFee = legacyReceipt.adminFee,
      transactionDate = legacyReceipt.displayDate,
      transactionTime = legacyReceipt.displayTime,
      refNumber = legacyReceipt.refNumber,
      transactionType = "Transfer Bank",
      transactionStatus = "Berhasil",
      notes = legacyReceipt.notes,
      confidenceScore = 75,
      detectedFields = detected,
      missingFields = missing,
      reviewWarnings = extraction.reviewRequired,
      rawOcrText = fullText,
      ocrResult = ocrResult
    )
  }
}
