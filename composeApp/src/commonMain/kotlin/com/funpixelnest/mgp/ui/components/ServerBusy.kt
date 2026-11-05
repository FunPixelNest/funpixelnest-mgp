package com.funpixelnest.mgp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.delay

/**
 * Full-screen "Please wait" loader while coins / gems are being updated on the server ([busyCount] > 0):
 * dims the app, blocks taps and shows a spinning coin. It appears only if the server takes longer than
 * 150 ms (quick answers don't flash it) and fades out shortly after the last update finishes, so a server
 * call followed by the balance refresh reads as one wait. Put it near the top of the root Box.
 */
@Composable
fun BoxScope.ServerBusyOverlay(busyCount: Int) {
    var visible by remember { mutableStateOf(false) }
    val busy = busyCount > 0
    LaunchedEffect(busy) {
        if (busy) {
            delay(150)
            visible = true
        } else if (visible) {
            delay(250)
            visible = false
        }
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99081A44))
                // Swallow taps: nothing behind can be pressed twice while the server works.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .semantics { contentDescription = "Please wait, updating your coins"; liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(min = 200.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .background(MgpColors.Card, RoundedCornerShape(24.dp))
                    .border(3.dp, Color(0xFF5B9BFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpinningCoin()
                Spacer(Modifier.height(12.dp))
                DisplayText("PLEASE WAIT", size = 22.sp, color = MgpColors.Primary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text("Updating your coins…", color = MgpColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                LoadingDots()
            }
        }
    }
}

/** A coin turning on its edge, like a coin flip. */
@Composable
private fun SpinningCoin() {
    val spin = rememberInfiniteTransition(label = "coinSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    val bob by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "bob")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(46.dp)
                .alpha(0.18f)
                .background(MgpColors.Gold, CircleShape),
        )
        CoinIcon(
            48.dp,
            Modifier.graphicsLayer {
                rotationY = angle
                cameraDistance = 12f * density
                translationY = -6.dp.toPx() * bob
            },
        )
    }
}

/** Three dots pulsing one after another. */
@Composable
private fun LoadingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 3) {
            val on = ((phase - i + 3f) % 3f) < 1f
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { scaleX = if (on) 1.25f else 0.85f; scaleY = scaleX }
                    .background(if (on) MgpColors.Primary else MgpColors.Track, CircleShape),
            )
        }
    }
}
