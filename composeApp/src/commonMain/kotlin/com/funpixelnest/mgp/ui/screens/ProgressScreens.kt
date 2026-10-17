package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Challenge
import com.funpixelnest.mgp.domain.ChallengeCategory
import com.funpixelnest.mgp.domain.LeagueRow
import com.funpixelnest.mgp.domain.LeagueTier
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.Avatar
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.CoinAmount
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.Panel
import com.funpixelnest.mgp.ui.components.ProgressPill
import com.funpixelnest.mgp.ui.components.ResetTimer
import com.funpixelnest.mgp.ui.components.RewardChip
import com.funpixelnest.mgp.ui.components.SectionHeader
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.components.formatCountdown
import com.funpixelnest.mgp.ui.components.gameIconResource
import com.funpixelnest.mgp.ui.components.rememberCountdown
import com.funpixelnest.mgp.ui.theme.MgpColors

// ---------------------------------------------------------------- Challenges

/** Challenges tab: daily list, and weekly game / tournament / league / special challenges. */
@Composable
fun ChallengesScreen(
    repository: PlatformRepository,
    onClaim: (Challenge) -> Unit,
    onGo: (Challenge) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.challenges.collectAsState()
    var weekly by remember { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(Art.target, 66.dp)
                    Spacer(Modifier.width(10.dp))
                    DisplayText("CHALLENGES", size = 36.sp, maxLines = 1)
                }
                Text("Complete challenges and earn amazing rewards!", color = MgpColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF4F8FF)),
            ) {
                SegmentTab("Daily", Icons.Rounded.EventAvailable, !weekly, Modifier.weight(1f)) { weekly = false }
                SegmentTab("Weekly", Icons.Rounded.CalendarMonth, weekly, Modifier.weight(1f)) { weekly = true }
            }
        }
        if (!weekly) {
            item {
                Panel(padding = 12.dp) {
                    SectionHeader(
                        "Daily challenges",
                        vector = Icons.Rounded.Schedule,
                        color = MgpColors.Navy,
                        trailing = { ResetTimer("Resets in:", state.dailyResetsInMillis) },
                    )
                    Spacer(Modifier.height(8.dp))
                    state.of(ChallengeCategory.DAILY).forEach { ChallengeRow(it, onClaim, onGo) }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ResetTimer("Weekly reset in:", state.weeklyResetsInMillis, icon = Icons.Rounded.Schedule)
                }
            }
            item { MiniSection("Game challenges", Icons.Rounded.SportsEsports, Color(0xFF2E9A1C), state.of(ChallengeCategory.GAME), onClaim, onGo) }
            item { MiniSection("Tournament challenges", Icons.Rounded.EmojiEvents, MgpColors.Purple, state.of(ChallengeCategory.TOURNAMENT), onClaim, onGo) }
            item { MiniSection("League challenges", Icons.Rounded.WorkspacePremium, MgpColors.Orange, state.of(ChallengeCategory.LEAGUE), onClaim, onGo) }
            item {
                Panel(padding = 12.dp) {
                    SectionHeader("Special challenges", vector = Icons.Rounded.CardGiftcard, color = Color(0xFFE5342B))
                    Spacer(Modifier.height(8.dp))
                    state.of(ChallengeCategory.SPECIAL).forEach { special ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtImage(Art.challenge(special.id), 70.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(special.title, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(special.subtitle, color = MgpColors.TextMuted, fontSize = 12.sp)
                                ProgressPill(special.fraction, Modifier.fillMaxWidth().padding(top = 6.dp), "${special.progress} / ${special.target}")
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RewardChip(special.reward, fontSize = 13.sp, iconSize = 18.dp)
                                Spacer(Modifier.height(6.dp))
                                ChallengeButton(special, onClaim, onGo)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun SegmentTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Brush.verticalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF1458E0))) else Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F5FD))))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Color.White else MgpColors.Primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(label.uppercase(), color = if (selected) Color.White else MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
    }
}

