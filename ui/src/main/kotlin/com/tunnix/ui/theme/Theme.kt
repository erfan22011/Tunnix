package com.tunnix.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.ThemeMode

data class LiquidGlassColors(
    val background: Color,
    val surface: Color,
    val cardGlass: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentGlow: Color,
    val isDark: Boolean
)

val LocalLiquidGlass = compositionLocalOf {
    LiquidGlassColors(
        background = ObsidianBackground,
        surface = ObsidianSurface,
        cardGlass = ObsidianCardGlass,
        glassBorder = ObsidianGlassBorder,
        textPrimary = ObsidianTextPrimary,
        textSecondary = ObsidianTextSecondary,
        textMuted = ObsidianTextMuted,
        accent = EmeraldGreen,
        accentGlow = EmeraldGlow,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = EmeraldLight,
    background = ObsidianBackground,
    surface = ObsidianSurface,
    onPrimary = Color.Black,
    onBackground = ObsidianTextPrimary,
    onSurface = ObsidianTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    secondary = EmeraldLight,
    background = PearlBackground,
    surface = PearlSurface,
    onPrimary = Color.White,
    onBackground = PearlTextPrimary,
    onSurface = PearlTextPrimary
)

@Composable
fun TunnixTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    language: AppLanguage = AppLanguage.PERSIAN,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val liquidGlassColors = if (isDark) {
        LiquidGlassColors(
            background = ObsidianBackground,
            surface = ObsidianSurface,
            cardGlass = ObsidianCardGlass,
            glassBorder = ObsidianGlassBorder,
            textPrimary = ObsidianTextPrimary,
            textSecondary = ObsidianTextSecondary,
            textMuted = ObsidianTextMuted,
            accent = EmeraldGreen,
            accentGlow = EmeraldGlow,
            isDark = true
        )
    } else {
        LiquidGlassColors(
            background = PearlBackground,
            surface = PearlSurface,
            cardGlass = PearlCardGlass,
            glassBorder = PearlGlassBorder,
            textPrimary = PearlTextPrimary,
            textSecondary = PearlTextSecondary,
            textMuted = PearlTextMuted,
            accent = EmeraldGreen,
            accentGlow = EmeraldGlow,
            isDark = false
        )
    }

    val layoutDirection = if (language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = findActivity(view.context)
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                val barColor = if (isDark) ObsidianBackground.toArgb() else PearlBackground.toArgb()

                window.statusBarColor = barColor
                window.navigationBarColor = barColor

                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalLiquidGlass provides liquidGlassColors,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            content = content
        )
    }
}

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

object TunnixStyle {
    val glass: LiquidGlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLiquidGlass.current
}
