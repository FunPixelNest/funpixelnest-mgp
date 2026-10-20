package com.funpixelnest.mgp.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SportsRugby
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.SampleCatalog
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.CoinAmount
import com.funpixelnest.mgp.ui.components.CoinIcon
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.LabelText
import com.funpixelnest.mgp.ui.components.Panel
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.components.gameIconResource
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

// ---------------------------------------------------------------- Games tab

/** Category icon (falls back to a game pad). */
fun categoryIcon(category: String): ImageVector = when (category) {
    "Puzzle" -> Icons.Rounded.Extension
    "Cards & Casino" -> Icons.Rounded.Casino
    "Sports" -> Icons.Rounded.SportsRugby
    else -> Icons.Rounded.SportsEsports
}

/** Groups games by [SampleCatalog.categories] order; unknown categories go last. */
fun gamesByCategory(games: List<Game>): List<Pair<String, List<Game>>> {
    val order = SampleCatalog.categories
    return games.groupBy { it.category }
        .toList()
        .sortedBy { (c, _) -> order.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it } }
}

/** Games tab: every game, 2 per row. "See All" opens the games by category. */
@Composable
fun GamesScreen(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onSeeAll: (category: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "play_header") {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SportsEsports, null, tint = MgpColors.Primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text("PLAY GAMES", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                SeeAllButton { onSeeAll(null) }
            }
        }
        items(games, key = { it.id }) { game -> GameTile(game, onClick = { onGameClick(game) }) }
    }
}

/** Every game, filterable by category, scrollable (opened from "See All"). */
@Composable
fun AllGamesScreen(
    games: List<Game>,
    initialCategory: String?,
    onGameClick: (Game) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(initialCategory) { mutableStateOf(initialCategory) }
    val categories = remember(games) { gamesByCategory(games).map { it.first } }
    val shown = remember(games, selected) { if (selected == null) gamesByCategory(games).flatMap { it.second } else games.filter { it.category == selected } }
    Column(modifier.fillMaxSize()) {
        SimpleTopBar(if (selected == null) "All Games" else selected!!, onBack)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            item { CategoryChip("All", null, selected == null) { selected = null } }
            items(categories) { c -> CategoryChip(c, categoryIcon(c), selected == c) { selected = c } }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown, key = { it.id }) { game -> GameTile(game, onClick = { onGameClick(game) }) }
        }
    }
}

