package com.funpixelnest.mgp.ads

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.firebase.FirebaseSetup
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

/**
 * Ad settings from Firebase Remote Config. Keys (set them in Firebase console → Remote Config):
 *   show_banner (bool) · show_interstitial (bool) · interstitial_interval_seconds (number)
 *   interstitial_every_n_games (number) · show_rewarded (bool) · rewarded_double_enabled (bool)
 * Until Remote Config answers (or without Firebase) the defaults in [AdSettings] apply.
 * Values are fetched at start and kept for 1 hour; new values apply right away.
 */
class RemoteSettings {
    var ads by mutableStateOf(AdSettings())
        private set

    fun start() {
        if (!FirebaseSetup.isReady) return
        val config = runCatching { FirebaseRemoteConfig.getInstance() }.getOrNull() ?: return
        val d = AdSettings()
        config.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3_600 })
        config.setDefaultsAsync(
            mapOf(
                "show_banner" to d.showBanner,
                "show_interstitial" to d.showInterstitial,
                "interstitial_interval_seconds" to d.interstitialIntervalSeconds,
                "interstitial_every_n_games" to d.interstitialEveryNGames.toLong(),
                "show_rewarded" to d.showRewarded,
                "rewarded_double_enabled" to d.rewardedDoubleEnabled,
            ),
        )
        apply(config) // last activated values from the previous run
        config.fetchAndActivate().addOnCompleteListener { task ->
            if (!task.isSuccessful) Log.w("FpnRemoteConfig", "fetch failed", task.exception)
            apply(config)
        }
    }

    private fun apply(config: FirebaseRemoteConfig) {
        ads = AdSettings(
            showBanner = config.getBoolean("show_banner"),
            showInterstitial = config.getBoolean("show_interstitial"),
            interstitialIntervalSeconds = config.getLong("interstitial_interval_seconds").coerceAtLeast(0),
            interstitialEveryNGames = config.getLong("interstitial_every_n_games").toInt().coerceAtLeast(1),
            showRewarded = config.getBoolean("show_rewarded"),
            rewardedDoubleEnabled = config.getBoolean("rewarded_double_enabled"),
        )
    }
}
