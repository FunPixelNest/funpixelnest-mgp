package com.funpixelnest.mgp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.Game
import org.jetbrains.compose.resources.painterResource

/**
 * The one way a game's picture is drawn anywhere in the app.
 * Uses the game's icon image when one is bundled (see GameIcons.kt), otherwise
 * falls back to the emoji on the game's accent color.
 */
@Composable
fun GameIcon(
    game: Game,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = size * 0.22f,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val accent = Color(game.accentColor)
    val icon = gameIconResource(game.id)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
            Text(game.emoji, fontSize = fontSize)
        }
    }
}

/** Banner-style variant for cards: fills the width, image cropped to the box. */
@Composable
fun GameArt(game: Game, modifier: Modifier = Modifier) {
    val accent = Color(game.accentColor)
    val icon = gameIconResource(game.id)
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(game.emoji, fontSize = 44.sp)
        }
    }
}
