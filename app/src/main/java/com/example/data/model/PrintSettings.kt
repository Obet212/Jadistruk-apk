package com.example.data.model

data class PrintSettings(
  val storeName: String = "JADISTRUK POS",
  val storeAddress: String = "Layanan Cetak Struk Penerima",
  val storePhone: String = "",
  val footerMessage: String = "Terima kasih! Bukti transfer telah diverifikasi.",
  val paperWidthMm: Int = 58, // 58 or 80
  val showDate: Boolean = true,
  val showRefNumber: Boolean = false,
  val autoAddHeader: Boolean = true,
  val autoApplyAdminFee: Boolean = true,
  val adminFeeRules: List<AdminFeeRule> = AdminFeeRule.defaultRules(),
  val selectedPrinterAddress: String = "",
  val selectedPrinterName: String = "",
  val autoCutPaper: Boolean = true,
  val printFeedLines: Int = 3,
)
