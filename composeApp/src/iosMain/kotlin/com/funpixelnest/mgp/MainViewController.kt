package com.funpixelnest.mgp

import androidx.compose.ui.window.ComposeUIViewController
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.data.SupabasePlatformRepository
import com.funpixelnest.mgp.data.createPlatformRepository
import io.github.jan.supabase.auth.handleDeeplinks
import platform.Foundation.NSURL

/** One repository for the app, so sign-in redirects reach the same Supabase client. */
private val repository: PlatformRepository by lazy { createPlatformRepository() }

/** Called from Swift (iosApp/iosApp/ContentView.swift). */
fun MainViewController() = ComposeUIViewController {
    // TODO(unity): pass gameLauncher = an iOS UnityFramework launcher once Unity is added.
    App(repository = repository)
}

/** Called from Swift .onOpenURL: hands Google / Apple / Facebook sign-in back to Supabase. */
fun handleOpenUrl(url: NSURL) {
    if (url.scheme != "funpixelnest") return
    (repository as? SupabasePlatformRepository)?.client?.handleDeeplinks(url)
}
