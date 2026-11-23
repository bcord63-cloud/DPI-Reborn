package com.dpireborn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF07060B)
val SurfaceDark = Color(0xFF110F1A)
val SurfaceHigh = Color(0xFF1A1727)
val OutlineDim = Color(0xFF2C2640)
val TextPrimary = Color(0xFFEDE9FE)
val TextSecondary = Color(0xFFA79FC4)
val SuccessGreen = Color(0xFF34D399)
val DangerRed = Color(0xFFF87171)
val WarnAmber = Color(0xFFFBBF24)

data class AccentOption(val key: String, val label: String, val color: Color)

val AccentOptions = listOf(
    AccentOption("violet", "Violeta", Color(0xFF8B5CF6)),
    AccentOption("neon", "Roxo neon", Color(0xFFA855F7)),
    AccentOption("indigo", "Índigo", Color(0xFF6366F1)),
    AccentOption("magenta", "Magenta", Color(0xFFD946EF)),
)

fun accentColor(key: String): Color =
    AccentOptions.firstOrNull { it.key == key }?.color ?: AccentOptions[0].color

@Composable
fun DpiTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.25f),
        onPrimaryContainer = TextPrimary,
        secondary = Color(0xFFC4B5FD),
        onSecondary = Color.Black,
        background = BgBlack,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceHigh,
        onSurfaceVariant = TextSecondary,
        outline = OutlineDim,
        error = DangerRed,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
