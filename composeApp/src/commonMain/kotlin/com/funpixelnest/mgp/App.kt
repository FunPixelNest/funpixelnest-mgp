package com.funpixelnest.mgp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.data.BackendStatus
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.data.createPlatformRepository
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.game.GameLaunchRequest
import com.funpixelnest.mgp.game.GameLauncher
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.game.LaunchMode
import com.funpixelnest.mgp.ui.Navigator
import com.funpixelnest.mgp.ui.Screen
import com.funpixelnest.mgp.ui.Tab
import com.funpixelnest.mgp.ui.components.AnimatedBottomBar
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.MgpTopBar
import com.funpixelnest.mgp.ui.screens.FeaturedScreen
import com.funpixelnest.mgp.ui.screens.GameDetailScreen
import com.funpixelnest.mgp.ui.screens.HomeScreen
import com.funpixelnest.mgp.ui.screens.PlaceholderGameScreen
import com.funpixelnest.mgp.ui.screens.PoolLeaderboardScreen
import com.funpixelnest.mgp.ui.screens.PracticeResultScreen
import com.funpixelnest.mgp.ui.screens.ProfileScreen
import com.funpixelnest.mgp.ui.screens.RewardsScreen
import com.funpixelnest.mgp.ui.screens.StoreScreen
import com.funpixelnest.mgp.ui.theme.MgpColors
import com.funpixelnest.mgp.ui.theme.MgpTheme
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Root of the shared app, used by Android, iOS and Web.
 *
 * @param repository backend access: Supabase when configured, otherwise the in-memory demo backend.
 * @param navigator hoisted so each platform can hook up its system Back button.
 * @param gameLauncher platform game loader (Unity WebGL on web). Games it can't run use the placeholder screen.
 */
@Composable
fun App(
    repository: PlatformRepository = remember { createPlatformRepository() },
    navigator: Navigator = remember { Navigator() },
    gameLauncher: GameLauncher? = null,
) {
    MgpTheme {
        val status by repository.status.collectAsState()
        LaunchedEffect(repository) { repository.initialize() }

        when (val s = status) {
            BackendStatus.Loading -> StartupScreen(message = null, onRetry = null)
            is BackendStatus.Error -> {
                val scope = rememberCoroutineScope()
                StartupScreen(message = s.message, onRetry = { scope.launch { repository.initialize() } })
            }
            BackendStatus.Ready -> MainScaffold(repository, navigator, gameLauncher)
        }
    }
}