@Composable
private fun SeeAllButton(label: String = "See All", onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MgpColors.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = MgpColors.Primary, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun CategoryChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .shadow(if (selected) 4.dp else 1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MgpColors.Primary else MgpColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) Color.White else MgpColors.Primary
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) Color.White else MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun GameTile(game: Game, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFCDE9FF), Color(0xFF8FC9F7))))
            .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(gameIconResource(game.id)),
            contentDescription = game.name,
            contentScale = ContentScale.Crop,
            // Slightly taller than wide.
            modifier = Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(16.dp)),
        )
        DisplayText(
            game.name.uppercase(),
            size = 17.sp,
            color = Color.White,
            outline = Color(0xFF173B87),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 7.dp),
        )
        Row(
            Modifier.padding(top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(categoryIcon(game.category), null, tint = Color(0xFF173B87), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(game.category, color = Color(0xFF173B87), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Game detail

/** Visual tier per tournament card, smallest entry fee first (Beginner → Mega style from the mockups). */
private data class TierStyle(val label: String, val color: Color, val button: ButtonColor, val tint: Color)

private val tierStyles = listOf(
    TierStyle("Beginner", Color(0xFF34A11E), ButtonColor.Green, Color(0xFFEFFAEA)),
    TierStyle("Intermediate", Color(0xFF1E6CF2), ButtonColor.Blue, MgpColors.Tint),
    TierStyle("Advanced", Color(0xFF7B3FE4), ButtonColor.Purple, Color(0xFFF3ECFF)),
    TierStyle("Expert", Color(0xFFF06A00), ButtonColor.Orange, Color(0xFFFFF2E6)),
    TierStyle("Mega", Color(0xFFE0186D), ButtonColor.Pink, Color(0xFFFFEBF3)),
)

/** A game's page: header, banner and the game's pool tournaments as tier cards (no practice, no 1v1 duels). */
@Composable
fun GameDetailScreen(
    game: Game,
    tournaments: List<Tournament>,
    onPlay: (Tournament) -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(tournaments) { tournaments.filterNot { it.isDuel }.sortedBy { it.entryFee } }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(96.dp).shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(MgpColors.Card).padding(4.dp),
                ) { GameIcon(game, 88.dp, cornerRadius = 18.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    DisplayText(game.name.uppercase(), size = 26.sp, maxLines = 2)
                    Text("Play tournaments and win big rewards!", color = MgpColors.Navy, fontSize = 14.sp)
                }
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(MgpColors.Primary).clickable(onClick = onInfo),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Info, "How to play", tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
        }
        item { CompeteBanner() }
        items(sorted.size) { index ->
            TournamentCard(sorted[index], tierStyles[index.coerceAtMost(tierStyles.lastIndex)], index, onPlay = { onPlay(sorted[index]) })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MgpColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Everyone in a pool plays the same board. Results update live as players finish.",
                    color = MgpColors.Navy,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CompeteBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1846B8), Color(0xFF2C6FF0), Color(0xFF4A3FD0)))),
    ) {
        Image(
            painterResource(Art.trophy),
            null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(150.dp),
        )
        Image(
            painterResource(Art.coinsPile),
            null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 6.dp).size(62.dp),
        )
        Column(Modifier.padding(18.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            DisplayText("COMPETE, WIN", size = 26.sp, color = Color.White)
            DisplayText("& BECOME A CHAMP!", size = 18.sp, color = MgpColors.Gold)
            Text(
                "Join exciting tournaments and\nwin big rewards!",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun TournamentCard(tournament: Tournament, style: TierStyle, index: Int, onPlay: () -> Unit) {
    val prizePool = Leaderboard.prizePool(tournament)
    val firstPrize = Leaderboard.prizePayouts(tournament).first()
    val shape = RoundedCornerShape(20.dp)
    // Compact card (~112 dp): art | name, prize pool, pool facts | win up to, Play, entry fee.
    Row(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .shadow(6.dp, shape, ambientColor = MgpColors.Glow, spotColor = MgpColors.Glow)
            .clip(shape)
            .background(Brush.horizontalGradient(if (MgpColors.isDark) listOf(style.color.copy(alpha = 0.28f).compositeOver(MgpColors.Card), MgpColors.Card, MgpColors.Card) else listOf(style.tint, Color.White, Color.White)))
            .border(1.dp, MgpColors.CardEdge, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tier colour strip + art
        Box(Modifier.width(6.dp).fillMaxHeight().background(style.color))
        ArtImage(Art.pack(index), 62.dp, Modifier.padding(start = 6.dp))
        // Name, prize pool, facts
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 4.dp), verticalArrangement = Arrangement.Center) {
            Text(tournament.title.uppercase(), color = style.color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisplayText(formatCoins(prizePool), size = 24.sp, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                CoinIcon(20.dp)
            }
            LabelText("Prize pool", color = MgpColors.Navy, size = 10.sp)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Groups, "Players per pool", tint = MgpColors.PrimaryDark, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("${tournament.poolSize}", color = MgpColors.Navy, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.EmojiEvents, "Winners", tint = MgpColors.PrimaryDark, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text("Top 3", color = MgpColors.Navy, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }
        // Win up to, Play, entry fee
        Column(
            Modifier.width(118.dp).padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelText("Win ", color = MgpColors.Navy, size = 10.sp)
                Text(formatCoins(firstPrize), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.width(2.dp))
                CoinIcon(14.dp)
            }
            GlossyButton("Play", onClick = onPlay, color = style.button, height = 42.dp, fontSize = 19.sp, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ConfirmationNumber, "Entry fee", tint = MgpColors.PrimaryDark, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                CoinAmount(tournament.entryFee, iconSize = 14.dp, fontSize = 13.sp, iconFirst = false)
            }
        }
    }
}

// ---------------------------------------------------------------- Joining

/** Shown while the server places the player in a pool. */
@Composable
fun JoiningScreen(game: Game, modifier: Modifier = Modifier) {
    val tip = remember { SampleCatalog.proTips.random() }
    val t = rememberInfiniteTransition()
    val pulse by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.scale(pulse).size(190.dp).shadow(10.dp, RoundedCornerShape(42.dp)).clip(RoundedCornerShape(42.dp)).background(MgpColors.Card).padding(10.dp),
        ) { GameIcon(game, 170.dp, cornerRadius = 34.dp) }
        Spacer(Modifier.height(18.dp))
        DisplayText(game.name.uppercase(), size = 34.sp, color = Color.White, outline = Color(0xFF1655DA), textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text("Joining Tournament...", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(Modifier.size(54.dp), color = MgpColors.Primary, trackColor = MgpColors.Track, strokeWidth = 6.dp)
        Spacer(Modifier.height(26.dp))
        Text("Pro Tip: $tip", color = MgpColors.PrimaryDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Small helper so other screens can show a game picture at a size. */
@Composable
fun GameBadge(game: Game, size: Dp) = GameIcon(game, size)
