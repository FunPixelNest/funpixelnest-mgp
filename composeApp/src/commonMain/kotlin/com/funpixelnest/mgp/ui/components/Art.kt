package com.funpixelnest.mgp.ui.components

import com.funpixelnest.mgp.domain.AVATAR_COUNT
import com.funpixelnest.mgp.domain.LeagueTier
import com.funpixelnest.mgp.resources.Res
import com.funpixelnest.mgp.resources.art_cart
import com.funpixelnest.mgp.resources.art_gift
import com.funpixelnest.mgp.resources.art_gift_small
import com.funpixelnest.mgp.resources.art_hero_chest
import com.funpixelnest.mgp.resources.art_league_shield
import com.funpixelnest.mgp.resources.art_logo
import com.funpixelnest.mgp.resources.art_target
import com.funpixelnest.mgp.resources.art_trophy
import com.funpixelnest.mgp.resources.art_trophy_confetti
import com.funpixelnest.mgp.resources.art_welcome_chest
import com.funpixelnest.mgp.resources.avatar_01
import com.funpixelnest.mgp.resources.avatar_02
import com.funpixelnest.mgp.resources.avatar_03
import com.funpixelnest.mgp.resources.avatar_04
import com.funpixelnest.mgp.resources.avatar_05
import com.funpixelnest.mgp.resources.avatar_06
import com.funpixelnest.mgp.resources.avatar_07
import com.funpixelnest.mgp.resources.avatar_08
import com.funpixelnest.mgp.resources.avatar_09
import com.funpixelnest.mgp.resources.avatar_10
import com.funpixelnest.mgp.resources.avatar_11
import com.funpixelnest.mgp.resources.avatar_12
import com.funpixelnest.mgp.resources.avatar_13
import com.funpixelnest.mgp.resources.avatar_14
import com.funpixelnest.mgp.resources.avatar_15
import com.funpixelnest.mgp.resources.ch_chest
import com.funpixelnest.mgp.resources.ch_crown
import com.funpixelnest.mgp.resources.ch_cup
import com.funpixelnest.mgp.resources.ch_gamepad
import com.funpixelnest.mgp.resources.ch_lp
import com.funpixelnest.mgp.resources.ch_podium
import com.funpixelnest.mgp.resources.ch_promote
import com.funpixelnest.mgp.resources.ch_star
import com.funpixelnest.mgp.resources.ch_target
import com.funpixelnest.mgp.resources.ch_trophy
import com.funpixelnest.mgp.resources.ch_trophy_red
import com.funpixelnest.mgp.resources.chest_diamond
import com.funpixelnest.mgp.resources.chest_epic
import com.funpixelnest.mgp.resources.chest_gold
import com.funpixelnest.mgp.resources.chest_silver
import com.funpixelnest.mgp.resources.chest_wooden
import com.funpixelnest.mgp.resources.coins_pile
import com.funpixelnest.mgp.resources.coins_stack
import com.funpixelnest.mgp.resources.earn_checkin
import com.funpixelnest.mgp.resources.earn_free_chest
import com.funpixelnest.mgp.resources.earn_invite
import com.funpixelnest.mgp.resources.earn_rate
import com.funpixelnest.mgp.resources.earn_spin
import com.funpixelnest.mgp.resources.earn_video
import com.funpixelnest.mgp.resources.game_arrows
import com.funpixelnest.mgp.resources.game_bingo
import com.funpixelnest.mgp.resources.game_blackjack
import com.funpixelnest.mgp.resources.game_block_puzzle
import com.funpixelnest.mgp.resources.game_bubble_shooter
import com.funpixelnest.mgp.resources.game_catdoku
import com.funpixelnest.mgp.resources.game_kitty_hole
import com.funpixelnest.mgp.resources.game_mahjong
import com.funpixelnest.mgp.resources.game_rugby_rumble
import com.funpixelnest.mgp.resources.game_snake_war
import com.funpixelnest.mgp.resources.ic_coin
import com.funpixelnest.mgp.resources.ic_gem
import com.funpixelnest.mgp.resources.ic_how_to_play
import com.funpixelnest.mgp.resources.ic_stopwatch
import com.funpixelnest.mgp.resources.league_bronze
import com.funpixelnest.mgp.resources.league_champion
import com.funpixelnest.mgp.resources.league_diamond
import com.funpixelnest.mgp.resources.league_gold
import com.funpixelnest.mgp.resources.league_platinum
import com.funpixelnest.mgp.resources.league_silver
import com.funpixelnest.mgp.resources.league_silver_big
import com.funpixelnest.mgp.resources.logo_apple
import com.funpixelnest.mgp.resources.logo_facebook
import com.funpixelnest.mgp.resources.logo_funpixelnest
import com.funpixelnest.mgp.resources.logo_google
import com.funpixelnest.mgp.resources.medal_1
import com.funpixelnest.mgp.resources.medal_2
import com.funpixelnest.mgp.resources.medal_3
import com.funpixelnest.mgp.resources.medal_small_1
import com.funpixelnest.mgp.resources.medal_small_2
import com.funpixelnest.mgp.resources.medal_small_3
import com.funpixelnest.mgp.resources.pack_1
import com.funpixelnest.mgp.resources.pack_2
import com.funpixelnest.mgp.resources.pack_3
import com.funpixelnest.mgp.resources.pack_4
import com.funpixelnest.mgp.resources.pack_5
import org.jetbrains.compose.resources.DrawableResource

