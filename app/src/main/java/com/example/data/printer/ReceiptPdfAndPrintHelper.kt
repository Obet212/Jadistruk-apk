package com.example.data.printer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import java.io.FileInputStream
import java.io.IOException

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

      val jobName = "Struk_JADISTRUK_${receipt.id}_${receipt.recipientBank.filter { it.isLetterOrDigit() }}"
      val adapter = object : PrintDocumentAdapter() {
        private var pdfDocument: PdfDocument? = null

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
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(384, 600, 1).create()
            val page = doc.startPage(pageInfo)

            drawReceiptOnCanvas(page.canvas, receipt, settings, 384f)
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
      val doc = PdfDocument()
      // Lebar 384pt setara thermal 58mm, tinggi disesuaikan isi
      val pageWidth = 384
      val pageHeight = 650
      val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
      val page = doc.startPage(pageInfo)

      drawReceiptOnCanvas(page.canvas, receipt, settings, pageWidth.toFloat())
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
   * Ukuran proporsional, font monospace rapi sesuai lebar kolom, seluruh teks terbaca, dan tidak terpotong.
   */
  fun exportAndShareJpg(context: Context, receipt: Receipt, settings: PrintSettings) {
    try {
      val lineWidth = ReceiptFormatter.getLineWidth(settings.paperWidthMm)
      val formattedLines = ReceiptFormatter.getFormattedLines(receipt, settings)

      // Resolusi tajam: 720px untuk 58mm (32 kolom), 960px untuk 88mm (48 kolom)
      val imageWidth = if (lineWidth == 48) 960 else 720
      val sideMargin = if (lineWidth == 48) 48f else 36f
      val printableWidth = imageWidth - (sideMargin * 2f)

      // Kalibrasi ukuran font monospace agar tepat lineWidth karakter mengisi printableWidth tanpa subpixel drift
      val testPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 30f
      }
      val probeWidth = testPaint.measureText("0".repeat(lineWidth))
      val exactBaseTextSize = 30f * (printableWidth / probeWidth)

      val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 20, 20)
        typeface = Typeface.MONOSPACE
        textSize = exactBaseTextSize
      }

      val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(10, 10, 10)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = exactBaseTextSize
      }

      val doubleHeightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = exactBaseTextSize * 1.25f
        textScaleX = 0.8f // Menjaga lebar karakter tidak meluber keluar kertas
      }

      val normalMetrics = normalPaint.fontMetrics
      val normalLineAdvance = (normalMetrics.descent - normalMetrics.ascent) * 1.18f

      val doubleMetrics = doubleHeightPaint.fontMetrics
      val doubleLineAdvance = (doubleMetrics.descent - doubleMetrics.ascent) * 1.18f

      val topMargin = 40f
      val bottomMargin = 48f
      var totalHeight = topMargin + bottomMargin
      for (line in formattedLines) {
        totalHeight += if (line.isDoubleHeight) doubleLineAdvance else normalLineAdvance
      }

      val finalHeight = totalHeight.toInt().coerceAtLeast(240)
      val bitmap = Bitmap.createBitmap(imageWidth, finalHeight, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)

      // Latar belakang putih bersih
      canvas.drawColor(Color.WHITE)

      // Garis tepi tipis dan rapi khas batas kertas struk
      val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 220, 215)
        style = Paint.Style.STROKE
        strokeWidth = 2f
      }
      canvas.drawRect(1f, 1f, imageWidth.toFloat() - 1f, finalHeight.toFloat() - 1f, borderPaint)

      // Gambar setiap baris secara presisi dan terstruktur
      var currentY = topMargin
      for (line in formattedLines) {
        val paint = when {
          line.isDoubleHeight -> doubleHeightPaint
          line.isBold -> boldPaint
          else -> normalPaint
        }
        val lineAdvance = if (line.isDoubleHeight) doubleLineAdvance else normalLineAdvance
        val baselineY = currentY - paint.fontMetrics.ascent

        if (line.text.isNotBlank()) {
          val textWidth = paint.measureText(line.text)
          val x = if (line.isCentered) {
            sideMargin + ((printableWidth - textWidth) / 2f).coerceAtLeast(0f)
          } else {
            sideMargin
          }
          canvas.drawText(line.text, x, baselineY, paint)
        }
        currentY += lineAdvance
      }

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

  private fun drawReceiptOnCanvas(canvas: Canvas, receipt: Receipt, settings: PrintSettings, width: Float) {
    // Latar belakang putih bersih
    canvas.drawColor(Color.WHITE)

    when (settings.receiptTheme) {
      com.example.data.model.ReceiptTheme.SEDERHANA -> drawSederhanaCanvas(canvas, receipt, settings, width)
      com.example.data.model.ReceiptTheme.PROFESIONAL -> drawProfesionalCanvas(canvas, receipt, settings, width)
      com.example.data.model.ReceiptTheme.MODERN -> drawModernCanvas(canvas, receipt, settings, width)
    }
  }

  private fun drawSederhanaCanvas(canvas: Canvas, receipt: Receipt, settings: PrintSettings, width: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 11.5f
      typeface = Typeface.MONOSPACE
    }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 12.5f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val paintDashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.GRAY
      strokeWidth = 1f
      pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }
    val paintSolid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.DKGRAY
      strokeWidth = 1f
    }

    var y = 26f
    val margin = 20f

    fun drawCenter(text: String, p: Paint) {
      val textWidth = p.measureText(text)
      canvas.drawText(text, (width - textWidth) / 2, y, p)
      y += p.textSize + 5f
    }
    fun drawRow(left: String, right: String, pL: Paint = paint, pR: Paint = paint) {
      canvas.drawText(left, margin, y, pL)
      val rWidth = pR.measureText(right)
      canvas.drawText(right, width - margin - rWidth, y, pR)
      y += 17f
    }
    fun drawLine(isSolid: Boolean = false) {
      y += 2f
      val p = if (isSolid) paintSolid else paintDashed
      canvas.drawLine(margin, y, width - margin, y, p)
      y += 10f
    }

    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) drawCenter(settings.storeName.uppercase(), paintBold)
      if (settings.storePhone.isNotBlank()) drawCenter("Telp: ${settings.storePhone}", paint)
      drawLine(false)
    }

    drawCenter("BUKTI TRANSFER", paintBold)
    drawLine(false)

    if (receipt.transactionDate.isNotBlank()) drawRow("Tgl", receipt.transactionDate)
    if (receipt.recipientBank.isNotBlank()) drawRow("Bank", receipt.recipientBank)
    if (receipt.recipientAccount.isNotBlank()) drawRow("No.Rek", receipt.recipientAccount)
    if (receipt.recipientName.isNotBlank()) drawRow("Penerima", receipt.recipientName)
    drawLine(true)

    drawRow("Jumlah", receipt.formattedAmount)
    drawRow("Admin", receipt.formattedAdminFee)
    drawLine(true)
    drawRow("TOTAL", receipt.formattedTotal, paintBold, paintBold)

    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) drawRow("Ref", receipt.refNumber)
    if (receipt.notes.isNotBlank()) drawRow("Ket", receipt.notes)
    drawLine(false)

    if (settings.footerMessage.isNotBlank()) {
      for (line in settings.footerMessage.lines()) {
        if (line.isNotBlank()) drawCenter(line.trim(), paint)
      }
    } else {
      drawCenter("Terima Kasih", paint)
    }
  }

  private fun drawProfesionalCanvas(canvas: Canvas, receipt: Receipt, settings: PrintSettings, width: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 12f
      typeface = Typeface.MONOSPACE
    }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 13f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 15f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val paintDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.DKGRAY
      strokeWidth = 1.2f
    }

    var y = 28f
    val margin = 20f

    fun drawCenter(text: String, p: Paint) {
      val textWidth = p.measureText(text)
      canvas.drawText(text, (width - textWidth) / 2, y, p)
      y += p.textSize + 5f
    }
    fun drawRow(left: String, right: String, pL: Paint = paint, pR: Paint = paint) {
      canvas.drawText(left, margin, y, pL)
      val rWidth = pR.measureText(right)
      canvas.drawText(right, width - margin - rWidth, y, pR)
      y += 18f
    }
    fun drawDivider(isDouble: Boolean = false) {
      y += 3f
      canvas.drawLine(margin, y, width - margin, y, paintDivider)
      if (isDouble) {
        y += 3f
        canvas.drawLine(margin, y, width - margin, y, paintDivider)
      }
      y += 12f
    }

    if (settings.autoAddHeader) {
      if (settings.storeName.isNotBlank()) drawCenter(settings.storeName.uppercase(), paintTitle)
      if (settings.storeAddress.isNotBlank()) drawCenter(settings.storeAddress, paint)
      if (settings.storePhone.isNotBlank()) drawCenter("Telp/WA: ${settings.storePhone}", paint)
      drawDivider(true)
    }

    drawCenter("STRUK BUKTI PENERIMA", paintBold)
    drawCenter("[ STATUS: TRANSAKSI BERHASIL ]", paint)
    drawDivider(false)

    if (receipt.transactionDate.isNotBlank()) drawRow("Waktu Transaksi", receipt.transactionDate)
    if (receipt.recipientBank.isNotBlank()) drawRow("Bank Penyelenggara", receipt.recipientBank)
    if (receipt.recipientAccount.isNotBlank()) drawRow("No. Rekening/HP", receipt.recipientAccount)
    if (receipt.recipientName.isNotBlank()) drawRow("Nama Penerima", receipt.recipientName, paint, paintBold)
    drawDivider(false)

    drawRow("Nominal Transfer", receipt.formattedAmount)
    drawRow("Biaya Layanan Admin", receipt.formattedAdminFee)
    drawDivider(false)

    drawRow("TOTAL PEMBAYARAN", receipt.formattedTotal, paintBold, paintBold)

    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) drawRow("No. Referensi Bank", receipt.refNumber)
    if (receipt.notes.isNotBlank()) drawRow("Catatan / Berita", receipt.notes)
    drawDivider(true)

    drawCenter("Simpan struk ini sebagai bukti pembayaran sah.", paint)
    if (settings.footerMessage.isNotBlank()) {
      for (line in settings.footerMessage.lines()) {
        if (line.isNotBlank()) drawCenter(line.trim(), paint)
      }
    }
  }

  private fun drawModernCanvas(canvas: Canvas, receipt: Receipt, settings: PrintSettings, width: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 12f
      typeface = Typeface.SANS_SERIF
    }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 12.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val paintSection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(20, 70, 160)
      textSize = 11.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(10, 45, 110)
      textSize = 15f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val paintDots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(120, 140, 170)
      strokeWidth = 1f
      pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(200, 210, 225)
      strokeWidth = 1.2f
    }
    val paintTotalBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(240, 245, 255)
      style = Paint.Style.FILL
    }
    val paintTotalBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(50, 100, 200)
      style = Paint.Style.STROKE
      strokeWidth = 1.2f
    }

    var y = 28f
    val margin = 20f

    fun drawCenter(text: String, p: Paint) {
      val textWidth = p.measureText(text)
      canvas.drawText(text, (width - textWidth) / 2, y, p)
      y += p.textSize + 5f
    }
    fun drawRow(left: String, right: String, pL: Paint = paint, pR: Paint = paint) {
      canvas.drawText(left, margin, y, pL)
      val rWidth = pR.measureText(right)
      canvas.drawText(right, width - margin - rWidth, y, pR)
      y += 18f
    }
    fun drawDivider(isDots: Boolean = false) {
      y += 3f
      val p = if (isDots) paintDots else paintLine
      canvas.drawLine(margin, y, width - margin, y, p)
      y += 12f
    }

    if (settings.autoAddHeader) {
      drawDivider(true)
      if (settings.storeName.isNotBlank()) drawCenter("★ ${settings.storeName.uppercase()} ★", paintHeader)
      if (settings.storeAddress.isNotBlank()) drawCenter(settings.storeAddress, paint)
      if (settings.storePhone.isNotBlank()) drawCenter("WA/CS: ${settings.storePhone}", paint)
      drawDivider(true)
    }

    drawCenter(">>> BUKTI TRANSFER DIGITAL <<<", paintSection)
    y += 4f

    drawCenter("## INFORMASI PENERIMA ##", paintSection)
    if (receipt.transactionDate.isNotBlank()) drawRow("Waktu", receipt.transactionDate)
    if (receipt.recipientBank.isNotBlank()) drawRow("Bank/Tujuan", receipt.recipientBank)
    if (receipt.recipientAccount.isNotBlank()) drawRow("ID / No. Rek", receipt.recipientAccount)
    if (receipt.recipientName.isNotBlank()) drawRow("Atas Nama", receipt.recipientName, paint, paintBold)
    drawDivider(false)

    drawCenter("## RINCIAN BIAYA ##", paintSection)
    drawRow("Nominal", receipt.formattedAmount)
    drawRow("Biaya Admin", receipt.formattedAdminFee)
    drawDivider(false)

    // Highlighted Total Box
    val totalBoxRect = android.graphics.RectF(margin, y - 4f, width - margin, y + 24f)
    canvas.drawRoundRect(totalBoxRect, 6f, 6f, paintTotalBox)
    canvas.drawRoundRect(totalBoxRect, 6f, 6f, paintTotalBorder)
    y += 16f
    canvas.drawText("TOTAL BAYAR", margin + 8f, y, paintBold)
    val totalVal = ">>> ${receipt.formattedTotal} <<<"
    val tWidth = paintHeader.measureText(totalVal)
    canvas.drawText(totalVal, width - margin - 8f - tWidth, y, paintHeader)
    y += 18f

    if (settings.showRefNumber && receipt.refNumber.isNotBlank()) drawRow("Ref ID", receipt.refNumber)
    if (receipt.notes.isNotBlank()) drawRow("Catatan", receipt.notes)
    drawDivider(true)

    drawCenter("TERIMA KASIH ATAS KUNJUNGAN ANDA", paintBold)
    if (settings.footerMessage.isNotBlank()) {
      for (line in settings.footerMessage.lines()) {
        if (line.isNotBlank()) drawCenter(line.trim(), paint)
      }
    }
    drawCenter("• Powered by JADISTRUK •", paint)
  }
}
