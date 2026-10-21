package com.funpixelnest.mgp.firebase

import android.content.Context
import com.funpixelnest.mgp.config.BackendConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * One Firebase app for push, Analytics, Crashlytics and Remote Config, set up from BackendConfig
 * (FIREBASE_* values in local.properties / config/backend.properties), so no google-services.json is needed.
 * Without those values every Firebase feature is simply off and the app uses its defaults.
 */
object FirebaseSetup {
    val isConfigured: Boolean
        get() = BackendConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BackendConfig.FIREBASE_API_KEY.isNotBlank() &&
            BackendConfig.FIREBASE_ANDROID_APP_ID.isNotBlank()

    @Volatile
    var isReady: Boolean = false
        private set

    /** Starts Firebase once. Returns false when it isn't configured (or failed to start). */
    fun init(context: Context): Boolean {
        if (isReady) return true
        if (!isConfigured) return false
        isReady = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BackendConfig.FIREBASE_PROJECT_ID)
                    .setApiKey(BackendConfig.FIREBASE_API_KEY)
                    .setApplicationId(BackendConfig.FIREBASE_ANDROID_APP_ID)
                    .apply { if (BackendConfig.FIREBASE_SENDER_ID.isNotBlank()) setGcmSenderId(BackendConfig.FIREBASE_SENDER_ID) }
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            true
        }.getOrDefault(false)
        return isReady
    }
}
