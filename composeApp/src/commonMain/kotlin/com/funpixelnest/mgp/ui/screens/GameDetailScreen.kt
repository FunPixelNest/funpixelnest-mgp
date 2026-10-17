package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedButton
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
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.SectionTitle
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch

@Composable
fun GameDetailScreen(
    game: Game,
    repository: PlatformRepository,
    coins: Long,
    onJoined: (MatchTicket) -> Unit,
    onPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var joiningId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val tournaments = repository.tournamentsFor(game.id)
    val duels = tournaments.filter { it.isDuel }
    val pools = tournaments.filterNot { it.isDuel }

    fun join(tournament: Tournament) {
        joiningId = tournament.id
        message = null
        scope.launch {
            when (val result = repository.joinTournament(tournament.id)) {
                is JoinResult.Joined -> onJoined(result.ticket)
                JoinResult.InsufficientCoins -> message = "Not enough coins. Get more in the Store or Rewards."
                is JoinResult.Error -> message = result.message
            }
            joiningId = null
        }
    }

    @Composable
    fun card(tournament: Tournament) = TournamentCard(
        tournament = tournament,
        accent = Color(game.accentColor),
        canAfford = coins >= tournament.entryFee,
        isJoining = joiningId == tournament.id,
        onJoin = { join(tournament) },
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon(game, size = 72.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(game.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(game.category, color = MgpColors.TextMuted)
                }
            }
        }

        item { PracticeCard(onPractice = onPractice) }

        message?.let { text ->
            item { Text(text, color = MgpColors.Error, fontWeight = FontWeight.Bold) }
        }

        if (duels.isNotEmpty()) {
            item {
                SectionHeader("1v1 Duels", "Same board as your opponent. Higher score wins the pot.")
            }
            items(duels, key = { it.id }) { card(it) }
        }

        if (pools.isNotEmpty()) {
            item {
                SectionHeader("5-player tournaments", "Same board for all 5. Top 3 win: 50% · 30% · 20%.")
            }
            items(pools, key = { it.id }) { card(it) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle(title)
        Text(subtitle, color = MgpColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PracticeCard(onPractice: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MgpColors.Surface)
            .border(2.dp, MgpColors.Sunshine, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Practice for free", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Learn the game with no coins at stake.", color = MgpColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onPractice) { Text("Practice") }
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
                Text(
                    if (tournament.isDuel) "1v1 · ${tournament.title}" else tournament.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    if (tournament.isDuel) "Winner takes 🪙 ${formatCoins(Leaderboard.prizePool(tournament))}"
                    else "Prize pool 🪙 ${formatCoins(Leaderboard.prizePool(tournament))}",
                    color = MgpColors.TextMuted,
                )
            }
            Button(
                onClick = onJoin,
                enabled = canAfford && !isJoining,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            ) {
                Text(
                    when {
                        isJoining -> "Joining…"
                        tournament.isDuel -> "Play 🪙 ${formatCoins(tournament.entryFee)}"
                        else -> "Join 🪙 ${formatCoins(tournament.entryFee)}"
                    },
                )
            }
        }
        if (!tournament.isDuel) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                payouts.forEachIndexed { index, prize ->
                    PrizeChip(rank = index + 1, prize = prize)
                }
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
