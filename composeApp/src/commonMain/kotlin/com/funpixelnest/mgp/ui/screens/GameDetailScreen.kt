package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.JoinResult
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch

@Composable
fun GameDetailScreen(
    game: Game,
    repository: PlatformRepository,
    coins: Long,
    onJoined: (MatchTicket) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var joiningId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon(game, size = 64.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(game.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(game.category, color = MgpColors.TextMuted)
                }
            }
        }
        item {
            Text(
                "Tournaments · 5 players · same board for everyone",
                color = MgpColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        message?.let { text ->
            item { Text(text, color = Color(0xFFFF8A80)) }
        }
        items(repository.tournamentsFor(game.id), key = { it.id }) { tournament ->
            TournamentCard(
                tournament = tournament,
                accent = Color(game.accentColor),
                canAfford = coins >= tournament.entryFee,
                isJoining = joiningId == tournament.id,
                onJoin = {
                    joiningId = tournament.id
                    message = null
                    scope.launch {
                        when (val result = repository.joinTournament(tournament.id)) {
                            is JoinResult.Joined -> onJoined(result.ticket)
                            JoinResult.InsufficientCoins -> message = "Not enough coins for this tournament."
                            is JoinResult.Error -> message = result.message
                        }
                        joiningId = null
                    }
                },
            )
        }
    }
}

@Composable
fun TournamentCard(
    tournament: Tournament,
    accent: Color,
    canAfford: Boolean,
    isJoining: Boolean,
    onJoin: () -> Unit,
    /** Shown above the title, e.g. the game name on the Featured tab. */
    overline: String? = null,
    /** When set, the game's icon is shown at the start of the card (Featured tab). */
    leadingGame: Game? = null,
) {
    val payouts = Leaderboard.prizePayouts(tournament)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MgpColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingGame?.let {
                GameIcon(it, size = 48.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                overline?.let { Text(it, color = MgpColors.Primary, style = MaterialTheme.typography.labelMedium) }
                Text(tournament.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Prize pool 🪙 ${formatCoins(Leaderboard.prizePool(tournament))}",
                    color = MgpColors.TextMuted,
                )
            }
            Button(
                onClick = onJoin,
                enabled = canAfford && !isJoining,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            ) {
                Text(if (isJoining) "Joining…" else "Join 🪙 ${formatCoins(tournament.entryFee)}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            payouts.forEachIndexed { index, prize ->
                PrizeChip(rank = index + 1, prize = prize)
            }
        }
    }
}

@Composable
fun PrizeChip(rank: Int, prize: Long) {
    val label = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }
    Text(
        text = "$label ${formatCoins(prize)}",
        modifier = Modifier
            .background(MgpColors.SurfaceRaised, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}
