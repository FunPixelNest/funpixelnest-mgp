package com.funpixelnest.mgp

import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.BackendStatus
import com.funpixelnest.mgp.data.JoinResult
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.data.PurchaseResult
import com.funpixelnest.mgp.data.createPlatformRepository
import com.funpixelnest.mgp.domain.ChallengeCategory
import com.funpixelnest.mgp.domain.ClaimResult
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.game.GameLaunchRequest
import com.funpixelnest.mgp.game.GameLauncher
import com.funpixelnest.mgp.game.GAME_HUD_HEIGHT
import com.funpixelnest.mgp.game.GameHud
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.game.LaunchMode
import com.funpixelnest.mgp.platform.PlatformBackHandler
import com.funpixelnest.mgp.platform.edgeSwipeBack
import com.funpixelnest.mgp.platform.usesEdgeSwipeBack
import com.funpixelnest.mgp.ui.AppSettings
import com.funpixelnest.mgp.ui.Navigator
import com.funpixelnest.mgp.ui.Popup
import com.funpixelnest.mgp.ui.Screen
import com.funpixelnest.mgp.ui.Tab
import com.funpixelnest.mgp.ui.components.MgpBottomBar
import com.funpixelnest.mgp.ui.components.PlayerHeader
import com.funpixelnest.mgp.ui.components.SkyBackground
import com.funpixelnest.mgp.ui.screens.ChallengesScreen
import com.funpixelnest.mgp.ui.screens.EconomyActions
import com.funpixelnest.mgp.ui.screens.EndGameScreen
import com.funpixelnest.mgp.ui.screens.GameDetailScreen
import com.funpixelnest.mgp.ui.screens.AllGamesScreen
import com.funpixelnest.mgp.ui.screens.GameHudOverlay
import com.funpixelnest.mgp.ui.screens.GameOverScreen
import com.funpixelnest.mgp.ui.screens.GameRunningBackdrop
import com.funpixelnest.mgp.ui.screens.GamesScreen
import com.funpixelnest.mgp.ui.screens.HelpCenterScreen
import com.funpixelnest.mgp.ui.screens.InboxScreen
import com.funpixelnest.mgp.ui.screens.JoiningScreen
import com.funpixelnest.mgp.ui.screens.LeaguesScreen
import com.funpixelnest.mgp.ui.screens.LegalScreen
import com.funpixelnest.mgp.ui.screens.LoginScreen
import com.funpixelnest.mgp.ui.screens.MenuItem
import com.funpixelnest.mgp.ui.screens.MoreOptionsDrawer
import com.funpixelnest.mgp.ui.screens.PAUSE_LIMIT_MILLIS
import com.funpixelnest.mgp.ui.screens.PauseScreen
import com.funpixelnest.mgp.ui.screens.PlaceholderGameScreen
import com.funpixelnest.mgp.ui.screens.PoolLeaderboardScreen
import com.funpixelnest.mgp.ui.screens.PopupActions
import com.funpixelnest.mgp.ui.screens.PopupHost
import com.funpixelnest.mgp.ui.screens.PracticeResultScreen
import com.funpixelnest.mgp.ui.screens.ProfileScreen
import com.funpixelnest.mgp.ui.screens.ReferEarnScreen
import com.funpixelnest.mgp.ui.screens.RewardsScreen
import com.funpixelnest.mgp.ui.screens.STORE_URL
import com.funpixelnest.mgp.ui.screens.SUPPORT_EMAIL
import com.funpixelnest.mgp.ui.screens.SettingsScreen
import com.funpixelnest.mgp.ui.screens.SplashScreen
import com.funpixelnest.mgp.ui.screens.StoreScreen
import com.funpixelnest.mgp.ui.screens.SupportScreen
import com.funpixelnest.mgp.ui.screens.WEBSITE_URL
import com.funpixelnest.mgp.ui.screens.playerCode
import com.funpixelnest.mgp.ui.theme.MgpColors
import com.funpixelnest.mgp.ui.theme.MgpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Root of the shared app, used by Android, iOS and Web.
 *
 * @param repository backend access: Supabase when configured, otherwise the in-memory demo backend.
 * @param navigator tabs, screens, pop-ups and the platform Back button.
 * @param gameLauncher platform game loader (Unity WebGL on web). Games it can't run use the placeholder screen.
 */
