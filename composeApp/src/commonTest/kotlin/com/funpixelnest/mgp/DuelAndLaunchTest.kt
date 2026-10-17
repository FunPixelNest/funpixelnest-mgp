package com.funpixelnest.mgp

import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolEntry
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.game.GameLaunchRequest
import com.funpixelnest.mgp.game.LaunchMode
import com.funpixelnest.mgp.game.parseGameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DuelAndLaunchTest {

    private val duel = Tournament.duel("solitaire_duel_quick", "solitaire", "Quick", entryFee = 20, platformFeePercent = 10)

    @Test
    fun duelWinnerTakesThePotAfterFee() {
        // 2 × 20 = 40, minus 10% = 36 (same as the server test).
        assertEquals(36, Leaderboard.prizePool(duel))
        assertEquals(listOf(36L), Leaderboard.prizePayouts(duel))
    }

    @Test
    fun finishedDuelPaysOnlyTheWinner() {
        val pool = Pool(
            id = "p",
            tournamentId = duel.id,
            seed = 1,
            entries = listOf(
                PoolEntry("a", "A", score = 500, durationMillis = 90_000, submitSequence = 1),
                PoolEntry("b", "B", score = 700, durationMillis = 95_000, submitSequence = 2),
            ),
            status = PoolStatus.COMPLETED,
        )
        val rows = Leaderboard.rank(pool, duel)
        assertEquals(listOf("b", "a"), rows.map { it.entry.playerId })
        assertEquals(listOf(36L, 0L), rows.map { it.prize })
    }

    @Test
    fun serverRanksAndPrizesWinOnceThePoolIsSettled() {
        // The server's official result is shown even if local rules would order it differently.
        val pool = Pool(
            id = "p",
            tournamentId = duel.id,
            seed = 1,
            entries = listOf(
                PoolEntry("a", "A", score = 500, durationMillis = 1, submitSequence = 1, finalRank = 1, finalPrize = 36),
                PoolEntry("b", "B", score = 700, durationMillis = 1, submitSequence = 2, finalRank = 2, finalPrize = 0),
            ),
            status = PoolStatus.COMPLETED,
        )
        val rows = Leaderboard.rank(pool, duel)
        assertEquals(listOf("a", "b"), rows.map { it.entry.playerId })
        assertEquals(listOf(36L, 0L), rows.map { it.prize })
    }

    @Test
    fun launchJsonSendsTheSeedAsAString() {
        val request = GameLaunchRequest(
            game = Game("solitaire", "Solitaire", "Cards", "🃏", 0),
            ticket = MatchTicket("t1", "p1", duel.id, "solitaire", seed = 8461298374512398123),
            playerId = "me",
            displayName = "Ravi",
            mode = LaunchMode.DUEL,
        )
        val json = request.toLaunchJson()
        assertTrue("\"seed\":\"8461298374512398123\"" in json, json)
        assertTrue("\"mode\":\"duel\"" in json, json)
    }

    @Test
    fun parsesGameResultsFromTheBridge() {
        val result = parseGameResult("""{"score":4210,"durationMs":125000,"eventLog":"[\"move:a1\",\"move:b2\"]"}""")
        assertEquals(4210, result?.score)
        assertEquals(125_000, result?.durationMillis)
        assertEquals(listOf("move:a1", "move:b2"), result?.eventLog)
        assertNull(parseGameResult(""))
        assertNull(parseGameResult("quit"))
        assertNull(parseGameResult("not json"))
    }
}
