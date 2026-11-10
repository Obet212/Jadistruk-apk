package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.remote.AppUpdateStatus
import com.example.ui.MainApp
import com.example.ui.screens.update.ForcedUpdateScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReceiptViewModel

import android.content.Intent
import android.net.Uri
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
  private val viewModel: ReceiptViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // FLAG_SECURE: cegah screenshot, screen recording, dan recent apps preview
    com.example.data.security.ScreenProtectionManager.applyProtection(this)
    handleIncomingShareIntent(intent)
    enableEdgeToEdge()
    setContent {
      val settings by viewModel.settings.collectAsState()
      val appUpdateStatus by viewModel.appUpdateStatus.collectAsState()
      val isFailureDismissed by viewModel.isUpdateFailureDismissed.collectAsState()

      MyApplicationTheme(appTheme = settings.appTheme) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          when (val currentStatus = appUpdateStatus) {
            is AppUpdateStatus.ForcedUpdateRequired -> {
              // Full Screen Forced Update: tidak dapat dilewati, tanpa tombol tutup, cegah back button
              ForcedUpdateScreen(updateInfo = currentStatus)
            }

            else -> {
              // Versi memenuhi syarat atau sedang dicek: pengguna dapat menggunakan aplikasi seperti biasa
              Box(modifier = Modifier.fillMaxSize()) {
                MainApp(viewModel = viewModel)

                // Jika internet tidak tersedia / pemeriksaan gagal: jangan mengunci aplikasi, sediakan tombol coba lagi
                AnimatedVisibility(
                  visible = currentStatus is AppUpdateStatus.CheckFailed && !isFailureDismissed,
                  enter = fadeIn() + expandVertically(),
                  exit = fadeOut() + shrinkVertically(),
                  modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                  UpdateCheckFailureBanner(
                    errorMessage = (currentStatus as? AppUpdateStatus.CheckFailed)?.errorMessage
                      ?: "Gagal memeriksa versi aplikasi.",
                    onRetry = { viewModel.checkForAppUpdate() },
                    onDismiss = { viewModel.dismissUpdateFailure() }
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingShareIntent(intent)
  }

  private fun handleIncomingShareIntent(intent: Intent?) {
    if (intent == null) return
    if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
      val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
      if (imageUri != null) {
        viewModel.onSharedImageReceived(imageUri)
      }
    } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true) {
      val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
      val firstUri = uris?.firstOrNull() ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
      if (firstUri != null) {
        viewModel.onSharedImageReceived(firstUri)
      }
    }
  }

  override fun onPause() {
    super.onPause()
    // Backup otomatis saat aplikasi ditangguhkan / diminimize (tidak hanya onDestroy)
    viewModel.triggerAutoBackup("app_lifecycle_pause")
  }

  override fun onStop() {
    super.onStop()
    viewModel.triggerAutoBackup("app_lifecycle_stop")
  }
}

/**
 * Banner non-blocking ketika pemeriksaan versi Firebase Remote Config gagal
 * (misalnya perangkat offline atau koneksi timeout).
 * Menjamin pengguna tetap dapat menggunakan seluruh fitur offline secara normal.
 */
@Composable
private fun UpdateCheckFailureBanner(
  errorMessage: String,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .shadow(8.dp, RoundedCornerShape(12.dp))
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
      .testTag("banner_update_check_failure"),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.WifiOff,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Mode Offline / Cek Versi Gagal",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          maxLines = 2
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      OutlinedButton(
        onClick = onRetry,
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = Modifier.testTag("btn_retry_update_check")
      ) {
        Icon(
          imageVector = Icons.Filled.Refresh,
          contentDescription = null,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Coba Lagi",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )
      }

      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(28.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Tutup Peringatan",
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
