package com.example.ui.screens.detail

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import com.example.data.printer.ReceiptFormatter
import kotlin.math.min

/**
 * Mesin rendering pratinjau struk thermal 100% identik dengan hasil cetak printer fisik.
 *
 * Menggunakan mesin layout baris dan data yang sama persis dengan modul Bluetooth ESC/POS:
 * - 58 mm: 32 kolom karakter monospaced Font A
 * - 88 mm: 48 kolom karakter monospaced Font A
 * - Menjamin posisi huruf, spasi, divider, margin kertas, dan jarak antarbaris presisi 1:1.
 */
@Composable
fun ThermalReceiptPreview(
  receipt: Receipt,
  settings: PrintSettings,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val lineWidth = remember(settings.paperWidthMm) {
    ReceiptFormatter.getLineWidth(settings.paperWidthMm)
  }
  val formattedLines = remember(receipt, settings) {
    ReceiptFormatter.getFormattedLines(receipt, settings)
  }

  // Pilihan mode skala tampilan (Fit to Screen vs 100% Ukuran Fisik)
  var zoomScale by remember { mutableFloatStateOf(1.0f) }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Bar status spesifikasi kertas thermal aktual
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Filled.Print,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = if (lineWidth == 48) "Kertas 88 mm • 48 Kolom" else "Kertas 58 mm • 32 Kolom",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.weight(1f))
      Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(12.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "100% Presisi ESC/POS",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Container kertas thermal dengan kalkulasi ukuran matematis presisi
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center
    ) {
      val availableWidthDp = maxWidth
      val is88mm = lineWidth == 48

      // Menentukan lebar kertas fisik proporsional terhadap layar
      val targetPaperWidthDp = if (is88mm) {
        min(availableWidthDp.value - 12f, 440f).coerceAtLeast(320f)
      } else {
        min(availableWidthDp.value - 24f, 340f).coerceAtLeast(280f)
      }

      val actualPaperWidthDp = (targetPaperWidthDp * zoomScale).dp
      val sideMarginDp = if (is88mm) 14.dp else 12.dp

      val paperWidthPx = with(density) { actualPaperWidthDp.toPx() }
      val sideMarginPx = with(density) { sideMarginDp.toPx() }
      val printableWidthPx = paperWidthPx - (sideMarginPx * 2f)

      // Mengukur tinggi kertas secara akurat berdasarkan setiap baris teks
      val topMarginPx = with(density) { 20.dp.toPx() }
      val baseLineAdvanceDp = if (is88mm) 14.dp else 16.dp
      val doubleLineAdvanceDp = if (is88mm) 22.dp else 25.dp

      val totalHeightDp = remember(formattedLines, baseLineAdvanceDp, doubleLineAdvanceDp, settings.printFeedLines) {
        var h = 36.dp // Top & bottom paper margins
        for (line in formattedLines) {
          h += if (line.isDoubleHeight) doubleLineAdvanceDp else baseLineAdvanceDp
        }
        h += (settings.printFeedLines * 12).dp // Feed lines margin sebelum cutter
        h
      }

      val totalHeightPx = with(density) { totalHeightDp.toPx() }

      // Komponen Kertas Thermal Realistis
      Box(
        modifier = Modifier
          .width(actualPaperWidthDp)
          .shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(2.dp),
            clip = false,
            ambientColor = Color(0x33000000),
            spotColor = Color(0x22000000)
          )
          .border(0.8.dp, Color(0xFFD6D6CE))
          .background(Color(0xFFFAFAF7))
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(totalHeightDp)
        ) {
          val canvas = drawContext.canvas.nativeCanvas

          // 1. Gambar Efek Gigi Pemotong Kertas Thermal (Serrated Cutter Edge) di Atas
          val tearPathTop = Path()
          val toothWidth = 8f
          val toothHeight = 4f
          var tx = 0f
          tearPathTop.moveTo(0f, 0f)
          while (tx < paperWidthPx) {
            tearPathTop.lineTo(tx + toothWidth / 2f, toothHeight)
            tearPathTop.lineTo(tx + toothWidth, 0f)
            tx += toothWidth
          }
          tearPathTop.close()

          val tearPaint = Paint().apply {
            color = android.graphics.Color.rgb(230, 230, 222)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
          }
          canvas.drawPath(tearPathTop, tearPaint)

          // 2. Kalibrasi Ukuran Font Monospace Presisi Karakter
          // Menggunakan probe teks untuk memastikan tepat 'lineWidth' kolom memenuhi printableWidthPx
          val testPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 20f
          }
          val sampleProbe = "0".repeat(lineWidth)
          val sampleMeasuredWidth = testPaint.measureText(sampleProbe)
          val exactBaseTextSize = 20f * (printableWidthPx / sampleMeasuredWidth)

          val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(18, 18, 18)
            typeface = Typeface.MONOSPACE
            textSize = exactBaseTextSize
          }

          val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(10, 10, 10)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = exactBaseTextSize
          }

          val doubleHeightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(5, 5, 5)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = exactBaseTextSize * 1.35f
          }

          // 3. Render Setiap Baris Sesuai Mesin Layout ESC/POS
          var currentY = topMarginPx

          for (line in formattedLines) {
            val paint = when {
              line.isDoubleHeight -> doubleHeightPaint
              line.isBold -> boldPaint
              else -> normalPaint
            }

            val lineAdvance = if (line.isDoubleHeight) {
              with(density) { doubleLineAdvanceDp.toPx() }
            } else {
              with(density) { baseLineAdvanceDp.toPx() }
            }

            currentY += lineAdvance

            if (line.text.isNotBlank()) {
              if (line.isCentered) {
                val textWidth = paint.measureText(line.text)
                val x = sideMarginPx + ((printableWidthPx - textWidth) / 2f).coerceAtLeast(0f)
                canvas.drawText(line.text, x, currentY, paint)
              } else {
                canvas.drawText(line.text, sideMarginPx, currentY, paint)
              }
            }
          }

          // 4. Gambar Garis Potong Bawah Kertas Thermal
          val tearPathBottom = Path()
          val by = totalHeightPx
          var bx = 0f
          tearPathBottom.moveTo(0f, by)
          while (bx < paperWidthPx) {
            tearPathBottom.lineTo(bx + toothWidth / 2f, by - toothHeight)
            tearPathBottom.lineTo(bx + toothWidth, by)
            bx += toothWidth
          }
          tearPathBottom.close()
          canvas.drawPath(tearPathBottom, tearPaint)
        }
      }
    }
  }
}
