package com.example.data.smartreader

import com.example.data.model.OcrResult

/**
 * Interface dasar untuk parser spesifik masing-masing aplikasi pembayaran & bank.
 * Memastikan arsitektur modular sehingga setiap sumber memiliki logika parsing mandiri.
 */
interface SmartReceiptParser {
  val source: TransactionSource

  /**
   * Mengekstrak seluruh data transaksi secara akurat sesuai tata letak sumber.
   */
  fun parse(lines: List<String>, fullText: String, ocrResult: OcrResult?): SmartParsedReceipt
}
