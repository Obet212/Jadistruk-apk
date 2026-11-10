package com.example.data.model

import androidx.compose.ui.graphics.Color

/**
 * Pilihan tema antarmuka (UI Theme) aplikasi JADISTRUK.
 * Mengubah palet warna, latar belakang, kartu, tombol, teks, dan aksen visual
 * secara menyeluruh di seluruh aplikasi secara konsisten dan real-time.
 */
enum class AppTheme(
  val id: String,
  val displayName: String,
  val subtitle: String,
  val primaryColor: Color,
  val surfaceColor: Color,
  val backgroundColor: Color,
  val accentColor: Color,
  val isDark: Boolean = false
) {
  LIGHT_MINIMALIS(
    id = "light_minimalis",
    displayName = "Light Minimalis",
    subtitle = "Putih bersih dan sederhana",
    primaryColor = Color(0xFF1E293B),
    surfaceColor = Color(0xFFFFFFFF),
    backgroundColor = Color(0xFFF8FAFC),
    accentColor = Color(0xFF64748B),
    isDark = false
  ),
  DARK_MODE(
    id = "dark_mode",
    displayName = "Dark Mode",
    subtitle = "Gelap elegan dan nyaman di malam hari",
    primaryColor = Color(0xFF60A5FA),
    surfaceColor = Color(0xFF1E293B),
    backgroundColor = Color(0xFF0F172A),
    accentColor = Color(0xFF38BDF8),
    isDark = true
  ),
  OCEAN_BLUE(
    id = "ocean_blue",
    displayName = "Ocean Blue",
    subtitle = "Biru modern dan profesional",
    primaryColor = Color(0xFF0284C7),
    surfaceColor = Color(0xFFFFFFFF),
    backgroundColor = Color(0xFFF0F9FF),
    accentColor = Color(0xFF0D9488),
    isDark = false
  ),
  EMERALD_GREEN(
    id = "emerald_green",
    displayName = "Emerald Green",
    subtitle = "Hijau segar dan menenangkan",
    primaryColor = Color(0xFF059669),
    surfaceColor = Color(0xFFFFFFFF),
    backgroundColor = Color(0xFFF0FDF4),
    accentColor = Color(0xFF10B981),
    isDark = false
  ),
  LAVENDER(
    id = "lavender",
    displayName = "Lavender",
    subtitle = "Ungu lembut dan elegan",
    primaryColor = Color(0xFF7C3AED),
    surfaceColor = Color(0xFFFFFFFF),
    backgroundColor = Color(0xFFFAF5FF),
    accentColor = Color(0xFF8B5CF6),
    isDark = false
  ),
  SUNSET_WARM(
    id = "sunset_warm",
    displayName = "Sunset Warm",
    subtitle = "Perpaduan warna hangat nyaman dilihat",
    primaryColor = Color(0xFFEA580C),
    surfaceColor = Color(0xFFFFFFFF),
    backgroundColor = Color(0xFFFFF7ED),
    accentColor = Color(0xFFD97706),
    isDark = false
  ),
  GLASS_MODERN(
    id = "glass_modern",
    displayName = "Glass Modern",
    subtitle = "Modern dengan aksen halus & elegan",
    primaryColor = Color(0xFF2563EB),
    surfaceColor = Color(0xFFFFFFFF),
    backgroundColor = Color(0xFFF1F5F9),
    accentColor = Color(0xFF06B6D4),
    isDark = false
  );

  companion object {
    fun fromId(id: String?): AppTheme {
      return entries.find { it.id.equals(id, ignoreCase = true) } ?: OCEAN_BLUE
    }
  }
}
