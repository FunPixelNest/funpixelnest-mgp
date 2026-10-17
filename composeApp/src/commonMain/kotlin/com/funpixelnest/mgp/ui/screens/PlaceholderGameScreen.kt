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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlin.random.Random

/**
 * Stand-in for the Unity game until it's integrated.
 * It shows exactly what Unity will receive (game + seed) and returns a demo score.
 */
@Composable
fun PlaceholderGameScreen(
    game: Game,
    ticket: MatchTicket,
    /** Null result = the player quit. */
    onFinished: (GameResult?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var submitting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        GameIcon(game, size = 96.dp)
        Text(game.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MgpColors.Surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("This game's build isn't added yet", fontWeight = FontWeight.Bold)
            Text("Launch data sent to Unity:", color = MgpColors.TextMuted)
            Text("gameId = ${ticket.gameId}", color = MgpColors.TextMuted)
            Text("seed = ${ticket.seed}", color = MgpColors.TextMuted)
            Text("ticket = ${ticket.ticketId}", color = MgpColors.TextMuted)
        }
        Button(
            onClick = {
                submitting = true
                onFinished(
                    GameResult(
                        score = Random.nextLong(1_000, 10_000),
                        durationMillis = Random.nextLong(60_000, 240_000),
                    ),
                )
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ticket.isPractice) "Finish with a demo score" else "Finish & submit demo score")
        }
        OutlinedButton(
            onClick = {
                submitting = true
                onFinished(null)
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ticket.isPractice) "Quit" else "Quit (counts as score 0)")
        }
        if (!ticket.isPractice) Text(
            "Your entry is already paid, so quitting still submits a score of 0.",
            color = MgpColors.TextMuted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
