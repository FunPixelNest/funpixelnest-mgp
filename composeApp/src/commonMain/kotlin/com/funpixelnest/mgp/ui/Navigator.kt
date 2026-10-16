package com.funpixelnest.mgp.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.domain.MatchTicket

/** Bottom-bar tabs, in display order. Games (home) sits in the center. */
enum class Tab(val label: String, val icon: String) {
    Rewards("Rewards", "🎁"),
    Store("Store", "🛒"),
    Home("Games", "🎮"),
    Featured("Featured", "⭐"),
    Profile("Profile", "👤"),
}

/** Screens pushed on top of a tab. */
sealed interface Screen {
    data class GameDetail(val gameId: String) : Screen
    data class PlayGame(val ticket: MatchTicket) : Screen
    data class PoolLeaderboard(val poolId: String) : Screen
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
