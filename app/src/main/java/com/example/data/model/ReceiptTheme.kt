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
  SEDERHANA(
    id = "sederhana",
    displayName = "Sederhana",
    tagLine = "Minimalis & Ringkas",
    description = "Tata letak ringkas, hemat kertas, garis putus-putus klasik, cepat dibaca"
  ),
  PROFESIONAL(
    id = "profesional",
    displayName = "Profesional",
    tagLine = "Format Resmi Perbankan",
    description = "Standar resmi bukti transfer bank dengan garis ganda tegas dan status validasi"
  ),
  MODERN(
    id = "modern",
    displayName = "Modern",
    tagLine = "Fintech & Retail Kekinian",
    description = "Desain visual modern, hierarki blok terstruktur, dan aksen dekoratif elegan"
  );

  companion object {
    fun fromId(id: String?): ReceiptTheme {
      return entries.find { it.id.equals(id, ignoreCase = true) } ?: PROFESIONAL
    }
  }
}
