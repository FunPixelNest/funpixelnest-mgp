package com.funpixelnest.mgp.ui.screens

import com.funpixelnest.mgp.ENDED_EARLY_EVENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.game.ScoreLine
import com.funpixelnest.mgp.game.scoreLines
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF13205B)
private val TotalBlue = Color(0xFF1E5BE6)
private val RowLine = Color(0x332E7DF6)

/**
 * After every match: the score board for this game (rows sent by the game, e.g. Base Score / Time Bonus,
 * plus the Total), then Submit Score → the app submits and opens the leaderboard.
 *
 * @param result null when the player left the match early (it counts as score 0).
 */
@Composable
fun GameOverScreen(
    game: Game,
    result: GameResult?,
    submitting: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = result?.score ?: 0L
    val lines = remember(result) {
        val fromGame = result?.scoreLines().orEmpty()
        fromGame.ifEmpty {
            val label = when {
                result == null -> "Left the match"
                ENDED_EARLY_EVENT in result.eventLog -> "Score so far"
                else -> "Score"
            }
            listOf(ScoreLine(label, total))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        GameOverSky()
        Clouds(Modifier.align(Alignment.BottomCenter))
        val screenHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                // At least the screen's height, so short boards sit in the middle like the mockup.
                .heightIn(min = screenHeight - 32.dp)
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon(game, 34.dp, cornerRadius = 9.dp)
                Spacer(Modifier.width(8.dp))
                Text(game.name.uppercase(), color = Ink.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(18.dp))
            GameOverTitle()
            Spacer(Modifier.height(28.dp))

            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                lines.forEachIndexed { i, line ->
                    ScoreRow(line, index = i, isTotal = false)
                    HorizontalDivider(color = RowLine, thickness = 1.5.dp)
                }
                ScoreRow(ScoreLine("Total Score", total), index = lines.size, isTotal = true)
            }

            Spacer(Modifier.height(30.dp))
            var showButton by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(250L + 140L * (lines.size + 1))
                showButton = true
            }
            Box(Modifier.height(66.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(showButton, enter = fadeIn(tween(200)) + scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.6f)) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(40.dp), color = MgpColors.Green, strokeWidth = 4.dp)
                    } else {
                        GlossyButton(
                            "Submit score",
                            onClick = onSubmit,
                            color = ButtonColor.Green,
                            height = 60.dp,
                            fontSize = 22.sp,
                            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
                        )
                    }
                }
            }
            if (error != null) {
                Text(
                    error,
                    color = MgpColors.Error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Light blue sky with soft white rays behind the title. */
@Composable
private fun BoxScope.GameOverSky() {
    val t = rememberInfiniteTransition()
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)))
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFA9D2FF), Color(0xFFDDEEFF), Color(0xFFC3E0FF), Color(0xFF9CCBFF))))
        val c = Offset(size.width / 2f, size.height * 0.34f)
        val r = size.maxDimension
        val rays = 16
        for (i in 0 until rays) {
            val a0 = (spin + i * 360f / rays) * PI.toFloat() / 180f
            val a1 = a0 + (PI.toFloat() / rays) * 0.55f
            val p = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + r * cos(a0), c.y + r * sin(a0))
                lineTo(c.x + r * cos(a1), c.y + r * sin(a1))
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), c, r * 0.55f))
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), c, size.width * 0.45f), size.width * 0.45f, c)
    }
}

