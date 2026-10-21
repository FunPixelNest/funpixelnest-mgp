package com.funpixelnest.mgp.ui.screens

import androidx.compose.material.icons.rounded.OndemandVideo
import com.funpixelnest.mgp.ui.components.LevelBadge
import com.funpixelnest.mgp.domain.LevelRewards
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.data.SampleCatalog
import com.funpixelnest.mgp.domain.LeagueTier
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.domain.SpinResult
import com.funpixelnest.mgp.ui.Popup
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.CoinAmount
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.ResetTimer
import com.funpixelnest.mgp.ui.components.RewardChip
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch

/** What pop-ups can ask the app to do. */
class PopupActions(
    val dismiss: (Popup) -> Unit,
    val ackWelcome: () -> Unit,
    val claimDaily: () -> Unit,
    val spin: suspend () -> Result<SpinResult>,
    val rename: suspend (String) -> String?,
    val logout: () -> Unit,
    val openStore: () -> Unit,
    /** "Watch video for 2x" can be offered (rewarded ads on). */
    val doubleEnabled: Boolean = false,
    /**
     * Plays a rewarded video, then pays [rewards] again (several for a multi-level level-up).
     * [onDone] gets true when the extra reward was paid.
     */
    val doubleReward: (source: String, rewards: List<RewardGrant>, onDone: (Boolean) -> Unit) -> Unit = { _, _, done -> done(false) },
)

/**
 * Draws the top pop-up (if any) over the whole app. The dim backdrop fades in; the card pops in with a
 * little bounce and shrinks away when closed (also when one pop-up replaces another).
 */
@Composable
fun PopupHost(popup: Popup?, repository: PlatformRepository, actions: PopupActions) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(popup != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99081A44))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (popup != null && popup !is Popup.Welcome) actions.dismiss(popup)
                    },
            )
        }
        AnimatedContent(
            targetState = popup,
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 380f), initialScale = 0.6f) +
                    slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it / 6 }) togetherWith
                    (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.85f))
            },
            label = "popup",
        ) { shown ->
            if (shown != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .imePadding()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) { PopupContent(shown, repository, actions) }
            }
        }
    }
}