@Composable
private fun ChallengeRow(challenge: Challenge, onClaim: (Challenge) -> Unit, onGo: (Challenge) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(62.dp).shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F6FD)),
            contentAlignment = Alignment.Center,
        ) { ArtImage(Art.challenge(challenge.id), 46.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(challenge.title, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(challenge.subtitle, color = MgpColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressPill(
                challenge.fraction,
                Modifier.fillMaxWidth().padding(top = 5.dp),
                "${formatCoins(challenge.progress)} / ${formatCoins(challenge.target)}",
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RewardChip(challenge.reward, fontSize = 14.sp, iconSize = 20.dp)
            ChallengeButton(challenge, onClaim, onGo)
        }
    }
}

@Composable
private fun ChallengeButton(challenge: Challenge, onClaim: (Challenge) -> Unit, onGo: (Challenge) -> Unit) {
    when {
        challenge.claimed -> Box(Modifier.width(70.dp).height(42.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CheckCircle, "Claimed", tint = MgpColors.Green, modifier = Modifier.size(34.dp))
        }
        challenge.canClaim -> GlossyButton("Claim", { onClaim(challenge) }, color = ButtonColor.Green, height = 42.dp, fontSize = 14.sp, modifier = Modifier.width(76.dp))
        else -> GlossyButton("Go", { onGo(challenge) }, height = 42.dp, fontSize = 17.sp, modifier = Modifier.width(70.dp))
    }
}

@Composable
private fun MiniSection(
    title: String,
    icon: ImageVector,
    color: Color,
    challenges: List<Challenge>,
    onClaim: (Challenge) -> Unit,
    onGo: (Challenge) -> Unit,
) {
    Panel(padding = 12.dp) {
        SectionHeader(title, vector = icon, color = color)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            challenges.forEach { c ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, MgpColors.Border, RoundedCornerShape(14.dp))
                        .clickable { if (c.canClaim) onClaim(c) else if (!c.claimed) onGo(c) }
                        .padding(8.dp),
                ) {
                    val gameArt = c.gameId?.let { gameIconResource(it) }
                    if (gameArt != null) {
                        ArtImage(gameArt, 44.dp, Modifier.clip(RoundedCornerShape(10.dp)))
                    } else {
                        ArtImage(Art.challenge(c.id), 44.dp)
                    }
                    Text(c.title, color = MgpColors.Navy, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp).weight(1f))
                    ProgressPill(c.fraction, Modifier.fillMaxWidth().padding(vertical = 4.dp), "${formatCompact(c.progress)}/${formatCompact(c.target)}", color = color, height = 14.dp)
                    when {
                        c.claimed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = MgpColors.Green, modifier = Modifier.size(16.dp))
                            Text(" Claimed", color = MgpColors.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        c.canClaim -> GlossyButton("Claim", { onClaim(c) }, color = ButtonColor.Green, height = 32.dp, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (c.reward.coins > 0) CoinAmount(c.reward.coins, iconSize = 16.dp, fontSize = 13.sp)
                            if (c.reward.gems > 0) CoinAmount(c.reward.gems, iconSize = 16.dp, fontSize = 13.sp, gems = true)
                        }
                    }
                }
            }
        }
    }
}

private fun formatCompact(v: Long): String = if (v >= 10_000) "${v / 1_000}K" else formatCoins(v)

// ---------------------------------------------------------------- Leagues

