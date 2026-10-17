@file:OptIn(ExperimentalWasmJsInterop::class)

package com.funpixelnest.mgp.game

import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Plays Unity WebGL builds on the web version.
 *
 * The heavy lifting is in `fpn-unity-host.js` (loaded by index.html): it reads `<gamesBaseUrl>catalog.json`,
 * opens the game full-screen over the app, gives Unity the launch data through `window.FPN`, and calls back
 * with the result when the game submits a score or the player exits.
 */
class WebGlGameLauncher(gamesBaseUrl: String) : GameLauncher {

    init {
        hostInit(gamesBaseUrl)
    }

    override fun isAvailable(gameId: String): Boolean = hostHas(gameId)

    override suspend fun launch(request: GameLaunchRequest): GameResult? = suspendCancellableCoroutine { cont ->
        hostLaunch(request.game.id, request.game.name, request.toLaunchJson()) { raw ->
            if (cont.isActive) cont.resume(parseGameResult(raw))
        }
    }
}

@JsFun("(base) => { if (window.FPNHost) window.FPNHost.init(base); }")
private external fun hostInit(base: String)

@JsFun("(gameId) => !!(window.FPNHost && window.FPNHost.has(gameId))")
private external fun hostHas(gameId: String): Boolean

@JsFun(
    "(gameId, gameName, launchJson, onDone) => {" +
        " if (!window.FPNHost) { onDone(''); return; }" +
        " window.FPNHost.launch(gameId, gameName, launchJson, (r) => onDone(r || ''));" +
        "}",
)
private external fun hostLaunch(gameId: String, gameName: String, launchJson: String, onDone: (String) -> Unit)