/** "GAME" in gold and "OVER!" in sky blue, chunky with dark outlines, popping in, with twinkling stars. */
@Composable
private fun GameOverTitle() {
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    val t = rememberInfiniteTransition()
    val twinkle by t.animateFloat(0.82f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val bob by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse))

    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        // stars: (x, y) as a share of the box, size, colour, phase
        val stars = listOf(
            Triple(Offset(0.10f, 0.20f), 30.dp, Purple), Triple(Offset(0.04f, 0.52f), 38.dp, Gold),
            Triple(Offset(0.13f, 0.82f), 24.dp, Purple), Triple(Offset(0.90f, 0.20f), 32.dp, Purple),
            Triple(Offset(0.96f, 0.50f), 40.dp, Gold), Triple(Offset(0.86f, 0.78f), 24.dp, Purple),
        )
        Box(Modifier.fillMaxSize()) {
            stars.forEachIndexed { i, (pos, s, col) ->
                val scale = if (i % 2 == 0) twinkle else 1.94f - twinkle
                StarShape(
                    s,
                    col,
                    Modifier
                        .layoutFraction(pos)
                        .graphicsLayer { scaleX = scale * pop.value; scaleY = scale * pop.value; rotationZ = (i * 17f) },
                )
            }
        }
        Column(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; translationY = bob },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTitle("GAME", 76.sp, Brush.verticalGradient(listOf(Color(0xFFFFE15A), Color(0xFFFFB21E), Color(0xFFF58A07))), Color(0xFF8A3B05))
            OutlinedTitle("OVER!", 76.sp, Brush.verticalGradient(listOf(Color(0xFFD9F4FF), Color(0xFF69C8FF), Color(0xFF2B8FF0))), Color(0xFF123B9C), Modifier.offset(y = (-14).dp))
        }
    }
}

private val Purple = Brush.verticalGradient(listOf(Color(0xFFE29BFF), Color(0xFF9B3FE0)))
private val Gold = Brush.verticalGradient(listOf(Color(0xFFFFE477), Color(0xFFFFA81A)))

/** Places a child at a share of the parent's size (x, y in 0..1), centred on that point. */
private fun Modifier.layoutFraction(pos: Offset): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val parentW = constraints.maxWidth
    val parentH = constraints.maxHeight
    layout(parentW, parentH) {
        placeable.place((parentW * pos.x - placeable.width / 2f).toInt(), (parentH * pos.y - placeable.height / 2f).toInt())
    }
}

@Composable
private fun StarShape(size: Dp, fill: Brush, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val outer = this.size.minDimension / 2f
        val inner = outer * 0.48f
        val p = Path()
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) outer else inner
            val a = (-90f + k * 36f) * PI.toFloat() / 180f
            val x = c.x + r * cos(a)
            val y = c.y + r * sin(a)
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, Color(0x33000000), alpha = 0.6f)
        drawPath(p, fill)
        drawPath(p, Color.White.copy(alpha = 0.85f), style = Stroke(width = outer * 0.12f))
    }
}

@Composable
private fun OutlinedTitle(text: String, size: TextUnit, fill: Brush, outline: Color, modifier: Modifier = Modifier) {
    val base = MaterialTheme.typography.headlineMedium.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold)
    Box(modifier) {
        Text(text, style = base.copy(color = outline, drawStyle = Stroke(width = size.value / 4.2f)), maxLines = 1)
        Text(
            text,
            style = TextStyle(brush = fill).merge(base).copy(shadow = Shadow(Color(0x44001040), Offset(0f, 6f), 6f)),
            maxLines = 1,
        )
    }
}

/** One score row: icon, label, value counting up. Rows slide in one after another. */
@Composable
private fun ScoreRow(line: ScoreLine, index: Int, isTotal: Boolean) {
    val appear = remember { Animatable(0f) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(line) {
        delay(250L + 140L * index)
        appear.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        count.animateTo(1f, tween(if (isTotal) 900 else 600, easing = FastOutSlowInEasing))
    }
    val shown = (line.value * count.value).toLong()
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (isTotal) 84.dp else 76.dp)
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * 120f
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { RowIcon(line.label, isTotal) }
        Spacer(Modifier.width(14.dp))
        Text(
            line.label.uppercase(),
            color = Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isTotal) 19.sp else 17.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            (if (line.value < 0) "-" else "") + formatCoins(kotlin.math.abs(shown)),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (isTotal) 40.sp else 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTotal) TotalBlue else if (line.value < 0) MgpColors.Error else Ink,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun RowIcon(label: String, isTotal: Boolean) {
    val l = label.lowercase()
    when {
        isTotal -> ArtImage(Art.cup, 50.dp)
        "time" in l -> ArtImage(Art.stopwatch, 46.dp)
        "live" in l || "life" in l -> Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(44.dp))
        "penalt" in l -> StarShape(40.dp, Brush.verticalGradient(listOf(Color(0xFFFF9AA8), Color(0xFFE5304B))))
        else -> StarShape(46.dp, Gold)
    }
}
