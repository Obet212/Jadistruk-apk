package com.example.data.model

import java.text.NumberFormat
import java.util.Locale

data class Receipt(
  val id: Long = 0L,
  // 1. WAKTU
  val transactionDate: String = "",
  // 2. BANK
  val recipientBank: String = "",
  // 3. REKENING
  val recipientAccount: String = "",
  // 4. PENERIMA
  val recipientName: String = "",
  // 5. NOMINAL
  val amount: Long = 0L,
  // 6. ADMIN (diisi manual)
  val adminFee: Long = 0L,
  // Other supporting fields
  val refNumber: String = "",
  val notes: String = "",
  val rawOcrText: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val ocrResult: OcrResult? = null,
  val candidateConflicts: Map<TargetRecipientField, List<String>> = emptyMap(),
  val reviewFlags: List<String> = emptyList(),
) {
  // 7. TOTAL (dihitung otomatis: NOMINAL + ADMIN)
  val total: Long
    get() = amount + adminFee

  val formattedAmount: String
    get() = formatRupiah(amount)

  val formattedAdminFee: String
    get() = formatRupiah(adminFee)

  val formattedTotal: String
    get() = formatRupiah(total)

  private fun formatRupiah(value: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("id").setRegion("ID").build())
    format.maximumFractionDigits = 0
    return format.format(value).replace("Rp", "Rp ")
  }
}
