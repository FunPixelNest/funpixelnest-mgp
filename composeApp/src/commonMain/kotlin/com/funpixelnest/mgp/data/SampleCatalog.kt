package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.domain.Tournament

/** Local catalog used until the backend serves games and tournaments. */
object SampleCatalog {

    val games: List<Game> = listOf(
        Game("solitaire", "Solitaire", "Cards & Casino", "🃏", 0xFF2E7D32),
        Game("block_puzzle", "Block Puzzle", "Puzzle", "🧩", 0xFF6A1B9A),
        Game("bingo", "Bingo Flash", "Cards & Casino", "🎱", 0xFFC62828),
        Game("bubble_shooter", "Bubble Shooter", "Arcade", "🫧", 0xFF0277BD),
        Game("mahjong", "Mahjong", "Puzzle", "🀄", 0xFF00695C),
        Game("blackjack", "Blackjack", "Cards & Casino", "♠️", 0xFF37474F),
        Game("arrows", "Arrows", "Puzzle", "➡️", 0xFFEF6C00),
        Game("slots", "Skill Slots", "Cards & Casino", "🎰", 0xFFAD1457),
        Game("snake_war", "Snake War", "Arcade", "🐍", 0xFF558B2F),
        // Renamed from "Flappy Bird" (trademarked name).
        Game("flap_dash", "Flap Dash", "Arcade", "🐤", 0xFFF9A825),
        Game("kitty_hole", "Kitty Hole", "Arcade", "🐱", 0xFF5D4037),
        Game("catdoku", "Catdoku", "Puzzle", "😺", 0xFF4527A0),
        Game("rugby_rumble", "Rugby Rumble", "Sports", "🏉", 0xFF1565C0),
    )

    /** Category order on the Games tab. Games list their category by these names. */
    val categories: List<String> = listOf("Puzzle", "Cards & Casino", "Arcade", "Sports")

    /** Same packs as the server seed (migration 004). Display prices only until billing is added. */
    val storePacks: List<StorePack> = listOf(
        StorePack("coins_10k", "10K Coins", coins = 10_000, bonusCoins = 0, priceLabel = "₹89.00"),
        StorePack("coins_25k", "25K Coins", coins = 25_000, bonusCoins = 0, priceLabel = "₹179.00"),
        StorePack("coins_50k", "50K Coins", coins = 50_000, bonusCoins = 0, priceLabel = "₹349.00", tag = "Best value"),
        StorePack("coins_100k", "100K Coins", coins = 100_000, bonusCoins = 0, priceLabel = "₹699.00"),
        StorePack("coins_250k", "250K Coins", coins = 250_000, bonusCoins = 0, priceLabel = "₹1,599.00"),
    )

    /** Short rules shown under "How to play". */
    fun howToPlay(gameId: String): String = when (gameId) {
        "solitaire" -> "Move all cards to the four foundations, Ace to King by suit. Fewer moves and faster finishes score more."
        "block_puzzle" -> "Drag blocks onto the board. Fill a full row or column to clear it. Clear several at once for combo points."
        "bingo" -> "Tap called numbers on your cards. Complete lines, columns or the full card for a BINGO. Quick daubs earn bonus points."
        "bubble_shooter" -> "Aim and shoot bubbles. Match 3 or more of the same color to pop them. Drop big groups for extra points."
        "mahjong" -> "Match pairs of free tiles (not covered and open on one side). Clear the board as fast as you can."
        "blackjack" -> "Get closer to 21 than the dealer without going over. Hit, stand, double or split to grow your chips."
        "arrows" -> "Tap arrows to slide them off the board in the direction they point. Don't let them crash into each other."
        "slots" -> "Stop each reel at the right moment to line up symbols. Timing, not luck, decides your score."
        "snake_war" -> "Steer your snake, eat to grow and avoid running into others. The biggest snake scores the most."
        "flap_dash" -> "Tap to flap and fly through the gaps. Every gap you pass scores a point."
        "kitty_hole" -> "Move the hole to swallow objects and grow. The bigger you get, the more you can eat."
        "catdoku" -> "Fill the grid so every row, column and box has each cat exactly once."
        "rugby_rumble" -> "Run, dodge and pass to score tries before the timer runs out."
        else -> "Score as many points as you can. Everyone in your tournament plays the same board."
    }

    /** Tips shown while joining a tournament. */
    val proTips = listOf(
        "Plan your moves ahead to score higher!",
        "Everyone in your pool gets the same board, so skill decides the winner.",
        "Ties go to the player who finished faster.",
        "Top 3 in a 5-player tournament win prizes.",
        "Practice is free. Warm up before you play for coins!",
    )

    /** Three entry levels per game. Platform fee is a placeholder until the economy is designed. */
    val tournaments: List<Tournament> = games.flatMap { game ->
        listOf(
            // Async 1v1 duels (same ids as the Supabase seed).
            Tournament.duel("${game.id}_duel_quick", game.id, "Quick", entryFee = 20, platformFeePercent = 10),
            Tournament.duel("${game.id}_duel_high", game.id, "High Stakes", entryFee = 100, platformFeePercent = 10),
            // 5-player pools.
            Tournament("${game.id}_starter", game.id, "Starter", entryFee = 20, platformFeePercent = 10),
            Tournament("${game.id}_pro", game.id, "Pro", entryFee = 100, platformFeePercent = 10),
            Tournament("${game.id}_elite", game.id, "Elite", entryFee = 500, platformFeePercent = 10),
        )
    }
}
