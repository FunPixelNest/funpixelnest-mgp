package com.funpixelnest.mgp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.game.GameResult

/** Bottom-bar tabs, in display order. Games sits in the center. */
enum class Tab(val label: String, val icon: ImageVector) {
    Challenges("Challenges", Icons.Rounded.TrackChanges),
    Leagues("Leagues", Icons.Rounded.Shield),
    Games("Games", Icons.Rounded.SportsEsports),
    Rewards("Rewards", Icons.Rounded.CardGiftcard),
    Store("Store", Icons.Rounded.ShoppingCart),
}

/** Full screens pushed on top of a tab. */
sealed interface Screen {
    data class GameDetail(val gameId: String) : Screen
    /** Stand-in game (until the Unity build for this game/platform is added). */
    data class PlayGame(val ticket: MatchTicket) : Screen
    /** Shown behind a real (Unity) game while it runs. */
    data class GameRunning(val ticket: MatchTicket) : Screen
    data class PoolLeaderboard(val poolId: String) : Screen
    data class PracticeResult(val gameId: String, val result: GameResult?) : Screen
    data object Profile : Screen
    data object Inbox : Screen
    data object Settings : Screen
    data object HelpCenter : Screen
    data object Support : Screen
    data object ReferEarn : Screen
    data class Legal(val privacy: Boolean) : Screen
}

/** Pop-ups shown over everything (one at a time, newest on top). */
sealed interface Popup {
    data object Welcome : Popup
    data object DailyBonus : Popup
    data object LuckySpin : Popup
    data class Reward(val title: String, val reward: RewardGrant, val art: String? = null) : Popup
    data class Message(val title: String, val text: String) : Popup
    data class HowToPlay(val gameId: String) : Popup
    data object Rename : Popup
    data object ConfirmLogout : Popup
    data object LeagueRewards : Popup
    data object NeedCoins : Popup
}

/**
 * App navigation: one selected tab, a stack of pushed screens, pop-ups and the side menu.
 * [handleBack] implements the system Back button / gesture for every platform.
 */
@Stable
class Navigator {
    var tab by mutableStateOf(Tab.Games)
        private set

    private val stack = mutableStateListOf<Screen>()
    private val popups = mutableStateListOf<Popup>()

    var drawerOpen by mutableStateOf(false)

    /** Set by the game session while a match is running: Back opens the pause menu instead. */
    var gameBackHandler: (() -> Boolean)? = null

    val current: Screen? get() = stack.lastOrNull()
    val popup: Popup? get() = popups.lastOrNull()
    val canGoBack: Boolean get() = stack.isNotEmpty()

    /** Back to a clean Games tab (after sign-out). */
    fun reset() {
        tab = Tab.Games
        stack.clear()
        popups.clear()
        drawerOpen = false
        gameBackHandler = null
    }

    fun selectTab(tab: Tab) {
        this.tab = tab
        stack.clear()
        drawerOpen = false
    }

    fun push(screen: Screen) {
        drawerOpen = false
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

    /** Removes every pushed screen of this kind (e.g. after a game ends). */
    fun popWhile(predicate: (Screen) -> Boolean) {
        while (stack.isNotEmpty() && predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }

    fun show(popup: Popup) {
        if (popups.lastOrNull() != popup) popups.add(popup)
    }

    fun dismiss(popup: Popup? = this.popup) {
        if (popup != null) popups.remove(popup)
    }

    /** True when Back has something to do inside the app (otherwise the platform may close the app). */
    val wantsBack: Boolean get() = popups.isNotEmpty() || drawerOpen || stack.isNotEmpty() || tab != Tab.Games

    /**
     * System Back: close a pop-up → close the menu → pause the game → previous screen → Games tab.
     * Returns false when there is nothing left (on the Games tab with nothing open).
     */
    fun handleBack(): Boolean {
        when {
            popups.isNotEmpty() -> popups.removeAt(popups.lastIndex)
            drawerOpen -> drawerOpen = false
            gameBackHandler?.invoke() == true -> Unit
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            tab != Tab.Games -> tab = Tab.Games
            else -> return false
        }
        return true
    }
}
