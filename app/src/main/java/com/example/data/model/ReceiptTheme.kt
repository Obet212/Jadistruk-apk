package com.example.data.model

/**
 * Pilihan tema template cetak struk JADISTRUK.
 * Mendukung 3 gaya desain: Sederhana, Profesional, dan Modern.
 */
enum class ReceiptTheme(
  val id: String,
  val displayName: String,
  val tagLine: String,
  val description: String
) {
  PROFESIONAL(
    id = "profesional",
    displayName = "1. Standard",
    tagLine = "Format Standard",
    description = "Format standard lengkap dengan kode referensi dan data penerima terpisah"
  ),
  SEDERHANA(
    id = "sederhana",
    displayName = "2. Minimalis",
    tagLine = "Format Minimalis",
    description = "Tata letak ringkas tanpa sekat garis, waktu terpusat, dan data penerima langsung"
  ),
  MODERN(
    id = "modern",
    displayName = "3. Modern",
    tagLine = "Format Modern",
    description = "Header bukti transfer tegas, blok kepada, dan nominal total bayar besar terpusat"
  );

  companion object {
    fun fromId(id: String?): ReceiptTheme {
      if (id == null) return PROFESIONAL
      return when (id.lowercase()) {
        "standard", "standar", "profesional" -> PROFESIONAL
        "minimalis", "sederhana" -> SEDERHANA
        "modern" -> MODERN
        else -> entries.find { it.id.equals(id, ignoreCase = true) } ?: PROFESIONAL
      }
    }
  }
}
