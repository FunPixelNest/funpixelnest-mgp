package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.theme.MgpColors
import com.funpixelnest.mgp.ui.theme.medalColor

@Composable
fun PoolLeaderboardScreen(
    poolId: String,
    repository: PlatformRepository,
    modifier: Modifier = Modifier,
) {
    val pool by remember(poolId) { repository.observePool(poolId) }.collectAsState(initial = null)
    val current = pool
    val tournament = current?.let { repository.tournament(it.tournamentId) }

    if (current == null || tournament == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val rows = Leaderboard.rank(current, tournament)
    val emptySlots = tournament.poolSize - current.entries.size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatusHeader(current, tournament, repository) }
        if (tournament.isDuel && current.status == PoolStatus.COMPLETED) {
            item { DuelResultBanner(rows = rows, myId = repository.player.id) }
        }
        items(rows, key = { it.entry.playerId }) { row ->
            LeaderboardRow(row = row, isMe = row.entry.playerId == repository.player.id)
        }
        items(emptySlots.coerceAtLeast(0)) { EmptySlotRow() }
    }
}

@Composable
private fun StatusHeader(pool: Pool, tournament: Tournament, repository: PlatformRepository) {
    val game = repository.game(tournament.gameId)
    val joined = pool.entries.size
    val submitted = pool.entries.count { it.hasSubmitted }
    val opponent = pool.entries.firstOrNull { it.playerId != repository.player.id }
    val status = if (tournament.isDuel) {
        when (pool.status) {
            PoolStatus.OPEN -> "Finding your opponent… Your score is saved; we'll match you with the next player."
            PoolStatus.FULL -> "Waiting for ${opponent?.displayName ?: "your opponent"} to finish"
            PoolStatus.COMPLETED -> "Final result"
        }
    } else {
        when (pool.status) {
            PoolStatus.OPEN -> "Waiting for ${tournament.poolSize - joined} more player(s) to join"
            PoolStatus.FULL -> "Waiting for ${tournament.poolSize - submitted} player(s) to finish"
            PoolStatus.COMPLETED -> "Final results"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MgpColors.Surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            game?.let {
                GameIcon(it, size = 36.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (tournament.isDuel) "${game?.name ?: ""} · 1v1 ${tournament.title}" else "${game?.name ?: ""} · ${tournament.title}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pool.status != PoolStatus.COMPLETED) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                status,
                color = if (pool.status == PoolStatus.COMPLETED) MgpColors.Win else MgpColors.TextMuted,
            )
        }
        if (tournament.isDuel) {
            Text(
                "Winner takes 🪙 ${formatCoins(Leaderboard.prizePool(tournament))}",
                fontWeight = FontWeight.Bold,
                color = MgpColors.Primary,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Leaderboard.prizePayouts(tournament).forEachIndexed { index, prize ->
                    PrizeChip(rank = index + 1, prize = prize)
                }
            }
        }
    }
}

/** Big "You won / You lost" card at the top of a finished duel. */
@Composable
private fun DuelResultBanner(rows: List<Leaderboard.Row>, myId: String) {
    val me = rows.firstOrNull { it.entry.playerId == myId }
    val winner = rows.firstOrNull { it.rank == 1 }
    val iWon = me?.rank == 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (iWon) MgpColors.Win else MgpColors.Secondary, RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (iWon) "🏆 You won!" else "${winner?.entry?.displayName ?: "Your opponent"} won",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (iWon) "+🪙 ${formatCoins(me?.prize ?: 0L)} added to your balance" else "Close one! Try a rematch.",
            color = Color.White,
        )
    }
}

@Composable
private fun LeaderboardRow(row: Leaderboard.Row, isMe: Boolean) {
    val medal = medalColor(row.rank)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MgpColors.Surface, RoundedCornerShape(14.dp))
            .then(
                if (isMe) Modifier.border(2.dp, MgpColors.Primary, RoundedCornerShape(14.dp)) else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(medal ?: MgpColors.SurfaceRaised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                row.rank?.toString() ?: "–",
                fontWeight = FontWeight.Bold,
                color = if (medal != null) Color.Black else MgpColors.TextPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.entry.displayName, fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal)
            if (!row.entry.hasSubmitted) {
                Text("Playing…", color = MgpColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            row.entry.score?.let { Text(formatCoins(it), fontWeight = FontWeight.Bold) }
            if (row.prize > 0) {
                Text("+🪙 ${formatCoins(row.prize)}", color = MgpColors.Win, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptySlotRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MgpColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text("Waiting for player…", color = MgpColors.TextMuted)
    }
}
