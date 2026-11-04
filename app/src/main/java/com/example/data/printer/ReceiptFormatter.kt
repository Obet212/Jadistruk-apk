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
  // 1. TEMA SEDERHANA (Minimalis: Bersih, Rapi, Alamat di Bawah Nama Toko)
  // =========================================================================
  private fun generateSederhanaLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDash = makeRepeatedPattern("- ", lineWidth)
    val dividerSolid = "-".repeat(lineWidth)

    // 1. Header Minimalis
    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) {
        val wrappedName = wrapText(settings.storeName.trim(), lineWidth)
        for (w in wrappedName) {
          list.add(FormattedLine(text = w, isBold = true, isCentered = true))
        }
      }
      // Tampilkan alamat toko tepat di bawah nama toko jika tersedia
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddr = wrapText(settings.storeAddress.trim(), lineWidth)
        for (w in wrappedAddr) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        val wrappedPhone = wrapText("Telp: ${settings.storePhone.trim()}", lineWidth)
        for (w in wrappedPhone) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      list.add(FormattedLine(text = dividerDash))
    }

    // 2. Judul Struk
    list.add(FormattedLine(text = "BUKTI TRANSFER", isBold = true, isCentered = true))
    list.add(FormattedLine(text = dividerDash))

    // 3. Data Penerima
    if (receipt.transactionDate.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Tgl", receipt.transactionDate, lineWidth))
    }
    if (receipt.recipientBank.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Bank", receipt.recipientBank, lineWidth))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("No.Rek", receipt.recipientAccount, lineWidth))
    }
    if (receipt.recipientName.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Penerima", receipt.recipientName, lineWidth))
    }

    list.add(FormattedLine(text = dividerSolid))

    // 4. Finansial
    list.addAll(twoColumnsOrWrapped("Jumlah", receipt.formattedAmount, lineWidth))
    list.addAll(twoColumnsOrWrapped("Admin", receipt.formattedAdminFee, lineWidth))
    list.add(FormattedLine(text = dividerSolid))
    list.addAll(twoColumnsOrWrapped("TOTAL", receipt.formattedTotal, lineWidth, isBold = true))

    // 5. Informasi Pelengkap
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Ref", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Ket", receipt.notes, lineWidth))
    }

    list.add(FormattedLine(text = dividerDash))

    // 6. Footer Sederhana
    if (settings.footerMessage.isNotBlank()) {
      for (rawLine in settings.footerMessage.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isNotBlank()) {
          val wrapped = wrapText(trimmed, lineWidth)
          for (w in wrapped) {
            list.add(FormattedLine(text = w, isCentered = true))
          }
        }
      }
    } else {
      list.add(FormattedLine(text = "Terima Kasih", isCentered = true))
    }

    return list
  }

  // =========================================================================
  // 2. TEMA PROFESIONAL (Resmi Perbankan, Label "Transaksi", Tanpa Status Sukses)
  // =========================================================================
  private fun generateProfesionalLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDouble = "=".repeat(lineWidth)
    val dividerSingle = "-".repeat(lineWidth)

    // 1. Header Formal Toko
    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) {
        val wrappedStore = wrapText(settings.storeName.trim(), lineWidth)
        for (w in wrappedStore) {
          list.add(FormattedLine(text = w, isBold = true, isDoubleHeight = true, isCentered = true))
        }
      }
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddr = wrapText(settings.storeAddress.trim(), lineWidth)
        for (w in wrappedAddr) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        val wrappedPhone = wrapText("Telp/WA: ${settings.storePhone.trim()}", lineWidth)
        for (w in wrappedPhone) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      list.add(FormattedLine(text = dividerDouble))
    }

    // 2. Judul Struk Resmi (Bagian status transaksi sukses dihapus)
    list.add(FormattedLine(text = "STRUK BUKTI PENERIMA", isBold = true, isCentered = true))
    list.add(FormattedLine(text = dividerSingle))

    // 3. Kolom Data Penerima (Label "Waktu Transaksi" diubah menjadi "Transaksi")
    if (receipt.transactionDate.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Transaksi", receipt.transactionDate, lineWidth))
    }
    if (receipt.recipientBank.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Bank Penyelenggara", receipt.recipientBank, lineWidth))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("No. Rekening/HP", receipt.recipientAccount, lineWidth))
    }
    if (receipt.recipientName.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Nama Penerima", receipt.recipientName, lineWidth))
    }

    list.add(FormattedLine(text = dividerSingle))

    // 4. Rincian Finansial
    list.addAll(twoColumnsOrWrapped("Nominal Transfer", receipt.formattedAmount, lineWidth))
    list.addAll(twoColumnsOrWrapped("Biaya Layanan Admin", receipt.formattedAdminFee, lineWidth))
    list.add(FormattedLine(text = dividerSingle))
    list.addAll(twoColumnsOrWrapped("TOTAL PEMBAYARAN", receipt.formattedTotal, lineWidth, isBold = true))

    // 5. Informasi Pelengkap
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("No. Referensi Bank", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Catatan / Berita", receipt.notes, lineWidth))
    }

    list.add(FormattedLine(text = dividerDouble))

    // 6. Footer Resmi
    list.add(FormattedLine(text = "Simpan struk ini sebagai bukti pembayaran sah.", isCentered = true))
    if (settings.footerMessage.isNotBlank()) {
      for (rawLine in settings.footerMessage.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isNotBlank()) {
          val wrapped = wrapText(trimmed, lineWidth)
          for (w in wrapped) {
            list.add(FormattedLine(text = w, isCentered = true))
          }
        }
      }
    }

    return list
  }

  // =========================================================================
  // 3. TEMA MODERN (Fintech Bersih, Tanpa Simbol Dekoratif, Tanpa Powered By)
  // =========================================================================
  private fun generateModernLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDash = makeRepeatedPattern("- ", lineWidth)
    val dividerLine = "-".repeat(lineWidth)

    // 1. Header Modern Bersih (Tanpa simbol bintang dekoratif)
    if (settings.autoAddHeader) {
      list.add(FormattedLine(text = dividerDash))
      if (settings.storeName.isNotBlank()) {
        val wrapped = wrapText(settings.storeName.trim(), lineWidth)
        for (w in wrapped) {
          list.add(FormattedLine(text = w, isBold = true, isDoubleHeight = true, isCentered = true))
        }
      }
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddr = wrapText(settings.storeAddress.trim(), lineWidth)
        for (w in wrappedAddr) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        val wrappedPhone = wrapText("WA/CS: ${settings.storePhone.trim()}", lineWidth)
        for (w in wrappedPhone) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      list.add(FormattedLine(text = dividerDash))
    }

    // 2. Headline Modern (Tanpa simbol '>>>' dan '<<<')
    list.add(FormattedLine(text = "BUKTI TRANSFER DIGITAL", isBold = true, isCentered = true))
    list.add(FormattedLine(text = ""))

    // 3. Blok Rincian Penerima (Tanpa simbol '##')
    list.add(FormattedLine(text = "INFORMASI PENERIMA", isBold = true))
    if (receipt.transactionDate.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Waktu", receipt.transactionDate, lineWidth))
    }
    if (receipt.recipientBank.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Bank/Tujuan", receipt.recipientBank, lineWidth))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("ID / No. Rek", receipt.recipientAccount, lineWidth))
    }
    if (receipt.recipientName.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Atas Nama", receipt.recipientName, lineWidth))
    }

    list.add(FormattedLine(text = dividerLine))

    // 4. Blok Rincian Tagihan (Tanpa simbol '##')
    list.add(FormattedLine(text = "RINCIAN BIAYA", isBold = true))
    list.addAll(twoColumnsOrWrapped("Nominal", receipt.formattedAmount, lineWidth))
    list.addAll(twoColumnsOrWrapped("Biaya Admin", receipt.formattedAdminFee, lineWidth))
    list.add(FormattedLine(text = dividerLine))

    // Highlight Total (Tebal & Ukuran Ganda, Tanpa simbol '>>>' dan '<<<')
    list.addAll(
      twoColumnsOrWrapped("TOTAL BAYAR", receipt.formattedTotal, lineWidth, isBold = true, isDoubleHeight = true)
    )

    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Ref ID", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      list.addAll(twoColumnsOrWrapped("Catatan", receipt.notes, lineWidth))
    }

    list.add(FormattedLine(text = dividerDash))

    // 5. Footer Modern (Teks "Powered by JADISTRUK" telah dihapus)
    list.add(FormattedLine(text = "TERIMA KASIH ATAS KUNJUNGAN ANDA", isBold = true, isCentered = true))
    if (settings.footerMessage.isNotBlank()) {
      for (rawLine in settings.footerMessage.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isNotBlank()) {
          val wrapped = wrapText(trimmed, lineWidth)
          for (w in wrapped) {
            list.add(FormattedLine(text = w, isCentered = true))
          }
        }
      }
    }

    return list
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
