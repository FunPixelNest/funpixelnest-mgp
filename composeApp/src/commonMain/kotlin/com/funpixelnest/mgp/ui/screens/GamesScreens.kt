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

/** Games shown per category on the Games tab (2 rows of 2); "See All" opens the rest. */
private const val PREVIEW_PER_CATEGORY = 4

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

/** Games tab: banner, then games by category, 2 per row. "See All" opens every game. */
@Composable
fun GamesScreen(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onSeeAll: (category: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val sections = remember(games) { gamesByCategory(games) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HeroBanner(onPlay = { scope.launch { gridState.animateScrollToItem(1) } })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SportsEsports, null, tint = MgpColors.Primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text("PLAY GAMES", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                SeeAllButton { onSeeAll(null) }
            }
        }
        sections.forEach { (category, list) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "header_$category") {
                CategoryHeader(category, list.size, showSeeAll = list.size > PREVIEW_PER_CATEGORY) { onSeeAll(category) }
            }
            items(list.take(PREVIEW_PER_CATEGORY), key = { it.id }) { game -> GameTile(game, onClick = { onGameClick(game) }) }
        }
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
private fun SeeAllButton(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("See All", color = MgpColors.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = MgpColors.Primary, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun CategoryHeader(category: String, count: Int, showSeeAll: Boolean, onSeeAll: () -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).shadow(3.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(categoryIcon(category), null, tint = MgpColors.Primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(category.uppercase(), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.width(6.dp))
        Text("($count)", color = MgpColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (showSeeAll) SeeAllButton(onSeeAll)
    }
}

@Composable
private fun CategoryChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .shadow(if (selected) 4.dp else 1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MgpColors.Primary else Color.White)
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
private fun HeroBanner(onPlay: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(262.dp)
            .shadow(8.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF4FAFF), Color(0xFFD3EBFF), Color(0xFFE9F6FF))))
            .border(1.dp, Color.White, RoundedCornerShape(26.dp)),
    ) {
        val t = rememberInfiniteTransition()
        val bob by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(1_800), RepeatMode.Reverse))
        Image(
            painterResource(Art.heroChest),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(200.dp).offset(y = bob.dp),
        )
        Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            DisplayText("PLAY\nGAMES", size = 31.sp, color = MgpColors.Navy)
            DisplayText("WIN BIG", size = 31.sp, color = MgpColors.Primary)
            Text(
                "Play, compete and win\nexciting rewards!",
                color = MgpColors.Navy,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            GlossyButton(
                "Play games",
                onClick = onPlay,
                height = 44.dp,
                fontSize = 16.sp,
                icon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            )
        }
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
    TierStyle("Intermediate", Color(0xFF1E6CF2), ButtonColor.Blue, Color(0xFFEAF2FF)),
    TierStyle("Advanced", Color(0xFF7B3FE4), ButtonColor.Purple, Color(0xFFF3ECFF)),
    TierStyle("Expert", Color(0xFFF06A00), ButtonColor.Orange, Color(0xFFFFF2E6)),
    TierStyle("Mega", Color(0xFFE0186D), ButtonColor.Pink, Color(0xFFFFEBF3)),
)

/** A game's page: header, banner, free practice and every tournament / 1v1 duel as tier cards. */
@Composable
fun GameDetailScreen(
    game: Game,
    tournaments: List<Tournament>,
    onPlay: (Tournament) -> Unit,
    onPractice: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(tournaments) { tournaments.sortedWith(compareBy({ it.entryFee }, { it.isDuel.not() })) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(96.dp).shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(4.dp),
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
        item { PracticeCard(onPractice) }
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
private fun PracticeCard(onPractice: () -> Unit) {
    Panel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRACTICE", color = MgpColors.Primary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text("Free warm-up with a random board. No coins, no leaderboard.", color = MgpColors.TextMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            GlossyButton("Free", onClick = onPractice, color = ButtonColor.Blue, height = 46.dp, fontSize = 17.sp, modifier = Modifier.width(110.dp))
        }
    }
}

@Composable
private fun TournamentCard(tournament: Tournament, style: TierStyle, index: Int, onPlay: () -> Unit) {
    val prizePool = Leaderboard.prizePool(tournament)
    val firstPrize = Leaderboard.prizePayouts(tournament).first()
    val title = if (tournament.isDuel) "1v1 ${tournament.title}" else tournament.title
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x331E6CF2), spotColor = Color(0x331E6CF2))
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, Color.White, RoundedCornerShape(22.dp)),
    ) {
        // Left: tier + prize pool + art
        Box(Modifier.weight(1.05f).heightIn(min = 190.dp).background(Brush.horizontalGradient(listOf(style.tint, Color.White)))) {
            Column(Modifier.padding(start = 14.dp, top = 14.dp, end = 6.dp, bottom = 8.dp)) {
                Text(title.uppercase(), color = style.color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 2)
                LabelText("Prize pool", color = MgpColors.Navy, size = 11.sp, modifier = Modifier.padding(top = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DisplayText(formatCoins(prizePool), size = 30.sp, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    CoinIcon(26.dp)
                }
                ArtImage(Art.pack(index), 80.dp, Modifier.padding(top = 4.dp))
            }
        }
        // Middle: facts
        Column(Modifier.weight(1f).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact(Icons.Rounded.Groups, "Players", if (tournament.isDuel) "1 vs 1" else "${tournament.poolSize} per pool")
            Fact(Icons.Rounded.EmojiEvents, "Prizes", if (tournament.isDuel) "Winner takes all" else "Top 3 win")
            Fact(Icons.Rounded.ConfirmationNumber, "Entry fee", null) {
                CoinAmount(tournament.entryFee, iconSize = 18.dp, fontSize = 17.sp, iconFirst = false)
            }
        }
        // Right: badge + play + win up to
        Column(
            Modifier.weight(1f).padding(top = 12.dp, bottom = 12.dp, end = 10.dp, start = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (tournament.isDuel) "1V1 DUEL" else "${tournament.poolSize}-PLAYER POOL",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(style.color).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.height(2.dp))
            GlossyButton("Play", onClick = onPlay, color = style.button, height = 50.dp, fontSize = 22.sp, modifier = Modifier.fillMaxWidth())
            LabelText("Win up to", color = MgpColors.Navy, size = 11.sp)
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(style.tint).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatCoins(firstPrize), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.width(4.dp))
                CoinIcon(18.dp)
            }
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, label: String, value: String?, content: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MgpColors.PrimaryDark, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            LabelText(label, color = MgpColors.Navy, size = 10.sp)
            if (value != null) Text(value, color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2)
            content?.invoke()
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
            Modifier.scale(pulse).size(190.dp).shadow(10.dp, RoundedCornerShape(42.dp)).clip(RoundedCornerShape(42.dp)).background(Color.White).padding(10.dp),
        ) { GameIcon(game, 170.dp, cornerRadius = 34.dp) }
        Spacer(Modifier.height(18.dp))
        DisplayText(game.name.uppercase(), size = 34.sp, color = Color.White, outline = Color(0xFF1655DA), textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text("Joining Tournament...", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(Modifier.size(54.dp), color = MgpColors.Primary, trackColor = Color(0xFFBFDCFF), strokeWidth = 6.dp)
        Spacer(Modifier.height(26.dp))
        Text("Pro Tip: $tip", color = MgpColors.PrimaryDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Small helper so other screens can show a game picture at a size. */
@Composable
fun GameBadge(game: Game, size: Dp) = GameIcon(game, size)
