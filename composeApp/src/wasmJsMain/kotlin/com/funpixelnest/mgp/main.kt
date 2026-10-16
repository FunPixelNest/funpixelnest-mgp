package com.funpixelnest.mgp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        // TODO(unity): pass gameLauncher = a Unity WebGL launcher once the WebGL build is added.
        App()
    }
}
