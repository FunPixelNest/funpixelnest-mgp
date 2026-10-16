package com.funpixelnest.mgp.domain

/**
 * Ranking and prize rules for a pool.
 *
 * IMPORTANT: in production this logic runs on the SERVER (it decides winners and
 * pays prizes). The app uses the same rules only to display the leaderboard.
 */
object Leaderboard {

    /** A ranked row; [rank] is 1-based and null for players who haven't submitted. */
    data class Row(val rank: Int?, val entry: PoolEntry, val prize: Long)

    /**
     * Tie-breakers, in order:
     * 1. Higher score
     * 2. Shorter duration (finished faster)
     * 3. Earlier submission (server sequence) — guarantees there is never a tie.
     */
    private val order: Comparator<PoolEntry> =
        compareByDescending<PoolEntry> { it.score ?: Long.MIN_VALUE }
            .thenBy { it.durationMillis ?: Long.MAX_VALUE }
            .thenBy { it.submitSequence ?: Long.MAX_VALUE }

    fun rank(pool: Pool, tournament: Tournament): List<Row> {
        val payouts = prizePayouts(tournament)
        val submitted = pool.entries.filter { it.hasSubmitted }.sortedWith(order)
        val waiting = pool.entries.filterNot { it.hasSubmitted }

        val rankedRows = submitted.mapIndexed { index, entry ->
            // Prizes are only final (and only shown as won) once the pool is complete.
            val prize = if (pool.status == PoolStatus.COMPLETED) payouts.getOrElse(index) { 0L } else 0L
            Row(rank = index + 1, entry = entry, prize = prize)
        }
        return rankedRows + waiting.map { Row(rank = null, entry = it, prize = 0L) }
    }

    /** Total coins paid out to players for a full pool, after the platform fee. */
    fun prizePool(tournament: Tournament): Long {
        val collected = tournament.entryFee * tournament.poolSize
        return collected * (100 - tournament.platformFeePercent) / 100
    }

    /** Coins per paid rank. The last paid rank gets the remainder, so the total is exact. */
    fun prizePayouts(tournament: Tournament): List<Long> {
        val pool = prizePool(tournament)
        val shares = tournament.prizeStructure.sharesPercent
        val allButLast = shares.dropLast(1).map { pool * it / 100 }
        return allButLast + (pool - allButLast.sum())
    }
}
