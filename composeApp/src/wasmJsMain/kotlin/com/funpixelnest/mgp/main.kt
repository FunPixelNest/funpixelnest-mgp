package com.funpixelnest.mgp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.funpixelnest.mgp.config.BackendConfig
import com.funpixelnest.mgp.game.WebGlGameLauncher
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Unity WebGL builds listed in <GAMES_BASE_URL>catalog.json open full-screen over the app.
    val gameLauncher = WebGlGameLauncher(BackendConfig.GAMES_BASE_URL)
    ComposeViewport(document.body!!) {
        App(gameLauncher = gameLauncher)
    }
}
