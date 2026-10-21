package com.funpixelnest.mgp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// ---------------------------------------------------------------- formatting

/** 12450 → "12,450". */
fun formatCoins(value: Long): String {
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

/** 10000 → "10K", 250000 → "250K", 1500 → "1,500". */
fun formatShort(value: Long): String = when {
    value >= 1_000_000 && value % 1_000_000 == 0L -> "${value / 1_000_000}M"
    value >= 10_000 && value % 1_000 == 0L -> "${value / 1_000}K"
    else -> formatCoins(value)
}

/** Remaining time like the mockups: "6d 08h 24m", "08h 24m", "04:37". */
fun formatCountdown(millis: Long, clock: Boolean = false): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return when {
        clock -> "${two(totalSeconds / 60)}:${two(seconds)}"
        days > 0 -> "${days}d ${two(hours)}h ${two(minutes)}m"
        hours > 0 -> "${two(hours)}h ${two(minutes)}m"
        else -> "${two(minutes)}m ${two(seconds)}s"
    }
}

/** Counts down from [millis] (as received) once per second. Restarts whenever [millis] changes. */
@Composable
fun rememberCountdown(millis: Long): Long {
    var left by remember(millis) { mutableLongStateOf(millis) }
    LaunchedEffect(millis) {
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            left = (millis - start.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)
            if (left == 0L) break
            delay(1_000)
        }
    }
    return left
}

// ---------------------------------------------------------------- text

