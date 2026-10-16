package com.funpixelnest.mgp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.funpixelnest.mgp.domain.Game
import org.jetbrains.compose.resources.painterResource

/**
 * The one way a game's picture is drawn anywhere in the app (lists, headers, leaderboard…).
 * Shows the game's icon, or the FunPixelNest logo if it doesn't have one yet (see GameIcons.kt).
 */
@Composable
fun GameIcon(
    game: Game,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = size * 0.22f,
) {
    Image(
        painter = painterResource(gameIconResource(game.id)),
        contentDescription = game.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(game.accentColor)),
    )
}

/** Card variant: fills whatever box it's given (e.g. full card width, square). */
@Composable
fun GameArt(game: Game, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(gameIconResource(game.id)),
        contentDescription = game.name,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Color(game.accentColor)),
    )
}
