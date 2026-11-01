package com.example.data.model

data class OcrTemplate(
  val id: Long = 0L,
  val bankName: String,
  val category: String, // "Bank Transfer" or "E-Wallet"
  val detectionKeywords: String, // Comma-separated keywords to identify this receipt type
  val recipientPattern: String, // Regex or key phrases to extract recipient name
  val accountPattern: String, // Regex or key phrases for account number / HP
  val amountPattern: String, // Regex or key phrases for amount
  val isBuiltIn: Boolean = true,
  val areaConfigJson: String = "", // JSON configuration for field bounding box areas
  val sampleImageUri: String = "", // Persistent URI or internal file path of sample receipt image
)