/** Chunky display title (Fredoka), optionally with a white outline like the mockup headings. */
@Composable
fun DisplayText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 28.sp,
    color: Color = MgpColors.Navy,
    outline: Color? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 2,
) {
    val base = MaterialTheme.typography.headlineMedium.copy(
        fontSize = size,
        lineHeight = size * 1.05f,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign ?: TextAlign.Unspecified,
    )
    Box(modifier) {
        if (outline != null) {
            Text(
                text,
                style = base.copy(color = outline, drawStyle = Stroke(width = size.value / 3f)),
                maxLines = maxLines,
            )
        }
        Text(
            text,
            style = base.copy(
                color = color,
                shadow = if (outline != null) Shadow(Color(0x55000E40), Offset(0f, 3f), 4f) else null,
            ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LabelText(text: String, modifier: Modifier = Modifier, color: Color = MgpColors.TextMuted, size: TextUnit = 11.sp) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------- buttons

/** Button colors from the mockups. */
enum class ButtonColor(val top: Color, val bottom: Color, val edge: Color) {
    Blue(Color(0xFF3D8BFF), Color(0xFF1458E0), Color(0xFF0D3FA8)),
    Green(Color(0xFF7ED348), Color(0xFF34A11E), Color(0xFF237312)),
    Red(Color(0xFFFF6A55), Color(0xFFD9261C), Color(0xFF9E1710)),
    Purple(Color(0xFF9C6BFF), Color(0xFF6A2FE0), Color(0xFF4A1BA8)),
    Orange(Color(0xFFFFA23D), Color(0xFFF06A00), Color(0xFFB04C00)),
    Pink(Color(0xFFFF5FA2), Color(0xFFE0186D), Color(0xFFA30D4E)),
    Gold(Color(0xFFFFD866), Color(0xFFF2A818), Color(0xFFB87800)),
    Gray(Color(0xFFC9D3E3), Color(0xFFA7B4CA), Color(0xFF8593AB)),
}

/** Glossy 3D pill button used everywhere (PLAY, RESUME, CLAIM, GO…). */
@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: ButtonColor = ButtonColor.Blue,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    fontSize: TextUnit = 20.sp,
    icon: ImageVector? = null,
    image: DrawableResource? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.95f else 1f)
    val c = if (enabled) color else ButtonColor.Gray
    val shape = RoundedCornerShape(height / 2.6f)
    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .shadow(if (enabled) 6.dp else 0.dp, shape, ambientColor = c.edge, spotColor = c.edge)
            .clip(shape)
            .background(c.edge)
            .padding(bottom = 4.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.top, c.bottom)))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Shine on the upper half.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = height * 0.55f)
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(height))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.04f)))),
        )
        // Small buttons (cards, rows) keep icon and text close so both fit.
        val compact = height < 44.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 14.dp),
        ) {
            if (leading != null) leading()
            if (image != null) Image(painterResource(image), null, Modifier.size(height * 0.62f))
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(height * (if (compact) 0.45f else 0.5f)))
            if (text.isNotEmpty()) {
                Text(
                    text.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(c.edge, Offset(0f, 2f), 2f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small round icon button in a white tile (back, settings, close, menu). */
@Composable
fun TileIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = MgpColors.Primary,
    badge: Int = 0,
) {
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(MgpColors.Card)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.56f))
        }
        if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(start = 0.dp))
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .shadow(2.dp, CircleShape)
            .background(Color(0xFFF2301F), CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------- surfaces

/** Frosted white card. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    corner: Dp = 22.dp,
    color: Color = MgpColors.SurfaceSoft,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(corner), ambientColor = MgpColors.Glow, spotColor = MgpColors.Glow)
            .clip(RoundedCornerShape(corner))
            .background(color)
            .border(1.dp, MgpColors.CardEdge, RoundedCornerShape(corner))
            .padding(padding),
        content = content,
    )
}

/** Sky gradient behind every screen, with a few soft floating squares like the mockups. */
@Composable
fun SkyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MgpColors.SkyTop, MgpColors.Background, MgpColors.SkyBottom))),
    ) {
        val squareColor = Color.White.copy(alpha = if (MgpColors.isDark) 0.05f else 0.35f)
        Canvas(Modifier.fillMaxSize()) {
            val squares = listOf(0.08f to 0.06f, 0.86f to 0.12f, 0.06f to 0.42f, 0.92f to 0.55f, 0.12f to 0.78f)
            squares.forEachIndexed { i, (x, y) ->
                val s = (18 + i * 4).dp.toPx()
                drawRoundRect(
                    color = squareColor,
                    topLeft = Offset(size.width * x, size.height * y),
                    size = Size(s, s),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        content()
    }
}

/** Blue ribbon banner with folded tails ("GAME PAUSED", "LEADERBOARD"). */
@Composable
fun RibbonBanner(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 34.sp) {
    Box(modifier.fillMaxWidth().height(86.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tail = Path().apply {
                moveTo(0f, h * 0.30f); lineTo(w * 0.16f, h * 0.30f); lineTo(w * 0.16f, h * 0.92f)
                lineTo(0f, h * 0.92f); lineTo(w * 0.06f, h * 0.61f); close()
            }
            drawPath(tail, Color(0xFF0E46B8))
            val right = Path().apply {
                moveTo(w, h * 0.30f); lineTo(w * 0.84f, h * 0.30f); lineTo(w * 0.84f, h * 0.92f)
                lineTo(w, h * 0.92f); lineTo(w * 0.94f, h * 0.61f); close()
            }
            drawPath(right, Color(0xFF0E46B8))
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF3C8DFF), Color(0xFF1655DA))),
                topLeft = Offset(w * 0.09f, h * 0.08f),
                size = Size(w * 0.82f, h * 0.74f),
                cornerRadius = CornerRadius(h * 0.2f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.11f, h * 0.12f),
                size = Size(w * 0.78f, h * 0.22f),
                cornerRadius = CornerRadius(h * 0.12f),
            )
        }
        DisplayText(
            text,
            size = fontSize,
            color = Color.White,
            outline = Color(0xFF0B3A9C),
            modifier = Modifier.padding(bottom = 10.dp),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- currency & rewards

@Composable
fun CoinIcon(size: Dp = 20.dp, modifier: Modifier = Modifier) =
    Image(painterResource(Art.coin), "coins", modifier.size(size), contentScale = ContentScale.Fit)

@Composable
fun GemIcon(size: Dp = 20.dp, modifier: Modifier = Modifier) =
    Image(painterResource(Art.gem), "gems", modifier.size(size), contentScale = ContentScale.Fit)

/** "🪙 1,250" with the coin art. */
@Composable
fun CoinAmount(
    amount: Long,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    fontSize: TextUnit = 16.sp,
    color: Color = MgpColors.Navy,
    gems: Boolean = false,
    iconFirst: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (iconFirst) if (gems) GemIcon(iconSize) else CoinIcon(iconSize)
        Text(formatCoins(amount), color = color, fontSize = fontSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (!iconFirst) if (gems) GemIcon(iconSize) else CoinIcon(iconSize)
    }
}

/** Reward in a soft chip: coins and/or gems. */
@Composable
fun RewardChip(reward: RewardGrant, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp, iconSize: Dp = 22.dp) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (MgpColors.isDark) Color(0xFF3A3322) else Color(0xFFFFF7E6))
            .border(1.dp, if (MgpColors.isDark) Color(0xFF5E4C24) else Color(0xFFFFE7B3), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (reward.coins > 0) CoinAmount(reward.coins, iconSize = iconSize, fontSize = fontSize)
        if (reward.gems > 0) CoinAmount(reward.gems, iconSize = iconSize, fontSize = fontSize, gems = true)
    }
}

