package com.gwstreams.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// GWStreams identity — deep midnight base with an electric aqua signature accent.
val Midnight        = Color(0xFF0A0E14)
val Surface1        = Color(0xFF121821)
val Surface2        = Color(0xFF1B232F)
val SurfaceHi       = Color(0xFF26323F)
val Aqua            = Color(0xFF2DE2C4)   // signature accent
val AquaDim         = Color(0xFF15A98F)
val Coral           = Color(0xFFFF6B6B)   // alerts / live badge
val TextHi          = Color(0xFFF2F6FA)
val TextMid         = Color(0xFFA6B3C2)
val TextLow         = Color(0xFF6B7889)

private val GwColors = darkColorScheme(
    primary = Aqua,
    onPrimary = Midnight,
    secondary = AquaDim,
    background = Midnight,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    error = Coral,
    outline = SurfaceHi
)

@Composable
fun GWStreamsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GwColors,
        typography = GwTypography,
        content = content
    )
}
