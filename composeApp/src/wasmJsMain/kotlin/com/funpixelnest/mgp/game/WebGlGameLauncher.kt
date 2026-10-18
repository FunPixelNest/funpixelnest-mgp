@file:OptIn(ExperimentalWasmJsInterop::class)

package com.funpixelnest.mgp.game

import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Plays Unity WebGL builds on the web version.
 *
 * The heavy lifting is in `fpn-unity-host.js` (loaded by index.html): it reads `<gamesBaseUrl>catalog.json`,
 * opens the game over the app below the app's HUD bar, gives Unity the launch data through `window.FPN`, and calls back
 * with the result when the game submits a score or the player exits.
 */
class WebGlGameLauncher(gamesBaseUrl: String) : GameLauncher {

    private val hudState = MutableStateFlow(GameHud())
    override val hud: StateFlow<GameHud> = hudState.asStateFlow()

    init {
        hostInit(gamesBaseUrl, GAME_HUD_HEIGHT.value.toInt())
        hostSetHudListener { score, seconds, lives -> hudState.value = GameHud.parse(score, seconds, lives) }
    }

    override fun isAvailable(gameId: String): Boolean = hostHas(gameId)

    override suspend fun launch(request: GameLaunchRequest): GameResult? = suspendCancellableCoroutine { cont ->
        hudState.value = GameHud()
        hostLaunch(request.game.id, request.game.name, request.toLaunchJson()) { raw ->
            if (cont.isActive) cont.resume(parseGameResult(raw))
        }
    }

    override fun setPauseRequestListener(listener: (() -> Unit)?) {
        hostSetPauseListener(listener ?: {})
    }

    override fun pause() = hostCall("pause", "")
    override fun resume() = hostCall("resume", "")
    override fun forfeit() = hostCall("forfeit", "")
    override fun applySettings(settings: GameSettings) = hostCall("settings", settings.toJson())
}

@JsFun("(listener) => { if (window.FPNHost) window.FPNHost.onPauseRequest = () => listener(); }")
private external fun hostSetPauseListener(listener: () -> Unit)

@JsFun("(name, arg) => { if (window.FPNHost && window.FPNHost[name]) window.FPNHost[name](arg); }")
private external fun hostCall(name: String, arg: String)

@JsFun("(base, top) => { if (window.FPNHost) { window.FPNHost.init(base); window.FPNHost.embedded = true; window.FPNHost.topInset = top; } }")
private external fun hostInit(base: String, top: Int)

@JsFun("(listener) => { if (window.FPNHost) window.FPNHost.onHud = (s, t, l) => listener(s, t, l); }")
private external fun hostSetHudListener(listener: (Double, Double, Double) -> Unit)

@JsFun("(gameId) => !!(window.FPNHost && window.FPNHost.has(gameId))")
private external fun hostHas(gameId: String): Boolean

@JsFun(
    "(gameId, gameName, launchJson, onDone) => {" +
        " if (!window.FPNHost) { onDone(''); return; }" +
        " window.FPNHost.launch(gameId, gameName, launchJson, (r) => onDone(r || ''));" +
        "}",
)
private external fun hostLaunch(gameId: String, gameName: String, launchJson: String, onDone: (String) -> Unit)
