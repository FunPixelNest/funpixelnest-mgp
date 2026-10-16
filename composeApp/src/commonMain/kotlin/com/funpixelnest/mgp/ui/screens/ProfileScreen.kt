package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.Wallet
import com.funpixelnest.mgp.ui.components.SectionTitle
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.theme.MgpColors

private const val HISTORY_LIMIT = 15

/** Profile tab: player card, stats, balance, my tournaments and coin history. */
@Composable
fun ProfileScreen(
    repository: PlatformRepository,
    pools: List<Pool>,
    wallet: Wallet,
    onPoolClick: (Pool) -> Unit,
    onGetCoins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val myRanks = pools
        .filter { it.status == PoolStatus.COMPLETED }
        .mapNotNull { pool ->
            val tournament = repository.tournament(pool.tournamentId) ?: return@mapNotNull null
            Leaderboard.rank(pool, tournament).find { it.entry.playerId == repository.player.id }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MgpColors.Surface, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("👤", fontSize = 44.sp)
                Column(Modifier.weight(1f)) {
                    Text(repository.player.displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Guest account", color = MgpColors.TextMuted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Played", pools.size.toString(), Modifier.weight(1f))
                StatTile("Wins", myRanks.count { it.rank == 1 }.toString(), Modifier.weight(1f))
                StatTile("Top 3", myRanks.count { it.prize > 0 }.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MgpColors.SurfaceRaised, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Balance", color = MgpColors.TextMuted)
                    Text(
                        "🪙 ${formatCoins(wallet.coins)}",
                        color = MgpColors.Primary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Virtual coins (no real-money value yet)",
                        color = MgpColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onGetCoins) { Text("Get coins") }
            }
        }

        item { SectionTitle("My tournaments") }
        if (pools.isEmpty()) {
            item { Text("No tournaments yet. Join one from the Games tab.", color = MgpColors.TextMuted) }
        }
        items(pools, key = { it.id }) { pool ->
            val tournament = repository.tournament(pool.tournamentId) ?: return@items
            val game = repository.game(tournament.gameId)
            val myRow = Leaderboard.rank(pool, tournament).find { it.entry.playerId == repository.player.id }
            val statusText = when (pool.status) {
                PoolStatus.OPEN -> "Filling · ${pool.entries.size}/${tournament.poolSize} joined"
                PoolStatus.FULL -> "In progress · ${pool.entries.count { it.hasSubmitted }}/${tournament.poolSize} finished"
                PoolStatus.COMPLETED -> "Finished · Rank #${myRow?.rank ?: "-"}"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MgpColors.Surface, RoundedCornerShape(16.dp))
                    .clickable { onPoolClick(pool) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (game != null) GameIcon(game, size = 40.dp) else Text("🎮", fontSize = 28.sp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text("${game?.name ?: tournament.gameId} · ${tournament.title}", fontWeight = FontWeight.Bold)
                    Text(statusText, color = MgpColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                val prize = myRow?.prize ?: 0L
                if (prize > 0L) {
                    Text("+🪙 ${formatCoins(prize)}", color = MgpColors.Win, fontWeight = FontWeight.Bold)
                }
            }
        }

        item { SectionTitle("Coin history") }
        items(wallet.transactions.take(HISTORY_LIMIT), key = { it.id }) { tx ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MgpColors.Surface, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(tx.description, modifier = Modifier.weight(1f))
                Text(
                    (if (tx.amount > 0) "+" else "") + formatCoins(tx.amount),
                    color = if (tx.amount > 0) MgpColors.Win else MgpColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MgpColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MgpColors.Primary)
        Text(label, color = MgpColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
