package com.funpixelnest.mgp.firebase

import android.content.Context
import android.os.Bundle
import com.funpixelnest.mgp.analytics.Analytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Firebase Analytics events + the Crashlytics user id. Does nothing when Firebase isn't configured. */
class FirebaseAnalyticsService(context: Context) : Analytics {
    private val analytics: FirebaseAnalytics? =
        if (FirebaseSetup.init(context)) runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }.getOrNull() else null
    private val crashlytics: FirebaseCrashlytics? =
        if (FirebaseSetup.isReady) runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() else null

    override fun event(name: String, params: Map<String, Any?>) {
        val a = analytics ?: return
        val bundle = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Int -> bundle.putLong(k, v.toLong())
                is Long -> bundle.putLong(k, v)
                is Double -> bundle.putDouble(k, v)
                is Float -> bundle.putDouble(k, v.toDouble())
                is Boolean -> bundle.putLong(k, if (v) 1L else 0L)
                else -> bundle.putString(k, v.toString().take(100))
            }
        }
        a.logEvent(name, bundle)
        crashlytics?.log("event $name $params")
    }

    override fun screen(name: String) {
        val a = analytics ?: return
        a.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })
        crashlytics?.setCustomKey("screen", name)
    }

    override fun setUser(playerId: String?) {
        analytics?.setUserId(playerId)
        crashlytics?.setUserId(playerId ?: "")
    }
}
