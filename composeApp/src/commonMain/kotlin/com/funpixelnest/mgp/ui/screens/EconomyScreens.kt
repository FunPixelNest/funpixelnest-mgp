package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Challenge
import com.funpixelnest.mgp.domain.ChallengeCategory
import com.funpixelnest.mgp.domain.ChestOffer
import com.funpixelnest.mgp.domain.DailyBonusDay
import com.funpixelnest.mgp.domain.DailyBonusState
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.CoinAmount
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.Hint
import com.funpixelnest.mgp.ui.components.Panel
import com.funpixelnest.mgp.ui.components.ProgressPill
import com.funpixelnest.mgp.ui.components.ResetTimer
import com.funpixelnest.mgp.ui.components.RewardChip
import com.funpixelnest.mgp.ui.components.ScreenHeader
import com.funpixelnest.mgp.ui.components.SectionHeader
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.components.formatCountdown
import com.funpixelnest.mgp.ui.components.formatShort
import com.funpixelnest.mgp.ui.components.rememberCountdown
import com.funpixelnest.mgp.ui.theme.MgpColors
import org.jetbrains.compose.resources.DrawableResource

/** Actions the Store and Rewards tabs trigger (handled in App so pop-ups can show results). */
class EconomyActions(
    val buyPack: (StorePack) -> Unit,
    val watchVideo: () -> Unit,
    val invite: () -> Unit,
    val rate: () -> Unit,
    val claimDaily: () -> Unit,
    val spin: () -> Unit,
    val openChest: (String) -> Unit,
    val claimChallenge: (Challenge) -> Unit,
)

// ---------------------------------------------------------------- Store

