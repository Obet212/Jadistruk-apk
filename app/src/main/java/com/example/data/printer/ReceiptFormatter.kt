package com.example.data.printer

import com.example.data.model.PrintSettings
import com.example.data.model.Receipt

object ReceiptFormatter {

  fun formatText(receipt: Receipt, settings: PrintSettings): String {
    val lineWidth = if (settings.paperWidthMm == 80) 48 else 32
    val dividerDouble = "=".repeat(lineWidth)
    val dividerSingle = "-".repeat(lineWidth)

    val sb = StringBuilder()

    // 1. Header (Store info with Word-Wrap & Centering to prevent text clipping)
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
        sb.appendLine(centerText("Telp: ${settings.storePhone}", lineWidth))
      }
      sb.appendLine(dividerDouble)
    }

    // 2. Receipt Title
    sb.appendLine(centerText("STRUK BUKTI PENERIMA", lineWidth))
    sb.appendLine(dividerSingle)

    // 3. Exact 7-Column Order (Strictly Recipient Data, Zero Sender Data)
    // 1. WAKTU
    if (receipt.transactionDate.isNotBlank()) {
      sb.appendLine(twoColumns("Waktu", receipt.transactionDate, lineWidth))
    }

    // 2. BANK
    if (receipt.recipientBank.isNotBlank()) {
      sb.appendLine(twoColumns("Bank Tujuan", receipt.recipientBank, lineWidth))
    }

    // 3. REKENING
    if (receipt.recipientAccount.isNotBlank()) {
      sb.appendLine(twoColumns("No. Rek/HP", receipt.recipientAccount, lineWidth))
    }

    // 4. PENERIMA
    if (receipt.recipientName.isNotBlank()) {
      sb.appendLine(twoColumns("Penerima", receipt.recipientName, lineWidth))
    }

    sb.appendLine(dividerSingle)

    // 5. NOMINAL
    sb.appendLine(twoColumns("Nominal", receipt.formattedAmount, lineWidth))

    // 6. ADMIN
    sb.appendLine(twoColumns("Biaya Admin", receipt.formattedAdminFee, lineWidth))

    sb.appendLine(dividerSingle)

    // 7. TOTAL (otomatis terhitung Nominal + Admin)
    sb.appendLine(twoColumns("TOTAL", receipt.formattedTotal, lineWidth))

    // Supporting info if present (Ref & Notes)
    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
      sb.appendLine(twoColumns("No. Ref", receipt.refNumber, lineWidth))
    }
    if (receipt.notes.isNotBlank()) {
      sb.appendLine(twoColumns("Catatan", receipt.notes, lineWidth))
    }

    sb.appendLine(dividerDouble)

    // 4. Footer (Word-wrapped and centered)
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

    // 5. Minimal trailing feed (tight spacing for paper cutter without wasting paper)
    sb.appendLine()

    return sb.toString()
  }

  /**
   * Word-wraps long text across multiple lines so words are never cut in the middle.
   * Eliminates right/left clipping on thermal printers.
   */
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
          // If a single word is longer than lineWidth, chunk it
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
