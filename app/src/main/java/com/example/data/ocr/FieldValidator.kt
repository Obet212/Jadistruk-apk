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

  // =========================================================================
  // 1. TANGGAL (Standard format: DD/MM/YYYY dengan validasi kalender akurat)
  // =========================================================================

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

  fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
  }

  fun getDaysInMonth(month: Int, year: Int): Int {
    return when (month) {
      1, 3, 5, 7, 8, 10, 12 -> 31
      4, 6, 9, 11 -> 30
      2 -> if (isLeapYear(year)) 29 else 28
      else -> 0
    }
  }

  /**
   * Validasi Tanggal Transaksi:
   * - Format DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, YYYY-MM-DD, atau DD MMM YYYY.
   * - Validasi kalender hari & bulan (kabisat dll).
   * - Output standar: DD/MM/YYYY.
   * - Menolak tanggal lahir atau tanggal kedaluwarsa.
   */
  fun sanitizeDate(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Tanggal transaksi belum terisi")
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    // Tolak tanggal lahir atau kedaluwarsa
    if (lower.contains("lahir") || lower.contains("birth") ||
        lower.contains("exp") || lower.contains("berlaku") ||
        lower.contains("kadaluwarsa") || lower.contains("valid thru")) {
      return Pair("", "Teks merupakan tanggal lahir/kedaluwarsa, bukan tanggal transaksi.")
    }

    val clean = trimmed.replace(
      Regex("(?i)^(tanggal\\s*transaksi|transaction\\s*date|tgl\\s*transaksi|tanggal|tgl|date)[:\\-\\s]+"),
      ""
    ).trim()

    // 1. dd MMM yyyy (e.g. 28 Okt 2026 atau 28-Okt-2026)
    val textDateRegex = Regex("(?i)\\b(\\d{1,2})[\\s\\-\\./]+([A-Za-z]{3,12})[\\s\\-\\./]+(\\d{4})\\b")
    // 2. dd/MM/yyyy atau dd-MM-yyyy atau dd.MM.yyyy
    val numDateRegex = Regex("(?i)\\b(\\d{1,2})[/\\-\\.](\\d{1,2})[/\\-\\.](\\d{2,4})\\b")
    // 3. yyyy-MM-dd atau yyyy/MM/dd
    val isoDateRegex = Regex("(?i)\\b(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})\\b")

    var day: Int? = null
    var month: Int? = null
    var year: Int? = null

    val textMatch = textDateRegex.find(clean)
    if (textMatch != null) {
      val (dStr, mStr, yStr) = textMatch.destructured
      val mVal = MONTH_MAP[mStr.lowercase(Locale.ROOT)]
      if (mVal != null) {
        day = dStr.toIntOrNull()
        month = mVal
        year = yStr.toIntOrNull()
      }
    }

    if (day == null) {
      val numMatch = numDateRegex.find(clean)
      if (numMatch != null) {
        val (dStr, mStr, yStr) = numMatch.destructured
        var yVal = yStr.toIntOrNull() ?: 0
        if (yVal in 0..99) yVal += 2000
        day = dStr.toIntOrNull()
        month = mStr.toIntOrNull()
        year = yVal
      }
    }

    if (day == null) {
      val isoMatch = isoDateRegex.find(clean)
      if (isoMatch != null) {
        val (yStr, mStr, dStr) = isoMatch.destructured
        day = dStr.toIntOrNull()
        month = mStr.toIntOrNull()
        year = yStr.toIntOrNull()
      }
    }

    if (day == null || month == null || year == null) {
      return Pair("", "Format tanggal tidak dikenali.")
    }

    if (year !in 2018..2035) {
      return Pair("", "Tahun transaksi di luar batas wajar (2018–2035): $year")
    }

    if (month !in 1..12) {
      return Pair("", "Bulan tidak valid (harus 1–12): $month")
    }

    val maxDays = getDaysInMonth(month, year)
    if (day !in 1..maxDays) {
      return Pair("", "Tanggal tidak valid untuk kalender ($day melebihi batas $maxDays hari di bulan $month).")
    }

    val formatted = "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
    return Pair(formatted, null)
  }

  // =========================================================================
  // 2. WAKTU (Format 24 jam HH:MM:SS atau HH:MM)
  // =========================================================================

  /**
   * Validasi Waktu Transaksi:
   * - Format 24 jam HH:MM:SS atau HH:MM.
   * - Validasi jam 00–23, menit 00–59, detik 00–59.
   * - Menolak waktu status bar atau angka yang bukan waktu transaksi.
   */
  fun sanitizeTime(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Waktu transaksi belum terisi")
    }

    val clean = trimmed.replace(
      Regex("(?i)^(waktu\\s*transaksi|jam\\s*transaksi|transaction\\s*time|waktu|jam|time|pukul)[:\\-\\s]+"),
      ""
    ).replace(Regex("(?i)\\b(wib|wita|wit)\\b"), "").trim()

    val timeRegex = Regex("\\b(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b")
    val match = timeRegex.find(clean) ?: return Pair("", "Format jam tidak valid (contoh: 15:33 atau 15:33:00).")

    val (hStr, mStr, sStr) = match.destructured
    val hour = hStr.toIntOrNull() ?: -1
    val minute = mStr.toIntOrNull() ?: -1
    val second = if (sStr.isNotEmpty()) sStr.toIntOrNull() ?: -1 else null

    if (hour !in 0..23) {
      return Pair("", "Jam di luar batas format 24 jam (harus 00–23): $hour")
    }
    if (minute !in 0..59) {
      return Pair("", "Menit di luar batas valid (harus 00–59): $minute")
    }
    if (second != null && second !in 0..59) {
      return Pair("", "Detik di luar batas valid (harus 00–59): $second")
    }

    val formatted = if (second != null) {
      "${hStr.padStart(2, '0')}:${mStr.padStart(2, '0')}:${sStr.padStart(2, '0')}"
    } else {
      "${hStr.padStart(2, '0')}:${mStr.padStart(2, '0')}"
    }
    return Pair(formatted, null)
  }

  /**
   * Menggabungkan atau memisahkan sanitasi Tanggal & Waktu (kompatibilitas menyeluruh)
   */
  fun sanitizeDateTime(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return Pair("", "Waktu transaksi belum terdeteksi")

    val (dateStr, dateIssue) = sanitizeDate(trimmed)
    val (timeStr, _) = sanitizeTime(trimmed)

    return when {
      dateStr.isNotBlank() && timeStr.isNotBlank() -> Pair("$dateStr $timeStr", null)
      dateStr.isNotBlank() -> Pair(dateStr, null)
      timeStr.isNotBlank() -> Pair(timeStr, null)
      else -> Pair("", dateIssue ?: "Format tanggal/waktu transaksi tidak valid.")
    }
  }

  // =========================================================================
  // 3. REFERENSI (Serial Number / Kode Transaksi, prioritas > 15 digit)
  // =========================================================================

  /**
   * Validasi Nomor Referensi / SN / Kode Transaksi:
   * - Mempertahankan seluruh digit dan karakter asli, termasuk nol di depan.
   * - Memprioritaskan kode transaksi > 15 digit.
   * - Menolak nomor rekening, nomor telepon, atau angka nominal.
   */
  fun sanitizeRefNumber(raw: String, candidateAccount: String = ""): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Nomor referensi belum terisi")
    }

    // Bersihkan prefix label referensi
    val cleaned = trimmed.replace(
      Regex("(?i)^(nomor\\s*referensi|no\\.?\\s*referensi|kode\\s*transaksi|transaction\\s*id|id\\s*transaksi|serial\\s*number|reference|no\\.?\\s*transaksi|no\\.?\\s*ref|reff?|sn)[:\\-\\s]+"),
      ""
    ).trim()

    // Ambil karakter alfanumerik yang membentuk kode referensi
    val refCode = cleaned.filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    if (refCode.isBlank()) {
      return Pair("", "Kode referensi tidak terbaca.")
    }

    // Jangan mengambil nomor rekening sebagai referensi
    val cleanAcc = candidateAccount.filter { it.isDigit() }
    if (cleanAcc.isNotBlank() && refCode == cleanAcc) {
      return Pair("", "Nilai sama persis dengan nomor rekening.")
    }

    // Jangan mengambil mata uang / nominal sebagai referensi
    if (trimmed.contains("Rp", ignoreCase = true) || trimmed.contains("IDR", ignoreCase = true)) {
      return Pair("", "Nilai berupa nominal, bukan nomor referensi.")
    }

    if (refCode.length < 6) {
      return Pair("", "Kode referensi terlalu pendek (minimal 6 karakter).")
    }

    return Pair(refCode, null)
  }

  // =========================================================================
  // 4. BANK TUJUAN (Penerima, menolak bank pengirim)
  // =========================================================================

  /**
   * Validasi Bank Penerima:
   * - Menolak nama bank pengirim / sumber dana.
   * - Menggunakan kamus resmi BankRegistry.
   * - Jika tidak dapat dipastikan, biarkan kosong.
   */
  fun sanitizeBank(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Bank tujuan belum terdeteksi")
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower.contains("pengirim") || lower.contains("sumber dana") || lower.contains("rekening asal") || lower.contains("dari bank")) {
      return Pair("", "Teks merupakan data bank pengirim, bukan bank tujuan.")
    }

    val cleaned = trimmed.replace(
      Regex("(?i)^(bank\\s*tujuan|bank\\s*penerima|tujuan\\s*transfer|ke\\s*bank|nama\\s*bank|bank)[:\\-\\s]+"),
      ""
    ).trim()

    val match = BankRegistry.matchBank(cleaned)
    return if (match.isConfident) {
      Pair(match.officialName, null)
    } else {
      Pair("", "Nama bank tidak cocok dengan kamus resmi atau alias terdaftar.")
    }
  }

  // =========================================================================
  // 5. NOMOR REKENING (Maksimal 15 digit, pertahankan nol di depan)
  // =========================================================================

  /**
   * Validasi Nomor Rekening:
   * - Hanya boleh berisi angka 0–9.
   * - Maksimal 15 digit (syarat standar perbankan).
   * - Pertahankan angka nol di depan.
   * - Jika > 15 digit: JANGAN otomatis dipotong, tandai tidak valid agar dicek manual.
   * - Menolak nomor referensi, telepon, atau nominal.
   */
  fun sanitizeAccount(raw: String, candidateRef: String = ""): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Nomor rekening belum terisi")
    }

    // Menolak jika berupa nominal atau mata uang
    if (trimmed.contains("Rp", ignoreCase = true) || trimmed.contains("IDR", ignoreCase = true)) {
      return Pair("", "Nilai berupa nominal, bukan nomor rekening.")
    }

    // Bersihkan label nomor rekening
    val cleaned = trimmed.replace(
      Regex("(?i)^(nomor\\s*rekening|no\\.?\\s*rekening|rekening\\s*tujuan|rekening\\s*penerima|no\\.?\\s*rek|account\\s*number|account|akun\\s*tujuan|ke\\s*rekening)[:\\-\\s]+"),
      ""
    ).trim()

    // Pertahankan digit angka asli tanpa konversi tipe data numerik
    val digitsOnly = cleaned.filter { it.isDigit() }

    // Jangan mengambil nomor referensi sebagai rekening
    if (candidateRef.isNotBlank() && digitsOnly == candidateRef) {
      return Pair("", "Nilai tertukar dengan kode referensi transaksi.")
    }

    return when {
      digitsOnly.length > 15 -> {
        // ATURAN PENTING: Jangan otomatis memotong angka jika > 15 digit!
        Pair("", "Nomor rekening melebihi batas 15 digit (${digitsOnly.length} digit). Periksa manual apakah tertukar dengan No. Referensi.")
      }
      digitsOnly.length in 10..15 -> Pair(digitsOnly, null)
      digitsOnly.length in 1..9 -> Pair("", "Nomor rekening terlalu pendek (${digitsOnly.length} digit, syarat 10–15 digit).")
      else -> Pair("", "Nomor rekening tidak valid (tidak ada angka).")
    }
  }

  // =========================================================================
  // 6. NAMA PENERIMA (Menolak pengirim, bank, dan label Kepada:)
  // =========================================================================

  /**
   * Validasi Nama Penerima:
   * - Hapus label seperti "Kepada:", "Penerima:", "Ke:".
   * - Menolak nama pengirim / sumber dana.
   * - Menolak nama bank atau merchant.
   * - Jangan mengarang nama yang tidak terbaca.
   */
  fun sanitizeRecipientName(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
      return Pair("", "Nama penerima belum terdeteksi")
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower.contains("pengirim") || lower.contains("sumber dana") || lower.contains("rekening asal") || lower.contains("dari rekening")) {
      return Pair("", "Teks merupakan data pengirim, bukan nama penerima.")
    }

    var cleaned = trimmed
      .replace(Regex("(?i)^(nama\\s*penerima|nama\\s*tujuan|nama\\s*lengkap|penerima\\s*transfer|penerima|beneficiary|recipient|tujuan|ke\\s*nama|kirim\\s*ke|kepada|ke)[:\\-\\s]+"), "")
      .replace(Regex("(?i)\\b(transaksi\\s*berhasil|transfer\\s*berhasil|berhasil|sukses|status|detail\\s*transaksi)\\b"), "")
      .replace(Regex("[^a-zA-Z0-9\\s.'\\-,&()/]"), "")
      .replace(Regex("\\s+"), " ")
      .trim()

    // Tolak jika teks adalah nama bank
    if (BankRegistry.matchBank(cleaned).isConfident) {
      return Pair("", "Teks cocok dengan nama bank, bukan nama penerima.")
    }

    val letterCount = cleaned.count { it.isLetter() }
    return if (letterCount >= 2 && cleaned.length in 2..60) {
      Pair(cleaned, null)
    } else {
      Pair("", "Nama penerima tidak valid atau belum terbaca.")
    }
  }

  // =========================================================================
  // 7. NOMINAL TRANSFER (Jumlah uang diterima, menolak saldo & admin)
  // =========================================================================

  /**
   * Validasi Nominal Transfer:
   * - Format Rp 20.000, Rp20.000,00, IDR 20,000, 20000.
   * - Simpan sebagai Long murni.
   * - Menolak saldo atau biaya admin.
   */
  fun parseNominal(raw: String): Pair<Long, String?> {
    if (raw.isBlank()) {
      return Pair(0L, "Nominal transfer wajib diisi")
    }

    val lower = raw.lowercase(Locale.ROOT)
    if (lower.contains("biaya admin") || lower.contains("admin fee") ||
        lower.contains("sisa saldo") || lower.contains("saldo awal") || lower.contains("total saldo")) {
      return Pair(0L, "Teks bukan nominal transfer murni (saldo / biaya admin).")
    }

    var s = raw.trim()
    // Perbaiki kesalahan OCR umum 'O'/'o' menjadi '0'
    s = s.replace('O', '0').replace('o', '0')

    s = s.replace(
      Regex("(?i)(nominal\\s*transfer|jumlah\\s*transfer|total\\s*transfer|nominal|jumlah|amount|total\\s*diterima|rp\\.?|idr|[:=\\-])"),
      ""
    ).trim()

    // Hilangkan desimal sen di ujung (misal ,00 atau .00)
    if (s.endsWith(",00") || s.endsWith(".00")) {
      s = s.substring(0, s.length - 3).trim()
    }

    // Pisahkan ribuan dan desimal
    if (s.contains(",") && !s.contains(".")) {
      val parts = s.split(",")
      if (parts.size > 1 && parts.drop(1).all { it.length == 3 && it.all { c -> c.isDigit() } }) {
        s = parts.joinToString("")
      } else if (parts.size == 2 && parts[1].length <= 2) {
        s = parts[0]
      }
    } else if (s.contains(".") && !s.contains(",")) {
      val parts = s.split(".")
      if (parts.size > 1 && parts.drop(1).all { it.length == 3 && it.all { c -> c.isDigit() } }) {
        s = parts.joinToString("")
      }
    } else if (s.contains(".") && s.contains(",")) {
      val lastDot = s.lastIndexOf('.')
      val lastComma = s.lastIndexOf(',')
      if (lastComma > lastDot) {
        s = s.substring(0, lastComma).filter { it.isDigit() }
      } else {
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

  fun parseAdmin(raw: String): Long {
    val clean = raw.filter { it.isDigit() }
    val value = clean.toLongOrNull() ?: 0L
    return maxOf(0L, value)
  }

  fun calculateTotal(nominal: Long, admin: Long): Long {
    return maxOf(0L, nominal + admin)
  }

  // =========================================================================
  // LAPORAN VALIDASI LENGKAP SEMUA 7 KOLOM
  // =========================================================================

  fun validateReceipt(receipt: Receipt): FieldValidationReport {
    val errors = mutableMapOf<TargetRecipientField, String>()
    val warnings = mutableMapOf<TargetRecipientField, String>()

    // 1. TANGGAL
    val (dateClean, dateIssue) = sanitizeDate(receipt.displayDate)
    if (receipt.displayDate.isBlank()) {
      warnings[TargetRecipientField.TANGGAL] = "Tanggal transaksi belum terdeteksi. Silakan periksa manual."
    } else if (dateClean.isBlank() && dateIssue != null) {
      warnings[TargetRecipientField.TANGGAL] = dateIssue
    }

    // 2. WAKTU
    val (timeClean, timeIssue) = sanitizeTime(receipt.displayTime)
    if (receipt.displayTime.isBlank()) {
      warnings[TargetRecipientField.WAKTU] = "Waktu transaksi belum terdeteksi. Silakan periksa manual."
    } else if (timeClean.isBlank() && timeIssue != null) {
      warnings[TargetRecipientField.WAKTU] = timeIssue
    }

    // 3. REFERENSI
    val (refClean, refIssue) = sanitizeRefNumber(receipt.refNumber, receipt.recipientAccount)
    if (receipt.refNumber.isBlank()) {
      warnings[TargetRecipientField.REFERENSI] = "Nomor referensi / SN belum terdeteksi."
    } else if (refClean.isBlank() && refIssue != null) {
      warnings[TargetRecipientField.REFERENSI] = refIssue
    }

    // 4. BANK TUJUAN
    val (bankClean, bankIssue) = sanitizeBank(receipt.recipientBank)
    if (bankClean.isBlank()) {
      errors[TargetRecipientField.BANK] = "Bank tujuan penerima wajib diisi."
    } else if (bankIssue != null) {
      warnings[TargetRecipientField.BANK] = bankIssue
    }

    // 5. NOMOR REKENING
    val (rekClean, rekIssue) = sanitizeAccount(receipt.recipientAccount, receipt.refNumber)
    if (receipt.recipientAccount.isBlank()) {
      warnings[TargetRecipientField.REKENING] = "Nomor rekening penerima wajib 10–15 digit angka."
    } else if (rekClean.isBlank() && rekIssue != null) {
      errors[TargetRecipientField.REKENING] = rekIssue
    }

    // 6. NAMA PENERIMA
    val (nameClean, nameIssue) = sanitizeRecipientName(receipt.recipientName)
    if (nameClean.isBlank()) {
      errors[TargetRecipientField.PENERIMA] = "Nama penerima transfer wajib diisi."
    } else if (nameIssue != null) {
      warnings[TargetRecipientField.PENERIMA] = nameIssue
    }

    // 7. NOMINAL
    if (receipt.amount <= 0L) {
      errors[TargetRecipientField.NOMINAL] = "Nominal transfer wajib bernilai lebih dari Rp0."
    }

    // 8. BIAYA ADMIN
    if (receipt.adminFee < 0L) {
      errors[TargetRecipientField.ADMIN] = "Biaya admin tidak boleh bernilai negatif."
    }

    // 9. TOTAL
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
