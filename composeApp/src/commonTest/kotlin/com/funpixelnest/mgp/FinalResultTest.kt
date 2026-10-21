package com.funpixelnest.mgp

import com.funpixelnest.mgp.game.GameHud
import com.funpixelnest.mgp.game.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinalResultTest {
    private val hud = GameHud(score = 1_250, reported = true)

    @Test
    fun gameScoreIsKept() {
        val r = GameResult(900, 60_000, listOf("score:Base=900"))
        assertEquals(r, finalResult(r, hud, 70_000))
    }

    @Test
    fun endedWithoutScoreUsesHudScore() {
        val r = finalResult(null, hud, 42_000)!!
        assertEquals(1_250, r.score)
        assertEquals(42_000, r.durationMillis)
        assertEquals(listOf(ENDED_EARLY_EVENT), r.eventLog)
    }

    @Test
    fun zeroFromGameWhileHudShowedPointsUsesHudScore() {
        val r = finalResult(GameResult(0, 5_000), hud, 6_000)!!
        assertEquals(1_250, r.score)
        assertEquals(5_000, r.durationMillis)
    }

    @Test
    fun nothingReportedStaysNull() {
        assertNull(finalResult(null, GameHud(), 1_000))
        assertNull(finalResult(null, null, 1_000))
    }
}