/** Art cut from the app mockups (composeResources/drawable, see design/ASSETS_SPEC.md). */
object Art {
    val logo get() = Res.drawable.art_logo
    val heroChest get() = Res.drawable.art_hero_chest
    val welcomeChest get() = Res.drawable.art_welcome_chest
    val gift get() = Res.drawable.art_gift
    val giftSmall get() = Res.drawable.art_gift_small
    val target get() = Res.drawable.art_target
    val leagueShield get() = Res.drawable.art_league_shield
    val cart get() = Res.drawable.art_cart
    val trophy get() = Res.drawable.art_trophy
    val trophyConfetti get() = Res.drawable.art_trophy_confetti
    val coin get() = Res.drawable.ic_coin
    val gem get() = Res.drawable.ic_gem
    val stopwatch get() = Res.drawable.ic_stopwatch
    val howToPlay get() = Res.drawable.ic_how_to_play
    val coinsPile get() = Res.drawable.coins_pile
    val coinsStack get() = Res.drawable.coins_stack
    val chestEpic get() = Res.drawable.chest_epic
    val freeChest get() = Res.drawable.earn_free_chest
    val google get() = Res.drawable.logo_google
    val apple get() = Res.drawable.logo_apple
    val facebook get() = Res.drawable.logo_facebook
    val earnVideo get() = Res.drawable.earn_video
    val earnInvite get() = Res.drawable.earn_invite
    val earnRate get() = Res.drawable.earn_rate
    val earnCheckin get() = Res.drawable.earn_checkin
    val earnSpin get() = Res.drawable.earn_spin

    private val avatars by lazy {
        listOf(Res.drawable.avatar_01, Res.drawable.avatar_02, Res.drawable.avatar_03, Res.drawable.avatar_04, Res.drawable.avatar_05, Res.drawable.avatar_06, Res.drawable.avatar_07, Res.drawable.avatar_08, Res.drawable.avatar_09, Res.drawable.avatar_10, Res.drawable.avatar_11, Res.drawable.avatar_12, Res.drawable.avatar_13, Res.drawable.avatar_14, Res.drawable.avatar_15)
    }

    fun avatar(id: Int): DrawableResource = avatars[(id - 1).coerceIn(0, AVATAR_COUNT - 1)]

    fun league(tier: LeagueTier): DrawableResource = when (tier) {
        LeagueTier.BRONZE -> Res.drawable.league_bronze
        LeagueTier.SILVER -> Res.drawable.league_silver
        LeagueTier.GOLD -> Res.drawable.league_gold
        LeagueTier.PLATINUM -> Res.drawable.league_platinum
        LeagueTier.DIAMOND -> Res.drawable.league_diamond
        LeagueTier.CHAMPION -> Res.drawable.league_champion
    }

    /** Ribbon medal for ranks 1–3 (null otherwise). */
    fun medal(rank: Int?, small: Boolean = false): DrawableResource? = when (rank) {
        1 -> if (small) Res.drawable.medal_small_1 else Res.drawable.medal_1
        2 -> if (small) Res.drawable.medal_small_2 else Res.drawable.medal_2
        3 -> if (small) Res.drawable.medal_small_3 else Res.drawable.medal_3
        else -> null
    }

    /** Coin pack art, smallest to largest. */
    fun pack(index: Int): DrawableResource = when (index) {
        0 -> Res.drawable.pack_1
        1 -> Res.drawable.pack_2
        2 -> Res.drawable.pack_3
        3 -> Res.drawable.pack_4
        else -> Res.drawable.pack_5
    }

    fun chest(id: String): DrawableResource = when (id) {
        "wooden" -> Res.drawable.chest_wooden
        "silver" -> Res.drawable.chest_silver
        "gold" -> Res.drawable.chest_gold
        "diamond" -> Res.drawable.chest_diamond
        "epic" -> Res.drawable.chest_epic
        else -> Res.drawable.earn_free_chest
    }

    /** Icon for a challenge. */
    fun challenge(id: String): DrawableResource = when (id) {
        "d_play_3", "m_play_10" -> Res.drawable.ch_gamepad
        "d_win_1", "m_win_3" -> Res.drawable.ch_trophy
        "d_score_5000", "m_score_20000" -> Res.drawable.ch_star
        "w_tourn_3" -> Res.drawable.ch_podium
        "w_top3_1" -> Res.drawable.ch_cup
        "w_win_1" -> Res.drawable.ch_trophy_red
        "w_lp_1000" -> Res.drawable.ch_lp
        "w_top10" -> Res.drawable.ch_crown
        "w_promote" -> Res.drawable.ch_promote
        "m_challenges_5" -> Res.drawable.ch_target
        else -> Res.drawable.ch_chest
    }
}
