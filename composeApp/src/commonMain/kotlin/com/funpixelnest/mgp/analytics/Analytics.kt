package com.funpixelnest.mgp.analytics

/**
 * App analytics (Firebase Analytics on Android). Event and parameter names use Firebase's rules:
 * lowercase letters, digits and underscores, up to 40 characters.
 */
interface Analytics {
    fun event(name: String, params: Map<String, Any?> = emptyMap())

    /** A page the player is looking at ("games", "store", "game_detail"…). */
    fun screen(name: String)

    /** Signed-in player (also used for crash reports). */
    fun setUser(playerId: String?)
}

/** Does nothing (web, iOS for now, previews). */
object NoAnalytics : Analytics {
    override fun event(name: String, params: Map<String, Any?>) = Unit
    override fun screen(name: String) = Unit
    override fun setUser(playerId: String?) = Unit
}

/** Event names used by the app, in one place. */
object AnalyticsEvents {
    const val MATCH_START = "match_start"           // game_id, tournament_id, entry_fee
    const val MATCH_END = "match_end"               // game_id, score, duration_s, ended_early
    const val SCORE_SUBMITTED = "score_submitted"   // game_id, score
    const val LEVEL_UP = "level_up_reached"         // level, coins, gems
    const val REWARD_DOUBLED = "reward_doubled"     // source, coins, gems
    const val REWARDED_WATCHED = "rewarded_watched" // placement
    const val INTERSTITIAL_SHOWN = "interstitial_shown"
    const val REMOVE_ADS_PURCHASED = "remove_ads_purchased"
    const val COIN_PACK_TAPPED = "coin_pack_tapped" // pack_id
    const val THEME_CHANGED = "theme_changed"       // dark
}
