package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.Tournament

/** Local catalog used until the backend serves games and tournaments. */
object SampleCatalog {

    val games: List<Game> = listOf(
        Game("solitaire", "Solitaire", "Cards", "🃏", 0xFF2E7D32),
        Game("block_puzzle", "Block Puzzle", "Puzzle", "🧩", 0xFF6A1B9A),
        Game("bingo", "Bingo", "Classic", "🎱", 0xFFC62828),
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
