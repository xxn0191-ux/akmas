package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF002117),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = Color(0xFFE0FFF5),
    secondary = MintGlow,
    onSecondary = Color(0xFF002110),
    tertiary = VerifiedBlue,
    background = DarkBg,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    error = CoralAlert,
    outline = Color(0xFF2A3942)
)

private val LightColors = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F7EA),
    onPrimaryContainer = Color(0xFF002117),
    secondary = EmeraldPrimary,
    onSecondary = Color.White,
    tertiary = VerifiedBlue,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    error = CoralAlert,
    outline = Color(0xFFD1D7DB)
)

@Composable
fun AkMessengerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
