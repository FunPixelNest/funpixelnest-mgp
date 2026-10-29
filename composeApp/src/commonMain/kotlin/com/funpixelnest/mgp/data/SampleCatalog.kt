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
        Game("slots", "Solitaire Slots", "Cards & Casino", "🎰", 0xFFAD1457),
        Game("snake_war", "Snake War", "Arcade", "🐍", 0xFF558B2F),
        // Renamed from "Flappy Bird" (trademarked name).
        Game("flap_dash", "Bird Dash", "Arcade", "🐤", 0xFFF9A825),
        Game("kitty_hole", "Kitty Hole", "Arcade", "🐱", 0xFF5D4037),
        Game("catdoku", "Catdoku", "Puzzle", "😺", 0xFF4527A0),
        Game("queen_sudoku", "Queen Sudoku", "Puzzle", "👑", 0xFF8E24AA),
        Game("rugby_rumble", "Rugby Rumble", "Sports", "🏉", 0xFF1565C0),
    )

    /**
     * Games not shown in the app for now (coming in a later update). Their tournaments and old results stay
     * on the server; they just aren't listed. Remove an id here to bring the game back.
     */
    val hiddenGameIds: Set<String> = setOf("solitaire", "rugby_rumble", "catdoku")

    /** Games listed in the app (everything except [hiddenGameIds]). */
    val visibleGames: List<Game> get() = games.filter { it.id !in hiddenGameIds }

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
        "bingo" -> "Each ball is called with a short timer: tap its number on your card before the timer runs out. Fast taps score more and fill the multiplier. Complete a row, column, diagonal or the four corners for a BINGO, and clear the whole card for a big bonus. Tapping a number that wasn't called costs points. 60-second round."
        "bubble_shooter" -> "Aim and shoot bubbles to match 3 or more of the same colour, drop hanging bubbles for combo points, and clear the board before the timer runs out."
        "mahjong" -> "Tap two matching free tiles (not covered and open on the left or right) to clear them. Chain matches for combo bonuses and clear the board before the 2-minute timer runs out."
        "blackjack" -> "Tap a column to place the dealt card and make 21 in each column. Going over 21 costs one of your 3 lives. Park a card in Hold, use Undo, and clear the deck before time runs out."
        "arrows" -> "Tap arrows to slide them off the board in the direction they point. Don't let them crash into each other."
        "slots" -> "Spin to drop cards, match symbol clusters to win, use power moves (Convert to Wild, Remove, Place Wild) and collect your wins before your spins or the timer run out."
        "snake_war" -> "Steer your snake with the joystick and eat food to grow. Make other snakes crash into your body, and don't hit theirs. Grow as long as you can before the 2-minute timer ends."
        "flap_dash" -> "Tap to flap through the gaps. Pipes, coins and distance score points. Keep your 3 lives until the 60-second timer runs out."
        "kitty_hole" -> "Get every cat into a matching box to clear the board. Each cat, full box and cleared board scores. Clear as many boards as you can in 2 minutes; Freeze, Star and Magnet boosters help once per match."
        "catdoku" -> "Fill the grid so every row, column and box has each cat exactly once."
        "queen_sudoku" -> "Place one queen in every row, column and colour region. Queens can't touch, even diagonally. Finish before the 2-minute timer runs out; each wrong queen costs a life."
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
            // 5-player pools (same ids as the Supabase seed + migration 20261020000002).
            Tournament("${game.id}_rookie", game.id, "Rookie", entryFee = 10, platformFeePercent = 10),
            Tournament("${game.id}_starter", game.id, "Starter", entryFee = 20, platformFeePercent = 10),
            Tournament("${game.id}_pro", game.id, "Pro", entryFee = 100, platformFeePercent = 10),
            Tournament("${game.id}_champion", game.id, "Champion", entryFee = 250, platformFeePercent = 10),
            Tournament("${game.id}_elite", game.id, "Elite", entryFee = 500, platformFeePercent = 10),
        )
    }
}
