package com.example.ui.screens.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PrintSettings
import com.example.data.model.Receipt
import com.example.data.printer.ReceiptFormatter
import com.example.data.printer.ThermalReceiptRenderer

/**
 * Mesin rendering pratinjau struk thermal digital JADISTRUK 100% presisi.
 *
 * Menggunakan ThermalReceiptRenderer global yang teruji:
 * - Ukuran dokumen berbasis standar fisik gulungan thermal: 58 mm (32 kolom) & 88 mm (48 kolom).
 * - Grid karakter monospaced terkunci presisi, mencegah subpixel drift dan teks melebar.
 * - Sistem baris non-overlapping yang mengunci baseline di tengah slot, mencegah teks bertumpuk.
 * - Menghasilkan bitmap berkualitas tinggi yang identik dengan output ekspor gambar JPG.
 */
@Composable
fun ThermalReceiptPreview(
  receipt: Receipt,
  settings: PrintSettings,
  modifier: Modifier = Modifier
) {
  val lineWidth = remember(settings.paperWidthMm) {
    ReceiptFormatter.getLineWidth(settings.paperWidthMm)
  }

  // Pre-render bitmap dokumen struk menggunakan mesin kanonikal global
  val receiptBitmap = remember(receipt, settings) {
    ThermalReceiptRenderer.renderReceiptBitmap(
      receipt = receipt,
      settings = settings,
      includeCutterEffect = true
    )
  }

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
            text = "100% Presisi Thermal",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Container kertas thermal mandiri (independen dari ukuran layar / resolusi / DPI)
    // 58 mm: 290.dp, 88 mm: 360.dp (proporsional terhadap gulungan fisik)
    val paperWidthDp = if (lineWidth == 48) 360.dp else 290.dp

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .width(paperWidthDp)
          .shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(2.dp),
            clip = false,
            ambientColor = Color(0x33000000),
            spotColor = Color(0x22000000)
          )
          .background(Color(0xFFFAFAF7))
      ) {
        Image(
          bitmap = receiptBitmap.asImageBitmap(),
          contentDescription = "Pratinjau Struk Thermal",
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
          contentScale = ContentScale.FillWidth,
          filterQuality = FilterQuality.High
        )
      }
    }
  }
}
