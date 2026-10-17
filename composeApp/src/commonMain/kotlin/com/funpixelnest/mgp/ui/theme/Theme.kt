package com.funpixelnest.mgp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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

/**
 * "Sky Arcade" theme from the app mockups: light sky-blue background, frosted white cards,
 * glossy blue/green/red buttons, gold coins, blue gems and deep navy text.
 */
object MgpColors {
    val Background = Color(0xFFDDEFFF)
    val SkyTop = Color(0xFFCFE8FF)
    val SkyBottom = Color(0xFFEFF7FF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xF2F7FBFF)   // frosted cards
    val SurfaceRaised = Color(0xFFEAF3FF) // chips, tracks, inner panels
    val Border = Color(0xFFD3E5FA)
    val Primary = Color(0xFF1E6CF2)       // blue: buttons, links, selection
    val PrimaryDark = Color(0xFF0F4FC9)
    val PrimaryLight = Color(0xFF3D8BFF)
    val OnPrimary = Color(0xFFFFFFFF)
    val Navy = Color(0xFF13205B)           // titles
    val NavyCard = Color(0xFF122A6B)       // dark league card
    val TextPrimary = Color(0xFF13205B)
    val TextMuted = Color(0xFF55658F)
    val Green = Color(0xFF3FAE2A)
    val Red = Color(0xFFE5342B)
    val Purple = Color(0xFF7B3FE4)
    val Orange = Color(0xFFF57C12)
    val Pink = Color(0xFFE91E73)
    val Gold = Color(0xFFFFC23A)
    val Win = Color(0xFF2E9A1C)
    val Error = Color(0xFFD93025)
    val Silver = Color(0xFFC9D6E3)
    val Bronze = Color(0xFFE6A46E)
    val Secondary = Primary
    val Sunshine = Gold
}

private val colorScheme = lightColorScheme(
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
    MaterialTheme(colorScheme = colorScheme, typography = mgpTypography(), content = content)
}

/** Medal color for ranks 1–3, null otherwise. */
fun medalColor(rank: Int?): Color? = when (rank) {
    1 -> MgpColors.Gold
    2 -> MgpColors.Silver
    3 -> MgpColors.Bronze
    else -> null
}
