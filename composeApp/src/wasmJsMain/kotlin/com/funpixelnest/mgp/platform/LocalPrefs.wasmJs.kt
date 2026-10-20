package com.funpixelnest.mgp.platform

import kotlinx.browser.localStorage

actual object LocalPrefs {
    actual fun get(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()

    actual fun set(key: String, value: String) {
        runCatching { localStorage.setItem(key, value) }
    }
}
