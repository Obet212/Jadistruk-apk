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
  val isCentered: Boolean = false
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
  // 1. TEMA SEDERHANA (Minimalis, Ringkas, Hemat Kertas)
  // =========================================================================
  private fun generateSederhanaLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDash = makeRepeatedPattern("- ", lineWidth)
    val dividerSolid = "-".repeat(lineWidth)

    // 1. Header Ringkas
    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) {
        val wrapped = wrapText(settings.storeName.uppercase(), lineWidth)
        for (w in wrapped) {
          list.add(FormattedLine(text = w, isBold = true, isCentered = true))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        list.add(FormattedLine(text = "Telp: ${settings.storePhone}", isCentered = true))
      }
      list.add(FormattedLine(text = dividerDash))
    }

    // 2. Judul Struk
    list.add(FormattedLine(text = "BUKTI TRANSFER", isBold = true, isCentered = true))
    list.add(FormattedLine(text = dividerDash))

    // 3. Data Penerima
    if (receipt.transactionDate.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Tgl", receipt.transactionDate, lineWidth)))
    }
    if (receipt.recipientBank.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Bank", receipt.recipientBank, lineWidth)))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("No.Rek", receipt.recipientAccount, lineWidth)))
    }
    if (receipt.recipientName.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Penerima", receipt.recipientName, lineWidth)))
    }

    list.add(FormattedLine(text = dividerSolid))

    // 4. Finansial
    list.add(FormattedLine(text = twoColumns("Jumlah", receipt.formattedAmount, lineWidth)))
    list.add(FormattedLine(text = twoColumns("Admin", receipt.formattedAdminFee, lineWidth)))
    list.add(FormattedLine(text = dividerSolid))
    list.add(FormattedLine(text = twoColumns("TOTAL", receipt.formattedTotal, lineWidth), isBold = true))

    // 5. Informasi Pelengkap
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Ref", receipt.refNumber, lineWidth)))
    }
    if (receipt.notes.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Ket", receipt.notes, lineWidth)))
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
  // 2. TEMA PROFESIONAL (Resmi Perbankan, Tegas, Validasi Formal)
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
        val wrappedStore = wrapText(settings.storeName.uppercase(), lineWidth)
        for (w in wrappedStore) {
          list.add(FormattedLine(text = w, isBold = true, isDoubleHeight = true, isCentered = true))
        }
      }
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddr = wrapText(settings.storeAddress, lineWidth)
        for (w in wrappedAddr) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        list.add(FormattedLine(text = "Telp/WA: ${settings.storePhone}", isCentered = true))
      }
      list.add(FormattedLine(text = dividerDouble))
    }

    // 2. Judul Struk Resmi
    list.add(FormattedLine(text = "STRUK BUKTI PENERIMA", isBold = true, isCentered = true))
    list.add(FormattedLine(text = "[ STATUS: TRANSAKSI BERHASIL ]", isCentered = true))
    list.add(FormattedLine(text = dividerSingle))

    // 3. Kolom Data Penerima Lengkap
    if (receipt.transactionDate.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Waktu Transaksi", receipt.transactionDate, lineWidth)))
    }
    if (receipt.recipientBank.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Bank Penyelenggara", receipt.recipientBank, lineWidth)))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("No. Rekening/HP", receipt.recipientAccount, lineWidth)))
    }
    if (receipt.recipientName.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Nama Penerima", receipt.recipientName, lineWidth)))
    }

    list.add(FormattedLine(text = dividerSingle))

    // 4. Rincian Finansial
    list.add(FormattedLine(text = twoColumns("Nominal Transfer", receipt.formattedAmount, lineWidth)))
    list.add(FormattedLine(text = twoColumns("Biaya Layanan Admin", receipt.formattedAdminFee, lineWidth)))
    list.add(FormattedLine(text = dividerSingle))
    list.add(FormattedLine(text = twoColumns("TOTAL PEMBAYARAN", receipt.formattedTotal, lineWidth), isBold = true))

    // 5. Informasi Pelengkap
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("No. Referensi Bank", receipt.refNumber, lineWidth)))
    }
    if (receipt.notes.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Catatan / Berita", receipt.notes, lineWidth)))
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
  // 3. TEMA MODERN (Retail & Fintech Kekinian, Blok Terstruktur, Aksen Visual)
  // =========================================================================
  private fun generateModernLines(
    receipt: Receipt,
    settings: PrintSettings,
    lineWidth: Int
  ): List<FormattedLine> {
    val list = mutableListOf<FormattedLine>()
    val dividerDots = makeRepeatedPattern("* ", lineWidth)
    val dividerLine = "-".repeat(lineWidth)

    // 1. Header Modern Berbingkai
    if (settings.autoAddHeader) {
      list.add(FormattedLine(text = dividerDots))
      if (settings.storeName.isNotBlank()) {
        val wrapped = wrapText("★ ${settings.storeName.uppercase()} ★", lineWidth)
        for (w in wrapped) {
          list.add(FormattedLine(text = w, isBold = true, isDoubleHeight = true, isCentered = true))
        }
      }
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddr = wrapText(settings.storeAddress, lineWidth)
        for (w in wrappedAddr) {
          list.add(FormattedLine(text = w, isCentered = true))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        list.add(FormattedLine(text = "WA/CS: ${settings.storePhone}", isCentered = true))
      }
      list.add(FormattedLine(text = dividerDots))
    }

    // 2. Headline Modern
    list.add(FormattedLine(text = ">>> BUKTI TRANSFER DIGITAL <<<", isBold = true, isCentered = true))
    list.add(FormattedLine(text = ""))

    // 3. Blok Rincian Penerima
    list.add(FormattedLine(text = "## INFORMASI PENERIMA ##", isBold = true))
    if (receipt.transactionDate.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Waktu", receipt.transactionDate, lineWidth)))
    }
    if (receipt.recipientBank.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Bank/Tujuan", receipt.recipientBank, lineWidth)))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("ID / No. Rek", receipt.recipientAccount, lineWidth)))
    }
    if (receipt.recipientName.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Atas Nama", receipt.recipientName, lineWidth)))
    }

    list.add(FormattedLine(text = dividerLine))

    // 4. Blok Rincian Tagihan
    list.add(FormattedLine(text = "## RINCIAN BIAYA ##", isBold = true))
    list.add(FormattedLine(text = twoColumns("Nominal", receipt.formattedAmount, lineWidth)))
    list.add(FormattedLine(text = twoColumns("Biaya Admin", receipt.formattedAdminFee, lineWidth)))
    list.add(FormattedLine(text = dividerLine))

    // Highlight Total (Tebal & Ukuran Ganda)
    val totalStr = ">>> ${receipt.formattedTotal} <<<"
    list.add(
      FormattedLine(
        text = twoColumns("TOTAL BAYAR", totalStr, lineWidth),
        isBold = true,
        isDoubleHeight = true
      )
    )

    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Ref ID", receipt.refNumber, lineWidth)))
    }
    if (receipt.notes.isNotBlank()) {
      list.add(FormattedLine(text = twoColumns("Catatan", receipt.notes, lineWidth)))
    }

    list.add(FormattedLine(text = dividerDots))

    // 5. Footer Modern
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
    list.add(FormattedLine(text = "• Powered by JADISTRUK •", isCentered = true))

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
    if (text.length >= width) return text
    val padding = (width - text.length) / 2
    return " ".repeat(padding) + text
  }

  fun twoColumns(left: String, right: String, width: Int): String {
    val spacesNeeded = width - left.length - right.length
    val spaces = if (spacesNeeded > 0) " ".repeat(spacesNeeded) else "  "
    return "$left$spaces$right"
  }
}
