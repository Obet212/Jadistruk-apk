package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AppUpdateStatus {
  data object Checking : AppUpdateStatus

  data class UpToDate(
    val currentVersion: String,
    val latestVersion: String
  ) : AppUpdateStatus

  data class ForcedUpdateRequired(
    val currentVersion: String,
    val latestVersion: String,
    val minimumVersion: String,
    val updateTitle: String,
    val updateMessage: String,
    val updateUrl: String
  ) : AppUpdateStatus

  data class CheckFailed(
    val errorMessage: String,
    val currentVersion: String
  ) : AppUpdateStatus
}

data class RemoteConfigData(
  val latestVersion: String,
  val minimumVersion: String,
  val updateTitle: String,
  val updateMessage: String,
  val updateUrl: String
)

class RemoteConfigManager(private val context: Context) {

  companion object {
    private const val TAG = "RemoteConfigManager"

    const val KEY_LATEST_VERSION = "latest_version"
    const val KEY_MINIMUM_VERSION = "minimum_version"
    const val KEY_UPDATE_TITLE = "update_title"
    const val KEY_UPDATE_MESSAGE = "update_message"
    const val KEY_UPDATE_URL = "update_url"

    private const val DEFAULT_LATEST_VERSION = "1.6"
    private const val DEFAULT_MINIMUM_VERSION = "1.0"
    private const val DEFAULT_UPDATE_TITLE = "Pembaruan Aplikasi Tersedia"
    private const val DEFAULT_UPDATE_MESSAGE =
      "Versi aplikasi Anda saat ini sudah tidak didukung. Silakan perbarui aplikasi JadiStruk ke versi terbaru untuk menikmati fitur dan stabilitas terbaik."
    private const val DEFAULT_UPDATE_URL = "https://github.com/Obet212/Jadistruk-apk/releases"
  }

  private val remoteConfig: FirebaseRemoteConfig by lazy {
    Firebase.remoteConfig
  }

  init {
    try {
      val configSettings = remoteConfigSettings {
        // Interval 0 detik agar pembaruan langsung terdeteksi setiap kali aplikasi dibuka
        minimumFetchIntervalInSeconds = 0L
        fetchTimeoutInSeconds = 8L
      }
      remoteConfig.setConfigSettingsAsync(configSettings)

      // Tetapkan in-app default fallback
      val defaults = mapOf(
        KEY_LATEST_VERSION to DEFAULT_LATEST_VERSION,
        KEY_MINIMUM_VERSION to DEFAULT_MINIMUM_VERSION,
        KEY_UPDATE_TITLE to DEFAULT_UPDATE_TITLE,
        KEY_UPDATE_MESSAGE to DEFAULT_UPDATE_MESSAGE,
        KEY_UPDATE_URL to DEFAULT_UPDATE_URL
      )
      remoteConfig.setDefaultsAsync(defaults)
    } catch (e: Exception) {
      Log.e(TAG, "Gagal menginisialisasi setelan Remote Config: ${e.message}", e)
    }
  }

