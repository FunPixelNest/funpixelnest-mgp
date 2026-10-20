package com.funpixelnest.mgp

import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.game.ScoreLine
import com.funpixelnest.mgp.game.parseGameResult
import com.funpixelnest.mgp.game.scoreLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Game Over score rows sent by the games (SDK AddScoreLine → "score:<label>=<value>" in the event log). */
class ScoreLinesTest {
    @Test
    fun readsRowsInOrderAndIgnoresOtherEvents() {
        val r = GameResult(
            score = 3700,
            durationMillis = 1,
            eventLog = listOf("score:Base Score=2450", "m:3>7", "score:Time Bonus=1250", "score:Penalty=-50", "draw"),
        )
        assertEquals(
            listOf(ScoreLine("Base Score", 2450), ScoreLine("Time Bonus", 1250), ScoreLine("Penalty", -50)),
            r.scoreLines(),
        )
    }

    @Test
    fun skipsBrokenRowsAndKeepsAtMostSix() {
        val r = GameResult(1, 1, listOf("score:=5", "score:NoValue", "score:Bad=abc") + (1..8).map { "score:Row $it=$it" })
        val lines = r.scoreLines()
        assertEquals(6, lines.size)
        assertEquals(ScoreLine("Row 1", 1), lines.first())
    }

    @Test
    fun webBridgeResultKeepsRows() {
        val r = parseGameResult("""{"score":100,"durationMs":5,"eventLog":"[\"score:Distance=80\",\"score:Coins=20\"]"}""")
        assertEquals(listOf(ScoreLine("Distance", 80), ScoreLine("Coins", 20)), r!!.scoreLines())
        assertTrue(GameResult(5, 1).scoreLines().isEmpty())
    }
}
