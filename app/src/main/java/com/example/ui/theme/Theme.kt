package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppTheme

/**
 * Menghasilkan Material 3 ColorScheme terpadu untuk setiap tema aplikasi yang dipilih.
 * Memastikan kontras tinggi, kenyamanan visual, dan konsistensi di seluruh halaman.
 */
fun getAppColorScheme(theme: AppTheme): ColorScheme {
  return when (theme) {
    AppTheme.LIGHT_MINIMALIS -> lightColorScheme(
      primary = Color(0xFF1E293B),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFE2E8F0),
      onPrimaryContainer = Color(0xFF0F172A),
      secondary = Color(0xFF475569),
      onSecondary = Color(0xFFFFFFFF),
      secondaryContainer = Color(0xFFF1F5F9),
      onSecondaryContainer = Color(0xFF1E293B),
      background = Color(0xFFF8FAFC),
      onBackground = Color(0xFF0F172A),
      surface = Color(0xFFFFFFFF),
      onSurface = Color(0xFF0F172A),
      surfaceVariant = Color(0xFFF1F5F9),
      onSurfaceVariant = Color(0xFF475569),
      outline = Color(0xFFCBD5E1),
      outlineVariant = Color(0xFFE2E8F0)
    )

    AppTheme.DARK_MODE -> darkColorScheme(
      primary = Color(0xFF60A5FA),
      onPrimary = Color(0xFF0B192C),
      primaryContainer = Color(0xFF1E3A8A),
      onPrimaryContainer = Color(0xFFDBEAFE),
      secondary = Color(0xFF38BDF8),
      onSecondary = Color(0xFF0C4A6E),
      secondaryContainer = Color(0xFF1E293B),
      onSecondaryContainer = Color(0xFFE2E8F0),
      background = Color(0xFF0B0F19),
      onBackground = Color(0xFFF8FAFC),
      surface = Color(0xFF151C2C),
      onSurface = Color(0xFFF8FAFC),
      surfaceVariant = Color(0xFF1E293B),
      onSurfaceVariant = Color(0xFF94A3B8),
      outline = Color(0xFF334155),
      outlineVariant = Color(0xFF1E293B)
    )

    AppTheme.OCEAN_BLUE -> lightColorScheme(
      primary = Color(0xFF0284C7),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFE0F2FE),
      onPrimaryContainer = Color(0xFF0369A1),
      secondary = Color(0xFF0EA5E9),
      onSecondary = Color(0xFFFFFFFF),
      secondaryContainer = Color(0xFFBAE6FD),
      onSecondaryContainer = Color(0xFF075985),
      background = Color(0xFFF0F9FF),
      onBackground = Color(0xFF0C4A6E),
      surface = Color(0xFFFFFFFF),
      onSurface = Color(0xFF0C4A6E),
      surfaceVariant = Color(0xFFE0F2FE),
      onSurfaceVariant = Color(0xFF0369A1),
      outline = Color(0xFF7DD3FC),
      outlineVariant = Color(0xFFBAE6FD)
    )

    AppTheme.EMERALD_GREEN -> lightColorScheme(
      primary = Color(0xFF059669),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFD1FAE5),
      onPrimaryContainer = Color(0xFF065F46),
      secondary = Color(0xFF10B981),
      onSecondary = Color(0xFFFFFFFF),
      secondaryContainer = Color(0xFFA7F3D0),
      onSecondaryContainer = Color(0xFF064E3B),
      background = Color(0xFFF0FDF4),
      onBackground = Color(0xFF064E3B),
      surface = Color(0xFFFFFFFF),
      onSurface = Color(0xFF064E3B),
      surfaceVariant = Color(0xFFD1FAE5),
      onSurfaceVariant = Color(0xFF047857),
      outline = Color(0xFF6EE7B7),
      outlineVariant = Color(0xFFA7F3D0)
    )

    AppTheme.LAVENDER -> lightColorScheme(
      primary = Color(0xFF7C3AED),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFEDE9FE),
      onPrimaryContainer = Color(0xFF5B21B6),
      secondary = Color(0xFF8B5CF6),
      onSecondary = Color(0xFFFFFFFF),
      secondaryContainer = Color(0xFFDDD6FE),
      onSecondaryContainer = Color(0xFF4C1D95),
      background = Color(0xFFFAF5FF),
      onBackground = Color(0xFF3B0764),
      surface = Color(0xFFFFFFFF),
      onSurface = Color(0xFF3B0764),
      surfaceVariant = Color(0xFFEDE9FE),
      onSurfaceVariant = Color(0xFF6D28D9),
      outline = Color(0xFFC4B5FD),
      outlineVariant = Color(0xFFDDD6FE)
    )

    AppTheme.SUNSET_WARM -> lightColorScheme(
      primary = Color(0xFFEA580C),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFFFEDD5),
      onPrimaryContainer = Color(0xFF9A3412),
      secondary = Color(0xFFD97706),
      onSecondary = Color(0xFFFFFFFF),
      secondaryContainer = Color(0xFFFED7AA),
      onSecondaryContainer = Color(0xFF7C2D12),
      background = Color(0xFFFFF7ED),
      onBackground = Color(0xFF431407),
      surface = Color(0xFFFFFFFF),
      onSurface = Color(0xFF431407),
      surfaceVariant = Color(0xFFFFEDD5),
      onSurfaceVariant = Color(0xFFC2410C),
      outline = Color(0xFFFDBA74),
      outlineVariant = Color(0xFFFED7AA)
    )

    AppTheme.GLASS_MODERN -> lightColorScheme(
      primary = Color(0xFF2563EB),
      onPrimary = Color(0xFFFFFFFF),
      primaryContainer = Color(0xFFEFF6FF),
      onPrimaryContainer = Color(0xFF1D4ED8),
      secondary = Color(0xFF06B6D4),
      onSecondary = Color(0xFFFFFFFF),
      secondaryContainer = Color(0xFFE0F2FE),
      onSecondaryContainer = Color(0xFF0E7490),
      background = Color(0xFFF1F5F9),
      onBackground = Color(0xFF0F172A),
      surface = Color(0xFFFFFFFF),
      onSurface = Color(0xFF0F172A),
      surfaceVariant = Color(0xFFE2E8F0),
      onSurfaceVariant = Color(0xFF334155),
      outline = Color(0xFFCBD5E1),
      outlineVariant = Color(0xFFE2E8F0)
    )
  }
}

@Composable
fun MyApplicationTheme(
  appTheme: AppTheme = AppTheme.OCEAN_BLUE,
  content: @Composable () -> Unit,
) {
  val colorScheme = getAppColorScheme(appTheme)
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
