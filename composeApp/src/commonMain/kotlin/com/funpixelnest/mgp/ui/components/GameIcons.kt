package com.funpixelnest.mgp.ui.components

import com.funpixelnest.mgp.resources.Res
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
import com.funpixelnest.mgp.resources.logo_funpixelnest
import org.jetbrains.compose.resources.DrawableResource

/**
 * Game id → bundled icon image (files in composeApp/src/commonMain/composeResources/drawable).
 * Games without their own icon yet (Solitaire, Skill Slots, Flap Dash) show the FunPixelNest logo.
 */
fun gameIconResource(gameId: String): DrawableResource = gameIcons[gameId] ?: Res.drawable.logo_funpixelnest

private val gameIcons: Map<String, DrawableResource> by lazy {
    mapOf(
        "arrows" to Res.drawable.game_arrows,
        "mahjong" to Res.drawable.game_mahjong,
        "kitty_hole" to Res.drawable.game_kitty_hole,
        "snake_war" to Res.drawable.game_snake_war,
        "block_puzzle" to Res.drawable.game_block_puzzle,
        "bubble_shooter" to Res.drawable.game_bubble_shooter,
        "rugby_rumble" to Res.drawable.game_rugby_rumble,
        "bingo" to Res.drawable.game_bingo,
        "catdoku" to Res.drawable.game_catdoku,
        "blackjack" to Res.drawable.game_blackjack,
    )
}
