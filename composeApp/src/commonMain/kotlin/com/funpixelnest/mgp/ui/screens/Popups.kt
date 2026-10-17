package com.funpixelnest.mgp.ui.screens

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
)

/** Draws the top pop-up (if any) over the whole app. */
@Composable
fun PopupHost(popup: Popup?, repository: PlatformRepository, actions: PopupActions) {
    if (popup == null) return
    val close = { actions.dismiss(popup) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99081A44))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (popup !is Popup.Welcome) close()
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (popup) {
            Popup.Welcome -> WelcomePopup(repository, onClaim = {
                actions.ackWelcome()
                close()
            }, onClose = {
                actions.ackWelcome()
                close()
            })
            Popup.DailyBonus -> DailyBonusPopup(repository, onClaim = actions.claimDaily, onClose = close)
            Popup.LuckySpin -> LuckySpinPopup(repository, actions.spin, onClose = close)
            is Popup.Reward -> RewardPopup(popup, onClose = close)
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
                .background(Brush.verticalGradient(listOf(Color(0xFFF7FBFF), Color(0xFFE5F0FF))))
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
private fun WelcomePopup(repository: PlatformRepository, onClaim: () -> Unit, onClose: () -> Unit) {
    val profile by repository.profile.collectAsState()
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
                .background(Color.White)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(Art.coinsPile, 80.dp)
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DisplayText(formatCoins(profile.welcomeCoins), size = 40.sp)
                Text("COINS", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
        Text("Use coins to enter tournaments and win big rewards!", color = MgpColors.Navy, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        GlossyButton("Claim now", onClaim, height = 58.dp, fontSize = 24.sp, modifier = Modifier.fillMaxWidth())
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
                .background(Color.White)
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
private fun RewardPopup(popup: Popup.Reward, onClose: () -> Unit) {
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
            if (popup.reward.coins > 0) CoinAmount(popup.reward.coins, iconSize = 40.dp, fontSize = 32.sp)
            if (popup.reward.gems > 0) CoinAmount(popup.reward.gems, iconSize = 36.dp, fontSize = 32.sp, gems = true)
        }
        Spacer(Modifier.height(10.dp))
        GlossyButton("Awesome!", onClose, color = ButtonColor.Green, modifier = Modifier.fillMaxWidth())
    }
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
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
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
private fun LuckySpinPopup(repository: PlatformRepository, spin: suspend () -> Result<SpinResult>, onClose: () -> Unit) {
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
                RewardChip(result!!, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp), fontSize = 24.sp, iconSize = 30.dp)
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
