package com.funpixelnest.mgp.ui.screens

import com.funpixelnest.mgp.ui.components.CoinText
import com.funpixelnest.mgp.ui.components.CoinIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.DailyReward
import com.funpixelnest.mgp.domain.Mission
import com.funpixelnest.mgp.ui.components.SectionTitle
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch

@Composable
fun RewardsScreen(
    repository: PlatformRepository,
    modifier: Modifier = Modifier,
) {
    val rewards by repository.rewards.collectAsState()
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }

    fun grant(action: suspend () -> Long) {
        scope.launch {
            val coins = action()
            notice = if (coins > 0) "+🪙 ${formatCoins(coins)} added to your balance" else null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        notice?.let { text ->
            item {
                CoinText(
                    text,
                    color = MgpColors.Win,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MgpColors.Surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
        }

        item { SectionTitle("Daily reward") }
        item { DailyRewardCard(rewards.daily, onClaim = { grant { repository.claimDailyReward() } }) }

        item { SectionTitle("Free coins") }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MgpColors.Surface, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Watch a short video", fontWeight = FontWeight.Bold)
                    CoinText(
                        "🪙 ${formatCoins(rewards.freeCoinsAmount)} each · ${rewards.freeCoinsLeftToday} left today",
                        color = MgpColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = { grant { repository.claimFreeCoins() } },
                    enabled = rewards.freeCoinsLeftToday > 0,
                ) {
                    Text("▶ Watch")
                }
            }
        }

        item { SectionTitle("Missions") }
        items(rewards.missions, key = { it.id }) { mission ->
            MissionRow(mission, onClaim = { grant { repository.claimMission(mission.id) } })
        }
        item {
            Text(
                "Video ads aren't connected yet, so Watch grants the coins right away.",
                color = MgpColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DailyRewardCard(daily: DailyReward, onClaim: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MgpColors.Surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            daily.coinsPerDay.forEachIndexed { index, coins ->
                val dayNumber = index + 1
                val claimed = dayNumber < daily.day || (dayNumber == daily.day && daily.claimedToday)
                val isToday = dayNumber == daily.day
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (claimed) MgpColors.Primary.copy(alpha = 0.18f) else MgpColors.SurfaceRaised,
                            RoundedCornerShape(10.dp),
                        )
                        .then(
                            if (isToday) Modifier.border(2.dp, MgpColors.Primary, RoundedCornerShape(10.dp)) else Modifier,
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("D$dayNumber", color = MgpColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    if (claimed) {
                        Icon(Icons.Rounded.Check, contentDescription = "Claimed", tint = MgpColors.Win, modifier = Modifier.size(18.dp))
                    } else {
                        CoinIcon(Modifier.size(18.dp))
                    }
                    Text(
                        formatCoins(coins),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Button(onClick = onClaim, enabled = !daily.claimedToday, modifier = Modifier.fillMaxWidth()) {
            CoinText(
                if (daily.claimedToday) "Come back tomorrow for Day ${daily.day % 7 + 1}"
                else "Claim Day ${daily.day} · 🪙 ${formatCoins(daily.todayCoins)}",
            )
        }
    }
}

@Composable
private fun MissionRow(mission: Mission, onClaim: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MgpColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(mission.title, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { mission.progress.toFloat() / mission.target },
                modifier = Modifier.fillMaxWidth(),
                color = MgpColors.Primary,
                trackColor = MgpColors.SurfaceRaised,
            )
            CoinText(
                "${mission.progress}/${mission.target} · 🪙 ${formatCoins(mission.rewardCoins)}",
                color = MgpColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            mission.claimed -> Text("Claimed", color = MgpColors.Win)
            mission.canClaim -> Button(onClick = onClaim) { Text("Claim") }
            else -> OutlinedButton(onClick = {}, enabled = false) { Text("Claim") }
        }
    }
}
