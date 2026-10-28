package com.example.data.printer

import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import com.example.data.model.ReceiptTheme

object ReceiptFormatter {

  fun formatText(receipt: Receipt, settings: PrintSettings): String {
    val lineWidth = if (settings.paperWidthMm == 80) 48 else 32

    return when (settings.receiptTheme) {
      ReceiptTheme.SEDERHANA -> formatSederhana(receipt, settings, lineWidth)
      ReceiptTheme.PROFESIONAL -> formatProfesional(receipt, settings, lineWidth)
      ReceiptTheme.MODERN -> formatModern(receipt, settings, lineWidth)
    }
  }

  // =========================================================================
  // 1. TEMA SEDERHANA (Minimalis, Ringkas, Hemat Kertas)
  // =========================================================================
  private fun formatSederhana(receipt: Receipt, settings: PrintSettings, lineWidth: Int): String {
    val dividerDash = makeRepeatedPattern("- ", lineWidth)
    val dividerSolid = "-".repeat(lineWidth)
    val sb = StringBuilder()

    // 1. Header Ringkas
    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) {
        val wrapped = wrapText(settings.storeName.uppercase(), lineWidth)
        for (line in wrapped) {
          sb.appendLine(centerText(line, lineWidth))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        sb.appendLine(centerText("Telp: ${settings.storePhone}", lineWidth))
      }
      sb.appendLine(dividerDash)
    }

    // 2. Judul Struk
    sb.appendLine(centerText("BUKTI TRANSFER", lineWidth))
    sb.appendLine(dividerDash)

    // 3. Data Penerima
    if (receipt.transactionDate.isNotBlank()) {
      sb.appendLine(twoColumns("Tgl", receipt.transactionDate, lineWidth))
    }
    if (receipt.recipientBank.isNotBlank()) {
      sb.appendLine(twoColumns("Bank", receipt.recipientBank, lineWidth))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      sb.appendLine(twoColumns("No.Rek", receipt.recipientAccount, lineWidth))
    }
    if (receipt.recipientName.isNotBlank()) {
      sb.appendLine(twoColumns("Penerima", receipt.recipientName, lineWidth))
    }

    sb.appendLine(dividerSolid)

    // 4. Finansial
    sb.appendLine(twoColumns("Jumlah", receipt.formattedAmount, lineWidth))
    sb.appendLine(twoColumns("Admin", receipt.formattedAdminFee, lineWidth))
    sb.appendLine(dividerSolid)
    sb.appendLine(twoColumns("TOTAL", receipt.formattedTotal, lineWidth))

    // 5. Tambahan jika ada
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      sb.appendLine(twoColumns("Ref", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      sb.appendLine(twoColumns("Ket", receipt.notes, lineWidth))
    }

    sb.appendLine(dividerDash)

    // 6. Footer Sederhana
    if (settings.footerMessage.isNotBlank()) {
      for (line in settings.footerMessage.lines()) {
        val trimmed = line.trim()
        if (trimmed.isNotBlank()) {
          val wrapped = wrapText(trimmed, lineWidth)
          for (w in wrapped) {
            sb.appendLine(centerText(w, lineWidth))
          }
        }
      }
    } else {
      sb.appendLine(centerText("Terima Kasih", lineWidth))
    }

    sb.appendLine()
    return sb.toString()
  }

