package com.gwstreams.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Midnight   = Color(0xFF0A0E14)
val Surface1   = Color(0xFF121821)
val Surface2   = Color(0xFF1B232F)
val SurfaceHi  = Color(0xFF26323F)
val Aqua       = Color(0xFF2DE2C4)
val AquaDim    = Color(0xFF15A98F)
val Coral      = Color(0xFFFF6B6B)
val TextHi     = Color(0xFFF2F6FA)
val TextMid    = Color(0xFFA6B3C2)
val TextLow    = Color(0xFF6B7889)
val FocusGlow  = Color(0xFF2DE2C4)

private val Colors = darkColorScheme(
    primary = Aqua,
    onPrimary = Midnight,
    background = Midnight,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    error = Coral,
    outline = SurfaceHi
)

// Larger type scale for 10-foot viewing.
val TvType = Typography(
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 40.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
)

@Composable
fun GWSTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = TvType, content = content)
}
