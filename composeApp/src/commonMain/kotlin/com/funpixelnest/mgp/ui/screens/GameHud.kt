package com.funpixelnest.mgp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.game.GAME_HUD_HEIGHT
import com.funpixelnest.mgp.game.GameHud
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.TileIconButton
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors

private val HudTop = Color(0xFF1B2E78)
private val HudBottom = Color(0xFF0E1B52)
private val HudEdge = Color(0xFF5B8FE8)
private val HudLabel = Color(0xFF9FB8F0)

/**
 * The app's HUD above every game, the same for all games: pause button, live score and time left.
 *
 * The game reports score / time (Unity: `FpnPlatform.UpdateHud`) and hides its own HUD inside the app.
 * Games that don't report yet show the match type instead of the timer and no score.
 * The pause button opens the app's pause menu.
 */
@Composable
fun GameHudBar(
    hud: GameHud,
    modeLabel: String,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HudTop, HudBottom))),
    ) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(GAME_HUD_HEIGHT - 3.dp)
                .padding(horizontal = 12.dp),
        ) {
            TileIconButton(
                Icons.Rounded.Pause,
                "Pause",
                onPause,
                size = 44.dp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (hud.reported) HudScore(hud.score, Modifier.align(Alignment.Center))
            val seconds = hud.secondsLeft
            if (seconds != null) {
                HudTimer(seconds, Modifier.align(Alignment.CenterEnd))
            } else {
                HudTag(modeLabel, Modifier.align(Alignment.CenterEnd))
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(HudEdge))
    }
}

@Composable
private fun HudScore(score: Long, modifier: Modifier = Modifier) {
    // Small "pop" whenever the score goes up.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(score) {
        if (score > 0) {
            bump.snapTo(1.18f)
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    Column(
        modifier.semantics { contentDescription = "Score $score" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SCORE", color = HudLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        DisplayText(
            formatCoins(score),
            size = 26.sp,
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
private fun HudTimer(seconds: Int, modifier: Modifier = Modifier) {
    val urgent = seconds in 0..10
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (urgent) MgpColors.Red else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics { contentDescription = "Time left ${seconds / 60} minutes ${seconds % 60} seconds" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Timer, null, tint = if (urgent) Color.White else MgpColors.Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun HudTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
