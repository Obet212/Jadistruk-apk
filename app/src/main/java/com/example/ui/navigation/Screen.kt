package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Home : Screen(
        route = "home",
        title = "Beranda",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    )

    data object Scan : Screen(
        route = "scan",
        title = "Terima Share",
        selectedIcon = Icons.Filled.Share,
        unselectedIcon = Icons.Outlined.Share,
    )

    data object Templates : Screen(
        route = "templates",
        title = "Template & Kolom",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
    )

    data object History : Screen(
        route = "history",
        title = "Riwayat Share",
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
    )

    data object Settings : Screen(
        route = "settings",
        title = "Pengaturan",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    )

    data object Detail : Screen(
        route = "detail",
        title = "Preview Struk",
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description,
    )

    data object TemplateEditor : Screen(
        route = "template_editor",
        title = "Kalibrasi OCR",
        selectedIcon = Icons.Filled.CropFree,
        unselectedIcon = Icons.Outlined.CropFree,
    )

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Home, Scan, Templates, History, Settings)
    }
}
