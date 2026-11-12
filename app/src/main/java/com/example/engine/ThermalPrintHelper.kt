package com.example.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.ColumnConfig
import com.example.data.model.Template
import com.example.data.model.TextAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

object ThermalPrintHelper {

    // Standard SPP UUID for Bluetooth Serial Printers
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    data class BluetoothPrinter(
        val name: String,
        val address: String
    )

    /**
     * Lists bonded (paired) Bluetooth devices that can be thermal printers.
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothPrinters(context: Context): List<BluetoothPrinter> {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!bluetoothAdapter.isEnabled) return emptyList()

            bluetoothAdapter.bondedDevices.map { device ->
                BluetoothPrinter(
                    name = device.name ?: "Printer (${device.address})",
                    address = device.address
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Generates ESC/POS formatted bytes for the receipt.
     */
    fun generateEscPosBytes(
        template: Template,
        columns: List<ColumnConfig>,
        columnValues: Map<Long, String>
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        val width = if (template.paperWidthMm == 80) 48 else 32

        fun write(bytes: ByteArray) = stream.write(bytes)
        fun writeText(text: String) = stream.write(text.toByteArray(Charsets.US_ASCII))

        // ESC @: Initialize printer
        write(byteArrayOf(0x1B, 0x40))

        // Center align
        write(byteArrayOf(0x1B, 0x61, 0x01))

        // Double height + double width for Title
        write(byteArrayOf(0x1D, 0x21, 0x11))
        write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold ON
        writeText(EscPosCharacterNormalizer.normalizeText(template.headerTitle) + "\n")

        // Normal text size
        write(byteArrayOf(0x1D, 0x21, 0x00))
        write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold OFF
        if (template.headerSubtitle.isNotBlank()) {
            writeText(EscPosCharacterNormalizer.normalizeText(template.headerSubtitle) + "\n")
        }

        // Divider
        writeText(EscPosCharacterNormalizer.makeDivider(width, '=') + "\n")

        // Left align for body
        write(byteArrayOf(0x1B, 0x61, 0x00))

        val sortedColumns = columns.filter { it.isEnabled }.sortedBy { it.orderIndex }
        for (col in sortedColumns) {
            val rawValue = columnValues[col.id] ?: if (col.isManual) col.manualDefaultText else ""
            val cleanValue = EscPosCharacterNormalizer.normalizeText(rawValue)

            if (col.isManual) {
                // Rule: Manual columns are LEFT aligned
                writeText("${col.name}: $cleanValue\n")
            } else {
                // Rule: Auto columns are RIGHT aligned (Label left, value right)
                val line = EscPosCharacterNormalizer.formatTwoColumns(
                    left = col.name,
                    right = cleanValue,
                    maxWidth = width,
                    padChar = ' '
                )
                writeText("$line\n")
            }
        }

        // Divider
        write(byteArrayOf(0x1B, 0x61, 0x01)) // Center
        writeText(EscPosCharacterNormalizer.makeDivider(width, '-') + "\n")

        // Footer
        if (template.footerNote.isNotBlank()) {
            val footerLines = template.footerNote.split("\n")
            for (line in footerLines) {
                writeText(EscPosCharacterNormalizer.normalizeText(line) + "\n")
            }
        }

        // Feed paper & partial cut (GS V 66 3)
        writeText("\n\n\n")
        write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        return stream.toByteArray()
    }

    /**
     * Sends ESC/POS bytes directly to a paired Bluetooth Thermal Printer.
     */
    @SuppressLint("MissingPermission")
    suspend fun printToBluetooth(
        deviceAddress: String,
        data: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth tidak tersedia di perangkat ini"))
            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            socket.connect()

            val outputStream: OutputStream = socket.outputStream
            outputStream.write(data)
            outputStream.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Prints receipt via Android System PrintManager (PDF / Network / Any Printer).
     */
    fun printViaSystemPrint(
        context: Context,
        template: Template,
        columns: List<ColumnConfig>,
        columnValues: Map<Long, String>
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "CETAKIN_${template.templateName}_${System.currentTimeMillis()}"

        printManager.print(
            jobName,
            ReceiptPrintAdapter(context, template, columns, columnValues),
            PrintAttributes.Builder()
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
                .build()
        )
    }

    /**
     * Renders receipt into a Bitmap image that looks identical to a printed thermal receipt.
     */
    fun renderReceiptBitmap(
        template: Template,
        columns: List<ColumnConfig>,
        columnValues: Map<Long, String>
    ): Bitmap {
        val is80mm = template.paperWidthMm == 80
        val width = if (is80mm) 576 else 384
        val padding = 24f
        val contentWidth = width - (padding * 2)

        // Paint setup
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
            typeface = Typeface.MONOSPACE
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 18f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        // Calculate height dynamically
        val sortedColumns = columns.filter { it.isEnabled }.sortedBy { it.orderIndex }
        val footerLines = template.footerNote.split("\n").filter { it.isNotBlank() }
        val estimatedHeight = 160 + (sortedColumns.size * 34) + 60 + (footerLines.size * 26) + 80

        val bitmap = Bitmap.createBitmap(width, estimatedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 50f

        // Title
        canvas.drawText(EscPosCharacterNormalizer.normalizeText(template.headerTitle), width / 2f, y, titlePaint)
        y += 32f

        // Subtitle
        if (template.headerSubtitle.isNotBlank()) {
            canvas.drawText(EscPosCharacterNormalizer.normalizeText(template.headerSubtitle), width / 2f, y, subTitlePaint)
            y += 28f
        }

        // Divider
        y += 8f
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 24f

        // Body Columns
        for (col in sortedColumns) {
            val rawValue = columnValues[col.id] ?: if (col.isManual) col.manualDefaultText else ""
            val cleanValue = EscPosCharacterNormalizer.normalizeText(rawValue)

            if (col.isManual) {
                // Left aligned
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("${col.name}: $cleanValue", padding, y, textPaint)
            } else {
                // Label Left, Value Right
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(col.name, padding, y, textPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(cleanValue, width - padding, y, textPaint)
            }
            y += 34f
        }

        // Divider
        y += 8f
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 28f

        // Footer
        subTitlePaint.textAlign = Paint.Align.CENTER
        for (line in footerLines) {
            canvas.drawText(EscPosCharacterNormalizer.normalizeText(line), width / 2f, y, subTitlePaint)
            y += 26f
        }

        return bitmap
    }

    /**
     * Saves bitmap to cache and generates an Android Share Intent.
     */
    fun shareReceiptImage(context: Context, bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "shared_receipts")
            cachePath.mkdirs()
            val file = File(cachePath, "struk_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Struk Pembayaran dari CETAKIN")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Struk"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private class ReceiptPrintAdapter(
        private val context: Context,
        private val template: Template,
        private val columns: List<ColumnConfig>,
        private val columnValues: Map<Long, String>
    ) : PrintDocumentAdapter() {

        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }

            val info = PrintDocumentInfo.Builder("receipt.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()

            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val page = pdfDocument?.startPage(0)
            if (page != null) {
                val receiptBitmap = renderReceiptBitmap(template, columns, columnValues)
                val canvas = page.canvas
                val scale = (canvas.width.toFloat() / receiptBitmap.width.toFloat()).coerceAtMost(1f)
                canvas.scale(scale, scale)
                canvas.drawBitmap(receiptBitmap, 0f, 0f, null)
                pdfDocument?.finishPage(page)
            }

            try {
                pdfDocument?.writeTo(FileOutputStream(destination.fileDescriptor))
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                pdfDocument?.close()
                pdfDocument = null
            }
        }
    }
}
