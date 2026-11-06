package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.JadiStrukApp
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

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

    private const val DEFAULT_LATEST_VERSION = "2.1"
    private const val DEFAULT_MINIMUM_VERSION = "1.0"
    private const val DEFAULT_UPDATE_TITLE = "Pembaruan Aplikasi Tersedia"
    private const val DEFAULT_UPDATE_MESSAGE =
      "Versi aplikasi Anda saat ini sudah tidak didukung. Silakan perbarui aplikasi JadiStruk ke versi terbaru untuk menikmati fitur dan stabilitas terbaik."
    private const val DEFAULT_UPDATE_URL = "https://github.com/Obet212/Jadistruk-apk/releases"
  }

  @Volatile
  private var isConfigured = false

  /**
   * Mengambil instance FirebaseRemoteConfig secara aman.
   * Memastikan FirebaseApp telah terinisialisasi dan tidak memicu IllegalStateException
   * jika proses FirebaseApp belum siap atau berjalan di lingkungan offline/testing.
   */
  private fun getRemoteConfigInstance(): FirebaseRemoteConfig? {
    return try {
      val isReady = JadiStrukApp.ensureFirebaseInitialized(context)
      if (!isReady || FirebaseApp.getApps(context).isEmpty()) {
        Log.w(TAG, "FirebaseApp belum tersedia, menunda akses Remote Config.")
        return null
      }

      val config = Firebase.remoteConfig
      configureRemoteConfigSettings(config)
      config
    } catch (e: Exception) {
      Log.w(TAG, "Gagal mengakses Firebase Remote Config: ${e.message}")
      null
    }
  }

  /**
   * Mengonfigurasi parameter fetch interval dan nilai in-app default sekali saja.
   */
  private fun configureRemoteConfigSettings(config: FirebaseRemoteConfig) {
    if (isConfigured) return
    synchronized(this) {
      if (isConfigured) return
      try {
        val configSettings = remoteConfigSettings {
          // Interval 0 detik agar pembaruan langsung terdeteksi setiap kali aplikasi dibuka
          minimumFetchIntervalInSeconds = 0L
          fetchTimeoutInSeconds = 8L
        }
        config.setConfigSettingsAsync(configSettings)

        // Tetapkan in-app default fallback
        val defaults = mapOf(
          KEY_LATEST_VERSION to DEFAULT_LATEST_VERSION,
          KEY_MINIMUM_VERSION to DEFAULT_MINIMUM_VERSION,
          KEY_UPDATE_TITLE to DEFAULT_UPDATE_TITLE,
          KEY_UPDATE_MESSAGE to DEFAULT_UPDATE_MESSAGE,
          KEY_UPDATE_URL to DEFAULT_UPDATE_URL
        )
        config.setDefaultsAsync(defaults)
        isConfigured = true
        Log.d(TAG, "Firebase Remote Config berhasil dikonfigurasi dengan in-app defaults.")
      } catch (e: Exception) {
        Log.w(TAG, "Gagal mengonfigurasi setelan Remote Config: ${e.message}")
      }
    }
  }

  init {
    // Jalankan inisialisasi pasif tanpa memblokir konstruktor
    try {
      getRemoteConfigInstance()
    } catch (e: Exception) {
      Log.w(TAG, "Inisialisasi awal RemoteConfigManager dilewati: ${e.message}")
    }
  }

  /**
   * Mengambil konfigurasi terbaru dari Firebase Remote Config dan mengecek apakah
   * aplikasi memerlukan pembaruan paksa (forced update).
   *
   * Jika Firebase tidak terjangkau atau jaringan offline, aplikasi tetap mengizinkan
   * penggunaan normal tanpa memblokir alur kerja pengguna.
   */
  suspend fun checkAppVersion(currentVersionName: String = BuildConfig.VERSION_NAME): AppUpdateStatus =
    withContext(Dispatchers.IO) {
      try {
        val remoteConfig = getRemoteConfigInstance()
        if (remoteConfig == null) {
          Log.w(TAG, "Remote Config tidak tersedia (Firebase offline/tidak terinisialisasi). Menggunakan fallback lokal.")
          val isBelow = VersionComparator.isVersionBelowMinimum(currentVersionName, DEFAULT_MINIMUM_VERSION)
          return@withContext if (isBelow) {
            AppUpdateStatus.ForcedUpdateRequired(
              currentVersion = currentVersionName,
              latestVersion = DEFAULT_LATEST_VERSION,
              minimumVersion = DEFAULT_MINIMUM_VERSION,
              updateTitle = DEFAULT_UPDATE_TITLE,
              updateMessage = DEFAULT_UPDATE_MESSAGE,
              updateUrl = DEFAULT_UPDATE_URL
            )
          } else {
            // Versi valid, biarkan pengguna masuk ke aplikasi tanpa memblokir
            AppUpdateStatus.UpToDate(
              currentVersion = currentVersionName,
              latestVersion = DEFAULT_LATEST_VERSION
            )
          }
        }

        // Ambil dan aktifkan nilai terbaru dari server Firebase
        val fetchSuccess = try {
          suspendCancellableCoroutine<Boolean> { cont ->
            remoteConfig.fetchAndActivate()
              .addOnCompleteListener { task ->
                if (cont.isActive) {
                  cont.resume(task.isSuccessful)
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

        // Jika fetch gagal total (misal offline)
        if (!fetchSuccess && minimumVersion == DEFAULT_MINIMUM_VERSION) {
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
