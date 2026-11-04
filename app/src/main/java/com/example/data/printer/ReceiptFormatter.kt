package com.example.data.printer

import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import com.example.data.model.ReceiptTheme

/**
 * Representasi satu baris struk thermal presisi tinggi.
 * Digunakan bersama oleh mesin pencetakan Bluetooth ESC/POS dan mesin pratinjau thermal 100% identik.
 */
data class FormattedLine(
  val text: String,
  val isBold: Boolean = false,
  val isDoubleHeight: Boolean = false,
  val isCentered: Boolean = false,
  val leftCol: String? = null,
  val rightCol: String? = null
)

object ReceiptFormatter {

  fun getLineWidth(paperWidthMm: Int): Int {
    return if (paperWidthMm == 80 || paperWidthMm == 88) 48 else 32
  }

  /**
   * Menghasilkan daftar baris terstruktur untuk mesin layout cetak dan pratinjau.
   */
  fun getFormattedLines(receipt: Receipt, settings: PrintSettings): List<FormattedLine> {
    val lineWidth = getLineWidth(settings.paperWidthMm)

    return when (settings.receiptTheme) {
      ReceiptTheme.SEDERHANA -> generateSederhanaLines(receipt, settings, lineWidth)
      ReceiptTheme.PROFESIONAL -> generateProfesionalLines(receipt, settings, lineWidth)
      ReceiptTheme.MODERN -> generateModernLines(receipt, settings, lineWidth)
    }
  }

  /**
   * Menghasilkan teks monospaced utuh dengan lebar kolom absolut (32 atau 48 kolom).
   */
  fun formatText(receipt: Receipt, settings: PrintSettings): String {
    val lineWidth = getLineWidth(settings.paperWidthMm)
    val lines = getFormattedLines(receipt, settings)
    val sb = StringBuilder()

    for (line in lines) {
      if (line.isCentered && line.text.length < lineWidth) {
        sb.appendLine(centerText(line.text, lineWidth))
      } else {
        sb.appendLine(line.text)
      }
    }
    sb.appendLine()
    return sb.toString()
  }

  // =========================================================================
  // 1. TEMA MINIMALIS (Sesuai Gambar Tab "2. Minimalis")
  // =========================================================================
  private fun generateSederhanaLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDash = "-".repeat(lineWidth)
    val (datePart, timePart) = splitDateTime(receipt.transactionDate)

    // 1. Nama Toko
    val storeName = settings.storeName.ifBlank { "TRFBT CELL" }
    list.add(FormattedLine(text = storeName, isBold = true, isCentered = true))
    list.add(FormattedLine(text = ""))

    // 2. Waktu Transaksi (Tengah)
    val dateTimeStr = if (datePart.isNotBlank() && timePart.isNotBlank()) {
      "$datePart $timePart"
    } else {
      receipt.transactionDate.ifBlank { "2026-10-28 15:33:00" }
    }
    list.add(FormattedLine(text = dateTimeStr, isCentered = true))
    list.add(FormattedLine(text = ""))

    // 3. Bagian Penerima
    list.add(FormattedLine(text = "PENERIMA:"))
    val recipient = receipt.recipientName.ifBlank { "LANANG SUTRISNO" }
    list.add(FormattedLine(text = recipient, isBold = true))
    val bank = receipt.recipientBank.ifBlank { "BRI" }
    val acc = receipt.recipientAccount.ifBlank { "001601120412504" }
    list.add(FormattedLine(text = "$bank $acc".trim()))
    list.add(FormattedLine(text = ""))

    // 4. Finansial
    list.addAll(twoColumnsOrWrapped("NOMINAL", formatRp(receipt.formattedAmount), lineWidth))
    list.addAll(twoColumnsOrWrapped("ADMIN", formatRp(receipt.formattedAdminFee), lineWidth))
    list.add(FormattedLine(text = dividerDash))
    list.addAll(twoColumnsOrWrapped("TOTAL", formatRp(receipt.formattedTotal), lineWidth, isBold = true))
    list.add(FormattedLine(text = ""))

    // 5. Footer Bersama
    addCommonFooter(list, settings, receipt, dividerDash)

