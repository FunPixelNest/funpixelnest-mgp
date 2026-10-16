package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.Wallet
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors

// ------------------------------------------------------------------ My Games

@Composable
fun MyGamesScreen(
    pools: List<Pool>,
    repository: PlatformRepository,
    onPoolClick: (Pool) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pools.isEmpty()) {
        EmptyState("🏆", "No tournaments yet", "Join a tournament from the Play tab.", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                Text(game?.emoji ?: "🎮", fontSize = 32.sp)
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
    }
}

// ------------------------------------------------------------------ Wallet

@Composable
fun WalletScreen(wallet: Wallet, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MgpColors.SurfaceRaised, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Balance", color = MgpColors.TextMuted)
                Text(
                    "🪙 ${formatCoins(wallet.coins)}",
                    color = MgpColors.Primary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Virtual coins (no real-money value yet)",
                    color = MgpColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Text("History", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        items(wallet.transactions, key = { it.id }) { tx ->
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

// ------------------------------------------------------------------ Profile

@Composable
fun ProfileScreen(
    repository: PlatformRepository,
    pools: List<Pool>,
    modifier: Modifier = Modifier,
) {
    val completed = pools.filter { it.status == PoolStatus.COMPLETED }
    val myRanks = completed.mapNotNull { pool ->
        val tournament = repository.tournament(pool.tournamentId) ?: return@mapNotNull null
        Leaderboard.rank(pool, tournament).find { it.entry.playerId == repository.player.id }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MgpColors.Surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("👤", fontSize = 48.sp)
            Text(repository.player.displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Guest account", color = MgpColors.TextMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Played", pools.size.toString(), Modifier.weight(1f))
            StatTile("Wins", myRanks.count { it.rank == 1 }.toString(), Modifier.weight(1f))
            StatTile("Top 3", myRanks.count { it.prize > 0 }.toString(), Modifier.weight(1f))
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

@Composable
private fun EmptyState(icon: String, title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, fontSize = 48.sp)
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MgpColors.TextMuted, textAlign = TextAlign.Center)
        }
    }
}