// ---------------------------------------------------------------- progress

/** Rounded progress bar with an optional label drawn in the middle ("2 / 3"). */
@Composable
fun ProgressPill(
    fraction: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = MgpColors.Green,
    height: Dp = 16.dp,
    track: Color = MgpColors.Track,
) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color))),
        )
        if (label != null) {
            // The line box is trimmed to the font size and centered, so the digits sit in the middle of the bar
            // (by default the font's ascent / descent padding pushed them down).
            val size = (height.value * 0.62f).sp
            Text(
                label,
                color = if (fraction > 0.45f) Color.White else MgpColors.Navy,
                maxLines = 1,
                style = TextStyle(
                    fontSize = size,
                    lineHeight = size,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = MaterialTheme.typography.labelSmall.fontFamily,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
        }
    }
}

/** Blue 8-point star with the player's level. */
@Composable
fun LevelBadge(level: Int, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val points = 8
            val outer = this.size.minDimension / 2
            val inner = outer * 0.78f
            val c = center
            val path = Path()
            for (i in 0 until points * 2) {
                val r = if (i % 2 == 0) outer else inner
                val a = PI * i / points - PI / 2
                val x = c.x + (r * cos(a)).toFloat()
                val y = c.y + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF4A9BFF), Color(0xFF1652D8))))
            drawPath(path, Color.White, style = Stroke(width = 1.5.dp.toPx()))
        }
        Text("$level", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp)
    }
}

// ---------------------------------------------------------------- avatars & art

@Composable
fun Avatar(
    avatarId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color = Color.White,
    ringWidth: Dp = 3.dp,
) {
    Image(
        painterResource(Art.avatar(avatarId)),
        contentDescription = "Avatar",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFBFE0FF))
            .border(ringWidth, ring, CircleShape),
    )
}

@Composable
fun ArtImage(res: DrawableResource, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(res), description, modifier.size(size), contentScale = ContentScale.Fit)
}

/** Big tab header: art + title + subtitle (CHALLENGES, LEAGUES, STORE, REWARDS). */
@Composable
fun ScreenHeader(
    art: DrawableResource,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    artInTile: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (artInTile) {
            Box(
                Modifier.size(70.dp).shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(MgpColors.Card),
                contentAlignment = Alignment.Center,
            ) { ArtImage(art, 46.dp) }
        } else {
            ArtImage(art, 70.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            DisplayText(title.uppercase(), size = 34.sp, maxLines = 1)
            Text(subtitle, color = MgpColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (trailing != null) trailing()
    }
}

/** Section title row inside a card: icon + COLORED TITLE + optional trailing (View All, timer…). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MgpColors.Navy,
    icon: DrawableResource? = null,
    vector: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            ArtImage(icon, 26.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (vector != null) {
            Icon(vector, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title.uppercase(),
            color = color,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) trailing()
    }
}

/** "🕒 Resets in: 08h 24m" in blue. */
@Composable
fun ResetTimer(prefix: String, millis: Long, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val left = rememberCountdown(millis)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, null, tint = MgpColors.Primary, modifier = Modifier.size(16.dp))
        Text("$prefix ${formatCountdown(left)}", color = MgpColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** A short gray helper line. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Text(
        text,
        modifier = modifier,
        color = MgpColors.TextMuted,
        fontSize = 12.sp,
        textAlign = align,
        style = TextStyle(lineHeight = 16.sp),
    )
}
