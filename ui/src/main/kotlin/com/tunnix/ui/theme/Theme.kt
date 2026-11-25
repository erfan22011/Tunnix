package com.tunnix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmeraldGreen = Color(0xFF00E676)
val EmeraldLight = Color(0xFF00DF89)
val ObsidianDark = Color(0xFF0B0E14)
val ObsidianSurface = Color(0xFF121824)
val FrostedGlassBorderDark = Color(0x3300E676)

val PearlLight = Color(0xFFF8FAFC)
val PearlSurface = Color(0xFFFFFFFF)
val FrostedGlassBorderLight = Color(0x1F00E676)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    secondary = EmeraldLight,
    background = ObsidianDark,
    surface = ObsidianSurface,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    secondary = EmeraldLight,
    background = PearlLight,
    surface = PearlSurface,
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun TunnixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
