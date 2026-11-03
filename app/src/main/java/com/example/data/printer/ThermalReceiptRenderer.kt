package com.example.data.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt

/**
 * Dimensi layout struk thermal kanonikal.
 * Ukuran dan proporsi dokumen murni bergantung pada standar kertas struk,
 * sepenuhnya independen dari ukuran layar, DPI, density, maupun konfigurasi perangkat.
 */
data class ReceiptDimensions(
  val documentWidth: Float,
  val documentHeight: Float,
  val lineWidth: Int,
  val colWidth: Float,
  val sideMargin: Float,
  val printableWidth: Float,
  val normalLineHeight: Float,
  val doubleLineHeight: Float,
  val topMargin: Float,
  val bottomMargin: Float
)

/**
 * Mesin Rendering Global Struk JADISTRUK untuk Seluruh Tema (Sederhana, Profesional, Modern).
 *
 * Mengeliminasi seluruh sumber ketidakstabilan rendering:
 * 1. Ukuran dokumen berbasis standar fisik kertas (720px untuk 58mm, 960px untuk 88mm), bukan layar HP.
 * 2. Grid karakter monospaced terkunci presisi (20.0px/kolom untuk 58mm, 18.0px/kolom untuk 88mm)
 *    dengan kalibrasi letterSpacing otomatis yang kebal terhadap variasi font engine antar-Android.
 * 3. Sistem slot baris non-overlapping yang mengunci baseline di tengah slot, mencegah teks bertumpuk,
 *    meluber, bergeser, atau terpotong.
 * 4. Berbagi mesin rendering tunggal antara pratinjau layar, ekspor JPG, dan pratinjau cetak PDF/sistem,
 *    sehingga hasil ekspor gambar JPG 100% identik dengan tampilan pratinjau.
 */
object ThermalReceiptRenderer {

  // Standar Dokumen Kanonikal
  // 58 mm: 32 kolom karakter, lebar 720px, margin 40px -> area cetak 640px (tepat 20.0px per kolom)
  const val CANONICAL_WIDTH_58MM = 720f
  const val MARGIN_58MM = 40f
  const val COL_WIDTH_58MM = 20.0f

  // 88 mm: 48 kolom karakter, lebar 960px, margin 48px -> area cetak 864px (tepat 18.0px per kolom)
  const val CANONICAL_WIDTH_88MM = 960f
  const val MARGIN_88MM = 48f
  const val COL_WIDTH_88MM = 18.0f

  /**
   * Menghitung dimensi dokumen struk thermal secara absolut dan konsisten.
   */
  fun calculateDimensions(receipt: Receipt, settings: PrintSettings): ReceiptDimensions {
    val lineWidth = ReceiptFormatter.getLineWidth(settings.paperWidthMm)
    val is88mm = lineWidth == 48

    val docWidth = if (is88mm) CANONICAL_WIDTH_88MM else CANONICAL_WIDTH_58MM
    val margin = if (is88mm) MARGIN_88MM else MARGIN_58MM
    val colW = if (is88mm) COL_WIDTH_88MM else COL_WIDTH_58MM
    val printable = docWidth - (margin * 2f)

    val normalLineH = colW * 1.75f
    val doubleLineH = colW * 2.30f

    val topMargin = colW * 1.6f
    val bottomMargin = colW * 2.0f
    val feedLinesMargin = (settings.printFeedLines * colW * 0.9f).coerceAtLeast(0f)

    val lines = ReceiptFormatter.getFormattedLines(receipt, settings)
    var contentHeight = 0f
    for (line in lines) {
      contentHeight += if (line.isDoubleHeight) doubleLineH else normalLineH
    }

    val totalHeight = (topMargin + contentHeight + bottomMargin + feedLinesMargin).coerceAtLeast(240f)

    return ReceiptDimensions(
      documentWidth = docWidth,
      documentHeight = totalHeight,
      lineWidth = lineWidth,
      colWidth = colW,
      sideMargin = margin,
      printableWidth = printable,
      normalLineHeight = normalLineH,
      doubleLineHeight = doubleLineH,
      topMargin = topMargin,
      bottomMargin = bottomMargin
    )
  }

  /**
   * Menggambar dokumen struk utuh ke Canvas yang diberikan.
   */
  fun drawReceiptDocument(
    canvas: Canvas,
    receipt: Receipt,
    settings: PrintSettings,
    dimensions: ReceiptDimensions,
    includeCutterEffect: Boolean = true,
    backgroundColor: Int = Color.rgb(250, 250, 247)
  ) {
    // 1. Gambar latar belakang kertas struk
    canvas.drawColor(backgroundColor)

    val docW = dimensions.documentWidth
    val docH = dimensions.documentHeight

    // 2. Garis batas kertas (paper edge)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(214, 214, 206)
      style = Paint.Style.STROKE
      strokeWidth = 1.5f
    }
    canvas.drawRect(0.75f, 0.75f, docW - 0.75f, docH - 0.75f, borderPaint)

