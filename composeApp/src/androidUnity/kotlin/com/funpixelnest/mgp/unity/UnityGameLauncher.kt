package com.funpixelnest.mgp.unity

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.funpixelnest.mgp.game.ActivityHostedGame
import com.funpixelnest.mgp.game.GameHud
import com.funpixelnest.mgp.game.GameLaunchRequest
import com.funpixelnest.mgp.game.GameLauncher
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.game.GameSettings
import com.funpixelnest.mgp.game.toJson
import com.unity3d.player.IUnityPlayerLifecycleEvents
import com.unity3d.player.UnityPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Plays the games inside the Unity library (composeApp/unity/unityLibrary-release.aar) natively,
 * inside MainActivity: the UnityPlayer view is shown in the game screen, under the app's HUD and pause menu.
 *
 * Unity starts once (first match) and stays loaded; each later match is started with the
 * "OnPlatformLaunch" message (the SDK resets its state and the game reloads its scene).
 */
class UnityGameLauncher(private val activity: ComponentActivity) : GameLauncher, ActivityHostedGame {

    companion object {
        /** Games built into the Unity library. */
        val GAMES = setOf(
            "block_puzzle", "arrows", "blackjack", "kitty_hole", "queen_sudoku", "slots", "bubble_shooter", "mahjong",
            "flap_dash", "snake_war", "bingo",
        )

        private const val RECEIVER = "FunPixelNestPlatform"
        private const val LAUNCH_EXTRA = "fpn_launch_data"
        private val json = Json { ignoreUnknownKeys = true }
    }

    /** The Unity library has ARM code only (armeabi-v7a, arm64-v8a). */
    private val abiSupported = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }

    private var player by mutableStateOf<UnityPlayer?>(null)
    private var session by mutableStateOf<CompletableDeferred<GameResult?>?>(null)
    private var pauseListener: (() -> Unit)? = null
    private val hudState = MutableStateFlow(GameHud())
    override val hud: StateFlow<GameHud> = hudState.asStateFlow()

    override val isPlaying: Boolean get() = session != null

    override fun isAvailable(gameId: String): Boolean = abiSupported && gameId in GAMES

    override suspend fun launch(request: GameLaunchRequest): GameResult? {
        val done = CompletableDeferred<GameResult?>()
        hudState.value = GameHud()

        // Area the app's HUD covers, as a share of the screen height (the game keeps it clear).
        val metrics = activity.resources.displayMetrics
        // The game view is shorter than the screen when a banner sits under it.
        val screenDp = (metrics.heightPixels / metrics.density - request.belowGameDp).coerceAtLeast(1f)
        FpnUnityBridge.insetTop = (request.hudInsetTopDp / screenDp).coerceIn(0f, 0.5f)
        FpnUnityBridge.insetBottom = (request.hudInsetBottomDp / screenDp).coerceIn(0f, 0.5f)
        FpnUnityBridge.listener = object : FpnUnityBridge.Listener {
            override fun onScore(score: Long, durationMs: Long, eventLogJson: String) {
                val events = runCatching { json.decodeFromString(ListSerializer(String.serializer()), eventLogJson) }.getOrNull().orEmpty()
                done.complete(GameResult(score.coerceAtLeast(0), durationMs.coerceAtLeast(0), events))
            }

            override fun onQuit() {
                done.complete(null)
            }

            override fun onHud(score: Long, secondsLeft: Int, lives: Int, counter: Int) {
                hudState.value = GameHud.parse(score.toDouble(), secondsLeft.toDouble(), lives.toDouble(), counter.toDouble())
            }
        }

        val launchJson = request.toLaunchJson()
        // The SDK reads the first match from the activity's intent when Unity starts.
        activity.intent.putExtra(LAUNCH_EXTRA, launchJson)
        FpnUnityBridge.currentLaunch = launchJson
        if (player == null) {
            player = UnityPlayer(activity, object : IUnityPlayerLifecycleEvents {
                override fun onUnityPlayerUnloaded() = Unit
                override fun onUnityPlayerQuitted() = Unit
            })
        } else {
            UnityPlayer.UnitySendMessage(RECEIVER, "OnPlatformLaunch", launchJson)
        }
        session = done
        return try {
            done.await()
        } finally {
            session = null
            FpnUnityBridge.listener = null
            player?.pause()
        }
    }

    override fun setPauseRequestListener(listener: (() -> Unit)?) {
        pauseListener = listener
    }

    override fun pause() = send("OnPlatformPause", "1")
    override fun resume() = send("OnPlatformPause", "0")
    override fun applySettings(settings: GameSettings) = send("OnPlatformSettings", settings.toJson())

    /** End now: the match is over for the app; Unity stays loaded and restarts on the next match. */
    override fun forfeit() {
        send("OnPlatformPause", "1")
        session?.complete(null)
    }

    private fun send(method: String, arg: String) {
        if (player != null) UnityPlayer.UnitySendMessage(RECEIVER, method, arg)
    }

    @Composable
    override fun GameView(modifier: Modifier) {
        // Both are state: the view appears as soon as the match has started.
        val s = session
        val p = player
        if (s == null || p == null) return
        DisposableEffect(p) {
            // Light status bar icons over the game; Unity must not rotate the app.
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val lightBefore = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            // Unity may switch to fullscreen / change the notch mode; the app shows its HUD with the status bar.
            @Suppress("DEPRECATION")
            val uiBefore = window.decorView.systemUiVisibility
            val cutoutBefore = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode else 0
            onDispose {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = uiBefore
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = cutoutBefore }
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
                (p.parent as? ViewGroup)?.removeView(p)
                p.pause()
                controller.isAppearanceLightStatusBars = lightBefore
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        AndroidView(
            modifier = modifier,
            factory = { context ->
                FrameLayout(context).apply {
                    (p.parent as? ViewGroup)?.removeView(p)
                    addView(p, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    p.requestFocus()
                    p.windowFocusChanged(true)
                    p.resume()
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            },
        )
    }

    // ---- activity lifecycle
    override fun onActivityResume() { if (isPlaying) player?.resume() }
    override fun onActivityPause() { player?.pause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { if (isPlaying) player?.windowFocusChanged(hasFocus) }
    override fun onConfigurationChanged(newConfig: Configuration) { player?.configurationChanged(newConfig) }
    override fun onLowMemory() { player?.lowMemory() }
    override fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) player?.lowMemory()
    }
}
