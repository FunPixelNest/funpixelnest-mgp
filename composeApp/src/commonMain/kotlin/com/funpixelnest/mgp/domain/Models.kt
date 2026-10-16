package com.funpixelnest.mgp.domain

/** A game available on the platform. The actual gameplay lives in Unity. */
data class Game(
    val id: String,
    val name: String,
    val category: String,
    val emoji: String,
    /** ARGB color used for the game's card. */
    val accentColor: Long,
)

/**
 * A tournament template: players join it and get grouped into pools of [poolSize].
 * Everyone in a pool plays the same seed; the pool closes only when all players
 * have submitted (no timeout).
 */
data class Tournament(
    val id: String,
    val gameId: String,
    val title: String,
    val entryFee: Long,
    val poolSize: Int = 5,
    /** Cut kept by the platform before prizes are paid. 0 = everything goes to players. */
    val platformFeePercent: Int = 0,
    val prizeStructure: PrizeStructure = PrizeStructure.STANDARD,
)

/**
 * Share of the prize pool per rank (index 0 = 1st place).
 * The LAST paid rank receives whatever remains, so rounding never loses coins.
 */
data class PrizeStructure(val sharesPercent: List<Int>) {
    init {
        require(sharesPercent.isNotEmpty()) { "At least one paid rank is required" }
        require(sharesPercent.sum() == 100) { "Prize shares must add up to 100%" }
    }

    val paidRanks: Int get() = sharesPercent.size

    companion object {
        /** 1st = 50%, 2nd = 30%, 3rd = the remaining 20%. */
        val STANDARD = PrizeStructure(listOf(50, 30, 20))
    }
}

enum class PoolStatus {
    /** Fewer than poolSize players have joined. */
    OPEN,

    /** Pool is full; waiting for every player to submit a score. */
    FULL,

    /** All scores are in, ranks are final and prizes are paid. */
    COMPLETED,
}

data class PoolEntry(
    val playerId: String,
    val displayName: String,
    /** Null until the player submits. */
    val score: Long? = null,
    /** Time taken to finish; lower wins a tie on score. */
    val durationMillis: Long? = null,
    /** Server-assigned submit sequence; earlier wins an exact tie. */
    val submitSequence: Long? = null,
) {
    val hasSubmitted: Boolean get() = score != null
}

data class Pool(
    val id: String,
    val tournamentId: String,
    /** Same seed for every player in the pool, so everyone gets the identical board/deal. */
    val seed: Long,
    val entries: List<PoolEntry>,
    val status: PoolStatus,
)

/** Issued by the server when a player joins. One ticket = one score submission. */
data class MatchTicket(
    val ticketId: String,
    val poolId: String,
    val tournamentId: String,
    val gameId: String,
    val seed: Long,
)

data class Wallet(
    val coins: Long,
    val transactions: List<WalletTransaction>,
)

data class WalletTransaction(
    val id: String,
    val amount: Long,
    val description: String,
)

data class Player(
    val id: String,
    val displayName: String,
)
