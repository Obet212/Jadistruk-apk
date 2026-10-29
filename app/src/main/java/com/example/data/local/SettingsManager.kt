package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AdminFeeRule
import com.example.data.model.AppTheme
import com.example.data.model.PrintSettings
import com.example.data.model.ReceiptTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("jadistruk_settings", Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(loadSettings())
  val settings: StateFlow<PrintSettings> = _settings.asStateFlow()

  private fun loadSettings(): PrintSettings {
    val rulesJson = prefs.getString("admin_fee_rules", "") ?: ""
    val rules = AdminFeeRule.rulesFromJson(rulesJson)

    return PrintSettings(
      storeName = prefs.getString("store_name", "JADISTRUK POS") ?: "JADISTRUK POS",
      storeAddress = prefs.getString("store_address", "Layanan Cetak Struk Penerima") ?: "Layanan Cetak Struk Penerima",
      storePhone = prefs.getString("store_phone", "") ?: "",
      footerMessage = prefs.getString("footer_message", "Terima kasih! Bukti transfer telah diverifikasi.") ?: "Terima kasih! Bukti transfer telah diverifikasi.",
      paperWidthMm = prefs.getInt("paper_width", 58),
      showDate = prefs.getBoolean("show_date", true),
      showRefNumber = prefs.getBoolean("show_ref", false),
      autoAddHeader = prefs.getBoolean("auto_add_header", true),
      autoApplyAdminFee = prefs.getBoolean("auto_apply_admin_fee", true),
      adminFeeRules = rules,
      selectedPrinterAddress = prefs.getString("printer_address", "") ?: "",
      selectedPrinterName = prefs.getString("printer_name", "") ?: "",
      autoCutPaper = prefs.getBoolean("auto_cut_paper", true),
      printFeedLines = prefs.getInt("print_feed_lines", 3),
      receiptTheme = ReceiptTheme.fromId(prefs.getString("receipt_theme", "profesional")),
      appTheme = AppTheme.fromId(prefs.getString("app_ui_theme", "ocean_blue")),
    )
  }

  fun updateSettings(newSettings: PrintSettings) {
    prefs.edit()
      .putString("store_name", newSettings.storeName)
      .putString("store_address", newSettings.storeAddress)
      .putString("store_phone", newSettings.storePhone)
      .putString("footer_message", newSettings.footerMessage)
      .putInt("paper_width", newSettings.paperWidthMm)
      .putBoolean("show_date", newSettings.showDate)
      .putBoolean("show_ref", newSettings.showRefNumber)
      .putBoolean("auto_add_header", newSettings.autoAddHeader)
      .putBoolean("auto_apply_admin_fee", newSettings.autoApplyAdminFee)
      .putString("admin_fee_rules", AdminFeeRule.rulesToJson(newSettings.adminFeeRules))
      .putString("printer_address", newSettings.selectedPrinterAddress)
      .putString("printer_name", newSettings.selectedPrinterName)
      .putBoolean("auto_cut_paper", newSettings.autoCutPaper)
      .putInt("print_feed_lines", newSettings.printFeedLines)
      .putString("receipt_theme", newSettings.receiptTheme.id)
      .putString("app_ui_theme", newSettings.appTheme.id)
      .apply()
    _settings.value = newSettings
  }
}
