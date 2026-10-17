package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.Avatar
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.CoinIcon
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.RibbonBanner
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors

/**
 * Live result of a pool or 1v1 duel: medals, avatars, scores and prizes.
 * Players still playing show "Playing…", empty seats show "Waiting for player…".
 */
@Composable
fun PoolLeaderboardScreen(
    poolId: String,
    repository: PlatformRepository,
    onPlayAgain: (Tournament) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = remember(poolId) { repository.observePool(poolId) }
    val pool by flow.collectAsState(initial = null)
    val p = pool
    if (p == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val tournament = repository.tournament(p.tournamentId) ?: return
    val game = repository.game(tournament.gameId) ?: return
    val rows = Leaderboard.rank(p, tournament)
    val me = repository.player.id
    val done = p.status == PoolStatus.COMPLETED
    val waitingSeats = (tournament.poolSize - p.entries.size).coerceAtLeast(0)
    val stillPlaying = p.entries.count { !it.hasSubmitted }
    val myRow = rows.find { it.entry.playerId == me }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameIcon(game, 96.dp, cornerRadius = 24.dp)
        ArtImage(Art.trophy, 170.dp, Modifier.padding(top = 2.dp).height(80.dp))
        RibbonBanner(if (tournament.isDuel) "1V1 RESULT" else "LEADERBOARD", fontSize = 30.sp)
        Text(
            when {
                done && tournament.isDuel && myRow?.rank == 1 -> "You won the duel! The pot is yours."
                done && tournament.isDuel -> "Good game! Better luck in the next duel."
                done -> "Great game! See how you rank!"
                waitingSeats > 0 -> "Finding ${if (waitingSeats == 1) "1 more player" else "$waitingSeats more players"}… results update live."
                else -> "$stillPlaying still playing… results update live."
            },
            color = MgpColors.Navy,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                ResultRow(
                    rank = row.rank,
                    completed = done,
                    avatarId = row.entry.avatarId,
                    name = row.entry.displayName + if (row.entry.playerId == me) " (You)" else "",
                    score = row.entry.score,
                    prize = row.prize,
                    isMe = row.entry.playerId == me,
                )
            }
            repeat(waitingSeats) { ResultRow(null, false, null, "Waiting for player…", null, 0, isMe = false) }
        }
        Spacer(Modifier.height(18.dp))
        GlossyButton("Play again", onClick = { onPlayAgain(tournament) }, color = ButtonColor.Green, height = 60.dp, fontSize = 24.sp, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        GlossyButton("Exit", onClick = onExit, color = ButtonColor.Blue, height = 60.dp, fontSize = 24.sp, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ResultRow(
    rank: Int?,
    completed: Boolean,
    avatarId: Int?,
    name: String,
    score: Long?,
    prize: Long,
    isMe: Boolean,
) {
    val bg = when {
        completed && rank == 1 -> Brush.verticalGradient(listOf(Color(0xFFFFE57A), Color(0xFFFFC93C)))
        completed && rank == 2 -> Brush.verticalGradient(listOf(Color(0xFFF2F7FF), Color(0xFFDCE8FA)))
        completed && rank == 3 -> Brush.verticalGradient(listOf(Color(0xFFFFE5D8), Color(0xFFF7CDB8)))
        else -> Brush.verticalGradient(listOf(Color(0xFFF7FBFF), Color(0xFFE6F0FC)))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(if (isMe) 2.5.dp else 1.dp, if (isMe) MgpColors.Primary else Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(54.dp), contentAlignment = Alignment.Center) {
            val medal = if (completed) Art.medal(rank) else null
            when {
                medal != null -> ArtImage(medal, 50.dp)
                rank != null -> DisplayText("$rank", size = 30.sp)
                else -> Text("…", color = MgpColors.TextMuted, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (avatarId != null) {
            Avatar(avatarId, 56.dp)
        } else {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFDDE8F7)))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = if (avatarId == null) MgpColors.TextMuted else MgpColors.Navy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            when {
                score != null -> DisplayText(formatCoins(score), size = 24.sp, maxLines = 1)
                avatarId != null -> Text("Playing…", color = MgpColors.Primary, fontWeight = FontWeight.Bold)
            }
            if (completed && prize > 0) {
                Row(
                    Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33000000)).padding(start = 3.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoinIcon(22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("+${formatCoins(prize)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Result of a free practice round. */
@Composable
fun PracticeResultScreen(
    game: Game,
    result: GameResult?,
    onPlayAgain: () -> Unit,
    onPlayForCoins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GameIcon(game, 110.dp, cornerRadius = 26.dp)
        Spacer(Modifier.height(6.dp))
        RibbonBanner("PRACTICE")
        if (result != null) {
            Text("YOUR SCORE", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(Art.trophy, 110.dp)
                DisplayText(formatCoins(result.score), size = 56.sp)
            }
            Text("Nice warm-up! Ready to play for coins?", color = MgpColors.Navy, fontSize = 16.sp, textAlign = TextAlign.Center)
        } else {
            Text("Practice ended early. Try again whenever you're ready!", color = MgpColors.Navy, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(22.dp))
        GlossyButton("Play again", onClick = onPlayAgain, color = ButtonColor.Green, height = 58.dp, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        GlossyButton("Play for coins", onClick = onPlayForCoins, height = 58.dp, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
    }
}
