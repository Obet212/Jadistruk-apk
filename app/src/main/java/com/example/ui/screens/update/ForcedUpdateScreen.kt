package com.example.ui.screens.update

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AppUpdateStatus

/**
 * Layar Full Screen Forced Update yang tidak dapat dilewati jika versi aplikasi
 * berada di bawah minimum_version Firebase Remote Config.
 *
 * Mencegah tombol Back, tanpa tombol X / tutup, dan menyediakan tombol
 * 'UPDATE SEKARANG' untuk membuka update_url.
 */
@Composable
fun ForcedUpdateScreen(
  updateInfo: AppUpdateStatus.ForcedUpdateRequired,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // Cegah tombol back keluar dari layar ini
  BackHandler(enabled = true) {
    Toast.makeText(
      context,
      "Pembaruan wajib diperlukan untuk terus menggunakan JadiStruk.",
      Toast.LENGTH_SHORT
    ).show()
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("screen_forced_update"),
    color = MaterialTheme.colorScheme.background
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // Icon Aksen Pembaruan Visual Mewah
        Box(
          modifier = Modifier
            .size(110.dp)
            .shadow(12.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary)
            .background(
              brush = Brush.radialGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primaryContainer,
                  MaterialTheme.colorScheme.primary
                )
              ),
              shape = CircleShape
            )
            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(54.dp)
          )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Judul Pembaruan dari Remote Config
        Text(
          text = updateInfo.updateTitle,
          style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
          ),
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Pesan Penjelasan Pembaruan dari Remote Config
        Text(
          text = updateInfo.updateMessage,
          style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Kartu Ringkasan Versi
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Versi Terpasang",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Text(
                text = "v${updateInfo.currentVersion}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.Security,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Versi Minimum Diperlukan",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Text(
                text = "v${updateInfo.minimumVersion}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
            }

            if (updateInfo.latestVersion.isNotBlank()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Versi Terbaru Tersedia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = "v${updateInfo.latestVersion}",
                  style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Tombol UPDATE SEKARANG
        Button(
          onClick = {
            try {
              val targetUri = Uri.parse(updateInfo.updateUrl.ifBlank { "https://github.com/Obet212/Jadistruk-apk/releases" })
              val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              context.startActivity(intent)
            } catch (e: Exception) {
              Toast.makeText(
                context,
                "Tidak dapat membuka tautan pembaruan: ${e.localizedMessage}",
                Toast.LENGTH_LONG
              ).show()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("btn_update_now"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          ),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "UPDATE SEKARANG",
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.ExtraBold,
              fontSize = 15.sp,
              letterSpacing = 0.5.sp
            )
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Aplikasi akan otomatis dapat digunakan kembali setelah Anda memasang versi terbaru.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
