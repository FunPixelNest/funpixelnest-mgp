package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.JoinResult
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.ui.components.SectionTitle
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch

@Composable
fun FeaturedScreen(
    repository: PlatformRepository,
    coins: Long,
    onJoined: (MatchTicket) -> Unit,
    onGameClick: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var joiningId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroBanner() }

        item { SectionTitle("New games") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(repository.newGames(), key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onGameClick(game) }, modifier = Modifier.width(150.dp))
                }
            }
        }

        item { SectionTitle("Big prize events") }
        message?.let { text -> item { Text(text, color = Color(0xFFFF8A80)) } }
        items(repository.featuredTournaments(), key = { it.id }) { tournament ->
            val game = repository.game(tournament.gameId)
            TournamentCard(
                tournament = tournament,
                accent = Color(game?.accentColor ?: 0xFF7C5CFF),
                canAfford = coins >= tournament.entryFee,
                isJoining = joiningId == tournament.id,
                overline = game?.name,
                leadingGame = game,
                onJoin = {
                    joiningId = tournament.id
                    message = null
                    scope.launch {
                        when (val result = repository.joinTournament(tournament.id)) {
                            is JoinResult.Joined -> onJoined(result.ticket)
                            JoinResult.InsufficientCoins -> message = "Not enough coins. Get more in the Store or Rewards."
                            is JoinResult.Error -> message = result.message
                        }
                        joiningId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun HeroBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(MgpColors.Secondary, Color(0xFFE040FB))))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("5-player tournaments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            "Same board for everyone. Top 3 win: 50% · 30% · 20%",
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}
