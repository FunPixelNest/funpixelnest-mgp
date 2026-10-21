package com.funpixelnest.mgp.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Ad unit ids. These are Google's public TEST units (they always serve test ads and never pay).
 * Before release, replace them with the app's real AdMob ids (and the app id in AndroidManifest.xml).
 * Liftoff, Unity Ads and Mintegral serve through these same units once they are added to the units'
 * mediation groups in the AdMob dashboard; nothing changes in code.
 */
object AdUnits {
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"        // adaptive banner (test)
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"  // interstitial (test)
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"      // rewarded (test)

    /** At least this long between two interstitials. */
    const val INTERSTITIAL_MIN_GAP_MILLIS = 60_000L
}

/** Banner, interstitial and rewarded ads. Android uses AdMob; other platforms show none. */
interface AdService {
    /** Anchored banner across the screen width (nothing when ads aren't available). */
    @Composable
    fun Banner(modifier: Modifier)

    /** Shows an interstitial if one is loaded and the gap since the last one has passed; [onClosed] runs either way. */
    fun showInterstitial(onClosed: () -> Unit = {})

    /**
     * Shows a rewarded ad. [onReward] runs only when the player watched it to the end.
     * [onUnavailable] runs when no ad could be shown (none loaded, or failed to open).
     */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit)
}

/** No ads (web, iOS for now, previews): rewarded actions just grant the reward. */
object NoAds : AdService {
    @Composable
    override fun Banner(modifier: Modifier) = Unit

    override fun showInterstitial(onClosed: () -> Unit) = onClosed()

    override fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) = onReward()
}
