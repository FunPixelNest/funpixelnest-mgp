package com.funpixelnest.mgp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.AVATAR_COUNT
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.LoginMethod
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.ui.AppSettings
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.Avatar
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.CoinAmount
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.LevelBadge
import com.funpixelnest.mgp.ui.components.Panel
import com.funpixelnest.mgp.ui.components.TileIconButton
import com.funpixelnest.mgp.ui.components.XpBar
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors
import org.jetbrains.compose.resources.DrawableResource

/** Short public id shown on the profile, e.g. FPX7A3C91. */
fun playerCode(playerId: String): String = "FPX" + playerId.filter { it.isLetterOrDigit() }.take(6).uppercase()

const val APP_VERSION = "1.0.0"
const val SUPPORT_EMAIL = "support@funpixelnest.com"
const val STORE_URL = "https://play.google.com/store/apps/details?id=com.funpixelnest.mgp"
const val WEBSITE_URL = "https://funpixelnest.com"

/** Title bar for pushed account screens: back tile, centered title, optional action. */
@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TileIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, Modifier.align(Alignment.CenterStart))
        DisplayText(title.uppercase(), size = 30.sp, modifier = Modifier.align(Alignment.Center), maxLines = 1)
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

// ---------------------------------------------------------------- Profile

@Composable
fun ProfileScreen(
    repository: PlatformRepository,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onRename: () -> Unit,
    onCopy: (String, String) -> Unit,
    onPoolClick: (Pool) -> Unit,
    onAvatarPick: (Int) -> Unit,
) {
    val profile by repository.profile.collectAsState()
    val pools by repository.myPools.collectAsState()
    val level = profile.levelInfo
    val code = playerCode(repository.player.id)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Profile", onBack) { TileIconButton(Icons.Rounded.Settings, "Settings", onSettings) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Avatar(profile.avatarId, 116.dp, ringWidth = 4.dp)
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(34.dp).background(MgpColors.Primary, CircleShape).border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onRename)) {
                                DisplayText(repository.player.displayName, size = 28.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.size(26.dp).background(MgpColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Edit, "Edit name", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onCopy(code, "Player ID copied") }.padding(vertical = 4.dp),
                            ) {
                                Text("Player ID: $code", color = MgpColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.ContentCopy, "Copy", tint = MgpColors.Primary, modifier = Modifier.size(18.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LevelBadge(level.level, 40.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${formatCoins(level.xpIntoLevel)} / ${formatCoins(level.xpNeeded)} XP", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    XpBar(level.progress, Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MgpColors.Border)
                    Row(Modifier.fillMaxWidth()) {
                        BigStat(Art.trophy, formatCoins(profile.trophies), "Total Trophies", Modifier.weight(1f))
                        BigStat(Art.challenge("w_top10"), "${profile.stats.gamesWon}", "Games Won", Modifier.weight(1f))
                        BigStat(Art.target, "${profile.stats.winRate}%", "Win Rate", Modifier.weight(1f))
                    }
                }
            }
            item {
                Panel(padding = 16.dp) {
                    Text("CHOOSE AVATAR", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    (1..AVATAR_COUNT).chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { id ->
                                val selected = id == profile.avatarId
                                Box(Modifier.weight(1f).aspectRatio(1f).clickable { if (!selected) onAvatarPick(id) }) {
                                    Avatar(
                                        id,
                                        size = 64.dp,
                                        modifier = Modifier.fillMaxSize(),
                                        ring = if (selected) MgpColors.Primary else Color.White,
                                        ringWidth = if (selected) 4.dp else 3.dp,
                                    )
                                    if (selected) {
                                        Box(
                                            Modifier.align(Alignment.BottomEnd).size(24.dp).background(MgpColors.Green, CircleShape).border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                val s = profile.stats
                Panel(padding = 16.dp) {
                    Text("STATS OVERVIEW", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        SmallStat(Art.medal(1)!!, "${s.tournamentsPlayed}", "Tournaments Played", Modifier.weight(1f))
                        SmallStat(Art.coin, formatCoins(s.coinsWon), "Total Coins Won", Modifier.weight(1f))
                        SmallStat(null, "${s.winStreak}", "Win Streak", Modifier.weight(1f), vector = Icons.Rounded.LocalFireDepartment)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MgpColors.Border)
                    Row(Modifier.fillMaxWidth()) {
                        SmallStat(Art.challenge("m_challenges_5"), "${s.matchesPlayed}", "Total Matches", Modifier.weight(1f))
                        SmallStat(Art.league(profile.tier), profile.tier.label, "Current League", Modifier.weight(1f))
                        SmallStat(Art.earnCheckin, "${s.daysActive}", "Days Active", Modifier.weight(1f))
                    }
                }
            }
            item {
                Panel(padding = 14.dp) {
                    Text("RECENT MATCHES", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    if (pools.isEmpty()) Text("No matches yet. Pick a game and play!", color = MgpColors.TextMuted, fontSize = 13.sp)
                    pools.take(15).forEach { pool -> MatchRow(repository, pool) { onPoolClick(pool) } }
                }
            }
            item {
                GlossyButton(
                    "Share profile",
                    onClick = { onCopy("Play with me on FunPixelNest! My player ID: $code", "Copied! Paste it to your friends.") },
                    icon = Icons.Rounded.IosShare,
                    height = 58.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun BigStat(art: DrawableResource, value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ArtImage(art, 44.dp)
        DisplayText(value, size = 24.sp, maxLines = 1)
        Text(label, color = MgpColors.TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun SmallStat(art: DrawableResource?, value: String, label: String, modifier: Modifier, vector: ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (art != null) ArtImage(art, 34.dp)
        if (vector != null) Icon(vector, null, tint = MgpColors.Orange, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
            Text(label, color = MgpColors.TextMuted, fontSize = 11.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun MatchRow(repository: PlatformRepository, pool: Pool, onClick: () -> Unit) {
    val tournament = repository.tournament(pool.tournamentId) ?: return
    val game = repository.game(tournament.gameId) ?: return
    val myRow = Leaderboard.rank(pool, tournament).find { it.entry.playerId == repository.player.id }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIcon(game, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(game.name, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(
                (if (tournament.isDuel) "1v1 " else "") + tournament.title + " · " + when (pool.status) {
                    PoolStatus.COMPLETED -> "Finished #${myRow?.rank ?: "-"}"
                    PoolStatus.FULL -> "Waiting for scores"
                    PoolStatus.OPEN -> "Finding players"
                },
                color = MgpColors.TextMuted,
                fontSize = 12.sp,
            )
        }
        if (pool.status == PoolStatus.COMPLETED && (myRow?.prize ?: 0) > 0) {
            CoinAmount(myRow!!.prize, fontSize = 14.sp, iconSize = 18.dp, color = MgpColors.Win)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MgpColors.TextMuted)
    }
}

// ---------------------------------------------------------------- Side menu

enum class MenuItem(val label: String, val icon: ImageVector, val tint: Color) {
    Profile("Profile", Icons.Rounded.Person, Color(0xFF1E6CF2)),
    Inbox("Inbox", Icons.Rounded.Mail, Color(0xFF1E6CF2)),
    Settings("Settings", Icons.Rounded.Settings, Color(0xFF1E6CF2)),
    Help("Help Center", Icons.AutoMirrored.Rounded.HelpOutline, Color(0xFF2E9A1C)),
    Support("Support", Icons.Rounded.SupportAgent, Color(0xFF1E6CF2)),
    Refer("Refer & Earn", Icons.Rounded.CardGiftcard, Color(0xFF8B3FE4)),
    Rate("Rate Us", Icons.Rounded.Star, Color(0xFFFFB300)),
    Follow("Follow Us", Icons.Rounded.Favorite, Color(0xFFE91E63)),
    Terms("Terms & Conditions", Icons.Rounded.Description, Color(0xFF1E6CF2)),
    Privacy("Privacy Policy", Icons.Rounded.Shield, Color(0xFF1E6CF2)),
    Logout("Log Out", Icons.AutoMirrored.Rounded.Logout, Color(0xFFE5342B)),
}

/** "More options" panel that slides in from the right. */
@Composable
fun MoreOptionsDrawer(
    open: Boolean,
    repository: PlatformRepository,
    onClose: () -> Unit,
    onItem: (MenuItem) -> Unit,
    onRename: () -> Unit,
) {
    val profile by repository.profile.collectAsState()
    val wallet by repository.wallet.collectAsState()
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66102040)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(open, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.8f)
                    .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(Color(0xFFF2F7FF))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DisplayText("MORE OPTIONS", size = 24.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    TileIconButton(Icons.Rounded.Close, "Close", onClose, size = 42.dp, tint = MgpColors.Navy)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile.avatarId, 86.dp, ringWidth = 4.dp, modifier = Modifier.clickable { onItem(MenuItem.Profile) })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onRename)) {
                            Text(repository.player.displayName, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Rounded.Edit, "Edit name", tint = MgpColors.Primary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Level ${profile.levelInfo.level} ", color = MgpColors.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            LevelBadge(profile.levelInfo.level, 24.dp)
                        }
                        CoinAmount(wallet.coins, iconSize = 22.dp, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                MenuItem.entries.forEach { item ->
                    MenuRow(item, dot = item == MenuItem.Inbox && profile.unreadNotifications > 0) { onItem(item) }
                    Spacer(Modifier.height(8.dp))
                }
                Text("App Version $APP_VERSION", color = MgpColors.TextMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, dot: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(item.tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(
            item.label.uppercase(),
            color = if (item == MenuItem.Logout) item.tint else MgpColors.Navy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (dot) Box(Modifier.size(10.dp).background(Color(0xFFF2301F), CircleShape))
        if (item != MenuItem.Logout) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MgpColors.Primary, modifier = Modifier.padding(start = 6.dp))
    }
}

// ---------------------------------------------------------------- Inbox, settings, help, support, refer, legal

@Composable
fun InboxScreen(repository: PlatformRepository, onBack: () -> Unit) {
    val inbox by repository.inbox.collectAsState()
    LaunchedEffect(Unit) {
        runCatching { repository.refreshInbox() }
        repository.markInboxRead()
    }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Inbox", onBack)
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inbox.isEmpty()) item { Text("No messages yet.", color = MgpColors.TextMuted, modifier = Modifier.padding(8.dp)) }
            items(inbox, key = { it.id }) { msg ->
                Panel(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!msg.read) Box(Modifier.size(10.dp).background(Color(0xFFF2301F), CircleShape))
                        if (!msg.read) Spacer(Modifier.width(8.dp))
                        Text(msg.title, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    if (msg.body.isNotBlank()) Text(msg.body, color = MgpColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(repository: PlatformRepository, settings: AppSettings, onBack: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(padding = 8.dp) {
                ToggleRow("Music", settings.music) { settings.music = it }
                ToggleRow("Sound", settings.sound) { settings.sound = it }
                ToggleRow("Vibrations", settings.vibration) { settings.vibration = it }
                ToggleRow("Notifications", settings.notifications) { settings.notifications = it }
            }
            Panel(padding = 16.dp) {
                Text("ACCOUNT", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                val method = repository.signedInWith
                Text(
                    when (method) {
                        null -> "Signed in"
                        LoginMethod.GUEST -> "Playing as a guest. Your progress is saved on this device only."
                        LoginMethod.USERNAME -> "Signed in with your username."
                        else -> "Signed in with ${method.label}."
                    },
                    color = MgpColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Text("Player ID: ${playerCode(repository.player.id)}", color = MgpColors.TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                GlossyButton("Log out", onLogout, color = ButtonColor.Red, height = 50.dp, modifier = Modifier.fillMaxWidth())
            }
            Text("App Version $APP_VERSION", color = MgpColors.TextMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        MgpSwitch(checked, onChange)
    }
}

private val faqs = listOf(
    "How do tournaments work?" to "You join a pool of 5 players. Everyone plays the same board (same seed). When all 5 have finished, the pool closes and prizes are paid. There's no time limit.",
    "What are the prizes?" to "After a 10% platform fee, 1st place gets 50% of the prize pool, 2nd gets 30% and 3rd gets the rest.",
    "What happens with a tie?" to "There are no ties. If scores are equal, the faster finish wins; if that's equal too, whoever submitted first wins.",
    "How do 1v1 duels work?" to "Two players play the same board. The winner takes the whole pot (after the 10% fee).",
    "What are gems for?" to "Gems open chests on the Rewards tab. You earn them from the daily bonus, challenges, the lucky spin and league promotions.",
    "How do leagues work?" to "Every match earns league points: 1st 100, 2nd 60, 3rd 30, others 10 (duel win 80). Reach 1,000 for Silver, 2,000 for Gold and so on. Each new league pays a one-time reward.",
    "Can I pause a game?" to "Yes. Press Back or Pause. You have 5 minutes to resume, otherwise the game ends. Ending a paid game early counts as a score of 0.",
)

@Composable
fun HelpCenterScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Help Center", onBack)
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(faqs) { (q, a) ->
                var open by remember { mutableStateOf(false) }
                Panel(padding = 14.dp, modifier = Modifier.clickable { open = !open }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MgpColors.Primary)
                    }
                    if (open) Text(a, color = MgpColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit, onEmail: () -> Unit, onCopy: (String, String) -> Unit, playerId: String) {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Support", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel(padding = 16.dp) {
                Text("Need help?", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    "Email us and include your Player ID so we can find your account quickly.",
                    color = MgpColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Text(SUPPORT_EMAIL, color = MgpColors.Primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Player ID: ${playerCode(playerId)}", color = MgpColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                GlossyButton("Email support", onEmail, height = 50.dp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                GlossyButton("Copy Player ID", { onCopy(playerCode(playerId), "Player ID copied") }, color = ButtonColor.Green, height = 46.dp, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ReferEarnScreen(onBack: () -> Unit, playerId: String, onCopy: (String, String) -> Unit) {
    val code = playerCode(playerId)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Refer & Earn", onBack)
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ArtImage(Art.gift, 170.dp)
            Panel(padding = 16.dp, modifier = Modifier.fillMaxWidth()) {
                Text("Invite your friends", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text("Share your code so friends can find you and challenge you.", color = MgpColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF2FF)).border(2.dp, MgpColors.Primary, RoundedCornerShape(14.dp)).padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) { DisplayText(code, size = 30.sp, color = MgpColors.Primary) }
                Spacer(Modifier.height(12.dp))
                GlossyButton(
                    "Copy invite",
                    { onCopy("Join me on FunPixelNest and let's play! My player code: $code", "Invite copied! Paste it in any chat.") },
                    icon = Icons.Rounded.IosShare,
                    height = 52.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Friend rewards are coming soon.",
                    color = MgpColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun LegalScreen(privacy: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(if (privacy) "Privacy" else "Terms", onBack)
        Panel(padding = 16.dp, modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Text(if (privacy) "Privacy Policy" else "Terms & Conditions", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(
                "The full ${if (privacy) "Privacy Policy" else "Terms & Conditions"} will be published here before launch.",
                color = MgpColors.TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

