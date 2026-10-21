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

    /** Google Play product id of the one-time "Remove Ads" purchase (create it in Play Console). */
    const val REMOVE_ADS_PRODUCT = "remove_ads"
}

/**
 * How ads behave. On Android these come from Firebase Remote Config (keys in brackets), so they can be changed
 * without an app update; the values here are the defaults used until Remote Config answers (or without Firebase).
 */
data class AdSettings(
    /** Banner under the game while playing [show_banner]. */
    val showBanner: Boolean = true,
    /** Interstitial after a match, over the leaderboard [show_interstitial]. */
    val showInterstitial: Boolean = true,
    /** At least this many seconds between two interstitials [interstitial_interval_seconds]. */
    val interstitialIntervalSeconds: Long = 60,
    /** Interstitial after every Nth finished match [interstitial_every_n_games]. */
    val interstitialEveryNGames: Int = 1,
    /** Rewarded videos: Watch Video for coins and the 2x buttons [show_rewarded]. */
    val showRewarded: Boolean = true,
    /** "Watch video for 2x" on reward pop-ups [rewarded_double_enabled]. */
    val rewardedDoubleEnabled: Boolean = true,
)

/**
 * Banner, interstitial and rewarded ads, plus the "Remove Ads" purchase. Android uses AdMob + Google Play
 * Billing; other platforms show no ads. "Remove Ads" turns off banners and interstitials; rewarded videos
 * stay, because the player chooses to watch them for a reward.
 */
interface AdService {
    /** Current settings (Remote Config). Read in composition: changes recompose. */
    val settings: AdSettings

    /** True after the player bought "Remove Ads" (restored from Google Play on every start). */
    val adsRemoved: Boolean

    /** The banner should be shown during games (enabled, ads not removed, platform has ads). */
    val bannerEnabled: Boolean

    /** Height (dp) the banner takes under the game; 0 when there is no banner. */
    val bannerHeightDp: Int

    /** Rewarded videos can be offered (Watch Video, 2x). */
    val rewardedEnabled: Boolean get() = settings.showRewarded

    /** The "Watch video for 2x" button can be offered on reward pop-ups. */
    val doubleEnabled: Boolean get() = settings.showRewarded && settings.rewardedDoubleEnabled

    /** Anchored banner across the screen width (nothing when ads are off). */
    @Composable
    fun Banner(modifier: Modifier)

    /** A match just ended (counts matches for [AdSettings.interstitialEveryNGames]). */
    fun onMatchFinished()

    /** Shows an interstitial when allowed (settings, interval, every-N, not removed, one loaded); [onClosed] runs either way. */
    fun showInterstitial(onClosed: () -> Unit = {})

    /**
     * Shows a rewarded ad. [onReward] runs only when the player watched it to the end.
     * [onUnavailable] runs when no ad could be shown (none loaded, failed to open, or rewarded ads are off).
     */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit)

    /** "Remove Ads" can be bought on this device (Google Play connected and the product exists). */
    val removeAdsAvailable: Boolean

    /** Price from Google Play, e.g. "₹199.00"; null until known. */
    val removeAdsPrice: String?

    /** Starts the purchase. [onResult] gets null on success, otherwise a message to show. */
    fun buyRemoveAds(onResult: (String?) -> Unit)

    /** Asks Google Play again for past purchases (Restore purchases). */
    fun restorePurchases()
}

/** No ads (web, iOS for now, previews): rewarded actions just grant the reward. */
object NoAds : AdService {
    override val settings = AdSettings(showBanner = false, showInterstitial = false)
    override val adsRemoved = false
    override val bannerEnabled = false
    override val bannerHeightDp = 0
    override val removeAdsAvailable = false
    override val removeAdsPrice: String? = null

    @Composable
    override fun Banner(modifier: Modifier) = Unit

    override fun onMatchFinished() = Unit

    override fun showInterstitial(onClosed: () -> Unit) = onClosed()

    override fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) = onReward()

    override fun buyRemoveAds(onResult: (String?) -> Unit) = onResult("Remove Ads is available in the Android app.")

    override fun restorePurchases() = Unit
}
