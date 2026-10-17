package com.funpixelnest.mgp.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.data.isDemoBackend
import kotlinx.coroutines.launch
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
                PlayerNameEditor(repository = repository, modifier = Modifier.weight(1f))
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
            val statusText = if (tournament.isDuel) {
                when (pool.status) {
                    PoolStatus.OPEN -> "1v1 · Finding an opponent"
                    PoolStatus.FULL -> "1v1 · Waiting for opponent"
                    PoolStatus.COMPLETED -> if (myRow?.rank == 1) "1v1 · You won" else "1v1 · You lost"
                }
            } else {
                when (pool.status) {
                    PoolStatus.OPEN -> "Filling · ${pool.entries.size}/${tournament.poolSize} joined"
                    PoolStatus.FULL -> "In progress · ${pool.entries.count { it.hasSubmitted }}/${tournament.poolSize} finished"
                    PoolStatus.COMPLETED -> "Finished · Rank #${myRow?.rank ?: "-"}"
                }
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

/** Name with an Edit button; saving goes through the backend so leaderboards show the new name. */
@Composable
private fun PlayerNameEditor(repository: PlatformRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(repository.player.displayName) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!editing) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isDemoBackend) "Guest · demo mode" else "Guest account", color = MgpColors.TextMuted)
                TextButton(onClick = { draft = name; error = null; editing = true }) { Text("Edit name") }
            }
        } else {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(20) },
                singleLine = true,
                label = { Text("Display name") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            error = repository.setDisplayName(draft)
                            if (error == null) {
                                name = repository.player.displayName
                                editing = false
                            }
                            saving = false
                        }
                    },
                ) { Text(if (saving) "Saving…" else "Save") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        }
    }
}
