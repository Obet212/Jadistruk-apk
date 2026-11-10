package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ColumnType(val displayName: String) {
  MANUAL("Manual (Diisi Pengguna)"),
  AUTOMATIC("Otomatis (OCR dari Gambar)")
}

enum class ColumnAlignment(val displayName: String) {
  LEFT("Rata Kiri"),
  RIGHT("Rata Kanan")
}

enum class OcrFunctionType(val displayName: String, val description: String) {
  TANGGAL("Tanggal", "Angka dan pemisah tanggal (/, -, .)"),
  WAKTU("Waktu", "Angka dan pemisah jam (:, .)"),
  NAMA("Nama", "Hanya huruf dan spasi"),
  REKENING("Rekening", "Hanya angka"),
  NOMOR_HP("Nomor HP", "Hanya angka, wajib pertahankan angka 0 di depan"),
  NOMINAL("Nominal", "Angka dan pemisah nominal (Rp, .)"),
  REFERENSI("Referensi", "Huruf dan angka"),
  KETERANGAN("Keterangan", "Huruf, angka, dan spasi"),
  TEKS_BEBAS("Teks Bebas", "Seluruh karakter yang didukung")
}

enum class CharacterRule(val displayName: String) {
  HANYA_HURUF("Hanya Huruf"),
  HANYA_ANGKA("Hanya Angka"),
  HURUF_DAN_ANGKA("Huruf dan Angka"),
  KARAKTER_BEBAS("Karakter Bebas")
}

/**
 * Konfigurasi kolom fleksibel untuk setiap template aplikasi sumber di OBETSTRUK.
 * - Kolom Manual: diisi manual oleh pengguna, perataan wajib RATA KIRI.
 * - Kolom Otomatis: diambil via OCR berdasarkan koordinat dan toleransi, perataan wajib RATA KANAN.
 */
data class ObetColumn(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val type: ColumnType,
  val alignment: ColumnAlignment = if (type == ColumnType.MANUAL) ColumnAlignment.LEFT else ColumnAlignment.RIGHT,
  val order: Int = 0,
  val isEnabled: Boolean = true,
  val manualText: String = "",
  val ocrFunction: OcrFunctionType = OcrFunctionType.TEKS_BEBAS,
  val characterRule: CharacterRule = CharacterRule.KARAKTER_BEBAS,
  val maxLength: Int = 100,
  val normX: Float = 0.5f,
  val normY: Float = 0.5f,
  val toleranceWidth: Float = 0.8f,
  val toleranceHeight: Float = 0.05f,
  val currentValue: String = ""
) {

  fun applyRules(rawText: String): String {
    var filtered = rawText.trim()

    // 1. Filter sesuai OCR Function Type
    filtered = when (ocrFunction) {
      OcrFunctionType.TANGGAL -> {
        // Angka dan pemisah tanggal
        Regex("[^0-9/\\-.:\\s]").replace(filtered, "")
      }
      OcrFunctionType.WAKTU -> {
        // Angka dan tanda titik atau titik dua
        Regex("[^0-9:.]").replace(filtered, "")
      }
      OcrFunctionType.NAMA -> {
        // Hanya huruf dan spasi
        Regex("[^A-Za-z\\s]").replace(filtered, "")
      }
      OcrFunctionType.REKENING -> {
        // Hanya angka
        Regex("[^0-9]").replace(filtered, "")
      }
      OcrFunctionType.NOMOR_HP -> {
        // Hanya angka, WAJIB pertahankan angka 0 di depan
        val digitsOnly = Regex("[^0-9]").replace(filtered, "")
        if (digitsOnly.startsWith("62")) {
          "0" + digitsOnly.substring(2)
        } else {
          digitsOnly
        }
      }
      OcrFunctionType.NOMINAL -> {
        // Angka dan pemisah nominal
        filtered
      }
      OcrFunctionType.REFERENSI -> {
        // Huruf dan angka
        Regex("[^A-Za-z0-9]").replace(filtered, "")
      }
      OcrFunctionType.KETERANGAN -> {
        // Huruf, angka, dan spasi
        Regex("[^A-Za-z0-9\\s]").replace(filtered, "")
      }
      OcrFunctionType.TEKS_BEBAS -> filtered
    }

    // 2. Filter aturan karakter manual tambahan
    filtered = when (characterRule) {
      CharacterRule.HANYA_HURUF -> Regex("[^A-Za-z\\s]").replace(filtered, "")
      CharacterRule.HANYA_ANGKA -> Regex("[^0-9]").replace(filtered, "")
      CharacterRule.HURUF_DAN_ANGKA -> Regex("[^A-Za-z0-9\\s]").replace(filtered, "")
      CharacterRule.KARAKTER_BEBAS -> filtered
    }

    // 3. Batas panjang karakter
    if (maxLength in 1..filtered.length) {
      filtered = filtered.substring(0, maxLength)
    }

    return filtered
  }
}

data class SourceApp(
  val packageName: String,
  val appName: String,
  val templateId: Long = 0L,
  val iconName: String = "ic_launcher"
)