@Composable
fun App(
    repository: PlatformRepository = remember { createPlatformRepository() },
    navigator: Navigator = remember { Navigator() },
    gameLauncher: GameLauncher? = null,
    settings: AppSettings = remember { AppSettings() },
) {
    remember { AppSettings.loadTheme() }
    MgpTheme {
        val status by repository.status.collectAsState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(repository) { repository.initialize() }

        // Signed out (first launch or after Log out): start fresh on the Games tab.
        LaunchedEffect(status) {
            if (status == BackendStatus.SignedOut) navigator.reset()
        }

        PlatformBackHandler(enabled = status == BackendStatus.Ready && navigator.wantsBack) { navigator.handleBack() }

        val root = if (usesEdgeSwipeBack) {
            Modifier.fillMaxSize().edgeSwipeBack(status == BackendStatus.Ready && navigator.wantsBack) { navigator.handleBack() }
        } else {
            Modifier.fillMaxSize()
        }
        Box(root) {
            when (val s = status) {
                BackendStatus.Loading -> SplashScreen(message = null, onRetry = null)
                is BackendStatus.Error -> SplashScreen(message = s.message, onRetry = { scope.launch { repository.initialize() } })
                BackendStatus.SignedOut -> LoginScreen(repository)
                BackendStatus.Ready -> MainApp(repository, navigator, gameLauncher, settings)
            }
        }
    }
}

/** A match in progress (the Unity game or the placeholder). */
@Stable
private class GameSession(val ticket: MatchTicket, val game: Game, val real: Boolean) {
    var paused by mutableStateOf(false)
    var confirmEnd by mutableStateOf(false)
    var pauseStarted by mutableStateOf<TimeMark?>(null)
    var ended = false
}

