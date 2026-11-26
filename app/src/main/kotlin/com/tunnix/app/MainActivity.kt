package com.tunnix.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.tunnix.core.models.ThemeMode
import com.tunnix.settings.AppSettingsDataStore
import com.tunnix.ui.navigation.TunnixNavigation
import com.tunnix.ui.theme.TunnixStyle
import com.tunnix.ui.theme.TunnixTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set native window canvas and system bars dynamically before setContent
        // to prevent any black/white flash during cold and warm startup
        val cachedTheme = AppSettingsDataStore.getCachedThemeMode(this)
        val isDark = cachedTheme == ThemeMode.DARK
        val bgArgb = if (isDark) 0xFF0B0E14.toInt() else 0xFFF8FAFC.toInt()
        window.decorView.setBackgroundColor(bgArgb)
        window.statusBarColor = bgArgb
        window.navigationBarColor = bgArgb

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                val appearance = if (!isDark) {
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else {
                    0
                }
                controller.setSystemBarsAppearance(
                    appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* granted or denied */ }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    viewModel.toggleConnection(this)
                }
            }

            TunnixTheme(
                themeMode = uiState.themeMode,
                language = uiState.language
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TunnixStyle.glass.background
                ) {
                    if (uiState.isInitialized) {
                        TunnixNavigation(
                            language = uiState.language,
                            onLanguageChange = { viewModel.changeLanguage(it) },
                            themeMode = uiState.themeMode,
                            onThemeChange = { viewModel.changeTheme(it) },
                            vpnState = uiState.vpnState,
                            trafficStats = uiState.trafficStats,
                            servers = uiState.servers,
                            selectedServer = uiState.selectedServer,
                            onSelectServer = { viewModel.selectServer(it) },
                            onToggleConnection = {
                                val vpnIntent = viewModel.checkVpnPermission(this)
                                if (vpnIntent != null) {
                                    vpnPermissionLauncher.launch(vpnIntent)
                                } else {
                                    viewModel.toggleConnection(this)
                                }
                            },
                            onSubmitSubscription = { urlOrToken ->
                                viewModel.loadSubscription(urlOrToken)
                            },
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            downloadSettings = uiState.downloadSettings,
                            onSaveDownloadSettings = { viewModel.saveDownloadSettings(it) },
                            onChangeSubscription = { viewModel.clearSubscription() },
                            hasSubscription = uiState.hasSubscription,
                            bypassIran = uiState.bypassIran,
                            onBypassIranChange = { viewModel.changeBypassIran(it) }
                        )
                    }
                }
            }
        }
    }
}
