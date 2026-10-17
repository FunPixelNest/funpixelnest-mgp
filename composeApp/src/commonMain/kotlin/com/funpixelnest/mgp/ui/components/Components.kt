package com.funpixelnest.mgp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.ui.theme.MgpColors

@Composable
fun CoinBadge(coins: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MgpColors.SurfaceRaised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CoinIcon(Modifier.size(18.dp))
        Text(
            text = formatCoins(coins),
            color = MgpColors.Primary,
            fontWeight = FontWeight.Bold,
        )
        if (onClick != null) Text("+", color = MgpColors.Primary, fontWeight = FontWeight.Bold)
    }
}

/** Section heading used inside tab screens. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MgpTopBar(
    title: String,
    coins: Long?,
    onBack: (() -> Unit)? = null,
    onCoinsClick: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MgpColors.TextPrimary)
                }
            }
        },
        actions = {
            if (coins != null) CoinBadge(coins, Modifier.padding(end = 12.dp), onClick = onCoinsClick)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MgpColors.Background,
            titleContentColor = MgpColors.TextPrimary,
        ),
    )
}

/** 12345 → "12,345". Kept simple and platform-independent. */
fun formatCoins(value: Long): String {
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

/** Marker used in strings for "a coin goes here"; [CoinText] draws it as [CoinIcon]. */
const val COIN = "\uD83E\uDE99"

/**
 * A drawn gold coin. Used instead of the 🪙 emoji, which the web version can't display
 * (it renders text with the app's own fonts, which have no emoji).
 */
@Composable
fun CoinIcon(modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(color = Color(0xFFE09A00), radius = r)
        drawCircle(color = Color(0xFFFFC93C), radius = r * 0.80f)
        drawCircle(color = Color(0xFFFFE28A), radius = r * 0.32f, center = center + Offset(-r * 0.22f, -r * 0.22f))
        drawCircle(color = Color(0xFFE09A00), radius = r * 0.55f, style = Stroke(width = r * 0.12f))
    }
}

/** Text where every [COIN] marker is drawn as a coin, sized to the text. */
@Composable
fun CoinText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val parts = text.split(COIN)
    val annotated = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) appendInlineContent(COIN_INLINE_ID, "[coin]")
            append(part)
        }
    }
    val inline = mapOf(
        COIN_INLINE_ID to InlineTextContent(Placeholder(1.1.em, 1.1.em, PlaceholderVerticalAlign.TextCenter)) {
            CoinIcon(Modifier.fillMaxSize())
        },
    )
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        inlineContent = inline,
        style = style,
    )
}

private const val COIN_INLINE_ID = "coin"

/** Round medal with the rank number (gold / silver / bronze for 1–3). */
@Composable
fun MedalBadge(rank: Int, modifier: Modifier = Modifier.size(18.dp)) {
    val color = when (rank) {
        1 -> MgpColors.Gold
        2 -> MgpColors.Silver
        3 -> MgpColors.Bronze
        else -> MgpColors.SurfaceRaised
    }
    Box(modifier.background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(rank.toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MgpColors.TextPrimary)
    }
}
