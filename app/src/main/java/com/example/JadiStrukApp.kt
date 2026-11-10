package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Application class untuk JADISTRUK v2.0.
 * Bertanggung jawab menginisialisasi Firebase secara aman saat aplikasi pertama kali dijalankan,
 * mencegah inisialisasi ganda, dan memastikan aplikasi tetap dapat berjalan secara offline
 * jika inisialisasi Firebase gagal.
 */
class JadiStrukApp : Application() {

  companion object {
    private const val TAG = "JadiStrukApp"

    // Kredensial proyek Firebase jadistruk (dari google-services.json) untuk fallback jika ContentProvider belum berjalan
    private const val FIREBASE_APP_ID = "1:718386140735:android:50e7374fcfd2387e5c4a8a"
    private const val FIREBASE_API_KEY = "AIzaSyCIaF7CSNxV1ea3T1pTfujNuHzLn6LLXt0"
    private const val FIREBASE_PROJECT_ID = "jadistruk"
    private const val FIREBASE_STORAGE_BUCKET = "jadistruk.firebasestorage.app"
    private const val FIREBASE_GCM_SENDER_ID = "718386140735"

    /**
     * Memastikan FirebaseApp telah terinisialisasi secara aman.
     * Mengembalikan true jika FirebaseApp siap digunakan, false jika inisialisasi gagal.
     */
    @Synchronized
    fun ensureFirebaseInitialized(context: Context): Boolean {
      try {
        // 1. Cek apakah FirebaseApp sudah aktif (cegah inisialisasi ganda)
        val existingApps = FirebaseApp.getApps(context)
        if (existingApps.isNotEmpty()) {
          return true
        }

        val appContext = context.applicationContext ?: context

        // 2. Coba inisialisasi standar menggunakan resources bawaan google-services.json
        val defaultApp = FirebaseApp.initializeApp(appContext)
        if (defaultApp != null) {
          Log.i(TAG, "FirebaseApp berhasil diinisialisasi secara otomatis melalui google-services resources.")
          return true
        }

        // 3. Fallback: Inisialisasi eksplisit menggunakan FirebaseOptions dari konfigurasi proyek yang valid
        val options = FirebaseOptions.Builder()
          .setApplicationId(FIREBASE_APP_ID)
          .setApiKey(FIREBASE_API_KEY)
          .setProjectId(FIREBASE_PROJECT_ID)
          .setStorageBucket(FIREBASE_STORAGE_BUCKET)
          .setGcmSenderId(FIREBASE_GCM_SENDER_ID)
          .build()

        FirebaseApp.initializeApp(appContext, options)
        Log.i(TAG, "FirebaseApp berhasil diinisialisasi melalui FirebaseOptions fallback.")
        return true
      } catch (e: Exception) {
        // Tangani error inisialisasi dengan aman tanpa memicu crash aplikasi
        Log.w(TAG, "Inisialisasi FirebaseApp gagal atau dilewati: ${e.message}")
        return false
      }
    }

    /**
     * Memeriksa apakah FirebaseApp tersedia untuk digunakan oleh layanan seperti Remote Config.
     */
    fun isFirebaseAvailable(context: Context): Boolean {
      return try {
        ensureFirebaseInitialized(context)
        FirebaseApp.getApps(context).isNotEmpty()
      } catch (e: Exception) {
        false
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    try {
      val initialized = ensureFirebaseInitialized(this)
      Log.d(TAG, "Inisialisasi awal JadiStrukApp selesai. Firebase aktif: $initialized")
    } catch (e: Exception) {
      Log.e(TAG, "Error tidak terduga saat inisialisasi JadiStrukApp: ${e.message}", e)
    }
  }
}
