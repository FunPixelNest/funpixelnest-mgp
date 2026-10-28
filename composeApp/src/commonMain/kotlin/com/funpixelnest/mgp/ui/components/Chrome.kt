package com.funpixelnest.mgp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.LevelInfo
import com.funpixelnest.mgp.ui.Tab
import com.funpixelnest.mgp.ui.theme.MgpColors

/**
 * Top bar on every tab: avatar, name, level + XP, coins, gems and the menu (with unread badge).
 * Tapping coins/gems opens the Store; the avatar opens the Profile.
 */
@Composable
fun PlayerHeader(
    name: String,
    avatarId: Int,
    level: LevelInfo,
    coins: Long,
    gems: Long,
    unread: Int,
    onAvatarClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
    ) {
        val compact = maxWidth < 400.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                avatarId,
                size = if (compact) 52.dp else 62.dp,
                modifier = Modifier.clickable(onClick = onAvatarClick),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = MgpColors.Navy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(contentAlignment = Alignment.CenterStart) {
                    Column(
                        Modifier
                            .padding(start = 14.dp)
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(MgpColors.Card)
                            .padding(start = 20.dp, end = 8.dp, top = 3.dp, bottom = 4.dp),
                    ) {
                        Text(
                            "${formatCoins(level.xpIntoLevel)} / ${formatCoins(level.xpNeeded)} XP",
                            color = MgpColors.Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                        XpBar(level.progress, Modifier.fillMaxWidth().height(5.dp))
                    }
                    LevelBadge(level.level, size = 30.dp)
                }
            }
            Spacer(Modifier.width(6.dp))
            CurrencyPill(coins, gems = false, showPlus = !compact, onClick = onCurrencyClick, modifier = Modifier.rewardTarget(Currency.Coins))
            Spacer(Modifier.width(5.dp))
            CurrencyPill(gems, gems = true, showPlus = !compact, onClick = onCurrencyClick, modifier = Modifier.rewardTarget(Currency.Gems))
            Spacer(Modifier.width(5.dp))
            TileIconButton(Icons.Rounded.Menu, "More options", onMenuClick, size = 40.dp, tint = MgpColors.Navy, badge = unread)
        }
    }
}

@Composable
fun XpBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(MgpColors.Track)) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(MgpColors.Gold, Color(0xFFFFA726), MgpColors.PrimaryLight))),
        )
    }
}

@Composable
private fun CurrencyPill(amount: Long, gems: Boolean, showPlus: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(3.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MgpColors.Card)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = if (showPlus) 5.dp else 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (gems) GemIcon(22.dp) else CoinIcon(22.dp)
        Text(formatCoins(amount), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        if (showPlus) {
            Box(Modifier.size(18.dp).background(MgpColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "Get more", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Bottom tabs: Challenges · Leagues · Games · Rewards · Store.
 * The selected tab lifts onto a white tile, grows and gets a blue underline.
 */
@Composable
fun MgpBottomBar(selected: Tab, challengeBadge: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = MgpColors.Glow, spotColor = MgpColors.Glow)
                .clip(RoundedCornerShape(24.dp))
                .background(MgpColors.Sheet)
                .border(1.dp, MgpColors.CardEdge, RoundedCornerShape(24.dp))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                BottomItem(
                    tab = tab,
                    selected = tab == selected,
                    badge = if (tab == Tab.Challenges) challengeBadge else 0,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomItem(tab: Tab, selected: Boolean, badge: Int, onClick: () -> Unit, modifier: Modifier) {
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    val lift by animateDpAsState(if (selected) (-3).dp else 0.dp)
    val tint by animateColorAsState(if (selected) MgpColors.Primary else if (MgpColors.isDark) MgpColors.TextMuted else Color(0xFF2C4C9C))
    val underline by animateDpAsState(if (selected) 34.dp else 0.dp)
    Box(
        modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MgpColors.Card else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = lift)) {
            Box {
                Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(30.dp).scale(scale))
                if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                tab.label.uppercase(),
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .width(underline)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MgpColors.Primary),
        )
    }
}