  // =========================================================================
  // 2. TEMA PROFESIONAL (Resmi Perbankan, Tegas, Validasi Formal)
  // =========================================================================
  private fun formatProfesional(receipt: Receipt, settings: PrintSettings, lineWidth: Int): String {
    val dividerDouble = "=".repeat(lineWidth)
    val dividerSingle = "-".repeat(lineWidth)
    val sb = StringBuilder()

    // 1. Header Formal Toko
    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) {
        val wrappedStoreLines = wrapText(settings.storeName.uppercase(), lineWidth)
        for (line in wrappedStoreLines) {
          sb.appendLine(centerText(line, lineWidth))
        }
      }
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddressLines = wrapText(settings.storeAddress, lineWidth)
        for (line in wrappedAddressLines) {
          sb.appendLine(centerText(line, lineWidth))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        sb.appendLine(centerText("Telp/WA: ${settings.storePhone}", lineWidth))
      }
      sb.appendLine(dividerDouble)
    }

    // 2. Judul Struk Resmi
    sb.appendLine(centerText("STRUK BUKTI PENERIMA", lineWidth))
    sb.appendLine(centerText("[ STATUS: TRANSAKSI BERHASIL ]", lineWidth))
    sb.appendLine(dividerSingle)

    // 3. Kolom Data Penerima Lengkap
    if (receipt.transactionDate.isNotBlank()) {
      sb.appendLine(twoColumns("Waktu Transaksi", receipt.transactionDate, lineWidth))
    }
    if (receipt.recipientBank.isNotBlank()) {
      sb.appendLine(twoColumns("Bank Penyelenggara", receipt.recipientBank, lineWidth))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      sb.appendLine(twoColumns("No. Rekening/HP", receipt.recipientAccount, lineWidth))
    }
    if (receipt.recipientName.isNotBlank()) {
      sb.appendLine(twoColumns("Nama Penerima", receipt.recipientName, lineWidth))
    }

    sb.appendLine(dividerSingle)

    // 4. Rincian Finansial
    sb.appendLine(twoColumns("Nominal Transfer", receipt.formattedAmount, lineWidth))
    sb.appendLine(twoColumns("Biaya Layanan Admin", receipt.formattedAdminFee, lineWidth))
    sb.appendLine(dividerSingle)
    sb.appendLine(twoColumns("TOTAL PEMBAYARAN", receipt.formattedTotal, lineWidth))

    // 5. Informasi Pelengkap
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      sb.appendLine(twoColumns("No. Referensi Bank", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      sb.appendLine(twoColumns("Catatan / Berita", receipt.notes, lineWidth))
    }

    sb.appendLine(dividerDouble)

    // 6. Footer Resmi
    sb.appendLine(centerText("Simpan struk ini sebagai bukti pembayaran sah.", lineWidth))
    if (settings.footerMessage.isNotBlank()) {
      for (rawLine in settings.footerMessage.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isNotBlank()) {
          val wrapped = wrapText(trimmed, lineWidth)
          for (line in wrapped) {
            sb.appendLine(centerText(line, lineWidth))
          }
        }
      }
    }

    sb.appendLine()
    return sb.toString()
  }

  // =========================================================================
  // 3. TEMA MODERN (Retail & Fintech Kekinian, Blok Terstruktur, Aksen Visual)
  // =========================================================================
  private fun formatModern(receipt: Receipt, settings: PrintSettings, lineWidth: Int): String {
    val dividerDots = makeRepeatedPattern("* ", lineWidth)
    val dividerLine = "-".repeat(lineWidth)
    val sb = StringBuilder()

    // 1. Header Modern Berbingkai
    if (settings.autoAddHeader) {
      sb.appendLine(dividerDots)
      if (settings.storeName.isNotBlank()) {
        val wrapped = wrapText("★ ${settings.storeName.uppercase()} ★", lineWidth)
        for (line in wrapped) {
          sb.appendLine(centerText(line, lineWidth))
        }
      }
      if (settings.storeAddress.isNotBlank()) {
        val wrappedAddr = wrapText(settings.storeAddress, lineWidth)
        for (line in wrappedAddr) {
          sb.appendLine(centerText(line, lineWidth))
        }
      }
      if (settings.storePhone.isNotBlank()) {
        sb.appendLine(centerText("WA/CS: ${settings.storePhone}", lineWidth))
      }
      sb.appendLine(dividerDots)
    }

    // 2. Headline Modern
    sb.appendLine(centerText(">>> BUKTI TRANSFER DIGITAL <<<", lineWidth))
    sb.appendLine()

    // 3. Blok Rincian Penerima
    sb.appendLine("## INFORMASI PENERIMA ##")
    if (receipt.transactionDate.isNotBlank()) {
      sb.appendLine(twoColumns("Waktu", receipt.transactionDate, lineWidth))
    }
    if (receipt.recipientBank.isNotBlank()) {
      sb.appendLine(twoColumns("Bank/Tujuan", receipt.recipientBank, lineWidth))
    }
    if (receipt.recipientAccount.isNotBlank()) {
      sb.appendLine(twoColumns("ID / No. Rek", receipt.recipientAccount, lineWidth))
    }
    if (receipt.recipientName.isNotBlank()) {
      sb.appendLine(twoColumns("Atas Nama", receipt.recipientName, lineWidth))
    }

    sb.appendLine(dividerLine)

    // 4. Blok Rincian Tagihan
    sb.appendLine("## RINCIAN BIAYA ##")
    sb.appendLine(twoColumns("Nominal", receipt.formattedAmount, lineWidth))
    sb.appendLine(twoColumns("Biaya Admin", receipt.formattedAdminFee, lineWidth))
    sb.appendLine(dividerLine)

    // Highlight Total
    val totalStr = ">>> ${receipt.formattedTotal} <<<"
    sb.appendLine(twoColumns("TOTAL BAYAR", totalStr, lineWidth))

    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      sb.appendLine(twoColumns("Ref ID", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      sb.appendLine(twoColumns("Catatan", receipt.notes, lineWidth))
    }

    sb.appendLine(dividerDots)

    // 5. Footer Modern
    sb.appendLine(centerText("TERIMA KASIH ATAS KUNJUNGAN ANDA", lineWidth))
    if (settings.footerMessage.isNotBlank()) {
      for (rawLine in settings.footerMessage.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isNotBlank()) {
          val wrapped = wrapText(trimmed, lineWidth)
          for (line in wrapped) {
            sb.appendLine(centerText(line, lineWidth))
          }
        }
      }
    }
    sb.appendLine(centerText("• Powered by JADISTRUK •", lineWidth))

    sb.appendLine()
    return sb.toString()
  }

  // =========================================================================
  // HELPER FUNCTIONS
  // =========================================================================
  private fun makeRepeatedPattern(pattern: String, width: Int): String {
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