@Composable
fun StoreScreen(repository: PlatformRepository, actions: EconomyActions, modifier: Modifier = Modifier) {
    val shop by repository.shop.collectAsState()
    val bonus by repository.dailyBonus.collectAsState()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(Art.cart, "Store", "Buy coins, get rewards and more!", artInTile = true) }
        item {
            Panel(padding = 0.dp) {
                Text(
                    "BUY COINS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 22.dp, bottomEnd = 22.dp))
                        .background(Brush.horizontalGradient(listOf(MgpColors.Primary, MgpColors.PrimaryLight)))
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repository.storePacks().forEachIndexed { index, pack -> PackCard(pack, index, onBuy = { actions.buyPack(pack) }) }
                }
                Hint("Payments aren't connected yet, so buying is a free demo.", Modifier.fillMaxWidth().padding(bottom = 10.dp))
            }
        }
        item {
            Panel(padding = 12.dp) {
                SectionHeader("Earn more coins", icon = Art.coinsStack)
                Spacer(Modifier.height(10.dp))
                val today = bonus.rewards.getOrNull(bonus.day - 1)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EarnCard(Art.earnVideo, "Watch Video", "Watch a video and get extra coins!", Modifier.weight(1f),
                        reward = RewardGrant(shop.freeCoinsAmount),
                        button = if (shop.freeCoinsLeftToday > 0) "Watch" else null,
                        doneText = "Back tomorrow",
                        onClick = actions.watchVideo)
                    EarnCard(Art.earnInvite, "Invite Your Friend", "Share your code and play together!", Modifier.weight(1f),
                        button = "Invite", onClick = actions.invite)
                    EarnCard(Art.earnRate, "Rate The Game", "Tell us what you think on the store!", Modifier.weight(1f),
                        button = "Rate", onClick = actions.rate)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EarnCard(Art.earnCheckin, "Daily Check-in", "Check in daily and earn free rewards!", Modifier.weight(1f),
                        reward = today?.let { RewardGrant(it.coins, it.gems) }?.takeIf { !it.isEmpty },
                        button = if (bonus.claimedToday) null else "Claim",
                        doneText = "Claimed",
                        doneCheck = bonus.claimedToday,
                        onClick = actions.claimDaily)
                    EarnCard(Art.earnSpin, "Lucky Spin", "Spin the wheel and win exciting rewards!", Modifier.weight(1f),
                        timerMillis = shop.spinReadyInMillis.takeIf { it > 0 },
                        button = if (shop.spinReadyInMillis <= 0) "Spin now" else null,
                        onClick = actions.spin)
                    EarnCard(Art.freeChest, "Free Chest", "Open a chest and get amazing rewards!", Modifier.weight(1f),
                        timerMillis = shop.freeChestReadyInMillis.takeIf { it > 0 },
                        button = if (shop.freeChestReadyInMillis <= 0) "Open now" else null,
                        onClick = { actions.openChest("free") })
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
fun PackCard(
    pack: StorePack,
    index: Int,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier.width(108.dp),
    height: Dp = 200.dp,
    artSize: Dp = 78.dp,
) {
    val best = pack.tag != null
    Box(modifier) {
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(MgpColors.Card, MgpColors.CardLow)))
                .border(if (best) 2.dp else 1.dp, if (best) MgpColors.Primary else MgpColors.Border, RoundedCornerShape(16.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (best) 10.dp else 4.dp))
            DisplayText(formatShort(pack.totalCoins), size = 26.sp, maxLines = 1)
            Text("COINS", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ArtImage(Art.pack(index), artSize) }
            GlossyButton(
                pack.priceLabel,
                onBuy,
                color = if (best) ButtonColor.Pink else ButtonColor.Blue,
                height = 40.dp,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (best) {
            Text(
                pack.tag!!.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MgpColors.Pink)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun EarnCard(
    art: DrawableResource,
    title: String,
    subtitle: String,
    modifier: Modifier,
    reward: RewardGrant? = null,
    timerMillis: Long? = null,
    button: String?,
    doneText: String? = null,
    doneCheck: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MgpColors.Card)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (timerMillis != null) {
            val left = rememberCountdown(timerMillis)
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(MgpColors.SurfaceRaised).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = MgpColors.Navy, modifier = Modifier.size(12.dp))
                Text(" ${formatCountdown(left)}", color = MgpColors.Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        ArtImage(art, 58.dp)
        Text(title, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
        Text(subtitle, color = MgpColors.TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp, modifier = Modifier.weight(1f).padding(top = 2.dp))
        if (reward != null) RewardChip(reward, Modifier.padding(vertical = 4.dp), fontSize = 13.sp, iconSize = 18.dp)
        when {
            doneCheck -> Box(
                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).background(if (MgpColors.isDark) Color(0xFF1F3B2A) else Color(0xFFE9F7E4)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.CheckCircle, doneText, tint = MgpColors.Green, modifier = Modifier.size(26.dp)) }
            button != null -> GlossyButton(button, onClick, height = 38.dp, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            else -> Text(doneText ?: "Soon", color = MgpColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.height(36.dp).padding(top = 10.dp))
        }
    }
}

// ---------------------------------------------------------------- Rewards

@Composable
fun RewardsScreen(repository: PlatformRepository, actions: EconomyActions, onViewChallenges: () -> Unit, modifier: Modifier = Modifier) {
    val bonus by repository.dailyBonus.collectAsState()
    val shop by repository.shop.collectAsState()
    val challenges by repository.challenges.collectAsState()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(Art.giftSmall, "Rewards", "Play, complete challenges and earn exciting rewards!") }
        item {
            Panel(padding = 12.dp) {
                SectionHeader("Challenge rewards", icon = Art.target, trailing = {
                    Text("View all", color = MgpColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onViewChallenges))
                })
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    challenges.of(ChallengeCategory.MILESTONE).forEach { c -> MilestoneCard(c, Modifier.weight(1f)) { actions.claimChallenge(c) } }
                }
            }
        }
        item {
            Panel(padding = 12.dp) {
                SectionHeader("Other ways to earn")
                Spacer(Modifier.height(6.dp))
                val today = bonus.rewards.getOrNull(bonus.day - 1)
                OtherWayRow(Art.earnVideo, "Watch Video", "Watch videos and earn rewards", RewardGrant(shop.freeCoinsAmount),
                    button = if (shop.freeCoinsLeftToday > 0) "Watch" else null, done = shop.freeCoinsLeftToday <= 0, onClick = actions.watchVideo)
                OtherWayRow(Art.earnCheckin, "Login Bonus", "Log in daily and claim bonuses",
                    today?.let { RewardGrant(it.coins, it.gems) }?.takeIf { !it.isEmpty },
                    button = if (bonus.claimedToday) null else "Claim", done = bonus.claimedToday, onClick = actions.claimDaily)
                OtherWayRow(Art.earnInvite, "Invite Friends", "Invite friends and play together", null,
                    button = "Invite", done = false, onClick = actions.invite)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** The 7-day strip used on the Rewards tab and in the Daily Bonus pop-up. */
@Composable
fun DailyBonusRow(bonus: DailyBonusState, onClaim: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bonus.rewards.forEach { day ->
            DayTile(day, bonus, Modifier.weight(if (day.day == 7) 1.3f else 1f), onClaim)
        }
    }
}

@Composable
fun DayTile(day: DailyBonusDay, bonus: DailyBonusState, modifier: Modifier, onClaim: () -> Unit, big: Boolean = false) {
    val claimed = day.day < bonus.day || (day.day == bonus.day && bonus.claimedToday)
    val isToday = day.day == bonus.day && !bonus.claimedToday
    val epic = day.chest != null
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    epic -> Brush.verticalGradient(if (MgpColors.isDark) listOf(Color(0xFF3A2A66), Color(0xFF2A2052)) else listOf(Color(0xFFEBDDFF), Color(0xFFF7F0FF)))
                    else -> Brush.verticalGradient(listOf(MgpColors.Card, MgpColors.CardLow))
                },
            )
            .border(if (isToday) 2.dp else 1.dp, if (isToday) MgpColors.Primary else MgpColors.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = isToday, onClick = onClaim)
            .padding(vertical = if (big) 8.dp else 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "DAY ${day.day}",
            color = if (epic) MgpColors.Purple else MgpColors.Navy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (big) 15.sp else 10.sp,
            maxLines = 1,
        )
        Box(Modifier.height(if (big) 22.dp else 18.dp), contentAlignment = Alignment.Center) {
            when {
                claimed -> Icon(Icons.Rounded.CheckCircle, "Claimed", tint = MgpColors.Green, modifier = Modifier.size(if (big) 20.dp else 16.dp))
                isToday -> Text(
                    "TODAY",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MgpColors.Primary).padding(horizontal = 4.dp, vertical = 1.dp),
                )
                else -> Icon(Icons.Rounded.Lock, "Locked", tint = Color(0xFF8AA0C8), modifier = Modifier.size(if (big) 18.dp else 14.dp))
            }
        }
        val artSize = if (big) 62.dp else 34.dp
        when {
            epic -> ArtImage(Art.chestEpic, artSize + 6.dp)
            day.gems > 0 -> ArtImage(Art.gem, artSize * 0.8f)
            else -> ArtImage(Art.coinsStack, artSize)
        }
        Text(
            when {
                epic -> if (big) "EPIC CHEST" else "EPIC"
                day.gems > 0 -> "${day.gems}"
                else -> if (big) formatCoins(day.coins) else formatShort(day.coins)
            },
            color = if (epic) MgpColors.Purple else MgpColors.Navy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (big) 18.sp else 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        if (big && !epic) {
            Text(if (day.gems > 0) "GEMS" else "COINS", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MilestoneCard(c: Challenge, modifier: Modifier, onClaim: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(MgpColors.Card)
            .border(1.dp, MgpColors.Border, RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtImage(Art.challenge(c.id), 46.dp)
        Text(c.title, color = MgpColors.Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp, modifier = Modifier.weight(1f).padding(top = 2.dp))
        ProgressPill(c.fraction, Modifier.fillMaxWidth().padding(vertical = 4.dp), "${formatShort(c.progress)}/${formatShort(c.target)}", height = 13.dp)
        when {
            c.claimed -> Icon(Icons.Rounded.CheckCircle, "Claimed", tint = MgpColors.Green, modifier = Modifier.size(22.dp))
            c.canClaim -> GlossyButton("Claim", onClaim, color = ButtonColor.Green, height = 30.dp, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
            else -> {
                Text("Reward", color = MgpColors.TextMuted, fontSize = 10.sp)
                if (c.reward.coins > 0) CoinAmount(c.reward.coins, iconSize = 16.dp, fontSize = 13.sp)
                if (c.reward.gems > 0) CoinAmount(c.reward.gems, iconSize = 16.dp, fontSize = 13.sp, gems = true)
            }
        }
    }
}

@Composable
private fun ChestCard(chest: ChestOffer, modifier: Modifier, onOpen: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(MgpColors.Card)
            .border(1.dp, MgpColors.Border, RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(chest.name.uppercase(), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
        ArtImage(Art.chest(chest.id), 58.dp)
        Text(chest.description, color = MgpColors.Navy, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(4.dp))
        GlossyButton(
            "",
            onOpen,
            color = ButtonColor.Green,
            height = 32.dp,
            modifier = Modifier.fillMaxWidth(),
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OPEN ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    ArtImage(Art.gem, 13.dp)
                    Text(" ${chest.gemCost}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            },
        )
    }
}

@Composable
private fun OtherWayRow(
    art: DrawableResource,
    title: String,
    subtitle: String,
    reward: RewardGrant?,
    button: String?,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(art, 46.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(subtitle, color = MgpColors.TextMuted, fontSize = 12.sp)
        }
        if (reward != null) RewardChip(reward, fontSize = 14.sp, iconSize = 20.dp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Rounded.CheckCircle, "Done", tint = MgpColors.Green, modifier = Modifier.size(32.dp))
                button != null -> GlossyButton(button, onClick, height = 40.dp, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
