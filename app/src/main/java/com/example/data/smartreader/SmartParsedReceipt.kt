package com.example.data.smartreader

import com.example.data.model.OcrResult
import com.example.data.model.Receipt
import com.example.data.model.TargetRecipientField
import java.text.NumberFormat
import java.util.Locale

/**
 * Representasi data hasil ekstraksi otomatis JADISTRUK SMART READER.
 * Menyimpan seluruh kategori transaksi, metadata sumber aplikasi/bank,
 * daftar peringatan, serta field yang memerlukan pemeriksaan pengguna.
 */
data class SmartParsedReceipt(
  val source: TransactionSource = TransactionSource.GENERIC,
  val senderName: String = "",
  val recipientName: String = "",
  val recipientBank: String = "",
  val recipientAccount: String = "",
  val amount: Long = 0L,
  val adminFee: Long = 0L,
  val transactionDate: String = "",
  val transactionTime: String = "",
  val refNumber: String = "",
  val transactionType: String = "Transfer Bank",
  val transactionStatus: String = "Berhasil",
  val notes: String = "",
  val confidenceScore: Int = 85,
  val detectedFields: Set<TargetRecipientField> = emptySet(),
  val missingFields: List<String> = emptyList(),
  val reviewWarnings: List<String> = emptyList(),
  val rawOcrText: String = "",
  val ocrResult: OcrResult? = null
) {
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

  /**
   * Konversi hasil smart reading ke entitas Receipt bawaan JADISTRUK
   * agar dapat langsung disimpan ke database Room, dicetak via Bluetooth ESC/POS,
   * atau diedit di Thermal Receipt Preview.
   */
  fun toReceipt(receiptId: Long = 0L): Receipt {
    return Receipt(
      id = receiptId,
      transactionDate = displayDate,
      transactionTime = displayTime,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      recipientName = recipientName,
      amount = amount,
      adminFee = adminFee,
      refNumber = refNumber,
      notes = notes,
      rawOcrText = rawOcrText,
      ocrResult = ocrResult,
      reviewFlags = (missingFields + reviewWarnings).distinct(),
      senderName = senderName,
      sourceApp = source.displayName,
      transactionType = transactionType,
      transactionStatus = transactionStatus
    )
  }
}