@Composable
private fun PopupContent(popup: Popup, repository: PlatformRepository, actions: PopupActions) {
    val close = { actions.dismiss(popup) }
    run {
        when (popup) {
            Popup.Welcome -> WelcomePopup(repository, actions, onClaim = {
                actions.ackWelcome()
                close()
            }, onClose = {
                actions.ackWelcome()
                close()
            })
            Popup.DailyBonus -> DailyBonusPopup(repository, onClaim = actions.claimDaily, onClose = close)
            Popup.LuckySpin -> LuckySpinPopup(repository, actions, onClose = close)
            is Popup.Reward -> RewardPopup(popup, actions, onClose = close)
            is Popup.Message -> PopupCard(onClose = close) {
                DisplayText(popup.title.uppercase(), size = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text(popup.text, color = MgpColors.Navy, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                GlossyButton("OK", close, modifier = Modifier.fillMaxWidth())
            }
            is Popup.HowToPlay -> {
                val game = repository.game(popup.gameId)
                PopupCard(onClose = close) {
                    if (game != null) GameIcon(game, 90.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                    DisplayText("HOW TO PLAY", size = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                    Text(SampleCatalog.howToPlay(popup.gameId), color = MgpColors.Navy, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Everyone in your tournament gets the same board. Highest score wins; on a tie the faster finish wins.",
                        color = MgpColors.TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    GlossyButton("Got it", close, modifier = Modifier.fillMaxWidth())
                }
            }
            Popup.Rename -> RenamePopup(repository.player.displayName, actions.rename, onClose = close)
            Popup.ConfirmLogout -> PopupCard(onClose = close) {
                DisplayText("LOG OUT?", size = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (repository.signedInWith == com.funpixelnest.mgp.domain.LoginMethod.GUEST)
                        "You're playing as a guest. If you log out, this guest account and its coins can't be recovered."
                    else "You can sign in again any time to continue.",
                    color = MgpColors.Navy,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                GlossyButton("Log out", { close(); actions.logout() }, color = ButtonColor.Red, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                GlossyButton("Cancel", close, modifier = Modifier.fillMaxWidth())
            }
            Popup.LeagueRewards -> PopupCard(onClose = close) {
                DisplayText("LEAGUE REWARDS", size = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Paid once, the first time you reach each league.", color = MgpColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                LeagueTier.entries.drop(1).forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtImage(Art.league(t), 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("${formatCoins(t.minTrophies)} points", color = MgpColors.TextMuted, fontSize = 12.sp)
                        }
                        RewardChip(RewardGrant(t.rewardCoins, t.rewardGems), fontSize = 13.sp, iconSize = 18.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlossyButton("Close", close, modifier = Modifier.fillMaxWidth())
            }
            is Popup.LevelUp -> LevelUpPopup(popup, actions, onClose = close)
            Popup.NeedCoins -> PopupCard(onClose = close) {
                ArtImage(Art.coinsPile, 110.dp, Modifier.align(Alignment.CenterHorizontally))
                DisplayText("NOT ENOUGH COINS", size = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Get more coins in the Store, or claim free ones from the daily bonus, chests and challenges.",
                    color = MgpColors.Navy,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                GlossyButton("Get coins", { close(); actions.openStore() }, color = ButtonColor.Green, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** White rounded pop-up with a round close button. */
@Composable
fun PopupCard(onClose: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Column(
            Modifier
                .padding(top = 6.dp)
                .shadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(MgpColors.Card, MgpColors.CardLow)))
                .border(3.dp, Color(0xFF5B9BFF), RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            content = content,
        )
        if (onClose != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp)
                    .size(42.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MgpColors.Primary)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
        }
    }
}

@Composable
private fun WelcomePopup(repository: PlatformRepository, actions: PopupActions, onClaim: () -> Unit, onClose: () -> Unit) {
    val profile by repository.profile.collectAsState()
    var doubled by remember { mutableStateOf(false) }
    val shown = profile.welcomeCoins * (if (doubled) 2 else 1)
    PopupCard(onClose = onClose) {
        DisplayText("WELCOME BONUS!", size = 32.sp, color = MgpColors.Primary, modifier = Modifier.align(Alignment.CenterHorizontally), maxLines = 1)
        Text("Thank you for joining!\nHere's a special gift for you.", color = MgpColors.Navy, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ArtImage(Art.welcomeChest, 220.dp, Modifier.align(Alignment.CenterHorizontally))
        Text("YOU GOT", color = MgpColors.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp)
                .shadow(4.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(MgpColors.Card)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(Art.coinsPile, 80.dp)
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DisplayText(formatCoins(shown), size = 40.sp)
                Text("COINS", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
        Text("Use coins to enter tournaments and win big rewards!", color = MgpColors.Navy, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        DoubleRewardButton(actions, "welcome", listOf(RewardGrant(profile.welcomeCoins)), doubled, Modifier.padding(bottom = 10.dp)) { doubled = true }
        GlossyButton(if (doubled) "Claim ${formatCoins(shown)}" else "Claim now", onClaim, height = 58.dp, fontSize = 24.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DailyBonusPopup(repository: PlatformRepository, onClaim: () -> Unit, onClose: () -> Unit) {
    val bonus by repository.dailyBonus.collectAsState()
    Box(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Column(
            Modifier
                .padding(top = 50.dp)
                .shadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(MgpColors.Card)
                .border(3.dp, Color(0xFF5B9BFF), RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1C56D8), Color(0xFF3D8BFF)))).padding(top = 96.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DisplayText("DAILY BONUS", size = 36.sp, color = Color.White)
                Text("Log in every day and get amazing rewards!", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val days = bonus.rewards
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.take(4).forEach { DayTile(it, bonus, Modifier.weight(1f), onClaim, big = true) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.drop(4).forEach { DayTile(it, bonus, Modifier.weight(if (it.chest != null) 1.4f else 1f), onClaim, big = true) }
                }
                Spacer(Modifier.height(6.dp))
                GlossyButton(
                    if (bonus.claimedToday) "Come back tomorrow" else "Claim reward",
                    onClaim,
                    enabled = !bonus.claimedToday,
                    height = 56.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                ResetTimer("Resets in:", bonus.resetsInMillis, Modifier.align(Alignment.CenterHorizontally), icon = Icons.Rounded.Schedule)
            }
        }
        ArtImage(Art.gift, 170.dp, Modifier.align(Alignment.TopCenter))
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp).size(40.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
    }
}

@Composable
private fun RewardPopup(popup: Popup.Reward, actions: PopupActions, onClose: () -> Unit) {
    var doubled by remember(popup) { mutableStateOf(false) }
    val times = if (doubled) 2 else 1
    PopupCard(onClose = onClose) {
        val art = when {
            popup.art != null -> Art.chest(popup.art)
            popup.reward.gems > 0 && popup.reward.coins == 0L -> Art.gem
            else -> Art.coinsPile
        }
        ArtImage(art, 150.dp, Modifier.align(Alignment.CenterHorizontally))
        DisplayText(popup.title.uppercase(), size = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("YOU GOT", color = MgpColors.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (popup.reward.coins > 0) CoinAmount(popup.reward.coins * times, iconSize = 40.dp, fontSize = 32.sp)
            if (popup.reward.gems > 0) CoinAmount(popup.reward.gems * times, iconSize = 36.dp, fontSize = 32.sp, gems = true)
        }
        Spacer(Modifier.height(10.dp))
        if (popup.doublable) {
            DoubleRewardButton(actions, popup.title, listOf(popup.reward), doubled, Modifier.padding(bottom = 10.dp)) { doubled = true }
        }
        GlossyButton(if (doubled) "Collect" else "Awesome!", onClose, color = ButtonColor.Green, modifier = Modifier.fillMaxWidth())
    }
}

/** "LEVEL UP!": the new level in a big badge with turning light rays, and the coins / gems it paid. */
@Composable
private fun LevelUpPopup(popup: Popup.LevelUp, actions: PopupActions, onClose: () -> Unit) {
    val reward = remember(popup) { LevelRewards.between(popup.fromLevel, popup.toLevel) }
    var doubled by remember(popup) { mutableStateOf(false) }
    val times = if (doubled) 2 else 1
    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "angle")
    val glow by spin.animateFloat(0.85f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val pop = remember { Animatable(0.3f) }
    val count = remember { Animatable(0f) }
    LaunchedEffect(popup) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
        count.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    PopupCard(onClose = onClose) {
        DisplayText(
            "LEVEL UP!",
            size = 40.sp,
            color = MgpColors.Gold,
            outline = Color(0xFF8A3B05),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Box(Modifier.align(Alignment.CenterHorizontally).size(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(angle)) {
                val c = center
                val r = size.minDimension / 2
                for (i in 0 until 12) {
                    rotate(i * 30f, c) {
                        val p = Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x - r * 0.13f, c.y - r)
                            lineTo(c.x + r * 0.13f, c.y - r)
                            close()
                        }
                        drawPath(p, Brush.radialGradient(listOf(Color(0x99FFD54F), Color.Transparent), c, r))
                    }
                }
                drawCircle(Brush.radialGradient(listOf(Color(0x88FFF3B0), Color.Transparent), c, r * 0.7f * glow), r * 0.7f * glow, c)
            }
            LevelBadge(popup.toLevel, size = 120.dp, modifier = Modifier.scale(pop.value))
        }
        Text(
            "You reached Level ${popup.toLevel}",
            color = MgpColors.Navy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (popup.toLevel - popup.fromLevel > 1) {
            Text(
                "${popup.toLevel - popup.fromLevel} levels at once!",
                color = MgpColors.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!reward.isEmpty) {
            Text(
                "LEVEL REWARD",
                color = MgpColors.Primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp),
            )
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (reward.coins > 0) CoinAmount((reward.coins * times * count.value).toLong(), iconSize = 38.dp, fontSize = 30.sp)
                if (reward.gems > 0) CoinAmount((reward.gems * times * count.value).toLong(), iconSize = 34.dp, fontSize = 30.sp, gems = true)
            }
        }
        Text(
            "Keep playing to earn XP and reach Level ${popup.toLevel + 1}.",
            color = MgpColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        if (!reward.isEmpty) {
            val perLevel = ((popup.fromLevel + 1)..popup.toLevel).map { LevelRewards.forLevel(it) }.filterNot { it.isEmpty }
            DoubleRewardButton(actions, "level_up", perLevel, doubled, Modifier.padding(bottom = 10.dp)) { doubled = true }
        }
        GlossyButton("Collect", onClose, color = ButtonColor.Green, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * "2X" button with a video icon: plays a rewarded video, then the reward is paid again. Hidden after it worked,
 * or when rewarded ads are off.
 */
@Composable
fun DoubleRewardButton(
    actions: PopupActions,
    source: String,
    rewards: List<RewardGrant>,
    doubled: Boolean,
    modifier: Modifier = Modifier,
    onDoubled: () -> Unit,
) {
    if (!actions.doubleEnabled || doubled || rewards.isEmpty()) return
    var busy by remember { mutableStateOf(false) }
    GlossyButton(
        if (busy) "Loading video…" else "Watch video · 2X",
        onClick = {
            if (busy) return@GlossyButton
            busy = true
            actions.doubleReward(source, rewards) { ok ->
                busy = false
                if (ok) onDoubled()
            }
        },
        enabled = !busy,
        color = ButtonColor.Purple,
        icon = Icons.Rounded.OndemandVideo,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun RenamePopup(current: String, rename: suspend (String) -> String?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PopupCard(onClose = onClose) {
        DisplayText("YOUR NAME", size = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MgpColors.Card, unfocusedContainerColor = MgpColors.Card, focusedTextColor = MgpColors.Navy, unfocusedTextColor = MgpColors.Navy),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error!!, color = MgpColors.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
        GlossyButton("Save", {
            busy = true
            scope.launch {
                val e = rename(name)
                busy = false
                if (e == null) onClose() else error = e
            }
        }, enabled = !busy, color = ButtonColor.Green, modifier = Modifier.fillMaxWidth())
    }
}

/** Colors of the 8 wheel segments. */
private val wheelColors = listOf(
    Color(0xFFFF5A4E), Color(0xFF3D8BFF), Color(0xFFFFC23A), Color(0xFF34C759),
    Color(0xFF9B59F5), Color(0xFF26C6DA), Color(0xFFFF8A1F), Color(0xFFFF4FA0),
)

@Composable
private fun LuckySpinPopup(repository: PlatformRepository, actions: PopupActions, onClose: () -> Unit) {
    val spin = actions.spin
    var doubled by remember { mutableStateOf(false) }
    val shop by repository.shop.collectAsState()
    val segments = shop.spinSegments
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RewardGrant?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val measurer = rememberTextMeasurer()
    val ready = shop.spinReadyInMillis <= 0

    PopupCard(onClose = if (spinning) null else onClose) {
        DisplayText("LUCKY SPIN", size = 32.sp, color = MgpColors.Primary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("One free spin every day!", color = MgpColors.Navy, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(270.dp).rotate(rotation.value)) {
                val sweep = 360f / segments.size
                val radius = size.minDimension / 2
                segments.forEachIndexed { i, seg ->
                    drawArc(wheelColors[i % wheelColors.size], startAngle = -90f + i * sweep, sweepAngle = sweep, useCenter = true)
                    val label = if (seg.gems > 0) "${seg.gems} GEMS" else formatCoins(seg.coins)
                    rotate(degrees = i * sweep + sweep / 2, pivot = center) {
                        val layout = measurer.measure(label, TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
                        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - radius * 0.82f))
                    }
                }
                drawCircle(Color(0xFFFFC23A), radius = radius, style = Stroke(width = 10.dp.toPx()))
                drawCircle(Color.White, radius = radius * 0.16f)
                drawCircle(Color(0xFFFFC23A), radius = radius * 0.12f)
            }
            // Pointer at the top.
            Canvas(Modifier.size(34.dp).align(Alignment.TopCenter)) {
                val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
                drawPath(p, Color(0xFFE5342B))
                drawPath(p, Color.White, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            result != null -> {
                Text("YOU WON", color = MgpColors.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                val won = result!!
                val shownWin = if (doubled) RewardGrant(won.coins * 2, won.gems * 2) else won
                RewardChip(shownWin, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp), fontSize = 24.sp, iconSize = 30.dp)
                if (!won.isEmpty) DoubleRewardButton(actions, "lucky_spin", listOf(won), doubled, Modifier.padding(bottom = 10.dp)) { doubled = true }
                GlossyButton("Collect", onClose, color = ButtonColor.Green, modifier = Modifier.fillMaxWidth())
            }
            !ready && !spinning -> {
                ResetTimer("Next spin in:", shop.spinReadyInMillis, Modifier.align(Alignment.CenterHorizontally), icon = Icons.Rounded.Schedule)
                Spacer(Modifier.height(10.dp))
                GlossyButton("Close", onClose, modifier = Modifier.fillMaxWidth())
            }
            else -> GlossyButton(
                if (spinning) "Spinning…" else "Spin now",
                {
                    spinning = true
                    error = null
                    scope.launch {
                        val r = spin()
                        r.onSuccess { res ->
                            val sweep = 360f / segments.size
                            // Land the middle of the winning segment under the pointer.
                            val target = 360f * 6 + (360f - (res.segment * sweep + sweep / 2))
                            rotation.animateTo(rotation.value - rotation.value % 360f + target, tween(3_600, easing = FastOutSlowInEasing))
                            result = res.reward
                        }.onFailure { error = it.message }
                        spinning = false
                    }
                },
                enabled = !spinning,
                color = ButtonColor.Green,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) Text(error!!, color = MgpColors.Error, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