  /**
   * Mengambil konfigurasi terbaru dari Firebase Remote Config dan mengecek apakah
   * aplikasi memerlukan pembaruan paksa (forced update).
   */
  suspend fun checkAppVersion(currentVersionName: String = BuildConfig.VERSION_NAME): AppUpdateStatus =
    withContext(Dispatchers.IO) {
      try {
        // Ambil dan aktifkan nilai terbaru dari server Firebase
        val fetchSuccess = try {
          suspendCancellableCoroutine<Boolean> { cont ->
            remoteConfig.fetchAndActivate()
              .addOnCompleteListener { task ->
                if (cont.isActive) {
                  if (task.isSuccessful) {
                    cont.resume(true)
                  } else {
                    cont.resumeWithException(
                      task.exception ?: RuntimeException("Gagal mengambil data Firebase Remote Config")
                    )
                  }
                }
              }
          }
        } catch (fetchEx: Exception) {
          Log.w(TAG, "Fetch Remote Config gagal atau offline: ${fetchEx.message}")
          false
        }

        val latestVersion = remoteConfig.getString(KEY_LATEST_VERSION).ifBlank { DEFAULT_LATEST_VERSION }
        val minimumVersion = remoteConfig.getString(KEY_MINIMUM_VERSION).ifBlank { DEFAULT_MINIMUM_VERSION }
        val updateTitle = remoteConfig.getString(KEY_UPDATE_TITLE).ifBlank { DEFAULT_UPDATE_TITLE }
        val updateMessage = remoteConfig.getString(KEY_UPDATE_MESSAGE).ifBlank { DEFAULT_UPDATE_MESSAGE }
        val updateUrl = remoteConfig.getString(KEY_UPDATE_URL).ifBlank { DEFAULT_UPDATE_URL }

        Log.d(TAG, "Current: $currentVersionName, Min: $minimumVersion, Latest: $latestVersion, Fetched: $fetchSuccess")

        // Jika fetch gagal total (misal offline), jangan mengunci aplikasi, berikan opsi coba lagi
        if (!fetchSuccess && minimumVersion == DEFAULT_MINIMUM_VERSION) {
          // Tetap verifikasi apakah versi saat ini di bawah default minimum
          val isBelow = VersionComparator.isVersionBelowMinimum(currentVersionName, minimumVersion)
          return@withContext if (isBelow) {
            AppUpdateStatus.ForcedUpdateRequired(
              currentVersion = currentVersionName,
              latestVersion = latestVersion,
              minimumVersion = minimumVersion,
              updateTitle = updateTitle,
              updateMessage = updateMessage,
              updateUrl = updateUrl
            )
          } else {
            AppUpdateStatus.CheckFailed(
              errorMessage = "Tidak dapat terhubung ke server pembaruan. Silakan periksa koneksi internet Anda.",
              currentVersion = currentVersionName
            )
          }
        }

        val isForcedUpdate = VersionComparator.isVersionBelowMinimum(currentVersionName, minimumVersion)
        if (isForcedUpdate) {
          AppUpdateStatus.ForcedUpdateRequired(
            currentVersion = currentVersionName,
            latestVersion = latestVersion,
            minimumVersion = minimumVersion,
            updateTitle = updateTitle,
            updateMessage = updateMessage,
            updateUrl = updateUrl
          )
        } else {
          AppUpdateStatus.UpToDate(
            currentVersion = currentVersionName,
            latestVersion = latestVersion
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Pemeriksaan versi gagal: ${e.message}", e)
        AppUpdateStatus.CheckFailed(
          errorMessage = e.localizedMessage ?: "Gagal memeriksa versi aplikasi",
          currentVersion = currentVersionName
        )
      }
    }
}

object VersionComparator {
  /**
   * Mengembalikan true jika currentVersion lebih rendah dari minimumVersion.
   * Format: "1.5", "1.5.0", "v1.6", "2.0-beta"
   */
  fun isVersionBelowMinimum(currentVersion: String, minimumVersion: String): Boolean {
    val currParts = parseVersion(currentVersion)
    val minParts = parseVersion(minimumVersion)

    if (minParts.isEmpty()) return false

    val maxLen = maxOf(currParts.size, minParts.size)
    for (i in 0 until maxLen) {
      val curr = currParts.getOrElse(i) { 0 }
      val min = minParts.getOrElse(i) { 0 }
      if (curr < min) return true
      if (curr > min) return false
    }
    return false
  }

  fun parseVersion(version: String): List<Int> {
    val clean = version.trim().removePrefix("v").removePrefix("V")
    val mainVersion = clean.substringBefore("-").substringBefore("+").substringBefore("_")
    val tokens = mainVersion.split(".")
    return tokens.mapNotNull { token ->
      token.filter { it.isDigit() }.toIntOrNull()
    }
  }
}
