package com.mecnun.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B1020)
val Panel = Color(0xFF141B34)
val Panel2 = Color(0xFF1C2547)
val Violet = Color(0xFF7C5CFF)
val Teal = Color(0xFF22D3B6)
val Amber = Color(0xFFFFB84D)
val Rose = Color(0xFFFF6B81)
val Sky = Color(0xFF5CB8FF)
val TextHi = Color(0xFFEAF0FF)
val TextLo = Color(0xFF9AA6C8)

private val scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color(0xFF00201A),
    background = Bg,
    onBackground = TextHi,
    surface = Panel,
    onSurface = TextHi,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextLo,
    outline = Color(0xFF3A4570),
    error = Rose
)

@Composable
fun MecnunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