@Composable
private fun MainApp(
    repository: PlatformRepository,
    navigator: Navigator,
    gameLauncher: GameLauncher?,
    settings: AppSettings,
) {
    val wallet by repository.wallet.collectAsState()
    val profile by repository.profile.collectAsState()
    val challenges by repository.challenges.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var joining by remember { mutableStateOf<Game?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<GameSession?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching { uriHandler.openUri(url) }.onFailure { toast = "Couldn't open $url" }
    }

    fun copy(text: String, message: String) {
        runCatching { clipboard.setText(AnnotatedString(text)) }
        toast = message
    }

    fun showClaim(result: ClaimResult, art: String? = null) {
        when (result) {
            is ClaimResult.Granted -> navigator.show(Popup.Reward(result.title, result.reward, art))
            is ClaimResult.Failed -> navigator.show(Popup.Message("Oops", result.message))
        }
    }

    // Welcome bonus, then the daily bonus, when the player arrives.
    LaunchedEffect(Unit) {
        repository.refreshProgress()
        delay(500)
        if (!repository.dailyBonus.value.claimedToday) navigator.show(Popup.DailyBonus)
        if (repository.profile.value.welcomePending) navigator.show(Popup.Welcome)
    }
    // Fresh challenge / league / shop data whenever a tab opens.
    LaunchedEffect(navigator.tab) { repository.refreshProgress() }
    // Let a running game know when sound settings change.
    LaunchedEffect(settings.music, settings.sound, settings.vibration) {
        if (session?.real == true) gameLauncher?.applySettings(settings.forGame)
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_200)
            toast = null
        }
    }

    // ---------------------------------------------------------------- matches

    // Area the in-game HUD covers (status bar + HUD row) and the system bar at the bottom; the game keeps it clear.
    val hudInsetTop = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding() + GAME_HUD_HEIGHT
    val hudInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun modeFor(ticket: MatchTicket): LaunchMode = when {
        ticket.isPractice -> LaunchMode.PRACTICE
        repository.tournament(ticket.tournamentId)?.isDuel == true -> LaunchMode.DUEL
        else -> LaunchMode.TOURNAMENT
    }

    /** After a match: practice → result screen; paid match → Game Over score board (Submit → leaderboard). */
    fun finishMatch(ticket: MatchTicket, result: GameResult?) {
        if (ticket.isPractice) {
            navigator.push(Screen.PracticeResult(ticket.gameId, result))
            return
        }
        submitError = null
        navigator.push(Screen.GameOver(ticket, result))
    }

    /** Game Over → Submit Score: send the score, then show the pool's leaderboard. */
    fun submitGameOver(screen: Screen.GameOver) {
        if (submitting) return
        submitting = true
        submitError = null
        scope.launch {
            // A player who quits a paid match still used the entry, so it counts as score 0.
            val ok = runCatching {
                repository.submitScore(screen.ticket, screen.result ?: GameResult(score = 0, durationMillis = 0))
            }.isSuccess
            submitting = false
            if (ok) {
                navigator.replace(Screen.PoolLeaderboard(screen.ticket.poolId))
            } else {
                submitError = "Couldn't send your score. Check your connection and tap Submit again."
            }
        }
    }

    fun gameEnded(s: GameSession, result: GameResult?) {
        if (s.ended) return
        s.ended = true
        if (session === s) session = null
        gameLauncher?.setPauseRequestListener(null)
        navigator.popWhile { it is Screen.PlayGame || it is Screen.GameRunning }
        finishMatch(s.ticket, result)
    }

    fun pauseGame() {
        val s = session ?: return
        if (s.paused) return
        s.paused = true
        s.confirmEnd = false
        s.pauseStarted = TimeSource.Monotonic.markNow()
        if (s.real) gameLauncher?.pause()
    }

    fun resumeGame() {
        val s = session ?: return
        s.paused = false
        s.confirmEnd = false
        if (s.real) gameLauncher?.resume()
    }

    fun endGameNow() {
        val s = session ?: return
        s.paused = false
        if (s.real) gameLauncher?.forfeit() else gameEnded(s, null)
    }

    /** Opens the real game when this platform has it, otherwise the placeholder game screen. */
    fun startMatch(ticket: MatchTicket) {
        val game = repository.game(ticket.gameId) ?: return
        val launcher = gameLauncher
        if (launcher == null || !launcher.isAvailable(ticket.gameId)) {
            session = GameSession(ticket, game, real = false)
            navigator.push(Screen.PlayGame(ticket))
            return
        }
        val s = GameSession(ticket, game, real = true)
        session = s
        navigator.push(Screen.GameRunning(ticket))
        launcher.setPauseRequestListener { pauseGame() }
        scope.launch {
            val request = GameLaunchRequest(
                game = game,
                ticket = ticket,
                playerId = repository.player.id,
                displayName = repository.player.displayName,
                mode = modeFor(ticket),
                settings = settings.forGame,
                hudInsetTopDp = hudInsetTop.value.toInt(),
                hudInsetBottomDp = hudInsetBottom.value.toInt(),
            )
            val result = launcher.launch(request)
            gameEnded(s, result)
        }
    }

    fun startPractice(gameId: String) = startMatch(MatchTicket.practice(gameId, Random.nextLong()))

    fun play(tournament: Tournament) {
        val game = repository.game(tournament.gameId) ?: return
        if (joining != null) return
        joining = game
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            val result = repository.joinTournament(tournament.id)
            val shown = started.elapsedNow().inWholeMilliseconds
            if (shown < 1_400) delay(1_400 - shown)
            joining = null
            when (result) {
                is JoinResult.Joined -> startMatch(result.ticket)
                JoinResult.InsufficientCoins -> navigator.show(Popup.NeedCoins)
                is JoinResult.Error -> navigator.show(Popup.Message("Couldn't join", result.message))
            }
        }
    }

    // Back during a match opens the pause menu; Back while paused resumes.
    navigator.gameBackHandler = handler@{
        // The score must be sent: Back on the Game Over screen submits too.
        (navigator.current as? Screen.GameOver)?.let { submitGameOver(it); return@handler true }
        val s = session ?: return@handler false
        when {
            s.confirmEnd -> s.confirmEnd = false
            s.paused -> resumeGame()
            else -> pauseGame()
        }
        true
    }

    // A paused match ends after 5 minutes.
    val pausedSession = session?.takeIf { it.paused }
    LaunchedEffect(pausedSession, pausedSession?.pauseStarted) {
        val started = pausedSession?.pauseStarted ?: return@LaunchedEffect
        val left = PAUSE_LIMIT_MILLIS - started.elapsedNow().inWholeMilliseconds
        if (left > 0) delay(left)
        if (session === pausedSession && pausedSession?.paused == true) endGameNow()
    }

    // ---------------------------------------------------------------- rewards & store actions

    val economy = remember(repository) {
        EconomyActions(
            buyPack = { pack ->
                scope.launch {
                    when (val r = repository.purchasePack(pack.id)) {
                        is PurchaseResult.Success -> navigator.show(Popup.Reward("${pack.name} (demo)", RewardGrant(r.coinsAdded)))
                        is PurchaseResult.Error -> navigator.show(Popup.Message("Purchase failed", r.message))
                    }
                }
            },
            watchVideo = {
                scope.launch {
                    val coins = repository.claimFreeCoins()
                    if (coins > 0) navigator.show(Popup.Reward("Free coins", RewardGrant(coins)))
                    else navigator.show(Popup.Message("No more videos", "You've watched all of today's videos. Come back tomorrow!"))
                }
            },
            invite = { navigator.push(Screen.ReferEarn) },
            rate = { openUrl(STORE_URL) },
            claimDaily = {
                scope.launch {
                    navigator.dismiss(Popup.DailyBonus)
                    val result = repository.claimDailyBonus()
                    val epic = result is ClaimResult.Granted && result.title.contains("Epic")
                    showClaim(result, art = if (epic) "epic" else null)
                }
            },
            spin = { navigator.show(Popup.LuckySpin) },
            openChest = { id -> scope.launch { showClaim(repository.openChest(id), art = id) } },
            claimChallenge = { c -> scope.launch { showClaim(repository.claimChallenge(c.id)) } },
        )
    }

    // ---------------------------------------------------------------- layout

    val current = navigator.current
    val onTabRoot = current == null

    // Tabs: swiping the pager selects the tab, and tapping a tab (or code) scrolls the pager to it.
    val tabPager = rememberPagerState(initialPage = navigator.tab.ordinal) { Tab.entries.size }
    LaunchedEffect(navigator.tab) {
        val target = navigator.tab.ordinal
        if (tabPager.currentPage == target || tabPager.isScrollInProgress) return@LaunchedEffect
        // Not on screen (a pushed screen covers the tabs): jump; otherwise glide to the tab.
        if (tabPager.layoutInfo.visiblePagesInfo.isEmpty()) tabPager.scrollToPage(target) else tabPager.animateScrollToPage(target)
    }
    LaunchedEffect(tabPager) {
        snapshotFlow { tabPager.settledPage }.collect { index ->
            val tab = Tab.entries[index]
            if (navigator.current == null && navigator.tab != tab) navigator.selectTab(tab)
        }
    }
    val showChrome = joining != null || onTabRoot || current is Screen.GameDetail

    SkyBackground {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                showChrome,
                enter = expandVertically(tween(PAGE_MS)) + fadeIn(tween(PAGE_MS)),
                exit = shrinkVertically(tween(PAGE_MS)) + fadeOut(tween(PAGE_MS)),
            ) {
                PlayerHeader(
                    name = repository.player.displayName,
                    avatarId = profile.avatarId,
                    level = profile.levelInfo,
                    coins = wallet.coins,
                    gems = wallet.gems,
                    unread = profile.unreadNotifications,
                    onAvatarClick = { navigator.push(Screen.Profile) },
                    onCurrencyClick = { navigator.selectTab(Tab.Store) },
                    onMenuClick = { navigator.drawerOpen = true },
                )
            }
            val content = Modifier.weight(1f).fillMaxWidth()
            // The tab roots live in one pager (swipe left / right between tabs), so the page key ignores the tab.
            val page = Page(current, if (current == null) Tab.Games else navigator.tab, joining, navigator.depth)
            AnimatedContent(
                targetState = page,
                modifier = content,
                transitionSpec = { pageTransition(initialState, targetState) },
                label = "page",
            ) { shown ->
                val current = shown.screen
                val joiningGame = shown.joining
                if (joiningGame != null) {
                    JoiningScreen(joiningGame)
                } else {
                    when (current) {
                        is Screen.GameDetail -> {
                            val game = repository.game(current.gameId)
                            if (game != null) {
                                GameDetailScreen(
                                    game = game,
                                    tournaments = repository.tournamentsFor(game.id),
                                    onPlay = { play(it) },
                                    onInfo = { navigator.show(Popup.HowToPlay(game.id)) },
                                )
                            }
                        }
                        is Screen.AllGames -> AllGamesScreen(
                            games = repository.games(),
                            initialCategory = current.category,
                            onGameClick = { navigator.push(Screen.GameDetail(it.id)) },
                            onBack = { navigator.back() },
                        )
                        is Screen.PlayGame -> {
                            val game = repository.game(current.ticket.gameId)
                            val s = session
                            if (game != null) {
                                Box(Modifier.fillMaxSize()) {
                                    PlaceholderGameScreen(
                                        game = game,
                                        ticket = current.ticket,
                                        onFinished = { result -> if (s != null) gameEnded(s, result) else navigator.back() },
                                        modifier = Modifier.padding(top = hudInsetTop).windowInsetsPadding(WindowInsets.navigationBars),
                                    )
                                    GameHudOverlay(GameHud(), onPause = { pauseGame() })
                                }
                            }
                        }
                        is Screen.GameRunning -> {
                            // The game fills the screen; the app's HUD (same for every game) floats over its top.
                            val launcher = gameLauncher
                            val hud = launcher?.hud?.collectAsState()?.value ?: GameHud()
                            Box(Modifier.fillMaxSize()) {
                                repository.game(current.ticket.gameId)?.let { GameRunningBackdrop(it) }
                                // Android draws the game here (WebView); on web it opens over the app below the HUD.
                                launcher?.GameView(Modifier.fillMaxSize())
                                // Only once the game is actually playing (it has sent its first HUD values),
                                // not while Unity / the WebGL build is still loading.
                                androidx.compose.animation.AnimatedVisibility(visible = hud.reported, enter = fadeIn(), exit = fadeOut()) {
                                    GameHudOverlay(hud, onPause = { pauseGame() }, gameId = current.ticket.gameId)
                                }
                            }
                        }
                        is Screen.GameOver -> {
                            val game = repository.game(current.ticket.gameId)
                            if (game != null) {
                                GameOverScreen(
                                    game = game,
                                    result = current.result,
                                    submitting = submitting,
                                    error = submitError,
                                    onSubmit = { submitGameOver(current) },
                                )
                            }
                        }
                        is Screen.PoolLeaderboard -> PoolLeaderboardScreen(
                            poolId = current.poolId,
                            repository = repository,
                            onPlayAgain = { t ->
                                navigator.back()
                                if (navigator.current !is Screen.GameDetail) navigator.push(Screen.GameDetail(t.gameId))
                                play(t)
                            },
                            onExit = { navigator.back() },
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
                                )
                            }
                        }
                        Screen.Profile -> ProfileScreen(
                            repository = repository,
                            onBack = { navigator.back() },
                            onSettings = { navigator.push(Screen.Settings) },
                            onRename = { navigator.show(Popup.Rename) },
                            onCopy = { text, msg -> copy(text, msg) },
                            onPoolClick = { navigator.push(Screen.PoolLeaderboard(it.id)) },
                            onAvatarPick = { id -> scope.launch { repository.setAvatar(id)?.let { toast = it } } },
                        )
                        Screen.Inbox -> InboxScreen(repository) { navigator.back() }
                        Screen.Settings -> SettingsScreen(repository, settings, onBack = { navigator.back() }, onLogout = { navigator.show(Popup.ConfirmLogout) })
                        Screen.HelpCenter -> HelpCenterScreen { navigator.back() }
                        Screen.Support -> SupportScreen(
                            onBack = { navigator.back() },
                            onEmail = { openUrl("mailto:$SUPPORT_EMAIL?subject=Help%20(${playerCode(repository.player.id)})") },
                            onCopy = { text, msg -> copy(text, msg) },
                            playerId = repository.player.id,
                        )
                        Screen.ReferEarn -> ReferEarnScreen(onBack = { navigator.back() }, playerId = repository.player.id, onCopy = { t, m -> copy(t, m) })
                        is Screen.Legal -> LegalScreen(current.privacy) { navigator.back() }
                        null -> HorizontalPager(
                            state = tabPager,
                            modifier = Modifier.fillMaxSize(),
                            key = { Tab.entries[it] },
                        ) { index ->
                            when (Tab.entries[index]) {
                                Tab.Challenges -> ChallengesScreen(
                                    repository = repository,
                                    onClaim = economy.claimChallenge,
                                    onGo = { c ->
                                        when {
                                            c.gameId != null -> navigator.push(Screen.GameDetail(c.gameId))
                                            c.category == ChallengeCategory.LEAGUE -> navigator.selectTab(Tab.Leagues)
                                            else -> navigator.selectTab(Tab.Games)
                                        }
                                    },
                                )
                                Tab.Leagues -> LeaguesScreen(
                                    repository = repository,
                                    onPlayNow = { navigator.selectTab(Tab.Games) },
                                    onViewRewards = { navigator.show(Popup.LeagueRewards) },
                                )
                                Tab.Games -> GamesScreen(
                                    repository.games(),
                                    onGameClick = { navigator.push(Screen.GameDetail(it.id)) },
                                    onSeeAll = { navigator.push(Screen.AllGames(it)) },
                                )
                                Tab.Rewards -> RewardsScreen(repository, economy, onViewChallenges = { navigator.selectTab(Tab.Challenges) })
                                Tab.Store -> StoreScreen(repository, economy)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                showChrome,
                enter = expandVertically(tween(PAGE_MS), expandFrom = Alignment.Top) + fadeIn(tween(PAGE_MS)),
                exit = shrinkVertically(tween(PAGE_MS), shrinkTowards = Alignment.Top) + fadeOut(tween(PAGE_MS)),
            ) {
                MgpBottomBar(
                    selected = if (onTabRoot) Tab.entries[tabPager.targetPage] else navigator.tab,
                    challengeBadge = challenges.claimableCount,
                    onSelect = { navigator.selectTab(it) },
                )
            }
        }

        MoreOptionsDrawer(
            open = navigator.drawerOpen,
            repository = repository,
            onClose = { navigator.drawerOpen = false },
            onRename = { navigator.show(Popup.Rename) },
            darkMode = settings.darkMode,
            onDarkMode = { settings.darkMode = it },
            onItem = { item ->
                navigator.drawerOpen = false
                when (item) {
                    MenuItem.Profile -> navigator.push(Screen.Profile)
                    MenuItem.Inbox -> navigator.push(Screen.Inbox)
                    MenuItem.Settings -> navigator.push(Screen.Settings)
                    MenuItem.Help -> navigator.push(Screen.HelpCenter)
                    MenuItem.Support -> navigator.push(Screen.Support)
                    MenuItem.Refer -> navigator.push(Screen.ReferEarn)
                    MenuItem.Rate -> openUrl(STORE_URL)
                    MenuItem.Follow -> openUrl(WEBSITE_URL)
                    MenuItem.Terms -> navigator.push(Screen.Legal(privacy = false))
                    MenuItem.Privacy -> navigator.push(Screen.Legal(privacy = true))
                    MenuItem.Logout -> navigator.show(Popup.ConfirmLogout)
                }
            },
        )

        // Pause menu over everything while a match is paused.
        val s = session
        val pauseStarted = s?.pauseStarted
        if (s != null && s.paused && pauseStarted != null) {
            if (s.confirmEnd) {
                EndGameScreen(
                    game = s.game,
                    isPractice = s.ticket.isPractice,
                    pauseStarted = pauseStarted,
                    onResume = { resumeGame() },
                    onEnd = { endGameNow() },
                )
            } else {
                PauseScreen(
                    game = s.game,
                    pauseStarted = pauseStarted,
                    settings = settings,
                    onResume = { resumeGame() },
                    onEndNow = { s.confirmEnd = true },
                    onHowToPlay = { navigator.show(Popup.HowToPlay(s.game.id)) },
                )
            }
        }

        PopupHost(
            popup = navigator.popup,
            repository = repository,
            actions = PopupActions(
                dismiss = { navigator.dismiss(it) },
                ackWelcome = { scope.launch { repository.acknowledgeWelcome() } },
                claimDaily = economy.claimDaily,
                spin = { repository.spinWheel() },
                rename = { name -> repository.setDisplayName(name) },
                logout = { scope.launch { repository.signOut() } },
                openStore = { navigator.selectTab(Tab.Store) },
            ),
        )

        AnimatedVisibility(
            toast != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                toast ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE613205B)).padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- page transitions

