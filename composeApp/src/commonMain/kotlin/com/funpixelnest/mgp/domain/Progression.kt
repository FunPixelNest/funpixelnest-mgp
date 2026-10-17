package com.funpixelnest.mgp.domain

/** Number of selectable avatars (avatar_01 … avatar_15). */
const val AVATAR_COUNT = 15

/** Everything shown in the header and on the Profile screen. */
data class PlayerProfile(
    val avatarId: Int = 1,
    val xp: Long = 0,
    val trophies: Long = 0,
    val welcomePending: Boolean = false,
    val welcomeCoins: Long = 1_000,
    val unreadNotifications: Int = 0,
    val stats: PlayerStats = PlayerStats(),
) {
    val levelInfo: LevelInfo get() = LevelInfo.fromXp(xp)
    val tier: LeagueTier get() = LeagueTier.forTrophies(trophies)
}

data class PlayerStats(
    val tournamentsPlayed: Int = 0,
    val matchesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val winRate: Int = 0,
    val coinsWon: Long = 0,
    val winStreak: Int = 0,
    val daysActive: Int = 1,
)

/** Level L → L+1 needs 250 × L XP (same rule as the server). */
data class LevelInfo(val level: Int, val xpIntoLevel: Long, val xpNeeded: Long) {
    val progress: Float get() = if (xpNeeded <= 0) 0f else (xpIntoLevel.toFloat() / xpNeeded).coerceIn(0f, 1f)

    companion object {
        fun fromXp(totalXp: Long): LevelInfo {
            var level = 1
            var left = totalXp.coerceAtLeast(0)
            while (left >= 250L * level) {
                left -= 250L * level
                level++
            }
            return LevelInfo(level, left, 250L * level)
        }
    }
}

// ---------------------------------------------------------------- leagues

enum class LeagueTier(val id: String, val label: String, val minTrophies: Long, val rewardCoins: Long, val rewardGems: Long) {
    BRONZE("bronze", "Bronze", 0, 0, 0),
    SILVER("silver", "Silver", 1_000, 500, 10),
    GOLD("gold", "Gold", 2_000, 1_000, 20),
    PLATINUM("platinum", "Platinum", 3_000, 2_000, 40),
    DIAMOND("diamond", "Diamond", 4_000, 4_000, 80),
    CHAMPION("champion", "Champion", 5_000, 8_000, 150);

    val next: LeagueTier? get() = entries.getOrNull(ordinal + 1)

    /** Divisions 3 → 1 inside a tier (Champion has none). */
    fun division(trophies: Long): Int? {
        val upper = next?.minTrophies ?: return null
        val span = (upper - minTrophies).coerceAtLeast(1)
        val into = (trophies - minTrophies).coerceIn(0, span - 1)
        return 3 - (into * 3 / span).toInt()
    }

    companion object {
        fun forTrophies(trophies: Long): LeagueTier = entries.last { trophies >= it.minTrophies }
        fun fromId(id: String?): LeagueTier = entries.firstOrNull { it.id == id } ?: BRONZE
    }
}

data class LeagueRow(
    val rank: Int,
    val playerId: String,
    val displayName: String,
    val avatarId: Int,
    val points: Long,
    val tier: LeagueTier,
)

data class LeagueState(
    val trophies: Long = 0,
    val seasonPoints: Long = 0,
    val seasonEndsInMillis: Long = 0,
    val top: List<LeagueRow> = emptyList(),
    val myRank: Int? = null,
) {
    val tier: LeagueTier get() = LeagueTier.forTrophies(trophies)
}

/** League points for one finished match (same rule as the server). */
object LeaguePoints {
    fun forResult(rank: Int?, isDuel: Boolean): Long = when {
        isDuel -> if (rank == 1) 80 else 10
        rank == 1 -> 100
        rank == 2 -> 60
        rank == 3 -> 30
        else -> 10
    }

    fun xpForResult(rank: Int?, isDuel: Boolean): Long = 20 + when {
        isDuel -> if (rank == 1) 30 else 0
        rank == 1 -> 30
        rank == 2 -> 15
        rank == 3 -> 5
        else -> 0
    }
}

// ---------------------------------------------------------------- challenges

enum class ChallengePeriod { DAILY, WEEKLY }

enum class ChallengeCategory { DAILY, GAME, TOURNAMENT, LEAGUE, SPECIAL, MILESTONE }

data class Challenge(
    val id: String,
    val period: ChallengePeriod,
    val category: ChallengeCategory,
    val title: String,
    val subtitle: String,
    val gameId: String?,
    val target: Long,
    val progress: Long,
    val reward: RewardGrant,
    val claimed: Boolean,
) {
    val isComplete: Boolean get() = progress >= target
    val canClaim: Boolean get() = isComplete && !claimed
    val fraction: Float get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

data class ChallengesState(
    val dailyResetsInMillis: Long = 0,
    val weeklyResetsInMillis: Long = 0,
    val challenges: List<Challenge> = emptyList(),
) {
    val claimableCount: Int get() = challenges.count { it.canClaim }
    fun of(category: ChallengeCategory) = challenges.filter { it.category == category }
}

// ---------------------------------------------------------------- inbox

data class InboxMessage(val id: String, val title: String, val body: String, val read: Boolean)

// ---------------------------------------------------------------- sign-in

/** Ways to sign in, in the order shown on the login screen. */
enum class LoginMethod(val id: String, val label: String) {
    GOOGLE("google", "Google"),
    APPLE("apple", "Apple"),
    FACEBOOK("facebook", "Facebook"),
    PHONE("phone", "Mobile number"),
    USERNAME("username", "Username"),
    GUEST("guest", "Guest"),
}
