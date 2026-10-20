package com.funpixelnest.mgp.platform

import android.content.Context
import android.content.SharedPreferences

actual object LocalPrefs {
    private var prefs: SharedPreferences? = null

    /** Called once from MainActivity before the UI starts. */
    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("fpn_prefs", Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? = prefs?.getString(key, null)

    actual fun set(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }
}