@Composable
private fun MainScaffold(
    repository: PlatformRepository,
    navigator: Navigator,
    gameLauncher: GameLauncher?,
) {
    val wallet by repository.wallet.collectAsState()
    val myPools by repository.myPools.collectAsState()
    val scope = rememberCoroutineScope()

    fun modeFor(ticket: MatchTicket): LaunchMode = when {
        ticket.isPractice -> LaunchMode.PRACTICE
        repository.tournament(ticket.tournamentId)?.isDuel == true -> LaunchMode.DUEL
        else -> LaunchMode.TOURNAMENT
    }

    /** After a match: practice → result screen; paid match → submit score, then the leaderboard. */
    fun finishMatch(ticket: MatchTicket, result: GameResult?, replaceTop: Boolean) {
        if (ticket.isPractice) {
            val screen = Screen.PracticeResult(ticket.gameId, result)
            if (replaceTop) navigator.replace(screen) else navigator.push(screen)
            return
        }
        scope.launch {
            // A player who quits a paid match still used the entry, so it counts as score 0.
            runCatching { repository.submitScore(ticket, result ?: GameResult(score = 0, durationMillis = 0)) }
            val leaderboard = Screen.PoolLeaderboard(ticket.poolId)
            if (replaceTop) navigator.replace(leaderboard) else navigator.push(leaderboard)
        }
    }

    /** Opens the real game when this platform has it, otherwise the placeholder game screen. */
    fun startMatch(ticket: MatchTicket) {
        val game = repository.game(ticket.gameId) ?: return
        val launcher = gameLauncher
        if (launcher == null || !launcher.isAvailable(ticket.gameId)) {
            navigator.push(Screen.PlayGame(ticket))
            return
        }
        scope.launch {
            val request = GameLaunchRequest(
                game = game,
                ticket = ticket,
                playerId = repository.player.id,
                displayName = repository.player.displayName,
                mode = modeFor(ticket),
            )
            val result = launcher.launch(request)
            finishMatch(ticket, result, replaceTop = false)
        }
    }

    fun startPractice(gameId: String) = startMatch(MatchTicket.practice(gameId, Random.nextLong()))

    val current = navigator.current
    val title = when (current) {
        is Screen.GameDetail -> repository.game(current.gameId)?.name ?: "Game"
        is Screen.PlayGame -> if (current.ticket.isPractice) "Practice" else "Playing"
        is Screen.PoolLeaderboard -> "Leaderboard"
        is Screen.PracticeResult -> "Practice"
        null -> when (navigator.tab) {
            Tab.Rewards -> "Rewards"
            Tab.Store -> "Store"
            Tab.Home -> "FunPixelNest"
            Tab.Featured -> "Featured"
            Tab.Profile -> "Profile"
        }
    }

    Scaffold(
        containerColor = MgpColors.Background,
        topBar = {
            MgpTopBar(
                title = title,
                coins = wallet.coins,
                // No back button mid-game: the entry is paid, use Quit instead.
                onBack = if (navigator.canGoBack && current !is Screen.PlayGame) {
                    { navigator.back() }
                } else {
                    null
                },
                // Tapping the balance opens the Store (not mid-game).
                onCoinsClick = if (current !is Screen.PlayGame) {
                    { navigator.selectTab(Tab.Store) }
                } else {
                    null
                },
            )
        },
        bottomBar = {
            if (current == null) {
                AnimatedBottomBar(selected = navigator.tab, onSelect = { navigator.selectTab(it) })
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            is Screen.GameDetail -> {
                val game = repository.game(current.gameId)
                if (game != null) {
                    GameDetailScreen(
                        game = game,
                        repository = repository,
                        coins = wallet.coins,
                        onJoined = { ticket -> startMatch(ticket) },
                        onPractice = { startPractice(game.id) },
                        modifier = modifier,
                    )
                }
            }

            is Screen.PlayGame -> {
                val game = repository.game(current.ticket.gameId)
                if (game != null) {
                    PlaceholderGameScreen(
                        game = game,
                        ticket = current.ticket,
                        onFinished = { result -> finishMatch(current.ticket, result, replaceTop = true) },
                        modifier = modifier,
                    )
                }
            }

            is Screen.PoolLeaderboard -> PoolLeaderboardScreen(
                poolId = current.poolId,
                repository = repository,
                modifier = modifier,
            )

            is Screen.PracticeResult -> {
                val game = repository.game(current.gameId)
                if (game != null) {
                    PracticeResultScreen(
                        game = game,
                        result = current.result,
                        onPlayAgain = {
                            navigator.back()
                            startPractice(game.id)
                        },
                        onPlayForCoins = { navigator.back() },
                        modifier = modifier,
                    )
                }
            }

            null -> when (navigator.tab) {
                Tab.Rewards -> RewardsScreen(repository = repository, modifier = modifier)

                Tab.Store -> StoreScreen(repository = repository, modifier = modifier)

                Tab.Home -> HomeScreen(
                    games = repository.games(),
                    onGameClick = { navigator.push(Screen.GameDetail(it.id)) },
                    modifier = modifier,
                )

                Tab.Featured -> FeaturedScreen(
                    repository = repository,
                    coins = wallet.coins,
                    onJoined = { ticket -> startMatch(ticket) },
                    onGameClick = { navigator.push(Screen.GameDetail(it.id)) },
                    modifier = modifier,
                )

                Tab.Profile -> ProfileScreen(
                    repository = repository,
                    pools = myPools,
                    wallet = wallet,
                    onPoolClick = { navigator.push(Screen.PoolLeaderboard(it.id)) },
                    onGetCoins = { navigator.selectTab(Tab.Store) },
                    modifier = modifier,
                )
            }
        }
    }
}

/** Shown while connecting to the backend, or when connecting failed. */
@Composable
private fun StartupScreen(message: String?, onRetry: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("FunPixelNest", fontWeight = FontWeight.Bold, color = MgpColors.Primary)
            if (message == null) {
                CircularProgressIndicator()
                Text("Getting your games ready…", color = MgpColors.TextMuted)
            } else {
                Text("Couldn't connect", fontWeight = FontWeight.Bold)
                Text(message, color = MgpColors.TextMuted, textAlign = TextAlign.Center)
                if (onRetry != null) Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
