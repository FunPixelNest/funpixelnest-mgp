package com.funpixelnest.mgp.domain

/** Everything shown on the Rewards tab. */
data class RewardsState(
    val daily: DailyReward,
    /** Free coins from rewarded videos; limited per day. */
    val freeCoinsAmount: Long,
    val freeCoinsLeftToday: Int,
    val missions: List<Mission>,
)

/**
 * 7-day login streak. [day] is the next day to claim (1–7); after day 7 it restarts.
 * Missing a day resets the streak (handled by the server in production).
 */
data class DailyReward(
    val day: Int,
    val claimedToday: Boolean,
    val coinsPerDay: List<Long> = listOf(50, 75, 100, 125, 150, 200, 500),
) {
    val todayCoins: Long get() = coinsPerDay[(day - 1).coerceIn(0, coinsPerDay.lastIndex)]
}

data class Mission(
    val id: String,
    val title: String,
    val target: Int,
    val progress: Int,
    val rewardCoins: Long,
    val claimed: Boolean,
) {
    val isComplete: Boolean get() = progress >= target
    val canClaim: Boolean get() = isComplete && !claimed
}

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
