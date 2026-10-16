package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.ui.components.GameArt
import com.funpixelnest.mgp.ui.theme.MgpColors

private const val ALL = "All"

@Composable
fun HomeScreen(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf(ALL) }
    val categories = remember(games) { listOf(ALL) + games.map { it.category }.distinct() }
    val shown = if (category == ALL) games else games.filter { it.category == category }

    LazyVerticalGrid(
        // Always 2 per row so the icons stay big; the grid scrolls vertically.
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { name ->
                    CategoryChip(name, selected = name == category, onClick = { category = name })
                }
            }
        }
        items(shown, key = { it.id }) { game ->
            GameCard(game = game, onClick = { onGameClick(game) })
        }
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        color = if (selected) MgpColors.OnPrimary else MgpColors.TextPrimary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MgpColors.Primary else MgpColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MgpColors.Surface)
            .clickable(onClick = onClick),
    ) {
        // Square, so the full icon artwork shows without cropping.
        GameArt(
            game = game,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Column(Modifier.padding(12.dp)) {
            Text(game.name, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(game.category, color = MgpColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
