package com.example.data.smartreader

import java.util.Locale

/**
 * Mendeteksi secara otomatis sumber bukti transaksi berdasarkan karakteristik visual,
 * kata kunci struktural, logo teks, pola tata letak, dan frasa khas masing-masing aplikasi.
 */
object SourceDetector {

  data class DetectionResult(
    val source: TransactionSource,
    val confidence: Int, // 0..100
    val matchedMarkers: List<String>
  )

  /**
   * Menganalisis baris teks hasil OCR untuk menentukan sumber aplikasi / bank.
   * Mengembalikan DetectionResult dengan confidence score.
   */
  fun detectSource(fullText: String, lines: List<String> = emptyList()): DetectionResult {
    val normalized = fullText.lowercase(Locale.ROOT)
    val scores = mutableMapOf<TransactionSource, Int>()
    val markers = mutableMapOf<TransactionSource, MutableList<String>>()

    fun addScore(source: TransactionSource, points: Int, markerName: String) {
      scores[source] = (scores[source] ?: 0) + points
      markers.getOrPut(source) { mutableListOf() }.add(markerName)
    }

    // 1. DANA
    if (normalized.contains("dana protection")) addScore(TransactionSource.DANA, 50, "DANA Protection")
    if (normalized.contains("berhasil kirim uang")) addScore(TransactionSource.DANA, 45, "Berhasil Kirim Uang")
    if (normalized.contains("saldo dana")) addScore(TransactionSource.DANA, 35, "Saldo DANA")
    if (normalized.contains("kirim uang")) addScore(TransactionSource.DANA, 25, "Kirim Uang")
    if (normalized.contains("total pembayaran") || normalized.contains("total bayar")) addScore(TransactionSource.DANA, 20, "Total Pembayaran")
    if (Regex("\\bdana\\b").containsMatchIn(normalized)) addScore(TransactionSource.DANA, 25, "Keyword DANA")
    if (normalized.contains("bebas biaya") || normalized.contains("biaya transfer")) addScore(TransactionSource.DANA, 15, "Biaya Transaksi DANA")

    // 2. GoPay / Gojek
    if (normalized.contains("gopay plus") || normalized.contains("gopayplus")) addScore(TransactionSource.GOPAY, 50, "GoPay Plus")
    if (normalized.contains("transfer instan")) addScore(TransactionSource.GOPAY, 40, "Transfer Instan GoPay")
    if (normalized.contains("order id") || normalized.contains("order-id")) addScore(TransactionSource.GOPAY, 25, "Order ID")
    if (Regex("\\bgopay\\b").containsMatchIn(normalized)) addScore(TransactionSource.GOPAY, 35, "Keyword GoPay")
    if (Regex("\\bgojek\\b").containsMatchIn(normalized)) addScore(TransactionSource.GOPAY, 25, "Keyword Gojek")
    if (normalized.contains("kirim saldo")) addScore(TransactionSource.GOPAY, 20, "Kirim Saldo")

    // 3. OVO
    if (normalized.contains("ovo cash")) addScore(TransactionSource.OVO, 50, "OVO Cash")
    if (normalized.contains("ovo points")) addScore(TransactionSource.OVO, 40, "OVO Points")
    if (Regex("\\bovo\\b").containsMatchIn(normalized)) addScore(TransactionSource.OVO, 30, "Keyword OVO")
    if (normalized.contains("transfer ke sesama ovo") || normalized.contains("ke rekening bank")) {
      if (normalized.contains("ovo")) addScore(TransactionSource.OVO, 25, "Transfer OVO")
    }

    // 4. ShopeePay / SeaBank
    if (normalized.contains("shopeepay")) addScore(TransactionSource.SHOPEEPAY, 50, "ShopeePay")
    if (normalized.contains("seabank")) addScore(TransactionSource.SHOPEEPAY, 40, "SeaBank")
    if (normalized.contains("shopee") && (normalized.contains("transfer") || normalized.contains("berhasil"))) {
      addScore(TransactionSource.SHOPEEPAY, 35, "Shopee Transfer")
    }
    if (normalized.contains("waktu selesai") && normalized.contains("no. transaksi")) {
      addScore(TransactionSource.SHOPEEPAY, 20, "Tata Letak Shopee")
    }

    // 5. BRImo / Bank BRI
    if (normalized.contains("brimo")) addScore(TransactionSource.BRIMO, 55, "BRImo Header")
    if (normalized.contains("bank rakyat indonesia")) addScore(TransactionSource.BRIMO, 45, "Bank Rakyat Indonesia")
    if (Regex("\\bbri\\b").containsMatchIn(normalized) && (normalized.contains("sumber dana") || normalized.contains("nomor referensi"))) {
      addScore(TransactionSource.BRIMO, 35, "BRI Struk Marker")
    }
    if (normalized.contains("sumber dana") && normalized.contains("tujuan transfer")) {
      addScore(TransactionSource.BRIMO, 25, "Format Transfer BRImo")
    }

    // 6. BCA / myBCA / BCA Mobile
    if (normalized.contains("m-bca") || normalized.contains("bca mobile") || normalized.contains("mybca")) {
      addScore(TransactionSource.BCA, 55, "BCA Mobile App")
    }
    if (normalized.contains("m-transfer")) addScore(TransactionSource.BCA, 45, "m-Transfer BCA")
    if (normalized.contains("transfer antar bank") && normalized.contains("berhasil") && normalized.contains("bca")) {
      addScore(TransactionSource.BCA, 40, "Transfer Antar Bank BCA")
    }
    if (normalized.contains("bank central asia")) addScore(TransactionSource.BCA, 35, "Bank Central Asia")
    if (Regex("\\bbca\\b").containsMatchIn(normalized) && (normalized.contains("no. referensi") || normalized.contains("tgl:"))) {
      addScore(TransactionSource.BCA, 30, "Format Bukti BCA")
    }

    // 7. Livin' by Mandiri
    if (normalized.contains("livin' by mandiri") || normalized.contains("livin by mandiri") || normalized.contains("livin")) {
      addScore(TransactionSource.LIVIN, 55, "Livin' Header")
    }
    if (normalized.contains("resi transfer") && (normalized.contains("mandiri") || normalized.contains("rekening sumber"))) {
      addScore(TransactionSource.LIVIN, 45, "Resi Transfer Mandiri")
    }
    if (normalized.contains("bank mandiri")) addScore(TransactionSource.LIVIN, 30, "Bank Mandiri")

    // 8. BNI Mobile
    if (normalized.contains("bni mobile") || normalized.contains("bni mobile banking")) {
      addScore(TransactionSource.BNI, 55, "BNI Mobile Banking")
    }
    if (normalized.contains("rekening debet") && (normalized.contains("bni") || normalized.contains("bank negara indonesia"))) {
      addScore(TransactionSource.BNI, 40, "Format Debet BNI")
    }
    if (normalized.contains("bank negara indonesia")) addScore(TransactionSource.BNI, 35, "Bank Negara Indonesia")

    // 9. BSI Mobile
    if (normalized.contains("bsi mobile") || normalized.contains("bank syariah indonesia")) {
      addScore(TransactionSource.BSI, 55, "BSI Mobile")
    }
    if (Regex("\\bbsi\\b").containsMatchIn(normalized) && normalized.contains("transfer sukses")) {
      addScore(TransactionSource.BSI, 35, "BSI Sukses")
    }

    // Temukan skor tertinggi
    val bestMatch = scores.maxByOrNull { it.value }

    return if (bestMatch != null && bestMatch.value >= 25) {
      val normalizedConfidence = (bestMatch.value * 2).coerceIn(60, 99)
      DetectionResult(
        source = bestMatch.key,
        confidence = normalizedConfidence,
        matchedMarkers = markers[bestMatch.key] ?: emptyList()
      )
    } else {
      DetectionResult(
        source = TransactionSource.GENERIC,
        confidence = 50,
        matchedMarkers = listOf("Deteksi Otomatis: Format Bukti Transfer Umum")
      )
    }
  }
}
