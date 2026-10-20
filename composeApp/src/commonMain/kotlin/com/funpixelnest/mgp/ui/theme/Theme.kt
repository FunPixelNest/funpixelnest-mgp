package com.funpixelnest.mgp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.funpixelnest.mgp.resources.Res
import com.funpixelnest.mgp.resources.fredoka_bold
import com.funpixelnest.mgp.resources.fredoka_semibold
import com.funpixelnest.mgp.resources.nunito_bold
import com.funpixelnest.mgp.resources.nunito_extrabold
import com.funpixelnest.mgp.resources.nunito_regular
import com.funpixelnest.mgp.resources.nunito_semibold
import org.jetbrains.compose.resources.Font

/** Light (default) or dark look. Changed from Settings or the side menu; saved on the device. */
object ThemeState {
    var dark by mutableStateOf(false)
}

/**
 * "Sky Arcade" theme from the app mockups: light sky-blue background, frosted white cards,
 * glossy blue/green/red buttons, gold coins, blue gems and deep navy text.
 * Every surface and text color has a dark-mode twin; reading them inside composition follows [ThemeState.dark].
 */
object MgpColors {
    private inline fun pick(light: Long, dark: Long) = Color(if (ThemeState.dark) dark else light)

    val isDark: Boolean get() = ThemeState.dark
    val Background get() = pick(0xFFDDEFFF, 0xFF0B1430)
    val SkyTop get() = pick(0xFFCFE8FF, 0xFF13214A)
    val SkyBottom get() = pick(0xFFEFF7FF, 0xFF091027)
    val Surface get() = pick(0xFFFFFFFF, 0xFF18264E)
    val SurfaceSoft get() = pick(0xF2F7FBFF, 0xF2152249)   // frosted cards
    val SurfaceRaised get() = pick(0xFFEAF3FF, 0xFF203260) // chips, tracks, inner panels
    val Border get() = pick(0xFFD3E5FA, 0xFF2C3F72)
    /** Solid card / tile background (white in light mode). */
    val Card get() = pick(0xFFFFFFFF, 0xFF1B2A56)
    /** Lower end of soft card gradients. */
    val CardLow get() = pick(0xFFF2F7FF, 0xFF15224A)
    /** Thin edge around cards (white in light mode). */
    val CardEdge get() = pick(0xFFFFFFFF, 0x26FFFFFF)
    /** Soft blue highlight (selected rows, inner chips). */
    val Tint get() = pick(0xFFEAF2FF, 0xFF233A72)
    /** Empty part of progress bars. */
    val Track get() = pick(0xFFE1E8F2, 0xFF2A3B68)
    /** Drawer / sheet background. */
    val Sheet get() = pick(0xFFF2F7FF, 0xFF101B3D)
    val Primary get() = pick(0xFF1E6CF2, 0xFF4A8BFF)       // blue: buttons, links, selection
    val PrimaryDark = Color(0xFF0F4FC9)
    val PrimaryLight = Color(0xFF3D8BFF)
    val OnPrimary = Color(0xFFFFFFFF)
    val Navy get() = pick(0xFF13205B, 0xFFEAF0FF)           // titles
    /** Navy that stays dark in both modes (text on gold, white or light art). */
    val Ink = Color(0xFF13205B)
    val NavyCard = Color(0xFF122A6B)       // dark league card
    val TextPrimary get() = Navy
    val TextMuted get() = pick(0xFF55658F, 0xFF9FB0D8)
    val Green = Color(0xFF3FAE2A)
    val Red = Color(0xFFE5342B)
    val Purple = Color(0xFF7B3FE4)
    val Orange = Color(0xFFF57C12)
    val Pink = Color(0xFFE91E73)
    val Gold = Color(0xFFFFC23A)
    val Win get() = pick(0xFF2E9A1C, 0xFF5BD145)
    val Error = Color(0xFFD93025)
    val Silver = Color(0xFFC9D6E3)
    val Bronze = Color(0xFFE6A46E)
    val Secondary get() = Primary
    val Sunshine = Gold
    /** Shadow tint for cards. */
    val Glow get() = pick(0x331E6CF2, 0x66000000)
}

private fun mgpColorScheme(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = MgpColors.Primary,
    onPrimary = MgpColors.OnPrimary,
    secondary = MgpColors.Secondary,
    onSecondary = Color.White,
    tertiary = MgpColors.Gold,
    background = MgpColors.Background,
    onBackground = MgpColors.TextPrimary,
    surface = MgpColors.Surface,
    onSurface = MgpColors.TextPrimary,
    surfaceVariant = MgpColors.SurfaceRaised,
    onSurfaceVariant = MgpColors.TextMuted,
    outline = MgpColors.Border,
    error = MgpColors.Error,
)

@Composable
private fun mgpTypography(): Typography {
    // Fredoka: rounded display face for titles. Nunito: friendly, readable body text.
    val display = FontFamily(
        Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
        Font(Res.font.fredoka_bold, FontWeight.Bold),
    )
    val body = FontFamily(
        Font(Res.font.nunito_regular, FontWeight.Normal),
        Font(Res.font.nunito_semibold, FontWeight.SemiBold),
        Font(Res.font.nunito_bold, FontWeight.Bold),
        Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    )
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.copy(fontFamily = body, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun MgpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = mgpColorScheme(ThemeState.dark), typography = mgpTypography(), content = content)
}

/** Medal color for ranks 1–3, null otherwise. */
fun medalColor(rank: Int?): Color? = when (rank) {
    1 -> MgpColors.Gold
    2 -> MgpColors.Silver
    3 -> MgpColors.Bronze
    else -> null
}
