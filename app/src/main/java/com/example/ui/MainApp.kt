package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.Screen
import com.example.ui.screens.detail.ReceiptDetailScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.scan.ScanScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.templates.TemplatesScreen
import com.example.ui.viewmodel.ReceiptViewModel

@Composable
fun MainApp(
  viewModel: ReceiptViewModel = viewModel(),
) {
  var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
  var previousScreen by remember { mutableStateOf<Screen>(Screen.Home) }

  val ocrState by viewModel.ocrState.collectAsState()
  val currentReceipt by viewModel.currentReceipt.collectAsState()
  val currentImageUri by viewModel.currentImageUri.collectAsState()
  val savedReceipts by viewModel.savedReceipts.collectAsState()
  val ocrTemplates by viewModel.ocrTemplates.collectAsState()
  val receiptCount by viewModel.receiptCount.collectAsState()
  val totalAmountSum by viewModel.totalAmountSum.collectAsState()
  val settings by viewModel.settings.collectAsState()
  val appUpdateStatus by viewModel.appUpdateStatus.collectAsState()

  var templateToEdit by remember { mutableStateOf<com.example.data.model.OcrTemplate?>(null) }
  var templateSampleImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

  // Handle system back navigation if inside detail screen or template editor
  if (currentScreen == Screen.Detail) {
    BackHandler {
      currentScreen = previousScreen
    }
  } else if (currentScreen == Screen.TemplateEditor) {
    BackHandler {
      currentScreen = Screen.Templates
    }
  } else if (currentScreen != Screen.Home) {
    BackHandler {
      currentScreen = Screen.Home
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (currentScreen != Screen.Detail && currentScreen != Screen.TemplateEditor) {
        val navItems = remember {
          listOf(Screen.Home, Screen.Scan, Screen.Templates, Screen.History, Screen.Settings)
        }
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          modifier = Modifier.testTag("bottom_nav_bar")
        ) {
          navItems.forEach { screen ->
            val isSelected = currentScreen.route == screen.route
            NavigationBarItem(
              selected = isSelected,
              onClick = {
                currentScreen = screen
              },
              icon = {
                Icon(
                  imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                  contentDescription = screen.title
                )
              },
              label = {
                Text(screen.title)
              },
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
              ),
              modifier = Modifier.testTag("nav_item_${screen.route}")
            )
          }
        }
      }
    }
  ) { innerPadding ->
    androidx.compose.foundation.layout.Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(
          bottom = if (currentScreen == Screen.TemplateEditor) 0.dp else innerPadding.calculateBottomPadding()
        )
    ) {
      when (currentScreen) {
        Screen.Home -> {
          HomeScreen(
            receiptCount = receiptCount,
            totalAmountSum = totalAmountSum,
            recentReceipts = savedReceipts,
            onImageSelected = { uri ->
              viewModel.processImageUri(uri)
              previousScreen = Screen.Home
              currentScreen = Screen.Scan
            },
            onNavigateToScan = {
              currentScreen = Screen.Scan
            },
            onNavigateToHistory = {
              currentScreen = Screen.History
            },
            onReceiptClicked = { receipt ->
              viewModel.selectReceiptForDetail(receipt)
              previousScreen = Screen.Home
              currentScreen = Screen.Detail
            }
          )
        }

        Screen.Scan -> {
          ScanScreen(
            ocrState = ocrState,
            currentReceipt = currentReceipt,
            currentImageUri = currentImageUri,
            onProcessImageUri = { uri ->
              viewModel.processImageUri(uri)
            },
            onUpdateReceipt = { updated ->
              viewModel.updateCurrentReceipt(updated)
            },
            onSaveReceipt = { onSaved ->
              viewModel.saveCurrentReceipt(onSaved)
            },
            onViewPrintPreview = {
              previousScreen = Screen.Scan
              currentScreen = Screen.Detail
            },
            onResetScan = {
              viewModel.resetScanState()
            },
            availableTemplates = ocrTemplates,
            onApplyTemplate = { tpl ->
              viewModel.applyTemplate(tpl)
            },
            printSettings = settings
          )
        }

        Screen.Templates -> {
          TemplatesScreen(
            templates = ocrTemplates,
            onOpenTemplateEditor = { tpl, uri ->
              templateToEdit = tpl
              templateSampleImageUri = uri
              previousScreen = Screen.Templates
              currentScreen = Screen.TemplateEditor
            },
            onSaveTemplate = { newTpl ->
              viewModel.saveTemplate(newTpl)
            },
            onDeleteTemplate = { tpl ->
              viewModel.deleteTemplate(tpl)
            }
          )
        }

        Screen.History -> {
          HistoryScreen(
            receipts = savedReceipts,
            onReceiptClick = { receipt ->
              viewModel.selectReceiptForDetail(receipt)
              previousScreen = Screen.History
              currentScreen = Screen.Detail
            },
            onDeleteReceipt = { receiptId ->
              viewModel.deleteReceipt(receiptId)
            }
          )
        }

        Screen.Settings -> {
          SettingsScreen(
            currentSettings = settings,
            onSaveSettings = { newSettings ->
              viewModel.updateSettings(newSettings)
            },
            getPairedPrinters = {
              viewModel.getPairedPrinters()
            },
            onTestPrint = { onResult ->
              viewModel.testPrint(onResult)
            },
            appUpdateStatus = appUpdateStatus,
            onCheckForUpdate = {
              viewModel.checkForAppUpdate()
            },
            backupManager = viewModel.backupManager
          )
        }

        Screen.Detail -> {
          ReceiptDetailScreen(
            receipt = currentReceipt,
            settings = settings,
            onBack = {
              currentScreen = previousScreen
            },
            onPrintBluetooth = { r, onResult ->
              viewModel.printReceipt(r, onResult)
            },
            onTestPrint = { onResult ->
              viewModel.testPrint(onResult)
            },
            onSaveSettings = { newSettings ->
              viewModel.updateSettings(newSettings)
            },
            getPairedPrinters = {
              viewModel.getPairedPrinters()
            },
            printerConnectionState = viewModel.printerConnectionState,
            isBluetoothSupported = { viewModel.isBluetoothSupported() },
            isBluetoothEnabled = { viewModel.isBluetoothEnabled() },
            hasBluetoothPermission = { viewModel.hasBluetoothPermission() }
          )
        }

        Screen.TemplateEditor -> {
          com.example.ui.screens.templates.TemplateEditorScreen(
            initialTemplate = templateToEdit,
            initialImageUri = templateSampleImageUri,
            textRecognizer = viewModel.textRecognizer,
            onSaveTemplate = { savedTpl ->
              viewModel.saveTemplate(savedTpl)
            },
            onBack = {
              currentScreen = Screen.Templates
            }
          )
        }
      }
    }
  }
}
