package com.funpixelnest.mgp

import androidx.compose.ui.window.ComposeUIViewController

/** Called from Swift (iosApp/iosApp/ContentView.swift). */
fun MainViewController() = ComposeUIViewController {
    // TODO(unity): pass gameLauncher = an iOS UnityFramework launcher once Unity is added.
    App()
}
