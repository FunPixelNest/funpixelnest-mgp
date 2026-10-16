package com.funpixelnest.mgp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MgpColors {
    val Background = Color(0xFF0E0B1F)
    val Surface = Color(0xFF1A1533)
    val SurfaceRaised = Color(0xFF251E47)
    val Primary = Color(0xFFFFC83D) // coin gold
    val OnPrimary = Color(0xFF2A1C00)
    val Secondary = Color(0xFF7C5CFF)
    val TextPrimary = Color(0xFFF4F1FF)
    val TextMuted = Color(0xFFA59DC9)
    val Win = Color(0xFF3DDC97)
    val Gold = Color(0xFFFFD54F)
    val Silver = Color(0xFFCFD8DC)
    val Bronze = Color(0xFFD7A26C)
}

private val colorScheme = darkColorScheme(
    primary = MgpColors.Primary,
    onPrimary = MgpColors.OnPrimary,
    secondary = MgpColors.Secondary,
    background = MgpColors.Background,
    onBackground = MgpColors.TextPrimary,
    surface = MgpColors.Surface,
    onSurface = MgpColors.TextPrimary,
    surfaceVariant = MgpColors.SurfaceRaised,
    onSurfaceVariant = MgpColors.TextMuted,
)

@Composable
fun MgpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Medal color for ranks 1–3, null otherwise. */
fun medalColor(rank: Int?): Color? = when (rank) {
    1 -> MgpColors.Gold
    2 -> MgpColors.Silver
    3 -> MgpColors.Bronze
    else -> null
}
