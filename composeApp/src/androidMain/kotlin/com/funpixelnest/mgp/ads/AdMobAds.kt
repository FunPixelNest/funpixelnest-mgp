package com.funpixelnest.mgp.ads

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google AdMob with mediation (Liftoff Monetize, Unity Ads, Mintegral adapters are in the app; AdMob
 * initializes them). Interstitial and rewarded ads are loaded ahead of time and reloaded after each show.
 */
class AdMobAds(private val activity: Activity) : AdService {
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var lastInterstitialAt = 0L
    private var ready = false

    /** Starts the SDK (and the mediation adapters) off the main thread, then preloads the full-screen ads. */
    fun initialize() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            MobileAds.initialize(activity) { status ->
                status.adapterStatusMap.forEach { (name, s) -> Log.d(TAG, "adapter $name: ${s.initializationState} ${s.description}") }
                activity.runOnUiThread {
                    ready = true
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }
    }

    private fun loadInterstitial() {
        if (!ready || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(activity, AdUnits.INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                loadingInterstitial = false
                interstitial = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingInterstitial = false
                Log.w(TAG, "interstitial failed to load: ${error.message}")
            }
        })
    }

    private fun loadRewarded() {
        if (!ready || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(activity, AdUnits.REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loadingRewarded = false
                rewarded = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingRewarded = false
                Log.w(TAG, "rewarded failed to load: ${error.message}")
            }
        })
    }

    override fun showInterstitial(onClosed: () -> Unit) {
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        if (ad == null || (lastInterstitialAt != 0L && now - lastInterstitialAt < AdUnits.INTERSTITIAL_MIN_GAP_MILLIS)) {
            loadInterstitial()
            onClosed()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastInterstitialAt = SystemClock.elapsedRealtime()
                loadInterstitial()
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitial()
                onClosed()
            }
        }
        ad.show(activity)
    }

    override fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            loadRewarded()
            onUnavailable()
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadRewarded()
                onUnavailable()
            }
        }
        ad.show(activity) { earned = true }
    }

    @Composable
    override fun Banner(modifier: Modifier) {
        val context = LocalContext.current
        val widthDp = LocalConfiguration.current.screenWidthDp
        val size = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
        val view = remember(size) {
            AdView(context).apply {
                adUnitId = AdUnits.BANNER
                setAdSize(size)
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(view) { onDispose { view.destroy() } }
        AndroidView(factory = { view }, modifier = modifier.fillMaxWidth().height(size.height.dp))
    }

    private companion object {
        const val TAG = "FpnAds"
    }
}
