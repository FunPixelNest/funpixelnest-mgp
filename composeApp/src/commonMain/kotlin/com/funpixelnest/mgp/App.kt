package com.funpixelnest.mgp

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.funpixelnest.mgp.data.FakePlatformRepository
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.game.GameLaunchRequest
import com.funpixelnest.mgp.game.GameLauncher
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.ui.Navigator
import com.funpixelnest.mgp.ui.Screen
import com.funpixelnest.mgp.ui.Tab
import com.funpixelnest.mgp.ui.components.MgpTopBar
import com.funpixelnest.mgp.ui.screens.GameDetailScreen
import com.funpixelnest.mgp.ui.screens.HomeScreen
import com.funpixelnest.mgp.ui.screens.MyGamesScreen
import com.funpixelnest.mgp.ui.screens.PlaceholderGameScreen
import com.funpixelnest.mgp.ui.screens.PoolLeaderboardScreen
import com.funpixelnest.mgp.ui.screens.ProfileScreen
import com.funpixelnest.mgp.ui.screens.WalletScreen
import com.funpixelnest.mgp.ui.theme.MgpColors
import com.funpixelnest.mgp.ui.theme.MgpTheme
import kotlinx.coroutines.launch

/**
 * Root of the shared app, used by Android, iOS and Web.
 *
 * @param repository backend access. Swap FakePlatformRepository for the Nakama one later.
 * @param navigator hoisted so each platform can hook up its system Back button.
 * @param gameLauncher platform Unity launcher. Null = use the in-app placeholder game.
 */
@Composable
fun App(
    repository: PlatformRepository = remember { FakePlatformRepository() },
    navigator: Navigator = remember { Navigator() },
    gameLauncher: GameLauncher? = null,
) {
    MgpTheme {
        val wallet by repository.wallet.collectAsState()
        val myPools by repository.myPools.collectAsState()
        val scope = rememberCoroutineScope()

        fun submitAndShowLeaderboard(ticket: MatchTicket, result: GameResult, replaceTop: Boolean) {
            scope.launch {
                runCatching { repository.submitScore(ticket, result) }
                val leaderboard = Screen.PoolLeaderboard(ticket.poolId)
                if (replaceTop) navigator.replace(leaderboard) else navigator.push(leaderboard)
            }
        }

        fun onJoined(ticket: MatchTicket) {
            if (gameLauncher == null) {
                navigator.push(Screen.PlayGame(ticket))
                return
            }
            scope.launch {
                val request = GameLaunchRequest(ticket.gameId, ticket, repository.player.id)
                // A player who quits still has a paid entry, so it counts as score 0.
                val result = gameLauncher.launch(request) ?: GameResult(score = 0, durationMillis = 0)
                submitAndShowLeaderboard(ticket, result, replaceTop = false)
            }
        }

        val current = navigator.current
        val title = when (current) {
            is Screen.GameDetail -> repository.game(current.gameId)?.name ?: "Game"
            is Screen.PlayGame -> "Playing"
            is Screen.PoolLeaderboard -> "Leaderboard"
            null -> when (navigator.tab) {
                Tab.Home -> "FunPixelNest"
                Tab.MyGames -> "My Games"
                Tab.Wallet -> "Wallet"
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
                )
            },
            bottomBar = {
                if (current == null) {
                    NavigationBar(containerColor = MgpColors.Surface) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = navigator.tab == tab,
                                onClick = { navigator.selectTab(tab) },
                                icon = { Text(tab.icon) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = MgpColors.Primary,
                                    unselectedTextColor = MgpColors.TextMuted,
                                    indicatorColor = MgpColors.SurfaceRaised,
                                ),
                            )
                        }
                    }
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
                            onJoined = { ticket -> onJoined(ticket) },
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
                            onFinished = { result -> submitAndShowLeaderboard(current.ticket, result, replaceTop = true) },
                            modifier = modifier,
                        )
                    }
                }

                is Screen.PoolLeaderboard -> PoolLeaderboardScreen(
                    poolId = current.poolId,
                    repository = repository,
                    modifier = modifier,
                )

                null -> when (navigator.tab) {
                    Tab.Home -> HomeScreen(
                        games = repository.games(),
                        onGameClick = { navigator.push(Screen.GameDetail(it.id)) },
                        modifier = modifier,
                    )

                    Tab.MyGames -> MyGamesScreen(
                        pools = myPools,
                        repository = repository,
                        onPoolClick = { navigator.push(Screen.PoolLeaderboard(it.id)) },
                        modifier = modifier,
                    )

                    Tab.Wallet -> WalletScreen(wallet = wallet, modifier = modifier)
                    Tab.Profile -> ProfileScreen(repository = repository, pools = myPools, modifier = modifier)
                }
            }
        }
    }
}
