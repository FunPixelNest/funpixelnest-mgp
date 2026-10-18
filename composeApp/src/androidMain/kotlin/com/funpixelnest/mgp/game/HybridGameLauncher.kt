package com.funpixelnest.mgp.game

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A game player that lives inside MainActivity and needs the activity's lifecycle (Unity as a Library).
 */
interface ActivityHostedGame {
    /** True while a match is on screen. */
    val isPlaying: Boolean
    fun onActivityResume() {}
    fun onActivityPause() {}
    fun onWindowFocusChanged(hasFocus: Boolean) {}
    fun onConfigurationChanged(newConfig: Configuration) {}
    fun onLowMemory() {}
    fun onTrimMemory(level: Int) {}

    companion object {
        private const val UNITY_LAUNCHER = "com.funpixelnest.mgp.unity.UnityGameLauncher"

        /**
         * The native Unity games player, when this build includes the Unity library (composeApp/unity/), else null.
         * Loaded by name so the app also builds without the Unity library.
         */
        fun createUnityLauncher(activity: ComponentActivity): GameLauncher? = runCatching {
            Class.forName(UNITY_LAUNCHER)
                .getConstructor(ComponentActivity::class.java)
                .newInstance(activity) as GameLauncher
        }.getOrNull()
    }
}

/**
 * Picks the player per game: the native Unity build when the app includes it (better performance),
 * otherwise the WebGL build in a WebView. The rest of the app sees one [GameLauncher].
 */
class HybridGameLauncher(
    private val native: GameLauncher?,
    private val web: GameLauncher,
) : GameLauncher, ActivityHostedGame {

    private var current by mutableStateOf(web)
    private val hudState = MutableStateFlow(GameHud())
    override val hud: StateFlow<GameHud> = hudState.asStateFlow()
    private val scope = MainScope()

    init {
        // Forward the active player's HUD values.
        native?.let { n -> scope.launch { n.hud.collect { if (current === n) hudState.value = it } } }
        scope.launch { web.hud.collect { if (current === web) hudState.value = it } }
    }

    /** True when [gameId] runs natively (Unity as a Library) on this device. */
    fun isNative(gameId: String): Boolean = native?.isAvailable(gameId) == true

    override fun isAvailable(gameId: String): Boolean = isNative(gameId) || web.isAvailable(gameId)

    override suspend fun launch(request: GameLaunchRequest): GameResult? {
        val player = if (native != null && isNative(request.game.id)) native else web
        current = player
        hudState.value = GameHud()
        return player.launch(request)
    }

    override fun setPauseRequestListener(listener: (() -> Unit)?) {
        native?.setPauseRequestListener(listener)
        web.setPauseRequestListener(listener)
    }

    override fun pause() = current.pause()
    override fun resume() = current.resume()
    override fun forfeit() = current.forfeit()
    override fun applySettings(settings: GameSettings) = current.applySettings(settings)

    @Composable
    override fun GameView(modifier: Modifier) = current.GameView(modifier)

    // ---- activity lifecycle → native player
    private val hosted get() = native as? ActivityHostedGame
    override val isPlaying: Boolean get() = hosted?.isPlaying == true
    override fun onActivityResume() { hosted?.onActivityResume() }
    override fun onActivityPause() { hosted?.onActivityPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { hosted?.onWindowFocusChanged(hasFocus) }
    override fun onConfigurationChanged(newConfig: Configuration) { hosted?.onConfigurationChanged(newConfig) }
    override fun onLowMemory() { hosted?.onLowMemory() }
    override fun onTrimMemory(level: Int) { hosted?.onTrimMemory(level) }

    /** Back during a native game goes to the app (pause menu), not to Unity. */
    fun handlesBackKey(event: KeyEvent): Boolean = isPlaying && event.keyCode == KeyEvent.KEYCODE_BACK
}
