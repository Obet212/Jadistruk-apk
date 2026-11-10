package com.example.data.model

import java.text.NumberFormat
import java.util.Locale

data class Receipt(
  val id: Long = 0L,
  // 1. TANGGAL (DD/MM/YYYY)
  val transactionDate: String = "",
  // 2. WAKTU (HH:mm:ss atau HH:mm)
  val transactionTime: String = "",
  // 3. BANK TUJUAN
  val recipientBank: String = "",
  // 4. NOMOR REKENING
  val recipientAccount: String = "",
  // 5. NAMA PENERIMA
  val recipientName: String = "",
  // 6. NOMINAL
  val amount: Long = 0L,
  // 7. BIAYA ADMIN
  val adminFee: Long = 0L,
  // 8. REFERENSI / KODE TRANSAKSI
  val refNumber: String = "",
  val notes: String = "",
  val rawOcrText: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val ocrResult: OcrResult? = null,
  val candidateConflicts: Map<TargetRecipientField, List<String>> = emptyMap(),
  val reviewFlags: List<String> = emptyList(),
  // Field tambahan JADISTRUK SMART READER
  val senderName: String = "",
  val senderBank: String = "",
  val senderAccount: String = "",
  val sourceApp: String = "",
  val transactionType: String = "Transfer Bank",
  val transactionStatus: String = "Berhasil",
) {
  // TOTAL (dihitung otomatis: NOMINAL + ADMIN)
  val total: Long
    get() = amount + adminFee

  val displayDate: String
    get() {
      val trimmed = transactionDate.trim()
      if (trimmed.isBlank()) return ""
      val parts = trimmed.split(Regex("\\s+"))
      return parts.firstOrNull { it.contains("/") || it.contains("-") || it.contains(".") } ?: parts[0]
    }

  val displayTime: String
    get() {
      if (transactionTime.isNotBlank()) return transactionTime.trim()
      val trimmed = transactionDate.trim()
      val parts = trimmed.split(Regex("\\s+"))
      return parts.firstOrNull { it.contains(":") } ?: ""
    }

  val fullDateTime: String
    get() {
      val d = displayDate
      val t = displayTime
      return when {
        d.isNotBlank() && t.isNotBlank() -> "$d $t"
        d.isNotBlank() -> d
        t.isNotBlank() -> t
        else -> ""
      }
    }

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
