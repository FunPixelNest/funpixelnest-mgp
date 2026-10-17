package com.funpixelnest.mgp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.game.GameResult

/**
 * Bottom-bar tabs, in display order. Games (home) sits in the center.
 * Each tab has an outlined icon (not selected) and a filled icon (selected).
 */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Rewards("Rewards", Icons.Outlined.CardGiftcard, Icons.Rounded.CardGiftcard),
    Store("Store", Icons.Outlined.Storefront, Icons.Rounded.Storefront),
    Home("Games", Icons.Outlined.SportsEsports, Icons.Rounded.SportsEsports),
    Featured("Featured", Icons.Outlined.StarOutline, Icons.Rounded.Star),
    Profile("Profile", Icons.Outlined.Person, Icons.Rounded.Person),
}

/** Screens pushed on top of a tab. */
sealed interface Screen {
    data class GameDetail(val gameId: String) : Screen
    data class PlayGame(val ticket: MatchTicket) : Screen
    data class PoolLeaderboard(val poolId: String) : Screen
    data class PracticeResult(val gameId: String, val result: GameResult?) : Screen
}

/**
 * Minimal navigation: one selected tab plus a stack of pushed screens.
 * Hoisted so platform code can wire the system back button (see MainActivity).
 */
@Stable
class Navigator {
    var tab by mutableStateOf(Tab.Home)
        private set

    private val stack = mutableStateListOf<Screen>()

    val current: Screen? get() = stack.lastOrNull()
    val canGoBack: Boolean get() = stack.isNotEmpty()

    fun selectTab(tab: Tab) {
        this.tab = tab
        stack.clear()
    }

    fun push(screen: Screen) {
        stack.add(screen)
    }

    /** Replaces the top screen (e.g. game → leaderboard, so Back skips the finished game). */
    fun replace(screen: Screen) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(screen)
    }

    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
}
