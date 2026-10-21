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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.funpixelnest.mgp.analytics.Analytics
import com.funpixelnest.mgp.analytics.AnalyticsEvents
import com.funpixelnest.mgp.billing.RemoveAdsBilling
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
 * Google AdMob with mediation (the Liftoff Monetize, Unity Ads and Mintegral adapters are in the app; AdMob
 * initializes them), driven by Remote Config ([RemoteSettings]) and switched off by the "Remove Ads" purchase
 * ([RemoveAdsBilling]) — except rewarded videos, which the player chooses to watch.
 */
class AdMobAds(
    private val activity: Activity,
    private val analytics: Analytics,
) : AdService {
    private val remote = RemoteSettings()
    private val billing = RemoveAdsBilling(activity, AdUnits.REMOVE_ADS_PRODUCT)

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var lastInterstitialAt = 0L
    private var matchesSinceInterstitial = 0
    private var sdkReady = false

    override val settings: AdSettings get() = remote.ads
    override val adsRemoved: Boolean get() = billing.owned
    override val bannerEnabled: Boolean get() = settings.showBanner && !adsRemoved
    override val bannerHeightDp: Int
        get() = if (!bannerEnabled) 0 else {
            val widthDp = (activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density).toInt()
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp).height
        }
    override val removeAdsAvailable: Boolean get() = billing.available
    override val removeAdsPrice: String? get() = billing.price

    /** Starts Remote Config, billing and the ads SDK (with the mediation adapters, off the main thread). */
    fun initialize() {
        remote.start()
        billing.start()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            MobileAds.initialize(activity) { status ->
                status.adapterStatusMap.forEach { (name, s) -> Log.d(TAG, "adapter $name: ${s.initializationState} ${s.description}") }
                activity.runOnUiThread {
                    sdkReady = true
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }
    }

    private fun loadInterstitial() {
        if (!sdkReady || interstitial != null || loadingInterstitial || !settings.showInterstitial || adsRemoved) return
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
        if (!sdkReady || rewarded != null || loadingRewarded || !settings.showRewarded) return
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

    override fun onMatchFinished() {
        matchesSinceInterstitial++
    }

    override fun showInterstitial(onClosed: () -> Unit) {
        val s = settings
        val now = SystemClock.elapsedRealtime()
        val gapOk = lastInterstitialAt == 0L || now - lastInterstitialAt >= s.interstitialIntervalSeconds * 1_000
        val countOk = matchesSinceInterstitial >= s.interstitialEveryNGames
        val ad = interstitial
        if (!s.showInterstitial || adsRemoved || !gapOk || !countOk || ad == null) {
            loadInterstitial()
            onClosed()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastInterstitialAt = SystemClock.elapsedRealtime()
                matchesSinceInterstitial = 0
                analytics.event(AnalyticsEvents.INTERSTITIAL_SHOWN)
            }

            override fun onAdDismissedFullScreenContent() {
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
        if (!settings.showRewarded || ad == null) {
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

    override fun buyRemoveAds(onResult: (String?) -> Unit) = billing.buy { message ->
        if (message == null) {
            interstitial = null
            analytics.event(AnalyticsEvents.REMOVE_ADS_PURCHASED)
        }
        onResult(message)
    }

    override fun restorePurchases() = billing.restore()

    @Composable
    override fun Banner(modifier: Modifier) {
        if (!bannerEnabled) return
        val context = LocalContext.current
        val height = bannerHeightDp
        val view = remember {
            val widthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
            AdView(context).apply {
                adUnitId = AdUnits.BANNER
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(view) { onDispose { view.destroy() } }
        AndroidView(factory = { view }, modifier = modifier.fillMaxWidth().height(height.dp))
    }

    private companion object {
        const val TAG = "FpnAds"
    }
}
