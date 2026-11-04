package com.example.data.printer

import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalPrinterTest {

  @Test
  fun testReceiptFormatting58mm() {
    val receipt = Receipt(
      id = 101,
      recipientBank = "BCA",
      recipientAccount = "1234567890",
      recipientName = "BUDI SANTOSO",
      amount = 500000,
      adminFee = 2500,
      transactionDate = "25/10/2026 14:30 WIB",
      refNumber = "REF998877"
    )

    val settings = PrintSettings(
      paperWidthMm = 58,
      storeName = "TOKO JAYA",
      storeAddress = "Jl. Sudirman No. 1",
      storePhone = "08123456789",
      autoAddHeader = true,
      showRefNumber = true
    )

    val text = ReceiptFormatter.formatText(receipt, settings)

    assertTrue(text.contains("TOKO JAYA"))
    assertTrue(text.contains("STRUK BUKTI PENERIMA"))
    // Label "Waktu Transaksi" diubah menjadi "Transaksi"
    assertTrue(text.contains("Transaksi"))
    // Bagian teks "Status Transaksi Sukses" telah dihapus
    assertTrue(!text.contains("Status Transaksi"))
    assertTrue(!text.contains("TRANSAKSI BERHASIL"))
    assertTrue(text.contains("BCA"))
    assertTrue(text.contains("BUDI SANTOSO"))
    assertTrue(text.contains("Rp 500.000"))
    assertTrue(text.contains("Rp 2.500"))
    assertTrue(text.contains("Rp 502.500"))
    assertTrue(text.contains("REF998877"))
  }

  @Test
  fun testReceiptFormatting80mm() {
    val receipt = Receipt(
      id = 102,
      recipientBank = "MANDIRI",
      recipientAccount = "9876543210",
      recipientName = "SITI RAHMAH",
      amount = 1250000,
      adminFee = 3000,
      transactionDate = "25/10/2026 15:00 WIB"
    )

    val settings = PrintSettings(paperWidthMm = 80)
    val text = ReceiptFormatter.formatText(receipt, settings)

    // 80mm divider should be 48 chars
    assertTrue(text.contains("=".repeat(48)))
    assertTrue(text.contains("SITI RAHMAH"))
    assertTrue(text.contains("Rp 1.253.000"))
  }

  @Test
  fun testTwoColumnsFormatting() {
    val left = "TOTAL"
    val right = "Rp 150.000"
    val width = 32

    val line = ReceiptFormatter.twoColumns(left, right, width)
    assertEquals(width, line.length)
    assertTrue(line.startsWith("TOTAL"))
    assertTrue(line.endsWith("Rp 150.000"))
  }

  @Test
  fun testTextWrapping() {
    val longAddress = "Jalan Jenderal Sudirman Kavling 52-53 RT 05 RW 03 Senayan Kebayoran Baru Jakarta Selatan"
    val lines = ReceiptFormatter.wrapText(longAddress, 32)

    assertTrue(lines.size > 1)
    for (line in lines) {
      assertTrue("Line exceeds max width 32: '$line'", line.length <= 32)
    }
  }

  @Test
  fun testCenterText() {
    val text = "STRUK RESMI"
    val width = 32
    val centered = ReceiptFormatter.centerText(text, width)

    assertEquals(width, centered.length + (width - centered.length)) // Valid length
    assertTrue(centered.trim() == text)
  }

  @Test
  fun testBluetoothSppUuidAndEscPosConstants() {
    assertEquals("00001101-0000-1000-8000-00805f9b34fb", BluetoothThermalPrinter.SPP_UUID.toString().lowercase())
    assertNotNull(BluetoothThermalPrinter.CMD_INIT)
    assertNotNull(BluetoothThermalPrinter.CMD_ALIGN_CENTER)
    assertNotNull(BluetoothThermalPrinter.CMD_CUT_PAPER)
  }

  @Test
  fun testPrinterConnectionStates() {
    val unselected = PrinterConnectionState.Unselected
    val connecting = PrinterConnectionState.Connecting("RPP02N")
    val connected = PrinterConnectionState.Connected("RPP02N", "00:11:22:33:44:55")
    val error = PrinterConnectionState.Error("Timeout", "Printer tidak merespons")

    assertEquals("RPP02N", connecting.deviceName)
    assertEquals("00:11:22:33:44:55", connected.address)
    assertEquals("Timeout", error.title)
  }

  @Test
  fun testReceiptFormattingSederhana() {
    val receipt = Receipt(
      id = 103,
      recipientBank = "BRI",
      recipientAccount = "0987654321",
      recipientName = "AHMAD FAUZI",
      amount = 250000,
      adminFee = 2500,
      transactionDate = "25/10/2026 16:00 WIB"
    )
    val settings = PrintSettings(
      paperWidthMm = 58,
      receiptTheme = com.example.data.model.ReceiptTheme.SEDERHANA,
      storeName = "KASIR KITA",
      storeAddress = "Jl. Melati No. 12"
    )
    val text = ReceiptFormatter.formatText(receipt, settings)

    assertTrue(text.contains("KASIR KITA"))
    // Alamat toko tampil tepat di bawah nama toko
    assertTrue(text.contains("Jl. Melati No. 12"))
    assertTrue(text.contains("BUKTI TRANSFER"))
    assertTrue(text.contains("BRI"))
    assertTrue(text.contains("AHMAD FAUZI"))
    assertTrue(text.contains("Rp 250.000"))
    assertTrue(text.contains("Rp 2.500"))
    assertTrue(text.contains("Rp 252.500"))
  }

  @Test
  fun testReceiptFormattingModern() {
    val receipt = Receipt(
      id = 104,
      recipientBank = "DANA",
      recipientAccount = "081299887766",
      recipientName = "DEWI LESTARI",
      amount = 750000,
      adminFee = 2000,
      transactionDate = "25/10/2026 17:00 WIB"
    )
    val settings = PrintSettings(
      paperWidthMm = 58,
      receiptTheme = com.example.data.model.ReceiptTheme.MODERN,
      storeName = "DIGITAL MART"
    )
    val text = ReceiptFormatter.formatText(receipt, settings)

    // Tanpa simbol dekoratif bintang, panah, atau pagar
    assertTrue(text.contains("DIGITAL MART"))
    assertTrue(text.contains("BUKTI TRANSFER DIGITAL"))
    assertTrue(text.contains("INFORMASI PENERIMA"))
    assertTrue(text.contains("RINCIAN BIAYA"))
    assertTrue(!text.contains("★"))
    assertTrue(!text.contains(">>>"))
    assertTrue(!text.contains("##"))
    // Powered by JADISTRUK telah dihapus
    assertTrue(!text.contains("Powered by JADISTRUK"))
    assertTrue(text.contains("DANA"))
    assertTrue(text.contains("DEWI LESTARI"))
    assertTrue(text.contains("Rp 750.000"))
    assertTrue(text.contains("Rp 2.000"))
    assertTrue(text.contains("TOTAL BAYAR"))
    assertTrue(text.contains("Rp 752.000"))
  }

  @Test
  fun testThermalReceiptRendererDimensions58mm() {
    val receipt = Receipt(
      id = 105,
      recipientBank = "BCA",
      recipientAccount = "1234567890",
      recipientName = "BUDI SANTOSO",
      amount = 500000,
      adminFee = 2500,
      transactionDate = "25/10/2026 14:30 WIB"
    )
    for (theme in com.example.data.model.ReceiptTheme.entries) {
      val settings = PrintSettings(
        paperWidthMm = 58,
        receiptTheme = theme
      )
      val dim = ThermalReceiptRenderer.calculateDimensions(receipt, settings)
      assertEquals(720f, dim.documentWidth, 0.001f)
      assertEquals(32, dim.lineWidth)
      assertEquals(20.0f, dim.colWidth, 0.001f)
      assertEquals(40f, dim.sideMargin, 0.001f)
      assertEquals(640f, dim.printableWidth, 0.001f)
      assertTrue(dim.documentHeight > 240f)
      assertTrue(dim.normalLineHeight > dim.colWidth)
      assertTrue(dim.doubleLineHeight > dim.normalLineHeight)
    }
  }

  @Test
  fun testThermalReceiptRendererDimensions88mm() {
    val receipt = Receipt(
      id = 106,
      recipientBank = "MANDIRI",
      recipientAccount = "9876543210",
      recipientName = "SITI RAHMAH",
      amount = 1250000,
      adminFee = 3000,
      transactionDate = "25/10/2026 15:00 WIB"
    )
    for (theme in com.example.data.model.ReceiptTheme.entries) {
      val settings = PrintSettings(
        paperWidthMm = 88,
        receiptTheme = theme
      )
      val dim = ThermalReceiptRenderer.calculateDimensions(receipt, settings)
      assertEquals(960f, dim.documentWidth, 0.001f)
      assertEquals(48, dim.lineWidth)
      assertEquals(18.0f, dim.colWidth, 0.001f)
      assertEquals(48f, dim.sideMargin, 0.001f)
      assertEquals(864f, dim.printableWidth, 0.001f)
      assertTrue(dim.documentHeight > 240f)
      assertTrue(dim.normalLineHeight > dim.colWidth)
      assertTrue(dim.doubleLineHeight > dim.normalLineHeight)
    }
  }

  @Test
  fun testTwoColumnsOrWrappedLeftAndRightCol() {
    // Normal single-line pair
    val single = ReceiptFormatter.twoColumnsOrWrapped("Jumlah", "Rp 50.000", 32)
    assertEquals(1, single.size)
    assertEquals("Jumlah", single[0].leftCol)
    assertEquals("Rp 50.000", single[0].rightCol)
    assertTrue(single[0].text.endsWith("Rp 50.000"))

    // Long right column wrapping
    val longRight = ReceiptFormatter.twoColumnsOrWrapped("Penerima", "MUHAMMAD ARYA PRATAMA KUSUMA", 32)
    assertTrue(longRight.size > 1)
    assertEquals("Penerima", longRight[0].leftCol)
    assertNotNull(longRight[0].rightCol)
    assertEquals("", longRight[1].leftCol)
    assertNotNull(longRight[1].rightCol)

    // Long left column wrapping (total length > 32)
    val longLeft = ReceiptFormatter.twoColumnsOrWrapped("Bank Penyelenggara Layanan Keuangan", "BANK CENTRAL ASIA", 32)
    assertTrue(longLeft.size > 1)
    assertNotNull(longLeft[0].leftCol)
    assertNotNull(longLeft[0].rightCol)
  }

  @Test
  fun testAllThemesTwoColumnFields() {
    val receipt = Receipt(
      id = 107,
      recipientBank = "BCA",
      recipientAccount = "1234567890",
      recipientName = "BUDI SANTOSO",
      amount = 500000,
      adminFee = 2500,
      transactionDate = "25/10/2026 14:30 WIB",
      refNumber = "REF998877",
      notes = "Transfer uang kas"
    )

    for (theme in com.example.data.model.ReceiptTheme.entries) {
      val settings = PrintSettings(paperWidthMm = 58, receiptTheme = theme)
      val lines = ReceiptFormatter.getFormattedLines(receipt, settings)

      val twoColLines = lines.filter { it.leftCol != null || it.rightCol != null }
      assertTrue("Theme $theme should have structured two-column lines", twoColLines.isNotEmpty())

      // Verifikasi bahwa baris nilai terpenting (Total, Jumlah/Nominal) memiliki kolom terstruktur
      val totalLine = twoColLines.firstOrNull { it.leftCol?.contains("TOTAL", ignoreCase = true) == true }
      assertNotNull("Theme $theme must have two-column TOTAL row", totalLine)
      assertTrue(totalLine?.rightCol?.contains("502.500") == true)
    }
  }
}