    // 3. Efek gigi pemotong kertas thermal (Serrated Cutter Edge) jika diaktifkan
    if (includeCutterEffect) {
      val toothWidth = dimensions.colWidth * 0.8f
      val toothHeight = dimensions.colWidth * 0.35f

      val cutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(224, 224, 216)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
      }

      // Gigi atas
      val topPath = Path()
      topPath.moveTo(0f, 0f)
      var tx = 0f
      while (tx < docW) {
        topPath.lineTo(tx + toothWidth / 2f, toothHeight)
        topPath.lineTo((tx + toothWidth).coerceAtMost(docW), 0f)
        tx += toothWidth
      }
      canvas.drawPath(topPath, cutterPaint)

      // Gigi bawah
      val bottomPath = Path()
      bottomPath.moveTo(0f, docH)
      var bx = 0f
      while (bx < docW) {
        bottomPath.lineTo(bx + toothWidth / 2f, docH - toothHeight)
        bottomPath.lineTo((bx + toothWidth).coerceAtMost(docW), docH)
        bx += toothWidth
      }
      canvas.drawPath(bottomPath, cutterPaint)
    }

    // 4. Siapkan konfigurasi Paint monospaced dengan penguncian grid karakter presisi
    val baseTextSize = dimensions.colWidth * 1.45f

    // Normal Font
    val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(22, 22, 22)
      typeface = Typeface.MONOSPACE
      textSize = baseTextSize
    }
    val measuredNormalCharW = normalPaint.measureText("0")
    if (normalPaint.textSize > 0f) {
      normalPaint.letterSpacing = (dimensions.colWidth - measuredNormalCharW) / normalPaint.textSize
    }

    // Bold Font
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(8, 8, 8)
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      textSize = baseTextSize
    }
    val measuredBoldCharW = boldPaint.measureText("0")
    if (boldPaint.textSize > 0f) {
      boldPaint.letterSpacing = (dimensions.colWidth - measuredBoldCharW) / boldPaint.textSize
    }

    // Double Height Font (Font A ESC/POS GS ! 0x01: tinggi ganda, lebar tetap 1 kolom)
    val doubleHeightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(0, 0, 0)
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      textSize = baseTextSize * 1.35f
    }
    val measuredDoubleCharW = doubleHeightPaint.measureText("0")
    // Pastikan karakter tidak melebar ke kolom tetangga
    val targetCharW = dimensions.colWidth * 0.82f
    doubleHeightPaint.textScaleX = (targetCharW / measuredDoubleCharW).coerceAtMost(0.9f)
    val scaledDoubleCharW = doubleHeightPaint.measureText("0")
    if (doubleHeightPaint.textSize > 0f) {
      doubleHeightPaint.letterSpacing = (dimensions.colWidth - scaledDoubleCharW) / doubleHeightPaint.textSize
    }

    // 5. Render baris-baris struk ke dalam slot baris non-overlapping yang stabil
    val formattedLines = ReceiptFormatter.getFormattedLines(receipt, settings)
    var currentSlotTop = dimensions.topMargin

    for (line in formattedLines) {
      val slotHeight = if (line.isDoubleHeight) dimensions.doubleLineHeight else dimensions.normalLineHeight
      val paint = when {
        line.isDoubleHeight -> doubleHeightPaint
        line.isBold -> boldPaint
        else -> normalPaint
      }

      val fontMetrics = paint.fontMetrics
      val glyphHeight = fontMetrics.descent - fontMetrics.ascent
      val slotPadding = (slotHeight - glyphHeight) / 2f
      val baselineY = currentSlotTop + slotPadding - fontMetrics.ascent

      if (line.text.isNotBlank()) {
        val textLength = line.text.length
        val textWidth = textLength * dimensions.colWidth
        val startX = if (line.isCentered) {
          dimensions.sideMargin + ((dimensions.printableWidth - textWidth) / 2f).coerceAtLeast(0f)
        } else {
          dimensions.sideMargin
        }

        canvas.drawText(line.text, startX, baselineY, paint)
      }

      currentSlotTop += slotHeight
    }
  }

  /**
   * Menghasilkan Bitmap beresolusi tinggi 100% identik dari struk thermal.
   * Digunakan bersama oleh pratinjau digital di layar dan ekspor file JPG.
   */
  fun renderReceiptBitmap(
    receipt: Receipt,
    settings: PrintSettings,
    includeCutterEffect: Boolean = true
  ): Bitmap {
    val dimensions = calculateDimensions(receipt, settings)
    val width = dimensions.documentWidth.toInt()
    val height = dimensions.documentHeight.toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    drawReceiptDocument(
      canvas = canvas,
      receipt = receipt,
      settings = settings,
      dimensions = dimensions,
      includeCutterEffect = includeCutterEffect
    )

    return bitmap
  }
}
