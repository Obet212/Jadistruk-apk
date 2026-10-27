package com.example.data.ocr

import com.example.data.model.Receipt
import com.example.data.model.TargetRecipientField
import java.util.Locale

data class FieldValidationReport(
  val isValidForPrint: Boolean,
  val errors: Map<TargetRecipientField, String>,
  val warnings: Map<TargetRecipientField, String>,
) {
  fun getMessageFor(field: TargetRecipientField): String? = errors[field] ?: warnings[field]
  fun hasIssue(field: TargetRecipientField): Boolean = errors.containsKey(field) || warnings.containsKey(field)
}

object FieldValidator {

  // 1. BANK Normalization
  fun sanitizeBank(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Bank tujuan belum terdeteksi")
    }
    val match = BankRegistry.matchBank(trimmed)
    return if (match.isConfident) {
      Pair(match.officialName, null)
    } else {
      Pair("", "Nama bank tidak cocok dengan kamus resmi atau alias terdaftar.")
    }
  }

  // 2. REKENING Normalization (Strict 10 to 15 digits)
  fun sanitizeAccount(raw: String): Pair<String, String?> {
    if (raw.isBlank()) {
      return Pair("", "Nomor rekening belum terisi")
    }

    // Reject if string represents currency or amount
    if (raw.contains("Rp", ignoreCase = true) || raw.contains("IDR", ignoreCase = true)) {
      return Pair("", "Nilai berupa nominal, bukan nomor rekening.")
    }

    // Strip labels like "Nomor Rekening :", "No. Rek :", "No. HP :", "Ke Rek :", "DANA ID :"
    val cleaned = raw.replace(
      Regex("(?i)^(nomor\\s*rekening|no\\.?\\s*rekening|rekening\\s*tujuan|no\\.?\\s*rek|nomor\\s*hp|no\\.?\\s*hp|nomor\\s*ponsel|dana\\s*id|akun\\s*tujuan|ke\\s*rekening)[:\\-\\s]+"),
      ""
    ).trim()

    // Filter only digits without converting to numeric type (preserves leading zeroes)
    val digitsOnly = cleaned.filter { it.isDigit() }
    val hasHyphen = cleaned.contains("-")
    val finalAccount = if (hasHyphen) {
      cleaned.replace(Regex("[^0-9\\-]"), "")
    } else {
      digitsOnly
    }

    return when {
      digitsOnly.length in 10..15 -> Pair(finalAccount, null)
      digitsOnly.length in 1..9 -> Pair("", "Nomor rekening terlalu pendek (${digitsOnly.length} digit, syarat 10–15 digit).")
      digitsOnly.length > 15 -> Pair("", "Nomor rekening melebihi batas 15 digit (${digitsOnly.length} digit).")
      else -> Pair("", "Nomor rekening tidak valid (tidak ada angka).")
    }
  }

  // 3. PENERIMA Normalization
  fun sanitizeRecipientName(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Nama penerima belum terdeteksi")
    }

    // Reject if line is marked as sender
    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower.contains("pengirim") || lower.contains("sumber dana") || lower.contains("rekening asal")) {
      return Pair("", "Teks merupakan data pengirim, bukan penerima.")
    }

    // Strictly strip labels from the same line if present
    var cleaned = trimmed
      .replace(Regex("(?i)^(nama\\s*penerima|nama\\s*tujuan|nama\\s*lengkap|penerima|tujuan\\s*transfer|ke\\s*nama|kirim\\s*ke|tujuan|ke)[:\\-\\s]+"), "")
      .replace(Regex("(?i)\\b(transaksi\\s*berhasil|transfer\\s*berhasil|berhasil|sukses|status|detail\\s*transaksi)\\b"), "")
      .replace(Regex("[^a-zA-Z0-9\\s.'\\-,&()/]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()

    // Must not be a bank name
    if (BankRegistry.matchBank(cleaned).isConfident) {
      return Pair("", "Teks cocok dengan nama bank, bukan nama penerima.")
    }

    // Check validity: must have at least 2 letters
    val letterCount = cleaned.count { it.isLetter() }
    return if (letterCount >= 2 && cleaned.length in 2..60) {
      Pair(cleaned, null)
    } else {
      Pair("", "Nama penerima tidak valid atau belum terbaca.")
    }
  }

  // 4. WAKTU Normalization with Range Validation (NO CURRENT TIME FALLBACK)
  fun sanitizeDateTime(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Waktu transaksi belum terdeteksi")
    }

    val parsed = parseAndValidateDateTime(trimmed)
    return if (parsed != null) {
      Pair(parsed, null)
    } else {
      Pair("", "Format tanggal/waktu transaksi tidak valid.")
    }
  }

  private val MONTH_MAP = mapOf(
    "jan" to 1, "januari" to 1, "january" to 1,
    "feb" to 2, "februari" to 2, "february" to 2,
    "mar" to 3, "maret" to 3, "march" to 3,
    "apr" to 4, "april" to 4,
    "mei" to 5, "may" to 5,
    "jun" to 6, "juni" to 6, "june" to 6,
    "jul" to 7, "juli" to 7, "july" to 7,
    "ags" to 8, "agu" to 8, "agustus" to 8, "aug" to 8, "august" to 8,
    "sep" to 9, "september" to 9,
    "okt" to 10, "oktober" to 10, "oct" to 10, "october" to 10,
    "nov" to 11, "november" to 11,
    "des" to 12, "desember" to 12, "dec" to 12, "december" to 12
  )

  fun parseAndValidateDateTime(input: String): String? {
    val clean = input.replace(Regex("(?i)^(tanggal|tgl|waktu|date|time|transaksi)[:\\-\\s]+"), "").trim()

    // 1. dd/MM/yyyy or dd-MM-yyyy or dd.MM.yyyy with optional time
    val numDateRegex = Regex("(?i)\\b(\\d{1,2})[/\\-\\.](\\d{1,2})[/\\-\\.](\\d{2,4})\\b")
    // 2. yyyy-MM-dd
    val isoDateRegex = Regex("(?i)\\b(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})\\b")
    // 3. dd MMM yyyy (e.g. 18 Okt 2026 or 18 October 2026)
    val textDateRegex = Regex("(?i)\\b(\\d{1,2})\\s+([A-Za-z]{3,12})\\s+(\\d{4})\\b")
    // Time regex: HH:mm or HH:mm:ss
    val timeRegex = Regex("\\b(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b")

    var day: Int? = null
    var month: Int? = null
    var year: Int? = null
    var matchedDateStr: String? = null

    val textMatch = textDateRegex.find(clean)
    if (textMatch != null) {
      val (dStr, mStr, yStr) = textMatch.destructured
      val mVal = MONTH_MAP[mStr.lowercase(Locale.ROOT)]
      if (mVal != null) {
        day = dStr.toIntOrNull()
        month = mVal
        year = yStr.toIntOrNull()
        matchedDateStr = "$dStr $mStr $yStr"
      }
    }

    if (day == null) {
      val numMatch = numDateRegex.find(clean)
      if (numMatch != null) {
        val (dStr, mStr, yStr) = numMatch.destructured
        var yVal = yStr.toIntOrNull() ?: 0
        if (yVal in 0..99) yVal += 2000
        val dVal = dStr.toIntOrNull() ?: 0
        val mVal = mStr.toIntOrNull() ?: 0

        day = dVal
        month = mVal
        year = yVal
        matchedDateStr = "${dStr.padStart(2, '0')}/${mStr.padStart(2, '0')}/$year"
      }
    }

    if (day == null) {
      val isoMatch = isoDateRegex.find(clean)
      if (isoMatch != null) {
        val (yStr, mStr, dStr) = isoMatch.destructured
        day = dStr.toIntOrNull()
        month = mStr.toIntOrNull()
        year = yStr.toIntOrNull()
        if (day != null && month != null && year != null) {
          matchedDateStr = "${dStr.padStart(2, '0')}/${mStr.padStart(2, '0')}/$year"
        }
      }
    }

    // Validate date components
    if (day == null || month == null || year == null) return null
    if (day !in 1..31 || month !in 1..12 || year !in 2015..2035) return null

    // Look for time
    val timeMatch = timeRegex.find(clean)
    return if (timeMatch != null) {
      val (hStr, minStr, secStr) = timeMatch.destructured
      val hour = hStr.toIntOrNull() ?: -1
      val minute = minStr.toIntOrNull() ?: -1
      val second = if (secStr.isNotEmpty()) secStr.toIntOrNull() ?: 0 else 0

      if (hour in 0..23 && minute in 0..59 && second in 0..59) {
        val formattedTime = if (secStr.isNotEmpty()) {
          "${hStr.padStart(2, '0')}:${minStr.padStart(2, '0')}:${secStr.padStart(2, '0')}"
        } else {
          "${hStr.padStart(2, '0')}:${minStr.padStart(2, '0')}"
        }
        "$matchedDateStr $formattedTime"
      } else {
        null
      }
    } else {
      matchedDateStr
    }
  }

  // 5. NOMINAL Normalization (Smart Rupiah identification)
  fun parseNominal(raw: String): Pair<Long, String?> {
    if (raw.isBlank()) {
      return Pair(0L, "Nominal transfer wajib diisi")
    }

    val lower = raw.lowercase(Locale.ROOT)
    // Discard if line explicitly belongs to ADMIN or SALDO
    if (lower.contains("biaya admin") || lower.contains("admin fee") ||
        lower.contains("sisa saldo") || lower.contains("saldo awal")) {
      return Pair(0L, "Teks bukan nominal transfer murni.")
    }

    var s = raw.trim()
    // Replace common OCR confusion: uppercase 'O'/'o' to '0', lowercase 'l' to '1'
    s = s.replace('O', '0').replace('o', '0')

    // Remove prefixes
    s = s.replace(Regex("(?i)(nominal\\s*transfer|jumlah\\s*transfer|nominal|jumlah|amount|total\\s*diterima|rp\\.?|idr|[:=\\-])"), "").trim()

    // 1. Handle decimal cents at the end: e.g. ",00" or ".00"
    if (s.endsWith(",00") || s.endsWith(".00")) {
      s = s.substring(0, s.length - 3).trim()
    }

    // 2. Distinguish comma vs dot thousands separator:
    // If format is like "150,000" or "1,500,000" where comma is followed by 3 digits
    if (s.contains(",") && !s.contains(".")) {
      val parts = s.split(",")
      // If all parts after first are exactly 3 digits, it's thousands separator
      if (parts.size > 1 && parts.drop(1).all { it.length == 3 && it.all { c -> c.isDigit() } }) {
        s = parts.joinToString("")
      } else if (parts.size == 2 && parts[1].length <= 2) {
        // Cents, drop decimal
        s = parts[0]
      }
    } else if (s.contains(".") && !s.contains(",")) {
      val parts = s.split(".")
      if (parts.size > 1 && parts.drop(1).all { it.length == 3 && it.all { c -> c.isDigit() } }) {
        s = parts.joinToString("")
      }
    } else if (s.contains(".") && s.contains(",")) {
      // e.g. "150.000,00" or "150,000.00"
      val lastDot = s.lastIndexOf('.')
      val lastComma = s.lastIndexOf(',')
      if (lastComma > lastDot) {
        // Dot is thousand, comma is decimal: "150.000,50" -> "150000"
        s = s.substring(0, lastComma).filter { it.isDigit() }
      } else {
        // Comma is thousand, dot is decimal: "150,000.50" -> "150000"
        s = s.substring(0, lastDot).filter { it.isDigit() }
      }
    }

    val digitsOnly = s.filter { it.isDigit() }
    val value = digitsOnly.toLongOrNull() ?: 0L

    return if (value > 0L) {
      Pair(value, null)
    } else {
      Pair(0L, "Nominal transfer harus lebih dari Rp0")
    }
  }

  // 6. ADMIN Normalization
  fun parseAdmin(raw: String): Long {
    val clean = raw.filter { it.isDigit() }
    val value = clean.toLongOrNull() ?: 0L
    return maxOf(0L, value)
  }

  // 7. TOTAL Calculation (Aritmetika Angka Rupiah)
  fun calculateTotal(nominal: Long, admin: Long): Long {
    return maxOf(0L, nominal + admin)
  }

  /**
   * Evaluates all 7 fields for print readiness.
   * If crucial fields (Penerima, Bank, Nominal > 0) are missing or problematic,
   * blocks printing until corrected.
   */
  fun validateReceipt(receipt: Receipt): FieldValidationReport {
    val errors = mutableMapOf<TargetRecipientField, String>()
    val warnings = mutableMapOf<TargetRecipientField, String>()

    // 1. WAKTU
    val (_, waktuIssue) = sanitizeDateTime(receipt.transactionDate)
    if (receipt.transactionDate.isBlank()) {
      warnings[TargetRecipientField.WAKTU] = "Tanggal/jam transaksi belum terisi."
    } else if (waktuIssue != null) {
      warnings[TargetRecipientField.WAKTU] = waktuIssue
    }

    // 2. BANK
    val (bankClean, bankIssue) = sanitizeBank(receipt.recipientBank)
    if (bankClean.isBlank()) {
      errors[TargetRecipientField.BANK] = "Bank tujuan penerima wajib diisi."
    } else if (bankIssue != null) {
      warnings[TargetRecipientField.BANK] = bankIssue
    }

    // 3. REKENING
    val (rekClean, rekIssue) = sanitizeAccount(receipt.recipientAccount)
    if (rekClean.isBlank()) {
      warnings[TargetRecipientField.REKENING] = "Nomor rekening penerima wajib 10–15 digit angka."
    } else if (rekIssue != null) {
      warnings[TargetRecipientField.REKENING] = rekIssue
    }

    // 4. PENERIMA
    val (nameClean, nameIssue) = sanitizeRecipientName(receipt.recipientName)
    if (nameClean.isBlank()) {
      errors[TargetRecipientField.PENERIMA] = "Nama penerima transfer wajib diisi."
    } else if (nameIssue != null) {
      warnings[TargetRecipientField.PENERIMA] = nameIssue
    }

    // 5. NOMINAL
    if (receipt.amount <= 0L) {
      errors[TargetRecipientField.NOMINAL] = "Nominal transfer wajib bernilai lebih dari Rp0."
    }

    // 6. ADMIN
    if (receipt.adminFee < 0L) {
      errors[TargetRecipientField.ADMIN] = "Biaya admin tidak boleh bernilai negatif."
    }

    // 7. TOTAL
    if (receipt.total <= 0L && receipt.amount > 0L) {
      errors[TargetRecipientField.TOTAL] = "Total transaksi tidak valid."
    }

    val isValid = errors.isEmpty()
    return FieldValidationReport(
      isValidForPrint = isValid,
      errors = errors,
      warnings = warnings
    )
  }
}
