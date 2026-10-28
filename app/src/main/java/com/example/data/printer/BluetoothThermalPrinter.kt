package com.example.data.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BluetoothPrinterDevice(
  val name: String,
  val address: String,
  val isBonded: Boolean = true
)

enum class BluetoothDiagnosticStatus {
  UNSUPPORTED,
  PERMISSION_DENIED,
  BLUETOOTH_OFF,
  NO_BONDED_PRINTERS,
  READY
}

sealed interface PrinterConnectionState {
  data object Unselected : PrinterConnectionState
  data class Connecting(val deviceName: String) : PrinterConnectionState
  data class Connected(val deviceName: String, val address: String) : PrinterConnectionState
  data class Printing(val deviceName: String) : PrinterConnectionState
  data class Success(val message: String) : PrinterConnectionState
  data class Error(val title: String, val message: String, val canRetry: Boolean = true) : PrinterConnectionState
}

sealed interface PrintResult {
  data object Success : PrintResult
  data class Error(val message: String) : PrintResult
}

class BluetoothThermalPrinter(private val context: Context) {

  companion object {
    private const val TAG = "ThermalPrinter"

    // Standard Bluetooth Serial Port Profile (SPP) UUID used by 99% thermal POS printers
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Connection timeout in milliseconds
    const val CONNECT_TIMEOUT_MS = 8000L

    // ESC/POS Commands
    val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
    val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
    val CMD_DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
    val CMD_DOUBLE_WIDTH_ON = byteArrayOf(0x1B, 0x21, 0x20)
    val CMD_DOUBLE_SIZE_ON = byteArrayOf(0x1B, 0x21, 0x30)
    val CMD_NORMAL_SIZE = byteArrayOf(0x1B, 0x21, 0x00)
    val CMD_CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x41, 0x00) // GS V A 0
  }

  private val bluetoothManager: BluetoothManager? by lazy {
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  }

  val bluetoothAdapter: BluetoothAdapter?
    get() = bluetoothManager?.adapter

  private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Unselected)
  val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

  // Active socket reference
  private var activeSocket: BluetoothSocket? = null
  private var activeAddress: String? = null
  private var activeDeviceName: String? = null

  fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

  fun hasBluetoothPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val connectGranted = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
      val scanGranted = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.BLUETOOTH_SCAN
      ) == PackageManager.PERMISSION_GRANTED
      connectGranted && scanGranted
    } else {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.BLUETOOTH
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

  /**
   * Diagnosis komprehensif status Bluetooth perangkat saat ini.
   */
  fun getDiagnosticStatus(): BluetoothDiagnosticStatus {
    if (!isBluetoothSupported()) return BluetoothDiagnosticStatus.UNSUPPORTED
    if (!hasBluetoothPermission()) return BluetoothDiagnosticStatus.PERMISSION_DENIED
    if (!isBluetoothEnabled()) return BluetoothDiagnosticStatus.BLUETOOTH_OFF
    val paired = getPairedPrinters()
    if (paired.isEmpty()) return BluetoothDiagnosticStatus.NO_BONDED_PRINTERS
    return BluetoothDiagnosticStatus.READY
  }

  @SuppressLint("MissingPermission")
  fun getPairedPrinters(): List<BluetoothPrinterDevice> {
    if (!hasBluetoothPermission() || bluetoothAdapter == null || !isBluetoothEnabled()) {
      return emptyList()
    }

    return try {
      val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
      bonded.map { device ->
        BluetoothPrinterDevice(
          name = device.name?.takeIf { it.isNotBlank() } ?: "Printer (${device.address})",
          address = device.address,
          isBonded = true
        )
      }.sortedBy { it.name }
    } catch (e: Exception) {
      Log.e(TAG, "Gagal membaca daftar printer paired: ${e.message}")
      emptyList()
    }
  }

  fun isConnected(): Boolean {
    return activeSocket?.isConnected == true
  }

  /**
   * Membuka koneksi socket RFCOMM SPP dengan proteksi timeout & reflection fallback.
   */
  @SuppressLint("MissingPermission")
  private suspend fun establishSocketConnection(deviceAddress: String, deviceName: String): BluetoothSocket = withContext(Dispatchers.IO) {
    val adapter = bluetoothAdapter ?: throw IOException("Bluetooth adapter tidak tersedia.")
    if (!adapter.isEnabled) throw IOException("Bluetooth HP belum aktif. Harap aktifkan Bluetooth terlebih dahulu.")

    if (adapter.isDiscovering) {
      adapter.cancelDiscovery()
    }

    val device = try {
      adapter.getRemoteDevice(deviceAddress)
    } catch (e: Exception) {
      throw IOException("Alamat MAC printer tidak valid: $deviceAddress")
    }

    // Tutup socket lama jika ada
    closeActiveSocket()

    // Coba koneksi dengan timeout 8 detik
    withTimeout(CONNECT_TIMEOUT_MS) {
      var socket: BluetoothSocket? = null
      try {
        // Metode 1: Standard SPP UUID
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        socket
      } catch (primaryEx: Exception) {
        Log.w(TAG, "Standard SPP connect gagal, mencoba fallback reflection: ${primaryEx.message}")
        try {
          socket?.close()
        } catch (_: Exception) {}

        // Metode 2: Fallback reflection port 1 (sangat efektif untuk printer thermal clone / generic)
        try {
          val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
          val fallbackSocket = method.invoke(device, 1) as BluetoothSocket
          fallbackSocket.connect()
          fallbackSocket
        } catch (fallbackEx: Exception) {
          Log.e(TAG, "Reflection connect juga gagal: ${fallbackEx.message}")
          throw IOException(
            "Tidak dapat terhubung ke printer '$deviceName' ($deviceAddress). " +
            "Pastikan printer menyala, jarak dekat, dan tidak sedang terhubung ke perangkat lain."
          )
        }
      }
    }
  }

  /**
   * Menghubungkan secara manual ke printer yang dipilih.
   */
  suspend fun connect(deviceAddress: String, deviceName: String): Result<Unit> = withContext(Dispatchers.IO) {
    _connectionState.value = PrinterConnectionState.Connecting(deviceName)
    try {
      val socket = establishSocketConnection(deviceAddress, deviceName)
      activeSocket = socket
      activeAddress = deviceAddress
      activeDeviceName = deviceName
      _connectionState.value = PrinterConnectionState.Connected(deviceName, deviceAddress)
      Result.success(Unit)
    } catch (e: Exception) {
      val errMsg = e.localizedMessage ?: "Gagal terhubung ke printer"
      _connectionState.value = PrinterConnectionState.Error("Koneksi Gagal", errMsg)
      Result.failure(e)
    }
  }

  fun disconnect() {
    closeActiveSocket()
    _connectionState.value = PrinterConnectionState.Unselected
  }

  private fun closeActiveSocket() {
    try {
      activeSocket?.close()
    } catch (_: Exception) {}
    activeSocket = null
    activeAddress = null
    activeDeviceName = null
  }

  /**
   * Cetak Struk Bukti Penerima:
   * Melakukan validasi izin, membuka soket jika belum terhubung, mengirim ESC/POS, lalu menutup soket dengan aman.
   */
  @SuppressLint("MissingPermission")
  suspend fun printReceipt(
    receipt: Receipt,
    settings: PrintSettings,
    targetPrinterAddress: String? = null
  ): PrintResult = withContext(Dispatchers.IO) {
    if (!isBluetoothSupported()) {
      val err = "Perangkat tidak memiliki dukungan Bluetooth."
      _connectionState.value = PrinterConnectionState.Error("Bluetooth Tidak Didukung", err, false)
      return@withContext PrintResult.Error(err)
    }

    if (!hasBluetoothPermission()) {
      val err = "Izin Bluetooth belum diberikan. Izinkan akses Bluetooth pada pengaturan aplikasi."
      _connectionState.value = PrinterConnectionState.Error("Izin Dibutuhkan", err, false)
      return@withContext PrintResult.Error(err)
    }

    if (!isBluetoothEnabled()) {
      val err = "Bluetooth HP dinonaktifkan. Silakan aktifkan Bluetooth terlebih dahulu."
      _connectionState.value = PrinterConnectionState.Error("Bluetooth Mati", err, true)
      return@withContext PrintResult.Error(err)
    }

    val printerAddress = targetPrinterAddress?.takeIf { it.isNotBlank() }
      ?: settings.selectedPrinterAddress.takeIf { it.isNotBlank() }

    if (printerAddress.isNullOrBlank()) {
      val err = "Belum ada printer Bluetooth yang dipilih. Pilih printer dari daftar pairing."
      _connectionState.value = PrinterConnectionState.Error("Printer Belum Dipilih", err, true)
      return@withContext PrintResult.Error(err)
    }

    val printerName = settings.selectedPrinterName.ifBlank { "Printer Bluetooth" }

    var socket: BluetoothSocket? = null
    var out: OutputStream? = null

    try {
      _connectionState.value = PrinterConnectionState.Connecting(printerName)

      // Gunakan socket aktif jika masih terhubung ke printer yang sama
      socket = if (activeSocket?.isConnected == true && activeAddress == printerAddress) {
        activeSocket!!
      } else {
        establishSocketConnection(printerAddress, printerName)
      }

      _connectionState.value = PrinterConnectionState.Printing(printerName)
      out = socket.outputStream

      // Buat data ESC/POS biner
      val payload = buildEscPosData(receipt, settings)

      // Kirim ke printer
      out.write(payload)
      out.flush()

      // Berikan jeda transmisi buffer printer
      Thread.sleep(400)

      _connectionState.value = PrinterConnectionState.Success("Struk berhasil dicetak ke $printerName")
      PrintResult.Success
    } catch (e: Exception) {
      val errMsg = e.localizedMessage ?: "Koneksi ke printer thermal terputus saat mencetak."
      Log.e(TAG, "Gagal mencetak struk: $errMsg", e)
      _connectionState.value = PrinterConnectionState.Error("Gagal Mencetak Struk", errMsg, true)
      PrintResult.Error(errMsg)
    } finally {
      // Lepas resource soket agar printer siap untuk transaksi berikutnya
      try {
        out?.close()
      } catch (_: Exception) {}
      try {
        socket?.close()
      } catch (_: Exception) {}
      if (activeSocket == socket) {
        activeSocket = null
        activeAddress = null
        activeDeviceName = null
      }
    }
  }

  /**
   * Tes Cetak Printer Bluetooth Thermal:
   * Mengirimkan halaman pengujian kecil untuk memverifikasi kesiapan komunikasi printer ESC/POS.
   */
  @SuppressLint("MissingPermission")
  suspend fun testPrint(
    settings: PrintSettings,
    targetPrinterAddress: String? = null
  ): PrintResult = withContext(Dispatchers.IO) {
    val printerAddress = targetPrinterAddress?.takeIf { it.isNotBlank() }
      ?: settings.selectedPrinterAddress.takeIf { it.isNotBlank() }

    if (printerAddress.isNullOrBlank()) {
      return@withContext PrintResult.Error("Belum ada printer yang dipilih untuk tes cetak.")
    }

    val printerName = settings.selectedPrinterName.ifBlank { "Printer Thermal" }
    var socket: BluetoothSocket? = null
    var out: OutputStream? = null

    try {
      _connectionState.value = PrinterConnectionState.Connecting(printerName)
      socket = establishSocketConnection(printerAddress, printerName)
      _connectionState.value = PrinterConnectionState.Printing(printerName)

      out = socket.outputStream
      val testBytes = buildTestPrintData(settings)

      out.write(testBytes)
      out.flush()
      Thread.sleep(300)

      _connectionState.value = PrinterConnectionState.Success("Tes cetak berhasil pada $printerName!")
      PrintResult.Success
    } catch (e: Exception) {
      val errMsg = e.localizedMessage ?: "Gagal melakukan tes cetak printer"
      _connectionState.value = PrinterConnectionState.Error("Tes Cetak Gagal", errMsg, true)
      PrintResult.Error(errMsg)
    } finally {
      try {
        out?.close()
      } catch (_: Exception) {}
      try {
        socket?.close()
      } catch (_: Exception) {}
    }
  }

  /**
   * Payload biner untuk Tes Cetak Printer.
   */
  private fun buildTestPrintData(settings: PrintSettings): ByteArray {
    val lineWidth = if (settings.paperWidthMm == 80) 48 else 32
    val divider = "=".repeat(lineWidth) + "\n"
    val singleDiv = "-".repeat(lineWidth) + "\n"

    val bytes = ArrayList<Byte>()
    fun add(cmd: ByteArray) { for (b in cmd) bytes.add(b) }
    fun addLine(text: String) {
      val encoded = (text + "\n").toByteArray(charset("ISO-8859-1"))
      for (b in encoded) bytes.add(b)
    }

    add(CMD_INIT)
    add(CMD_ALIGN_CENTER)
    add(CMD_BOLD_ON)
    add(CMD_DOUBLE_HEIGHT_ON)
    addLine("TES KONEKSI PRINTER")
    add(CMD_NORMAL_SIZE)
    addLine("JADISTRUK POS SYSTEM")
    add(CMD_BOLD_OFF)
    addLine(divider)

    add(CMD_ALIGN_LEFT)
    addLine("Kertas     : ${settings.paperWidthMm} mm ($lineWidth kolom)")
    val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id", "ID")).format(Date())
    addLine("Waktu Tes  : $now WIB")
    addLine("Komunikasi : Bluetooth Classic SPP")
    addLine(singleDiv)

    add(CMD_ALIGN_CENTER)
    addLine("STATUS: BERHASIL TERHUBUNG!")
    addLine("Printer siap mencetak struk.")
    addLine(divider)

    val feed = settings.printFeedLines.coerceIn(2, 5)
    add(byteArrayOf(0x1B, 0x64, feed.toByte()))
    if (settings.autoCutPaper) {
      add(CMD_CUT_PAPER)
    }

    return bytes.toByteArray()
  }

  /**
   * Membangun payload biner ESC/POS yang presisi dan kompatibel dengan semua printer thermal 58mm & 80mm.
   */
  private fun buildEscPosData(receipt: Receipt, settings: PrintSettings): ByteArray {
    val lineWidth = if (settings.paperWidthMm == 80) 48 else 32
    val bytes = ArrayList<Byte>()
    fun add(cmd: ByteArray) { for (b in cmd) bytes.add(b) }
    fun addLine(text: String = "") {
      val encoded = (text + "\n").toByteArray(charset("ISO-8859-1"))
      for (b in encoded) bytes.add(b)
    }

    // 1. Inisialisasi printer
    add(CMD_INIT)

    when (settings.receiptTheme) {
      com.example.data.model.ReceiptTheme.SEDERHANA -> {
        val dividerDash = ("- ".repeat(lineWidth / 2 + 1)).take(lineWidth) + "\n"
        val dividerSolid = "-".repeat(lineWidth) + "\n"

        // Header Ringkas
        if (settings.autoAddHeader) {
          add(CMD_ALIGN_CENTER)
          if (settings.storeName.isNotBlank()) {
            add(CMD_BOLD_ON)
            addLine(settings.storeName.uppercase())
            add(CMD_BOLD_OFF)
          }
          if (settings.storePhone.isNotBlank()) {
            addLine("Telp: ${settings.storePhone}")
          }
          addLine(dividerDash)
        }

        // Judul Struk
        add(CMD_ALIGN_CENTER)
        add(CMD_BOLD_ON)
        addLine("BUKTI TRANSFER")
        add(CMD_BOLD_OFF)
        addLine(dividerDash)

        // Data Penerima
        add(CMD_ALIGN_LEFT)
        if (receipt.transactionDate.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Tgl", receipt.transactionDate, lineWidth))
        }
        if (receipt.recipientBank.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Bank", receipt.recipientBank, lineWidth))
        }
        if (receipt.recipientAccount.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("No.Rek", receipt.recipientAccount, lineWidth))
        }
        if (receipt.recipientName.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Penerima", receipt.recipientName, lineWidth))
        }

        addLine(dividerSolid)

        // Finansial
        addLine(ReceiptFormatter.twoColumns("Jumlah", receipt.formattedAmount, lineWidth))
        addLine(ReceiptFormatter.twoColumns("Admin", receipt.formattedAdminFee, lineWidth))
        addLine(dividerSolid)

        add(CMD_BOLD_ON)
        addLine(ReceiptFormatter.twoColumns("TOTAL", receipt.formattedTotal, lineWidth))
        add(CMD_BOLD_OFF)

        if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Ref", receipt.refNumber, lineWidth))
        }
        if (receipt.notes.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Ket", receipt.notes, lineWidth))
        }

        addLine(dividerDash)

        // Footer
        add(CMD_ALIGN_CENTER)
        if (settings.footerMessage.isNotBlank()) {
          for (line in settings.footerMessage.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
              val wrapped = ReceiptFormatter.wrapText(trimmed, lineWidth)
              for (w in wrapped) addLine(w)
            }
          }
        } else {
          addLine("Terima Kasih")
        }
      }

      com.example.data.model.ReceiptTheme.PROFESIONAL -> {
        val dividerDouble = "=".repeat(lineWidth) + "\n"
        val dividerSingle = "-".repeat(lineWidth) + "\n"

        // Header Toko Formal
        if (settings.autoAddHeader) {
          add(CMD_ALIGN_CENTER)
          if (settings.storeName.isNotBlank()) {
            add(CMD_BOLD_ON)
            add(CMD_DOUBLE_HEIGHT_ON)
            addLine(settings.storeName.uppercase())
            add(CMD_NORMAL_SIZE)
            add(CMD_BOLD_OFF)
          }
          if (settings.storeAddress.isNotBlank()) {
            val wrappedAddr = ReceiptFormatter.wrapText(settings.storeAddress, lineWidth)
            for (line in wrappedAddr) addLine(line)
          }
          if (settings.storePhone.isNotBlank()) {
            addLine("Telp/WA: ${settings.storePhone}")
          }
          addLine(dividerDouble)
        }

        // Judul Struk Formal & Status
        add(CMD_ALIGN_CENTER)
        add(CMD_BOLD_ON)
        addLine("STRUK BUKTI PENERIMA")
        add(CMD_BOLD_OFF)
        addLine("[ STATUS: TRANSAKSI BERHASIL ]")
        addLine(dividerSingle)

        // Baris Data Transaksi
        add(CMD_ALIGN_LEFT)
        if (receipt.transactionDate.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Waktu Transaksi", receipt.transactionDate, lineWidth))
        }
        if (receipt.recipientBank.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Bank Penyelenggara", receipt.recipientBank, lineWidth))
        }
        if (receipt.recipientAccount.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("No. Rekening/HP", receipt.recipientAccount, lineWidth))
        }
        if (receipt.recipientName.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Nama Penerima", receipt.recipientName, lineWidth))
        }

        addLine(dividerSingle)

        // Nominal & Admin
        addLine(ReceiptFormatter.twoColumns("Nominal Transfer", receipt.formattedAmount, lineWidth))
        addLine(ReceiptFormatter.twoColumns("Biaya Layanan Admin", receipt.formattedAdminFee, lineWidth))
        addLine(dividerSingle)

        // TOTAL (Penebalan & Ukuran Jelas)
        add(CMD_BOLD_ON)
        addLine(ReceiptFormatter.twoColumns("TOTAL PEMBAYARAN", receipt.formattedTotal, lineWidth))
        add(CMD_BOLD_OFF)

        if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("No. Referensi Bank", receipt.refNumber, lineWidth))
        }
        if (receipt.notes.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Catatan / Berita", receipt.notes, lineWidth))
        }

        addLine(dividerDouble)

        // Footer Resmi
        add(CMD_ALIGN_CENTER)
        addLine("Simpan struk ini sebagai bukti pembayaran sah.")
        if (settings.footerMessage.isNotBlank()) {
          for (rawLine in settings.footerMessage.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isNotBlank()) {
              val wrapped = ReceiptFormatter.wrapText(trimmed, lineWidth)
              for (line in wrapped) addLine(line)
            }
          }
        }
      }

      com.example.data.model.ReceiptTheme.MODERN -> {
        val dividerDots = ("* ".repeat(lineWidth / 2 + 1)).take(lineWidth) + "\n"
        val dividerLine = "-".repeat(lineWidth) + "\n"

        // Header Modern Berbingkai
        if (settings.autoAddHeader) {
          add(CMD_ALIGN_CENTER)
          addLine(dividerDots)
          if (settings.storeName.isNotBlank()) {
            add(CMD_BOLD_ON)
            add(CMD_DOUBLE_HEIGHT_ON)
            addLine("★ ${settings.storeName.uppercase()} ★")
            add(CMD_NORMAL_SIZE)
            add(CMD_BOLD_OFF)
          }
          if (settings.storeAddress.isNotBlank()) {
            val wrappedAddr = ReceiptFormatter.wrapText(settings.storeAddress, lineWidth)
            for (line in wrappedAddr) addLine(line)
          }
          if (settings.storePhone.isNotBlank()) {
            addLine("WA/CS: ${settings.storePhone}")
          }
          addLine(dividerDots)
        }

        // Headline Digital Modern
        add(CMD_ALIGN_CENTER)
        add(CMD_BOLD_ON)
        addLine(">>> BUKTI TRANSFER DIGITAL <<<")
        add(CMD_BOLD_OFF)
        addLine()

        // Blok Penerima
        add(CMD_ALIGN_LEFT)
        add(CMD_BOLD_ON)
        addLine("## INFORMASI PENERIMA ##")
        add(CMD_BOLD_OFF)
        if (receipt.transactionDate.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Waktu", receipt.transactionDate, lineWidth))
        }
        if (receipt.recipientBank.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Bank/Tujuan", receipt.recipientBank, lineWidth))
        }
        if (receipt.recipientAccount.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("ID / No. Rek", receipt.recipientAccount, lineWidth))
        }
        if (receipt.recipientName.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Atas Nama", receipt.recipientName, lineWidth))
        }

        addLine(dividerLine)

        // Blok Finansial
        add(CMD_BOLD_ON)
        addLine("## RINCIAN BIAYA ##")
        add(CMD_BOLD_OFF)
        addLine(ReceiptFormatter.twoColumns("Nominal", receipt.formattedAmount, lineWidth))
        addLine(ReceiptFormatter.twoColumns("Biaya Admin", receipt.formattedAdminFee, lineWidth))
        addLine(dividerLine)

        // Highlight TOTAL
        add(CMD_BOLD_ON)
        add(CMD_DOUBLE_HEIGHT_ON)
        val totalFormatted = ">>> ${receipt.formattedTotal} <<<"
        addLine(ReceiptFormatter.twoColumns("TOTAL BAYAR", totalFormatted, lineWidth))
        add(CMD_NORMAL_SIZE)
        add(CMD_BOLD_OFF)

        if (settings.showRefNumber && receipt.refNumber.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Ref ID", receipt.refNumber, lineWidth))
        }
        if (receipt.notes.isNotBlank()) {
          addLine(ReceiptFormatter.twoColumns("Catatan", receipt.notes, lineWidth))
        }

        addLine(dividerDots)

        // Footer Modern
        add(CMD_ALIGN_CENTER)
        add(CMD_BOLD_ON)
        addLine("TERIMA KASIH ATAS KUNJUNGAN ANDA")
        add(CMD_BOLD_OFF)
        if (settings.footerMessage.isNotBlank()) {
          for (rawLine in settings.footerMessage.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isNotBlank()) {
              val wrapped = ReceiptFormatter.wrapText(trimmed, lineWidth)
              for (line in wrapped) addLine(line)
            }
          }
        }
        addLine("• Powered by JADISTRUK •")
      }
    }

    // Feed Kertas
    val feedLines = settings.printFeedLines.coerceIn(1, 5)
    add(byteArrayOf(0x1B, 0x64, feedLines.toByte()))

    // Auto Cut
    if (settings.autoCutPaper) {
      add(CMD_CUT_PAPER)
    }

    return bytes.toByteArray()
  }
}
