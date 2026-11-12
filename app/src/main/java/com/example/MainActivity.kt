package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.Routes
import com.example.ui.screens.CalibrationScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiptPreviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SourceAppsScreen
import com.example.ui.screens.TemplateEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val controller = rememberNavController()
                    navController = controller

                    AppNavHost(
                        navController = controller,
                        viewModel = viewModel
                    )

                    // Check if intent contains shared image
                    LaunchedEffect(Unit) {
                        handleIncomingIntent(intent)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if ((Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action) && type?.startsWith("image/") == true) {
            val imageUri: Uri? = if (Intent.ACTION_SEND == action) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } else {
                // SEND_MULTIPLE: take the first image
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.firstOrNull()
            } ?: intent.clipData?.getItemAt(0)?.uri

            if (imageUri != null) {
                // Try to identify calling package name
                val callingPkg = detectSourcePackage(intent)
                viewModel.processIncomingImage(imageUri, callingPkg)
                navController?.navigate(Routes.PREVIEW) {
                    launchSingleTop = true
                }
            }
        }
    }

    private fun detectSourcePackage(intent: Intent): String? {
        // 1. Check callingPackage
        callingPackage?.let { return it }

        // 2. Check referrer URI (e.g., android-app://com.bca)
        try {
            val referrer = referrer
            if (referrer != null && referrer.scheme == "android-app") {
                return referrer.authority ?: referrer.host
            }
        } catch (_: Exception) {}

        // 3. Check extra referrer strings
        val extraRef = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
        if (!extraRef.isNullOrBlank()) {
            return extraRef.removePrefix("android-app://")
        }

        return null
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToPreview = { navController.navigate(Routes.PREVIEW) },
                onNavigateToCalibration = { navController.navigate(Routes.CALIBRATION) },
                onNavigateToSourceApps = { navController.navigate(Routes.SOURCE_APPS) },
                onNavigateToTemplateEditor = { navController.navigate(Routes.TEMPLATE_EDITOR) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.PREVIEW) {
            ReceiptPreviewScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCalibration = { navController.navigate(Routes.CALIBRATION) },
                onNavigateToTemplateSettings = { navController.navigate(Routes.TEMPLATE_EDITOR) }
            )
        }

        composable(Routes.CALIBRATION) {
            CalibrationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SOURCE_APPS) {
            SourceAppsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenTemplate = { sourceApp ->
                    viewModel.switchActiveSourceApp(sourceApp)
                    navController.navigate(Routes.TEMPLATE_EDITOR)
                }
            )
        }

        composable(Routes.TEMPLATE_EDITOR) {
            TemplateEditorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCalibration = { navController.navigate(Routes.CALIBRATION) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenReceipt = { history ->
                    viewModel.loadReceiptFromHistory(history)
                    navController.navigate(Routes.PREVIEW)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
