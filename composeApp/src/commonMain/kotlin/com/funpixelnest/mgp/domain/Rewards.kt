package com.funpixelnest.mgp.domain

/** A coin pack in the Store. Prices are display-only until payments are added. */
data class StorePack(
    val id: String,
    val name: String,
    val coins: Long,
    val bonusCoins: Long,
    val priceLabel: String,
    val tag: String? = null,
) {
    val totalCoins: Long get() = coins + bonusCoins
}

/** Coins and gems handed out by a claim (challenge, daily bonus, chest, spin…). */
data class RewardGrant(val coins: Long = 0, val gems: Long = 0) {
    val isEmpty: Boolean get() = coins == 0L && gems == 0L
}

/** Result of claiming/opening something that can fail with a friendly message. */
sealed interface ClaimResult {
    data class Granted(val reward: RewardGrant, val title: String) : ClaimResult
    data class Failed(val message: String) : ClaimResult
}

// ---------------------------------------------------------------- daily bonus

/** 7-day login streak with coins, gems and an epic chest on day 7. */
data class DailyBonusState(
    /** Day to claim next (1–7). */
    val day: Int = 1,
    val claimedToday: Boolean = false,
    val resetsInMillis: Long = 0,
    val rewards: List<DailyBonusDay> = DEFAULT_DAYS,
) {
    companion object {
        val DEFAULT_DAYS = listOf(
            DailyBonusDay(1, coins = 100), DailyBonusDay(2, gems = 5), DailyBonusDay(3, coins = 250),
            DailyBonusDay(4, gems = 10), DailyBonusDay(5, coins = 500), DailyBonusDay(6, gems = 15),
            DailyBonusDay(7, chest = "epic"),
        )
    }
}

data class DailyBonusDay(val day: Int, val coins: Long = 0, val gems: Long = 0, val chest: String? = null)

// ---------------------------------------------------------------- shop (chests, spin, free coins)

data class ChestOffer(val id: String, val name: String, val gemCost: Long, val description: String)

data class ShopState(
    /** 0 = the free chest can be opened now. */
    val freeChestReadyInMillis: Long = 0,
    /** 0 = the lucky spin is available now. */
    val spinReadyInMillis: Long = 0,
    val freeCoinsAmount: Long = 50,
    val freeCoinsLeftToday: Int = 3,
    val chests: List<ChestOffer> = DEFAULT_CHESTS,
    val spinSegments: List<RewardGrant> = DEFAULT_SEGMENTS,
) {
    companion object {
        val DEFAULT_CHESTS = listOf(
            ChestOffer("wooden", "Wooden Chest", 10, "Contains coins"),
            ChestOffer("silver", "Silver Chest", 25, "Contains coins & gems"),
            ChestOffer("gold", "Gold Chest", 60, "Contains more coins & gems"),
            ChestOffer("diamond", "Diamond Chest", 100, "Best rewards!"),
        )
        val DEFAULT_SEGMENTS = listOf(
            RewardGrant(coins = 50), RewardGrant(gems = 5), RewardGrant(coins = 100), RewardGrant(coins = 25),
            RewardGrant(coins = 200), RewardGrant(gems = 10), RewardGrant(coins = 500), RewardGrant(coins = 150),
        )
    }
}

data class SpinResult(val segment: Int, val reward: RewardGrant)
