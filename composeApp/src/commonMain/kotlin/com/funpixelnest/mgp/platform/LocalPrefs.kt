package com.funpixelnest.mgp.platform

/** Small on-device key/value store for app preferences (dark mode). */
expect object LocalPrefs {
    fun get(key: String): String?
    fun set(key: String, value: String)
}