private const val PAGE_MS = 320

/** What the content area shows: the pushed screen (or the tab root), the tab, the joining screen, stack depth. */
private data class Page(val screen: Screen?, val tab: Tab, val joining: Game?, val depth: Int)

private fun Screen?.isGame() = this is Screen.PlayGame || this is Screen.GameRunning

/**
 * Tween-style page changes: a pushed screen slides in from the right over the old one, Back slides it out
 * to the right, tabs glide sideways in tab order, and the joining / Game Over screens zoom in.
 * The running game itself appears and disappears without animation (Unity draws its own view).
 */
private fun AnimatedContentTransitionScope<Page>.pageTransition(from: Page, to: Page): ContentTransform {
    val ease = FastOutSlowInEasing
    fun <T> t() = tween<T>(PAGE_MS, easing = ease)
    return when {
        from.screen.isGame() || to.screen.isGame() -> {
            val enter = if (to.screen is Screen.GameOver) fadeIn(t()) + scaleIn(t(), initialScale = 0.85f) else EnterTransition.None
            enter togetherWith ExitTransition.None
        }
        to.joining != null || from.joining != null ->
            (fadeIn(t()) + scaleIn(t(), initialScale = 0.92f)) togetherWith (fadeOut(t()) + scaleOut(t(), targetScale = 1.04f))
        to.depth > from.depth ->
            (slideInHorizontally(t()) { it } + fadeIn(t())) togetherWith
                (slideOutHorizontally(t()) { -it / 4 } + fadeOut(t()))
        to.depth < from.depth ->
            (slideInHorizontally(t()) { -it / 4 } + fadeIn(t())) togetherWith
                (slideOutHorizontally(t()) { it } + fadeOut(t()))
        to.tab != from.tab -> {
            val dir = if (to.tab.ordinal > from.tab.ordinal) 1 else -1
            (slideInHorizontally(t()) { dir * it / 3 } + fadeIn(t())) togetherWith
                (slideOutHorizontally(t()) { -dir * it / 3 } + fadeOut(t()))
        }
        // Same depth, different screen (e.g. Game Over → leaderboard): cross-fade with a small zoom.
        else -> (fadeIn(t()) + scaleIn(t(), initialScale = 0.94f)) togetherWith fadeOut(t())
    }.also { if (to.depth > from.depth) it.targetContentZIndex = 1f else if (to.depth < from.depth) it.targetContentZIndex = -1f }
}
