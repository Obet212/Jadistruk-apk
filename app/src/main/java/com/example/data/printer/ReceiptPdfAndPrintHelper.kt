package com.example.data.printer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import java.io.File
import java.io.FileOutputStream

object ReceiptPdfAndPrintHelper {

  /**
   * Alternatif Cetak 1: Menggunakan Android Print Framework (PrintManager).
   * Mendukung semua driver printer Android (WiFi, Bluetooth via Print Service, Mopria, PDF).
   */
  fun printViaSystemDialog(context: Context, receipt: Receipt, settings: PrintSettings) {
    try {
      val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      if (printManager == null) {
        Toast.makeText(context, "Layanan cetak sistem Android tidak tersedia pada perangkat ini.", Toast.LENGTH_SHORT).show()
        return
      }

      val jobName = "Struk_OBETSTRUK_${receipt.id}_${receipt.recipientBank.filter { it.isLetterOrDigit() }}"
      val adapter = object : PrintDocumentAdapter() {

        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes?,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback?,
          extras: Bundle?
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
          }

          val info = PrintDocumentInfo.Builder("$jobName.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

          callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>?,
          destination: ParcelFileDescriptor?,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback?
        ) {
          try {
            val dimensions = ThermalReceiptRenderer.calculateDimensions(receipt, settings)
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(
              dimensions.documentWidth.toInt(),
              dimensions.documentHeight.toInt(),
              1
            ).create()
            val page = doc.startPage(pageInfo)

            ThermalReceiptRenderer.drawReceiptDocument(
              canvas = page.canvas,
              receipt = receipt,
              settings = settings,
              dimensions = dimensions,
              includeCutterEffect = false,
              backgroundColor = Color.WHITE
            )
            doc.finishPage(page)

            if (destination != null) {
              FileOutputStream(destination.fileDescriptor).use { output ->
                doc.writeTo(output)
              }
            }
            doc.close()
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage ?: "Gagal memproses dokumen cetak")
          }
        }
      }

      val printAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
        .build()

      printManager.print(jobName, adapter, printAttributes)
    } catch (e: Exception) {
      Toast.makeText(context, "Gagal membuka dialog cetak sistem: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Alternatif Cetak 2: Ekspor dan Bagikan Struk dalam bentuk file PDF resmi.
   */
  fun exportAndSharePdf(context: Context, receipt: Receipt, settings: PrintSettings) {
    try {
      val dimensions = ThermalReceiptRenderer.calculateDimensions(receipt, settings)
      val doc = PdfDocument()
      val pageInfo = PdfDocument.PageInfo.Builder(
        dimensions.documentWidth.toInt(),
        dimensions.documentHeight.toInt(),
        1
      ).create()
      val page = doc.startPage(pageInfo)

      ThermalReceiptRenderer.drawReceiptDocument(
        canvas = page.canvas,
        receipt = receipt,
        settings = settings,
        dimensions = dimensions,
        includeCutterEffect = false,
        backgroundColor = Color.WHITE
      )
      doc.finishPage(page)

      val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
      val pdfFile = File(receiptsDir, "Struk_${receipt.id}_${System.currentTimeMillis()}.pdf")

      FileOutputStream(pdfFile).use { out ->
        doc.writeTo(out)
      }
      doc.close()

      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, "Struk Bukti Transfer - ${receipt.recipientName}")
        putExtra(Intent.EXTRA_TEXT, "Berikut terlampir struk bukti penerima transfer ${receipt.recipientBank} senilai ${receipt.formattedAmount}.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(Intent.createChooser(shareIntent, "Kirim / Bagikan File PDF Struk"))
    } catch (e: Exception) {
      Toast.makeText(context, "Gagal membuat dokumen PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  /**
   * Alternatif Cetak & Berbagi: Ekspor dan Bagikan Struk dalam bentuk gambar JPG tajam berkualitas tinggi.
   * Menggunakan ThermalReceiptRenderer global: ukuran proporsional, font monospaced terkunci grid,
   * jarak baris stabil, dan 100% identik dengan tampilan pratinjau di layar.
   */
  fun exportAndShareJpg(context: Context, receipt: Receipt, settings: PrintSettings) {
    try {
      // Menggunakan bitmap kanonikal dari ThermalReceiptRenderer global
      // 100% identik dengan tampilan pratinjau di layar
      val bitmap = ThermalReceiptRenderer.renderReceiptBitmap(
        receipt = receipt,
        settings = settings,
        includeCutterEffect = true
      )

      val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
      val jpgFile = File(receiptsDir, "Struk_${receipt.id}_${System.currentTimeMillis()}.jpg")

      FileOutputStream(jpgFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
      }

      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        jpgFile
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        putExtra(Intent.EXTRA_SUBJECT, "Struk Bukti Transfer - ${receipt.recipientName}")
        putExtra(Intent.EXTRA_TEXT, "Struk bukti transfer ${receipt.recipientBank} senilai ${receipt.formattedAmount}.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      context.startActivity(Intent.createChooser(shareIntent, "Kirim / Bagikan Gambar Struk"))
    } catch (e: Exception) {
      Toast.makeText(context, "Gagal membagikan gambar struk: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }
}
