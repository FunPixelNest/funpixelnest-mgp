package com.funpixelnest.mgp

import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolEntry
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.PrizeStructure
import com.funpixelnest.mgp.domain.Tournament
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LeaderboardTest {

    private val noFee = Tournament("t", "solitaire", "Pro", entryFee = 100, poolSize = 5, platformFeePercent = 0)
    private val tenPercentFee = noFee.copy(platformFeePercent = 10)

    @Test
    fun prizesAre50_30_20OfThePool() {
        assertEquals(500, Leaderboard.prizePool(noFee))
        assertEquals(listOf(250L, 150L, 100L), Leaderboard.prizePayouts(noFee))
    }

    @Test
    fun platformFeeIsTakenBeforePrizes() {
        assertEquals(450, Leaderboard.prizePool(tenPercentFee))
        assertEquals(listOf(225L, 135L, 90L), Leaderboard.prizePayouts(tenPercentFee))
    }

    @Test
    fun thirdPlaceGetsTheRemainderSoNoCoinsAreLost() {
        val odd = noFee.copy(entryFee = 7) // pool = 35
        val payouts = Leaderboard.prizePayouts(odd)
        assertEquals(listOf(17L, 10L, 8L), payouts)
        assertEquals(35L, payouts.sum())
    }

    @Test
    fun equalScoresAreBrokenByDurationThenSubmitOrder() {
        val pool = completedPool(
            PoolEntry("a", "A", score = 900, durationMillis = 90_000, submitSequence = 1),
            PoolEntry("b", "B", score = 900, durationMillis = 60_000, submitSequence = 2), // faster → beats A
            PoolEntry("c", "C", score = 900, durationMillis = 60_000, submitSequence = 3), // same as B, later → after B
            PoolEntry("d", "D", score = 1_000, durationMillis = 200_000, submitSequence = 4),
            PoolEntry("e", "E", score = 100, durationMillis = 10_000, submitSequence = 5),
        )
        val order = Leaderboard.rank(pool, noFee).map { it.entry.playerId }
        assertEquals(listOf("d", "b", "c", "a", "e"), order)
    }

    @Test
    fun prizesAreOnlyAssignedWhenPoolIsCompleted() {
        val entries = listOf(
            PoolEntry("a", "A", score = 500, durationMillis = 1, submitSequence = 1),
            PoolEntry("b", "B"),
        )
        val open = Pool("p", "t", seed = 1, entries = entries, status = PoolStatus.OPEN)
        val rows = Leaderboard.rank(open, noFee)
        assertEquals(listOf(0L, 0L), rows.map { it.prize })
        assertEquals(listOf(1, null), rows.map { it.rank })
    }

    @Test
    fun completedPoolPaysTopThree() {
        val pool = completedPool(
            PoolEntry("a", "A", score = 5, durationMillis = 1, submitSequence = 1),
            PoolEntry("b", "B", score = 4, durationMillis = 1, submitSequence = 2),
            PoolEntry("c", "C", score = 3, durationMillis = 1, submitSequence = 3),
            PoolEntry("d", "D", score = 2, durationMillis = 1, submitSequence = 4),
            PoolEntry("e", "E", score = 1, durationMillis = 1, submitSequence = 5),
        )
        assertEquals(listOf(250L, 150L, 100L, 0L, 0L), Leaderboard.rank(pool, noFee).map { it.prize })
    }

    @Test
    fun prizeSharesMustAddUpTo100() {
        assertFailsWith<IllegalArgumentException> { PrizeStructure(listOf(50, 30, 10)) }
    }

    private fun completedPool(vararg entries: PoolEntry) =
        Pool("p", "t", seed = 42, entries = entries.toList(), status = PoolStatus.COMPLETED)
}
