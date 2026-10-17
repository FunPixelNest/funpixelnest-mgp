package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors

/** Shown after a free practice round: the score, then play again or go compete. */
@Composable
fun PracticeResultScreen(
    game: Game,
    result: GameResult?,
    onPlayAgain: () -> Unit,
    onPlayForCoins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        GameIcon(game, size = 96.dp)
        Text("Practice round", color = MgpColors.TextMuted, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MgpColors.Surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (result == null) {
                Text("You left the round", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("No score this time.", color = MgpColors.TextMuted)
            } else {
                Text("Your score", color = MgpColors.TextMuted)
                Text(
                    formatCoins(result.score),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MgpColors.Primary,
                )
                Text(
                    "Time: ${formatDuration(result.durationMillis)}",
                    color = MgpColors.TextMuted,
                )
            }
        }
        Text(
            "Practice is free and doesn't count. Ready to compete for coins?",
            color = MgpColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onPlayForCoins, modifier = Modifier.fillMaxWidth()) { Text("Play for coins") }
        OutlinedButton(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) { Text("Practice again") }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
