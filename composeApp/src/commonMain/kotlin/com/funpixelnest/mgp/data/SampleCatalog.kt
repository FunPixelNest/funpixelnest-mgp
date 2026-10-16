package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.domain.Tournament

/** Local catalog used until the backend serves games and tournaments. */
object SampleCatalog {

    val games: List<Game> = listOf(
        Game("solitaire", "Solitaire", "Cards", "🃏", 0xFF2E7D32),
        Game("block_puzzle", "Block Puzzle", "Puzzle", "🧩", 0xFF6A1B9A),
        Game("bingo", "Bingo Flash", "Classic", "🎱", 0xFFC62828),
        Game("bubble_shooter", "Bubble Shooter", "Arcade", "🫧", 0xFF0277BD),
        Game("mahjong", "Mahjong", "Puzzle", "🀄", 0xFF00695C),
        Game("blackjack", "Blackjack", "Cards", "♠️", 0xFF37474F),
        Game("arrows", "Arrows", "Puzzle", "➡️", 0xFFEF6C00),
        Game("slots", "Skill Slots", "Arcade", "🎰", 0xFFAD1457),
        Game("snake_war", "Snake War", "Arcade", "🐍", 0xFF558B2F),
        // Renamed from "Flappy Bird" (trademarked name).
        Game("flap_dash", "Flap Dash", "Arcade", "🐤", 0xFFF9A825),
        Game("kitty_hole", "Kitty Hole", "Casual", "🐱", 0xFF5D4037),
        Game("catdoku", "Catdoku", "Puzzle", "😺", 0xFF4527A0),
        Game("rugby_rumble", "Rugby Rumble", "Sports", "🏉", 0xFF1565C0),
    )

    val featuredTournamentIds = listOf("solitaire_elite", "bubble_shooter_pro", "mahjong_elite", "bingo_pro")
    val newGameIds = listOf("rugby_rumble", "catdoku", "kitty_hole", "snake_war", "arrows")

    /** Display prices only; real payments come in a later phase. */
    val storePacks: List<StorePack> = listOf(
        StorePack("pack_small", "Handful", coins = 500, bonusCoins = 0, priceLabel = "$0.99"),
        StorePack("pack_medium", "Pouch", coins = 1_200, bonusCoins = 100, priceLabel = "$1.99"),
        StorePack("pack_large", "Chest", coins = 3_000, bonusCoins = 500, priceLabel = "$4.99", tag = "Popular"),
        StorePack("pack_huge", "Vault", coins = 6_500, bonusCoins = 1_500, priceLabel = "$9.99", tag = "Best value"),
    )

    /** Three entry levels per game. Platform fee is a placeholder until the economy is designed. */
    val tournaments: List<Tournament> = games.flatMap { game ->
        listOf(
            Tournament("${game.id}_starter", game.id, "Starter", entryFee = 20, platformFeePercent = 10),
            Tournament("${game.id}_pro", game.id, "Pro", entryFee = 100, platformFeePercent = 10),
            Tournament("${game.id}_elite", game.id, "Elite", entryFee = 500, platformFeePercent = 10),
        )
    }
}
