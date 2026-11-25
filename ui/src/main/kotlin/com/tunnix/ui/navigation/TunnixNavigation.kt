package com.tunnix.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.ThemeMode
import com.tunnix.core.models.TrafficStats
import com.tunnix.core.models.VpnState
import com.tunnix.ui.screens.MainScreen
import com.tunnix.ui.screens.RedeemScreen
import com.tunnix.ui.screens.SettingsScreen
import com.tunnix.ui.theme.TunnixTheme

@Composable
fun TunnixNavigation(
    initialLanguage: AppLanguage = AppLanguage.PERSIAN,
    initialTheme: ThemeMode = ThemeMode.DARK,
    initialSubscriptionUrl: String? = null
) {
    val navController = rememberNavController()

    var language by remember { mutableStateOf(initialLanguage) }
    var themeMode by remember { mutableStateOf(initialTheme) }
    var downloadSettings by remember { mutableStateOf(DownloadSettingsConfig.DEFAULT) }

    var vpnState by remember { mutableStateOf<VpnState>(VpnState.Disconnected) }
    var trafficStats by remember { mutableStateOf(TrafficStats()) }

    // Sample default servers for UI demonstration before Phase 3 parser integrates live subs
    var servers by remember {
        mutableStateOf(
            listOf(
                ServerConfig(
                    id = "nl-1",
                    name = "Netherlands",
                    address = "ovv.pelqexa.ir",
                    port = 2053,
                    protocol = ProtocolType.VLESS,
                    network = NetworkType.XHTTP,
                    countryCode = "NL"
                ),
                ServerConfig(
                    id = "nl-2",
                    name = "Netherlands 2",
                    address = "ovv.pelqexa.ir",
                    port = 2053,
                    protocol = ProtocolType.VLESS,
                    network = NetworkType.XHTTP,
                    countryCode = "NL"
                ),
                ServerConfig(
                    id = "ch-1",
                    name = "Swiss",
                    address = "ovv.genus.ir",
                    port = 2053,
                    protocol = ProtocolType.VLESS,
                    network = NetworkType.XHTTP,
                    countryCode = "CH"
                ),
                ServerConfig(
                    id = "ch-2",
                    name = "Swiss 2",
                    address = "ovv.genus.ir",
                    port = 2053,
                    protocol = ProtocolType.VLESS,
                    network = NetworkType.XHTTP,
                    countryCode = "CH"
                )
            )
        )
    }

    var selectedServer by remember { mutableStateOf<ServerConfig?>(servers.firstOrNull()) }

    val startDestination = if (initialSubscriptionUrl.isNullOrBlank()) "redeem" else "main"

    TunnixTheme(themeMode = themeMode, language = language) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) }
        ) {
            composable("redeem") {
                RedeemScreen(
                    currentLanguage = language,
                    onLanguageChange = { language = it },
                    currentTheme = themeMode,
                    onThemeToggle = {
                        themeMode = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                    },
                    onSubmitSubscription = { validUrl ->
                        navController.navigate("main") {
                            popUpTo("redeem") { inclusive = true }
                        }
                    }
                )
            }

            composable("main") {
                MainScreen(
                    vpnState = vpnState,
                    trafficStats = trafficStats,
                    servers = servers,
                    selectedServer = selectedServer,
                    onSelectServer = { selectedServer = it },
                    onToggleConnection = {
                        // Toggle mock UI state for testing
                        vpnState = when (vpnState) {
                            is VpnState.Disconnected -> VpnState.Connecting
                            is VpnState.Connecting -> VpnState.Disconnected
                            is VpnState.Connected -> VpnState.Disconnected
                            is VpnState.Error -> VpnState.Connecting
                            is VpnState.Reconnecting -> VpnState.Disconnected
                        }
                    },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onChangeSubscription = {
                        navController.navigate("redeem") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    currentLanguage = language
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentLanguage = language,
                    onLanguageChange = { language = it },
                    currentTheme = themeMode,
                    onThemeChange = { themeMode = it },
                    downloadSettings = downloadSettings,
                    onSaveDownloadSettings = { downloadSettings = it },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
