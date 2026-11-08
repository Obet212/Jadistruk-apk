package com.example.data.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import android.widget.Toast

/**
 * Manages screen protection using Android's FLAG_SECURE.
 * 
 * Functions:
 * 1. Enables FLAG_SECURE on the current window to prevent system screenshots, screen recordings,
 *    and app previews in the Android Recent Apps / Overview switcher.
 * 2. On Android 14+ (API 34+), registers ScreenCaptureCallback to alert the user with a friendly
 *    message if a screenshot attempt is made.
 * 3. Does NOT interfere with Bluetooth printing or user-initiated explicit receipt sharing.
 */
object ScreenProtectionManager {

  /**
   * Applies window protection flags to prevent screenshots and screen capture.
   */
  fun applyProtection(activity: Activity, onScreenshotAttempted: (() -> Unit)? = null) {
    try {
      // Prevent screenshots, screen recording, and task switcher thumbnail leaks
      activity.window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
      )

      // Android 14+ (API 34+) screen capture callback for friendly notification
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val callback = Activity.ScreenCaptureCallback {
          activity.runOnUiThread {
            Toast.makeText(
              activity,
              "Tangkapan layar diblokir demi keamanan data sensitif transaksi.",
              Toast.LENGTH_LONG
            ).show()
            onScreenshotAttempted?.invoke()
          }
        }
        activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
      }
    } catch (e: Exception) {
      // In case of restricted window environments
    }
  }
}
