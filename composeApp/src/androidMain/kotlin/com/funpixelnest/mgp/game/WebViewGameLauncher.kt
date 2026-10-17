package com.funpixelnest.mgp.game

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Plays Unity WebGL builds inside the Android app, in a WebView.
 *
 * The builds are hosted on GitHub Pages (see /games-host): `<gamesBaseUrl>catalog.json` lists them and
 * `<gamesBaseUrl>play.html#<launch JSON>` runs one with the same host script as the web app.
 * The page talks back through `window.FPNNative` (result, pause request, live HUD values).
 * The page shows only the game; the app draws the HUD (pause, score, timer) above it.
 */
class WebViewGameLauncher(gamesBaseUrl: String) : GameLauncher {

    private val base = if (gamesBaseUrl.endsWith("/")) gamesBaseUrl else "$gamesBaseUrl/"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    /** Game ids that have a build online (filled from catalog.json). */
    @Volatile
    private var available: Set<String> = emptySet()

    private class Session(val url: String, val result: CompletableDeferred<String>)

    private var session by mutableStateOf<Session?>(null)
    private var webView: WebView? = null
    private var pauseListener: (() -> Unit)? = null
    private val hudState = MutableStateFlow(GameHud())
    override val hud: StateFlow<GameHud> = hudState.asStateFlow()

    init {
        refreshCatalog()
    }

    /** Loads the list of online games. Called at startup; cheap to call again. */
    fun refreshCatalog() {
        if (!base.startsWith("http")) return
        scope.launch {
            runCatching {
                val conn = URL(base + "catalog.json").openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("Cache-Control", "no-cache")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val games = Json.parseToJsonElement(text).jsonObject["games"] as? JsonObject
                available = games?.keys.orEmpty()
            }
        }
    }

    override fun isAvailable(gameId: String): Boolean = gameId in available

    override suspend fun launch(request: GameLaunchRequest): GameResult? {
        val done = CompletableDeferred<String>()
        hudState.value = GameHud()
        val url = base + "play.html#" + URLEncoder.encode(request.toLaunchJson(), "UTF-8").replace("+", "%20")
        session = Session(url, done)
        return try {
            parseGameResult(done.await())
        } finally {
            session = null
            webView?.let { wv -> main.post { wv.destroy() } }
            webView = null
        }
    }

    override fun setPauseRequestListener(listener: (() -> Unit)?) {
        pauseListener = listener
    }

    override fun pause() = js("window.FPNHost && FPNHost.pause()")
    override fun resume() = js("window.FPNHost && FPNHost.resume()")
    override fun applySettings(settings: GameSettings) =
        js("window.FPNHost && FPNHost.settings(${JsonPrimitive(settings.toJson())})")

    override fun forfeit() {
        val s = session ?: return
        js("window.FPNHost && FPNHost.forfeit()")
        // If the page doesn't answer (e.g. still loading), end anyway.
        main.postDelayed({ if (!s.result.isCompleted) s.result.complete("") }, 1_500)
    }

    private fun js(code: String) {
        val wv = webView ?: return
        main.post { wv.evaluateJavascript(code, null) }
    }

    /** Called from the page (JavaScript thread). */
    private inner class Bridge(private val result: CompletableDeferred<String>) {
        @JavascriptInterface
        fun onResult(json: String) {
            if (!result.isCompleted) result.complete(json)
        }

        @JavascriptInterface
        fun onPauseRequest(@Suppress("UNUSED_PARAMETER") unused: String) {
            main.post { pauseListener?.invoke() }
        }

        /** "score,secondsLeft" (secondsLeft < 0 = no timer). */
        @JavascriptInterface
        fun onHud(values: String) {
            val parts = values.split(',')
            val score = parts.getOrNull(0)?.toDoubleOrNull() ?: return
            val seconds = parts.getOrNull(1)?.toDoubleOrNull() ?: -1.0
            hudState.value = GameHud.parse(score, seconds)
        }

        @JavascriptInterface
        fun onReady(@Suppress("UNUSED_PARAMETER") unused: String) = Unit
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun GameView(modifier: Modifier) {
        val s = session ?: return
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.parseColor("#0E1A33"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(Bridge(s.result), "FPNNative")
                    loadUrl(s.url)
                    webView = this
                }
            },
        )
    }
}
