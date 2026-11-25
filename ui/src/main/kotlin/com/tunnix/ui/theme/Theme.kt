package com.tunnix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

object TunnixStyle {
    val glass: LiquidGlassColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLiquidGlass.current
}
