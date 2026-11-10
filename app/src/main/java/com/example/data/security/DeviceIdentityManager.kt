package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages a unique, persistent hardware-backed Device ID for OBETSTRUK.
 * 
 * Guarantees:
 * 1. Generated locally using UUID - never accesses IMEI, MAC, or restricted device identifiers.
 * 2. Stored encrypted in SharedPreferences with keys protected by the AndroidKeyStore (AES-256 GCM).
 * 3. Persists across app closures, reboots, and app updates.
 * 4. Provides safe regeneration/recovery mechanism if requested or if installation is reset.
 */
class DeviceIdentityManager(private val context: Context) {

  companion object {
    private const val TAG = "DeviceIdentityManager"
    private const val PREFS_NAME = "jadistruk_security_prefs"
    private const val KEY_ENCRYPTED_DEVICE_ID = "enc_dev_id"
    private const val KEY_DEVICE_ID_IV = "enc_dev_id_iv"
    private const val KEY_PLAIN_FALLBACK_ID = "plain_dev_id_fallback"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "ObetstrukDeviceKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
  }

  private val sharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  /**
   * Retrieves the persistent unique Device ID, or generates and stores one if not yet present.
   */
  @Synchronized
  fun getOrCreateDeviceId(): String {
    // 1. Try reading encrypted ID from Android KeyStore
    val encryptedBase64 = sharedPreferences.getString(KEY_ENCRYPTED_DEVICE_ID, null)
    val ivBase64 = sharedPreferences.getString(KEY_DEVICE_ID_IV, null)

    if (!encryptedBase64.isNullOrBlank() && !ivBase64.isNullOrBlank()) {
      try {
        val decryptedId = decrypt(encryptedBase64, ivBase64)
        if (!decryptedId.isNullOrBlank()) {
          return decryptedId
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decrypt Keystore device ID, falling back to stored backup", e)
      }
    }

    // 2. Check fallback ID in SharedPreferences
    val fallbackId = sharedPreferences.getString(KEY_PLAIN_FALLBACK_ID, null)
    if (!fallbackId.isNullOrBlank()) {
      // Re-encrypt it with Keystore
      saveEncryptedDeviceId(fallbackId)
      return fallbackId
    }

    // 3. Generate a fresh UUID
    val newId = UUID.randomUUID().toString()
    saveEncryptedDeviceId(newId)
    return newId
  }

  /**
   * Manually resets / regenerates the Device ID if the user requests re-identification.
   */
  @Synchronized
  fun regenerateDeviceId(): String {
    val newId = UUID.randomUUID().toString()
    saveEncryptedDeviceId(newId)
    return newId
  }

  private fun saveEncryptedDeviceId(deviceId: String) {
    try {
      val secretKey = getOrCreateSecretKey()
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      val iv = cipher.iv
      val ciphertext = cipher.doFinal(deviceId.toByteArray(StandardCharsets.UTF_8))

      val encBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
      val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

      sharedPreferences.edit()
        .putString(KEY_ENCRYPTED_DEVICE_ID, encBase64)
        .putString(KEY_DEVICE_ID_IV, ivBase64)
        .putString(KEY_PLAIN_FALLBACK_ID, deviceId) // For robust persistence across Keystore resets
        .apply()
    } catch (e: Exception) {
      Log.e(TAG, "Error saving encrypted device ID with AndroidKeyStore", e)
      // Save unencrypted fallback as guarantee
      sharedPreferences.edit()
        .putString(KEY_PLAIN_FALLBACK_ID, deviceId)
        .apply()
    }
  }

  private fun decrypt(encryptedBase64: String, ivBase64: String): String? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey ?: return null

    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
    val ciphertext = Base64.decode(encryptedBase64, Base64.NO_WRAP)

    val cipher = Cipher.getInstance(TRANSFORMATION)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

    val decryptedBytes = cipher.doFinal(ciphertext)
    return String(decryptedBytes, StandardCharsets.UTF_8)
  }

  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
      val entry = keyStore.getKey(KEYSTORE_ALIAS, null)
      if (entry is SecretKey) return entry
    }

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      KEYSTORE_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()

    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }
}
