package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Receipt

@Entity(tableName = "receipts")
data class ReceiptEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val recipientName: String,
  val recipientBank: String,
  val recipientAccount: String,
  val amount: Long,
  val adminFee: Long = 0L,
  val transactionDate: String,
  val refNumber: String,
  val notes: String,
  val rawOcrText: String,
  val createdAt: Long = System.currentTimeMillis(),
) {
  fun toModel(): Receipt {
    val trimmed = transactionDate.trim()
    val parts = trimmed.split(Regex("\\s+"))
    val datePart = parts.firstOrNull { it.contains("/") || it.contains("-") || it.contains(".") } ?: parts.firstOrNull() ?: ""
    val timePart = parts.firstOrNull { it.contains(":") } ?: ""

    return Receipt(
      id = id,
      recipientName = recipientName,
      recipientBank = recipientBank,
      recipientAccount = recipientAccount,
      amount = amount,
      adminFee = adminFee,
      transactionDate = datePart.ifBlank { trimmed },
      transactionTime = timePart,
      refNumber = refNumber,
      notes = notes,
      rawOcrText = rawOcrText,
      createdAt = createdAt,
    )
  }

  companion object {
    fun fromModel(model: Receipt): ReceiptEntity =
      ReceiptEntity(
        id = model.id,
        recipientName = model.recipientName,
        recipientBank = model.recipientBank,
        recipientAccount = model.recipientAccount,
        amount = model.amount,
        adminFee = model.adminFee,
        transactionDate = model.fullDateTime.ifBlank { model.transactionDate },
        refNumber = model.refNumber,
        notes = model.notes,
        rawOcrText = model.rawOcrText,
        createdAt = model.createdAt,
      )
  }
}