/** Leagues tab: current tier + points, the six tiers, this week's top players. */
@Composable
fun LeaguesScreen(
    repository: PlatformRepository,
    onPlayNow: () -> Unit,
    onViewRewards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val league by repository.league.collectAsState()
    val profile by repository.profile.collectAsState()
    val tier = league.tier
    val next = tier.next
    val seasonLeft = rememberCountdown(league.seasonEndsInMillis)
    val me = repository.player

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(Art.leagueShield, 70.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    DisplayText("LEAGUES", size = 36.sp, maxLines = 1)
                    Text("Compete in leagues, earn points and climb the ranks!", color = MgpColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier.shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("SEASON ENDS IN", color = MgpColors.Navy, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = MgpColors.Primary, modifier = Modifier.size(16.dp))
                        Text(" ${formatCountdown(seasonLeft)}", color = MgpColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0E2A78), Color(0xFF1C4FB8), Color(0xFF0F2B70))))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(Art.league(tier), 112.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(tier.label.uppercase()) }
                                append(" LEAGUE")
                            },
                            color = Color.White,
                            fontSize = 24.sp,
                        )
                        tier.division(league.trophies)?.let { Text("DIVISION $it", color = Color(0xFFBFD6FF), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        Text("Your League Points", color = Color.White, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = MgpColors.Gold, modifier = Modifier.size(26.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)) { append(" ${formatCoins(league.trophies)}") }
                                    if (next != null) append(" / ${formatCoins(next.minTrophies)}")
                                },
                                color = Color.White,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (next != null) {
                    val span = (next.minTrophies - tier.minTrophies).coerceAtLeast(1)
                    val fraction = ((league.trophies - tier.minTrophies).toFloat() / span).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressPill(fraction, Modifier.weight(1f), color = MgpColors.PrimaryLight, height = 14.dp, track = Color(0xFF0A1B4D))
                        Spacer(Modifier.width(6.dp))
                        ArtImage(Art.league(next), 34.dp)
                    }
                    Text("${formatCoins(next.minTrophies - league.trophies)} more to promote", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x332E6CF2)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("PROMOTION REWARD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                                CoinAmount(next.rewardCoins, color = Color.White, iconSize = 26.dp, fontSize = 18.sp)
                                CoinAmount(next.rewardGems, color = Color.White, iconSize = 26.dp, fontSize = 18.sp, gems = true)
                            }
                        }
                        GlossyButton("View rewards", onViewRewards, height = 44.dp, fontSize = 14.sp)
                    }
                } else {
                    Text("You're in the top league. Keep winning to stay on top!", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        item {
            Panel(padding = 12.dp) {
                SectionHeader("League tiers", trailing = {
                    Text("View rewards", color = MgpColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onViewRewards))
                })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LeagueTier.entries.forEach { t ->
                        val current = t == tier
                        val passed = t < tier
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (current) Color(0xFFEAF2FF) else Color.White)
                                .border(if (current) 2.dp else 1.dp, if (current) MgpColors.Primary else MgpColors.Border, RoundedCornerShape(12.dp))
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtImage(Art.league(t), 44.dp)
                            Text(t.label.uppercase(), color = MgpColors.Navy, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            when {
                                current -> Text("CURRENT", color = MgpColors.Primary, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                                passed -> Icon(Icons.Rounded.CheckCircle, null, tint = MgpColors.Green, modifier = Modifier.size(16.dp))
                                else -> Icon(Icons.Rounded.Lock, null, tint = Color(0xFF6E86B5), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel(padding = 12.dp) {
                SectionHeader("Top players", trailing = { ResetTimer("Resets in:", league.seasonEndsInMillis, icon = Icons.Rounded.Schedule) })
                Spacer(Modifier.height(6.dp))
                if (league.top.isEmpty()) {
                    Text("No points yet this week. Win matches to get on the board!", color = MgpColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                }
                league.top.forEach { row -> LeagueRowView(row, isMe = row.playerId == me.id) }
                if (league.top.none { it.playerId == me.id }) {
                    LeagueRowView(
                        LeagueRow(league.myRank ?: 0, me.id, me.displayName, profile.avatarId, league.seasonPoints, tier),
                        isMe = true,
                        youLabel = true,
                    )
                }
            }
        }
        item {
            Panel(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(Art.trophyConfetti, 64.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Play More, Earn More!", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("Win games, complete challenges and climb the league leaderboard.", color = MgpColors.TextMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlossyButton("Play now", onPlayNow, color = ButtonColor.Green, height = 48.dp, fontSize = 15.sp, icon = Icons.AutoMirrored.Rounded.ArrowForwardIos)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MgpColors.Primary, modifier = Modifier.size(14.dp))
                Text(
                    " League points: 1st 100 · 2nd 60 · 3rd 30 · played 10 · duel win 80",
                    color = MgpColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LeagueRowView(row: LeagueRow, isMe: Boolean, youLabel: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) Color(0xFFE6EFFC) else Color.White)
            .border(1.dp, MgpColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            val medal = Art.medal(row.rank, small = true)
            when {
                youLabel -> Text("You", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                medal != null -> ArtImage(medal, 34.dp)
                else -> Text("${row.rank}", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.width(6.dp))
        Avatar(row.avatarId, 48.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.displayName, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(Art.league(row.tier), 16.dp)
                Text(" ${row.tier.label} League" + if (youLabel && row.rank > 0) " · #${row.rank}" else "", color = MgpColors.TextMuted, fontSize = 12.sp)
            }
        }
        Icon(Icons.Rounded.Star, null, tint = MgpColors.Gold, modifier = Modifier.size(24.dp))
        Text(" ${formatCoins(row.points)}", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}
