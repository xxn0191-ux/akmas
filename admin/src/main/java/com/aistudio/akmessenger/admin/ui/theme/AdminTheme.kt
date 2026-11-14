package com.aistudio.akmessenger.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aistudio.akmessenger.shared.ui.AkFonts

val AdminBlack = Color(0xFF060A12)
val AdminNavy = Color(0xFF0D1729)
val AdminNavyCard = Color(0xFF132038)
val NeonCyan = Color(0xFF00D2FF)
val NeonBlue = Color(0xFF3A7BFF)
val NeonMint = Color(0xFF00E5A3)
val AdminAlert = Color(0xFFFF4D6D)
val AdminWarn = Color(0xFFFFB703)

private val AdminColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001E28),
    primaryContainer = Color(0xFF0C3654),
    onPrimaryContainer = Color(0xFFC2F3FF),
    secondary = NeonBlue,
    onSecondary = Color.White,
    tertiary = NeonMint,
    background = AdminBlack,
    onBackground = Color(0xFFEAF2FF),
    surface = AdminNavy,
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = AdminNavyCard,
    onSurfaceVariant = Color(0xFF8FA7C6),
    error = AdminAlert,
    outline = Color(0xFF1F3354)
)

private val AdminTypography = Typography(
    displaySmall = TextStyle(fontFamily = AkFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = AkFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = AkFonts.SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = AkFonts.PlusJakarta, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = AkFonts.PlusJakarta, fontSize = 13.5.sp),
    labelMedium = TextStyle(fontFamily = AkFonts.JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun AkAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdminColors,
        typography = AdminTypography,
        content = content
    )
}
