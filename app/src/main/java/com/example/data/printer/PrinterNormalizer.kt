package com.example.data.printer

import com.example.data.model.Receipt
import java.text.Normalizer

/**
 * Normalisasi karakter sebelum preview, cetak ESC/POS, dan berbagi gambar.
 * Sesuai aturan:
 * - Simbol bullet sensor (•) otomatis diganti dengan tanda bintang (*).
 * - Jumlah karakter harus tetap sama (contoh: •••••• menjadi ******).
 * - Karakter tidak berubah menjadi tanda tanya (?) saat dicetak pada printer thermal ESC/POS.
 */
object PrinterNormalizer {

  /**
   * Mengganti simbol sensor bullet (•) menjadi bintang (*) dengan mempertahankan panjang karakter,
   * serta menyaring karakter non-ASCII agar kompatibel penuh dengan printer ESC/POS.
   */
  fun normalizeForPrinter(text: String?): String {
    if (text.isNullOrEmpty()) return ""

    // 1. Ganti semua varian bullet sensor Unicode dengan asterisk (*) mempertahankan panjang persis
    val bulletRegex = Regex("[\u2022\u2023\u25E6\u2043\u2219\u00B7\u25CF\u25CB\u25D8\u22C5\u25AA\u25AB]")
    var result = bulletRegex.replace(text, "*")

    // 2. Normalisasi tanda kutip kurva, dash panjang, dan simbol umum
    result = result
      .replace('\u201C', '"')
      .replace('\u201D', '"')
      .replace('\u2018', '\'')
      .replace('\u2019', '\'')
      .replace('\u2013', '-')
      .replace('\u2014', '-')
      .replace("\u2026", "...")
      .replace("€", "EUR")
      .replace("£", "GBP")
      .replace("¥", "JPY")

    // 3. Decompose accents (misal é -> e)
    val decomposed = Normalizer.normalize(result, Normalizer.Form.NFD)
    val noDiacritics = Regex("\\p{InCombiningDiacriticalMarks}+").replace(decomposed, "")

    // 4. Pastikan karakter berada di printable ASCII 32..126 atau newline
    val sb = StringBuilder(noDiacritics.length)
    for (ch in noDiacritics) {
      if (ch.code in 32..126 || ch == '\n' || ch == '\r' || ch == '\t') {
        sb.append(ch)
      } else {
        // Jangan cetak karakter tak dikenal sebagai '?'
        sb.append(' ')
      }
    }

    return sb.toString()
  }

  fun normalizeReceipt(receipt: Receipt): Receipt {
    return receipt.copy(
      recipientName = normalizeForPrinter(receipt.recipientName),
      recipientBank = normalizeForPrinter(receipt.recipientBank),
      recipientAccount = normalizeForPrinter(receipt.recipientAccount),
      refNumber = normalizeForPrinter(receipt.refNumber),
      notes = normalizeForPrinter(receipt.notes),
      transactionDate = normalizeForPrinter(receipt.transactionDate),
      transactionTime = normalizeForPrinter(receipt.transactionTime)
    )
  }
}
