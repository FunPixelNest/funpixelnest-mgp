package com.funpixelnest.mgp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.game.GAME_HUD_HEIGHT
import com.funpixelnest.mgp.game.GameHud
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors

// Glass pieces that sit on top of any game's art.
private val Glass = Color(0x66000000)
private val GlassEdge = Color(0x59FFFFFF)
private val Label = Color(0xCCFFFFFF)
private val Heart = Color(0xFFFF4D6D)

/**
 * The app's HUD for every game: pause, lives (or the game's own counter, e.g. arrows left), score, timer.
 *
 * It floats over the top of the game (no bar of its own), with a soft shade behind the status bar, so the
 * HUD and the game read as one screen. The game keeps this area clear (launch data hudTop / FPN.insetTop)
 * and hides its own HUD inside the app. The lives pill appears only in games that have lives; the timer is
 * greyed out for games without one.
 */
@Composable
fun GameHudOverlay(
    hud: GameHud,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
    gameId: String? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x8C000000), Color(0x33000000), Color.Transparent))),
    ) {
        Box(
            Modifier
                // Status bar and notch/cutout: the notch stays even when a game hides the status bar.
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .fillMaxWidth()
                .height(GAME_HUD_HEIGHT)
                .padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PauseButton(onPause)
                // A game's own count (e.g. arrows left in Arrow Duel) takes the lives slot. Games without lives
                // show nothing here. Solitaire Slots shows its spins next to its own Spin button, so not here.
                val counter = hud.counter.takeIf { gameId !in GAMES_WITHOUT_HUD_COUNTER }
                val lives = hud.lives
                when {
                    counter != null -> CounterPill(counter, counterKind(gameId))
                    lives != null -> LivesPill(lives)
                }
            }
            ScorePill(hud.score, shown = hud.reported, modifier = Modifier.align(Alignment.Center))
            TimerPill(hud.secondsLeft, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

private fun Modifier.glass(shape: Shape): Modifier =
    this.clip(shape).background(Glass).border(1.5.dp, GlassEdge, shape)

@Composable
private fun PauseButton(onPause: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF4F9BFF), MgpColors.PrimaryDark)))
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Pause game", onClick = onPause)
            .semantics { contentDescription = "Pause" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Pause, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** Games whose counter is already shown inside the game (Slots: spins next to the Spin button). */
private val GAMES_WITHOUT_HUD_COUNTER = setOf("slots")

/** What a game's counter counts, shown with its own icon (screen readers say "<label> <count>"). */
private class CounterKind(val icon: ImageVector, val label: String)

private fun counterKind(gameId: String?): CounterKind = when (gameId) {
    "slots" -> CounterKind(Icons.Rounded.Casino, "Spins left")
    "bubble_shooter" -> CounterKind(Icons.Rounded.ViewHeadline, "Rows left")
    else -> CounterKind(Icons.Rounded.NorthEast, "Arrows left")
}

/** A game's own count in place of lives: arrows left (Arrows), spins left (Solitaire Slots), rows left (Bubble Shooter). */
@Composable
private fun CounterPill(count: Int, kind: CounterKind) {
    Row(
        Modifier
            .height(36.dp)
            .glass(RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = "${kind.label} $count" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kind.icon, null, tint = MgpColors.Gold, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(5.dp))
        Text("$count", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LivesPill(lives: Int) {
    Row(
        Modifier
            .height(36.dp)
            .glass(RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = "Lives $lives" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Favorite, null, tint = Heart, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(5.dp))
        Text("$lives", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ScorePill(score: Long, shown: Boolean, modifier: Modifier = Modifier) {
    // Small "pop" whenever the score goes up.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(score) {
        if (score > 0) {
            bump.snapTo(1.15f)
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    Column(
        modifier
            .widthIn(min = 104.dp)
            .heightIn(min = 50.dp)
            .glass(RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .semantics { contentDescription = "Score $score" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("SCORE", color = Label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        DisplayText(
            if (shown) formatCoins(score) else "–",
            size = 23.sp,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            },
        )
    }
}

@Composable
private fun TimerPill(seconds: Int?, modifier: Modifier = Modifier) {
    val enabled = seconds != null
    val urgent = seconds != null && seconds <= 10
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(36.dp)
            .then(if (urgent) Modifier.clip(shape).background(MgpColors.Red).border(1.5.dp, Color.White, shape) else Modifier.glass(shape))
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 10.dp)
            .semantics {
                contentDescription = if (seconds != null) "Time left ${seconds / 60} minutes ${seconds % 60} seconds" else "No timer in this game"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Timer, null, tint = if (urgent) Color.White else MgpColors.Gold, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (seconds != null) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "–:––",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
