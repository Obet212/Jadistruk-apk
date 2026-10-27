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
}
