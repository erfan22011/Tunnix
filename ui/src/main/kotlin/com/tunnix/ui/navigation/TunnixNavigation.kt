package com.tunnix.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.ThemeMode
import com.tunnix.core.models.TrafficStats
import com.tunnix.core.models.VpnState
import com.tunnix.ui.screens.MainScreen
import com.tunnix.ui.screens.RedeemScreen
import com.tunnix.ui.screens.SettingsScreen
import com.tunnix.ui.theme.TunnixStyle
import com.tunnix.ui.theme.TunnixTheme

@Composable
fun TunnixNavigation(
    language: AppLanguage = AppLanguage.PERSIAN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeChange: (ThemeMode) -> Unit = {},
    vpnState: VpnState = VpnState.Disconnected,
    trafficStats: TrafficStats = TrafficStats(),
    servers: List<ServerConfig> = emptyList(),
    selectedServer: ServerConfig? = null,
    onSelectServer: (ServerConfig) -> Unit = {},
    onToggleConnection: () -> Unit = {},
    onSubmitSubscription: (String) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    downloadSettings: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT,
    onSaveDownloadSettings: (DownloadSettingsConfig) -> Unit = {},
    onChangeSubscription: () -> Unit = {},
    hasSubscription: Boolean = false,
    bypassIran: Boolean = true,
    onBypassIranChange: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    val startDestination = if (hasSubscription) "main" else "redeem"

    // Synchronize navigation if subscription status changes externally
    LaunchedEffect(hasSubscription) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (hasSubscription && currentRoute == "redeem") {
            navController.navigate("main") {
                popUpTo("redeem") { inclusive = true }
            }
        } else if (!hasSubscription && currentRoute == "main") {
            navController.navigate("redeem") {
                popUpTo("main") { inclusive = true }
            }
        }
    }

    TunnixTheme(themeMode = themeMode, language = language) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .background(TunnixStyle.glass.background),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(240, easing = FastOutSlowInEasing),
                    initialOffset = { it / 6 }
                ) + fadeIn(tween(240, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(240, easing = FastOutSlowInEasing),
                    targetOffset = { it / 6 }
                ) + fadeOut(tween(200, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(240, easing = FastOutSlowInEasing),
                    initialOffset = { it / 6 }
                ) + fadeIn(tween(240, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(240, easing = FastOutSlowInEasing),
                    targetOffset = { it / 6 }
                ) + fadeOut(tween(200, easing = FastOutSlowInEasing))
            }
        ) {
            composable("redeem") {
                RedeemScreen(
                    currentLanguage = language,
                    onLanguageChange = onLanguageChange,
                    currentTheme = themeMode,
                    onThemeToggle = {
                        val next = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                        onThemeChange(next)
                    },
                    onSubmitSubscription = { input ->
                        onSubmitSubscription(input)
                    },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }

            composable("main") {
                MainScreen(
                    vpnState = vpnState,
                    trafficStats = trafficStats,
                    servers = servers,
                    selectedServer = selectedServer,
                    onSelectServer = onSelectServer,
                    onToggleConnection = onToggleConnection,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onChangeSubscription = {
                        onChangeSubscription()
                    },
                    currentLanguage = language
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentLanguage = language,
                    onLanguageChange = onLanguageChange,
                    currentTheme = themeMode,
                    onThemeChange = onThemeChange,
                    downloadSettings = downloadSettings,
                    onSaveDownloadSettings = onSaveDownloadSettings,
                    bypassIran = bypassIran,
                    onBypassIranChange = onBypassIranChange,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
