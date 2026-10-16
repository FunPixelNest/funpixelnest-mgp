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
 * "Bright & Playful" theme: sky-blue background, white cards, orange buttons,
 * sunny-yellow highlights and navy text. Matches the FunPixelNest logo.
 */
object MgpColors {
    val Background = Color(0xFFD9F0FF)   // sky blue
    val Surface = Color(0xFFFFFFFF)      // cards
    val SurfaceRaised = Color(0xFFEAF5FF) // chips, tracks, inner panels
    val Border = Color(0xFFBFDDF5)
    val Primary = Color(0xFFF26B1D)      // orange: buttons, coins, selection
    val OnPrimary = Color(0xFFFFFFFF)
    val Sunshine = Color(0xFFFFC93C)     // yellow highlight / glow
    val Secondary = Color(0xFF2B86F0)    // playful blue (banners)
    val SecondaryLight = Color(0xFF2FA8F2)
    val TextPrimary = Color(0xFF1D2B4F)  // navy
    val TextMuted = Color(0xFF5D6E8C)
    val Win = Color(0xFF17A34A)
    val Error = Color(0xFFD93025)
    val Gold = Color(0xFFFFC93C)
    val Silver = Color(0xFFC9D6E3)
    val Bronze = Color(0xFFE6A46E)
}

private val colorScheme = lightColorScheme(
    primary = MgpColors.Primary,
    onPrimary = MgpColors.OnPrimary,
    secondary = MgpColors.Secondary,
    onSecondary = Color.White,
    tertiary = MgpColors.Sunshine,
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
