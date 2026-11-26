package com.tunnix.app

import android.app.Activity
import android.os.Bundle
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
import com.tunnix.ui.navigation.TunnixNavigation
import com.tunnix.ui.theme.TunnixStyle
import com.tunnix.ui.theme.TunnixTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()

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
                    hasSubscription = uiState.hasSubscription
                )
            }
        }
    }
}
}