    return list
  }

  // =========================================================================
  // 2. TEMA STANDARD (Sesuai Gambar Tab "1. Standard")
  // =========================================================================
  private fun generateProfesionalLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDouble = "=".repeat(lineWidth)
    val dividerDash = "-".repeat(lineWidth)
    val (datePart, timePart) = splitDateTime(receipt.transactionDate)

    // 1. Nama Toko
    val storeName = settings.storeName.ifBlank { "TRFBT CELL" }
    list.add(FormattedLine(text = storeName, isBold = true, isCentered = true))

    // 2. Garis Batas Ganda
    list.add(FormattedLine(text = dividerDouble))

    // 3. Tanggal & Waktu
    val dateVal = datePart.ifBlank { if (receipt.transactionDate.isNotBlank()) receipt.transactionDate else "2026-10-28" }
    list.addAll(twoColumnsOrWrapped("TANGGAL", dateVal, lineWidth))
    val timeVal = timePart.ifBlank { "15:33:00" }
    list.addAll(twoColumnsOrWrapped("WAKTU", timeVal, lineWidth))

    // 4. Garis Batas Putus-Putus
    list.add(FormattedLine(text = dividerDash))

    // 5. Kode Referensi
    list.add(FormattedLine(text = "KODE REFERENSI", isBold = true, isCentered = true))
    val refNum = receipt.refNumber.ifBlank { "2026102843500282289238639" }
    list.add(FormattedLine(text = refNum, isCentered = true))

    // 6. Garis Batas Putus-Putus
    list.add(FormattedLine(text = dividerDash))

    // 7. Data Penerima
    list.add(FormattedLine(text = "DATA PENERIMA", isBold = true, isCentered = true))
    val bankVal = receipt.recipientBank.ifBlank { "BRI" }
    list.addAll(twoColumnsOrWrapped("BANK TUJUAN", bankVal, lineWidth))
    val accVal = receipt.recipientAccount.ifBlank { "001601120412504" }
    list.addAll(twoColumnsOrWrapped("NO REKENING", accVal, lineWidth))
    val nameVal = receipt.recipientName.ifBlank { "LANANG SUTRISNO" }
    list.addAll(twoColumnsOrWrapped("PENERIMA", nameVal, lineWidth))

    // 8. Garis Batas Putus-Putus
    list.add(FormattedLine(text = dividerDash))

    // 9. Nominal & Admin
    list.addAll(twoColumnsOrWrapped("NOMINAL", formatRp(receipt.formattedAmount), lineWidth))
    list.addAll(twoColumnsOrWrapped("ADMIN", formatRp(receipt.formattedAdminFee), lineWidth))

    // 10. Garis Batas Ganda & TOTAL
    list.add(FormattedLine(text = dividerDouble))
    list.addAll(twoColumnsOrWrapped("TOTAL", formatRp(receipt.formattedTotal), lineWidth, isBold = true))
    list.add(FormattedLine(text = dividerDouble))
    list.add(FormattedLine(text = ""))

    // 11. Footer Standar
    addCommonFooter(list, settings, receipt, dividerDash)

    return list
  }

  // =========================================================================
  // 3. TEMA MODERN (Sesuai Gambar Tab "3. Modern")
  // =========================================================================
  private fun generateModernLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDouble = "=".repeat(lineWidth)
    val dividerDash = "-".repeat(lineWidth)
    val (datePart, timePart) = splitDateTime(receipt.transactionDate)

    // 1. Nama Toko
    val storeName = settings.storeName.ifBlank { "TRFBT CELL" }
    list.add(FormattedLine(text = storeName, isBold = true, isCentered = true))
    list.add(FormattedLine(text = ""))

    // 2. Garis Batas Ganda & Judul
    list.add(FormattedLine(text = dividerDouble))
    list.add(FormattedLine(text = "BUKTI TRANSFER", isBold = true, isCentered = true))
    list.add(FormattedLine(text = dividerDouble))

    // 3. Waktu & Ref
    val dateVal = datePart.ifBlank { if (receipt.transactionDate.isNotBlank()) receipt.transactionDate else "2026-10-28" }
    list.addAll(twoColumnsOrWrapped("TANGGAL", dateVal, lineWidth))
    val timeVal = timePart.ifBlank { "15:33:00" }
    list.addAll(twoColumnsOrWrapped("WAKTU", timeVal, lineWidth))
    val refVal = receipt.refNumber.ifBlank { "2026102843500282289238639" }
    list.addAll(twoColumnsOrWrapped("REF", refVal, lineWidth))
    list.add(FormattedLine(text = ""))

    // 4. KEPADA: dan Detail Penerima
    list.add(FormattedLine(text = "KEPADA:"))
    val nameVal = receipt.recipientName.ifBlank { "LANANG SUTRISNO" }
    list.addAll(twoColumnsOrWrapped("PENERIMA", nameVal, lineWidth))
    val bankVal = receipt.recipientBank.ifBlank { "BRI" }
    list.addAll(twoColumnsOrWrapped("BANK", bankVal, lineWidth))
    val accVal = receipt.recipientAccount.ifBlank { "001601120412504" }
    list.addAll(twoColumnsOrWrapped("NO REKENING", accVal, lineWidth))
    list.add(FormattedLine(text = ""))

    // 5. Highlight TOTAL BAYAR
    list.add(FormattedLine(text = "TOTAL BAYAR", isBold = true, isCentered = true))
    list.add(
      FormattedLine(
        text = formatRp(receipt.formattedTotal),
        isBold = true,
        isDoubleHeight = true,
        isCentered = true
      )
    )
    list.add(FormattedLine(text = ""))

    // 6. Footer Modern
    addCommonFooter(list, settings, receipt, dividerDash)

    return list
  }

  // =========================================================================
  // HELPER FORMAT & FOOTER
  // =========================================================================
  fun splitDateTime(rawDate: String): Pair<String, String> {
    val trimmed = rawDate.trim()
    if (trimmed.isBlank()) return Pair("", "")
    val parts = trimmed.split(Regex("\\s+"))
    return if (parts.size >= 2) {
      val datePart = parts[0]
      val timePart = parts.drop(1).joinToString(" ").replace(Regex("(?i)wib|wita|wit"), "").trim()
      Pair(datePart, timePart)
    } else {
      Pair(trimmed, "")
    }
  }

  fun formatRp(amountStr: String): String {
    return amountStr.uppercase()
  }

  fun getTid(receipt: Receipt): String {
    return if (receipt.refNumber.length >= 6) {
      receipt.refNumber.takeLast(6).uppercase()
    } else if (receipt.id > 0) {
      "AAE" + receipt.id.toString().takeLast(3).padStart(3, '0')
    } else {
      "AAE7FA"
    }
  }

  private fun addCommonFooter(
    list: MutableList<FormattedLine>,
    settings: PrintSettings,
    receipt: Receipt,
    divider: String
  ) {
    list.add(FormattedLine(text = divider))
    list.add(FormattedLine(text = "** TRANSAKSI BERHASIL **", isBold = true, isCentered = true))

    val storeAppTag = settings.storeName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: "TRFBT"
    val defaultSubFooter = "SALINAN - VIA $storeAppTag APP"

    if (settings.footerMessage.isNotBlank()) {
      for (rawLine in settings.footerMessage.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isNotBlank()) {
          list.add(FormattedLine(text = trimmed, isCentered = true))
        }
      }
    } else {
      list.add(FormattedLine(text = defaultSubFooter, isCentered = true))
    }
    list.add(FormattedLine(text = "TERIMA KASIH", isBold = true, isCentered = true))
    list.add(FormattedLine(text = "TID: ${getTid(receipt)}", isCentered = true))
  }

  // =========================================================================
  // HELPER FUNCTIONS (Kalkulasi Kolom & Spasi Presisi Karakter)
  // =========================================================================
  fun makeRepeatedPattern(pattern: String, width: Int): String {
    val times = (width / pattern.length) + 1
    val result = pattern.repeat(times)
    return result.take(width)
  }

  fun wrapText(text: String, lineWidth: Int): List<String> {
    if (text.length <= lineWidth) return listOf(text)

    val words = text.split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var currentLine = StringBuilder()

    for (word in words) {
      if (word.isBlank()) continue

      if (currentLine.isEmpty()) {
        if (word.length <= lineWidth) {
          currentLine.append(word)
        } else {
          word.chunked(lineWidth).forEach { chunk ->
            lines.add(chunk)
          }
        }
      } else {
        if (currentLine.length + 1 + word.length <= lineWidth) {
          currentLine.append(" ").append(word)
        } else {
          lines.add(currentLine.toString())
          currentLine = StringBuilder(word)
        }
      }
    }

    if (currentLine.isNotEmpty()) {
      lines.add(currentLine.toString())
    }

    return lines
  }

  fun centerText(text: String, width: Int): String {
    if (text.length >= width) return text.take(width)
    val padding = (width - text.length) / 2
    return " ".repeat(padding) + text
  }

  fun twoColumns(left: String, right: String, width: Int): String {
    val spacesNeeded = width - left.length - right.length
    return if (spacesNeeded > 0) {
      "$left${" ".repeat(spacesNeeded)}$right"
    } else {
      "$left $right".take(width)
    }
  }

  /**
   * Menghasilkan satu atau beberapa baris terstruktur untuk dua kolom data,
   * menjamin bahwa tidak ada baris yang panjangnya melebihi lebar kolom kertas (tidak meluber/terpotong).
   */
  /**
   * Menghasilkan daftar baris terstruktur untuk layout dua kolom yang stabil:
   * - Kolom kiri (keterangan/label) rata kiri
   * - Kolom kanan (nilai/nominal) rata kanan dengan batas akhir yang seragam
   * - Menyimpan teks mentah (text) untuk printer Bluetooth ESC/POS tanpa perubahan
   * - Menyimpan leftCol dan rightCol untuk mesin rendering Canvas pratinjau & JPG
   */
  fun twoColumnsOrWrapped(
    left: String,
    right: String,
    width: Int,
    isBold: Boolean = false,
    isDoubleHeight: Boolean = false
  ): List<FormattedLine> {
    val totalLen = left.length + right.length
    if (totalLen + 1 <= width) {
      val spaces = " ".repeat(width - totalLen)
      return listOf(
        FormattedLine(
          text = "$left$spaces$right",
          isBold = isBold,
          isDoubleHeight = isDoubleHeight,
          leftCol = left,
          rightCol = right
        )
      )
    }

    val lines = mutableListOf<FormattedLine>()
    val maxLabelWidth = if (width == 48) 20 else 13
    val maxValueWidth = width - maxLabelWidth - 1

    if (left.length <= maxLabelWidth) {
      val availRight = width - left.length - 1
      val rightWrapped = wrapText(right, availRight)
      if (rightWrapped.isNotEmpty()) {
        val firstR = rightWrapped[0]
        val sp1 = " ".repeat((width - left.length - firstR.length).coerceAtLeast(1))
        lines.add(
          FormattedLine(
            text = "$left$sp1$firstR",
            isBold = isBold,
            isDoubleHeight = isDoubleHeight,
            leftCol = left,
            rightCol = firstR
          )
        )
        val indent = " ".repeat(width - availRight)
        for (i in 1 until rightWrapped.size) {
          val subR = rightWrapped[i]
          val spSub = " ".repeat((width - indent.length - subR.length).coerceAtLeast(0))
          lines.add(
            FormattedLine(
              text = "$indent$spSub$subR",
              isBold = isBold,
              isDoubleHeight = isDoubleHeight,
              leftCol = "",
              rightCol = subR
            )
          )
        }
        return lines
      }
    }

    // Jika label sendiri panjang: bungkus kedua kolom dalam tabel dua kolom yang konsisten
    val leftWrapped = wrapText(left, maxLabelWidth)
    val rightWrapped = wrapText(right, maxValueWidth)
    val maxLines = maxOf(leftWrapped.size, rightWrapped.size)

    for (i in 0 until maxLines) {
      val l = leftWrapped.getOrNull(i) ?: ""
      val r = rightWrapped.getOrNull(i) ?: ""
      val lineText = twoColumns(l, r, width)
      lines.add(
        FormattedLine(
          text = lineText,
          isBold = isBold,
          isDoubleHeight = isDoubleHeight,
          leftCol = l,
          rightCol = r
        )
      )
    }
    return lines
  }
}
