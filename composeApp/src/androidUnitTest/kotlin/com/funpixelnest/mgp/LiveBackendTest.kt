package com.funpixelnest.mgp

import com.funpixelnest.mgp.config.BackendConfig
import com.funpixelnest.mgp.data.BackendStatus
import com.funpixelnest.mgp.data.JoinResult
import com.funpixelnest.mgp.data.SampleCatalog
import com.funpixelnest.mgp.data.SupabasePlatformRepository
import com.funpixelnest.mgp.domain.Leaderboard
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.game.GameResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end check of the app's Supabase client against the LIVE project.
 *
 * Opt-in only: runs when FPN_LIVE_TEST=true (CI does this for commits whose message contains
 * "[live-test]"). It creates two guest players named "CI Alpha"/"CI Beta" who play one Quick duel.
 */
class LiveBackendTest {

    @Test
    fun duelThroughTheAppClient() = runBlocking {
        if (System.getenv("FPN_LIVE_TEST") != "true") return@runBlocking
        assertTrue(BackendConfig.SUPABASE_URL.isNotBlank(), "SUPABASE_URL is not configured")

        fun newPlayer() = SupabasePlatformRepository(
            BackendConfig.SUPABASE_URL,
            BackendConfig.SUPABASE_ANON_KEY,
            inMemorySession = true,
        )
        val alpha = newPlayer()
        val beta = newPlayer()

        alpha.initialize()
        beta.initialize()
        assertEquals(BackendStatus.Ready, alpha.status.value, "alpha: ${alpha.status.value}")
        assertEquals(BackendStatus.Ready, beta.status.value, "beta: ${beta.status.value}")
        assertEquals(1000, alpha.wallet.value.coins, "new guest gets the welcome bonus")

        assertNull(alpha.setDisplayName("CI Alpha"))
        assertNull(beta.setDisplayName("CI Beta"))

        val duel = SampleCatalog.tournaments.first { it.id == "solitaire_duel_quick" }
        val ticketA = assertIs<JoinResult.Joined>(alpha.joinTournament(duel.id)).ticket
        val ticketB = assertIs<JoinResult.Joined>(beta.joinTournament(duel.id)).ticket
        assertEquals(ticketA.poolId, ticketB.poolId, "both guests are matched in the same duel")
        assertEquals(ticketA.seed, ticketB.seed, "both get the same board")
        println("LIVE: duel ${ticketA.poolId} seed ${ticketA.seed}")

        alpha.submitScore(ticketA, GameResult(score = 5_000, durationMillis = 70_000))
        val settled = beta.submitScore(ticketB, GameResult(score = 3_000, durationMillis = 50_000))
        assertEquals(PoolStatus.COMPLETED, settled.status)

        val rows = Leaderboard.rank(settled, duel)
        assertEquals("CI Alpha", rows[0].entry.displayName)
        assertEquals(36, rows[0].prize)
        assertEquals(0, rows[1].prize)

        // Reading the pool back (what the leaderboard screen does).
        val observed = withTimeout(15_000) { alpha.observePool(settled.id).first { it != null } }
        assertEquals(PoolStatus.COMPLETED, observed?.status)

        // Balances refresh in the background after a match.
        withTimeout(15_000) {
            while (alpha.wallet.value.coins != 1016L || beta.wallet.value.coins != 980L) delay(300)
        }
        println("LIVE: wallets alpha=${alpha.wallet.value.coins} beta=${beta.wallet.value.coins}")
        assertTrue(alpha.wallet.value.transactions.any { it.description == "Prize: won 1v1 Solitaire Quick" })

        withTimeout(15_000) {
            while (alpha.rewards.value.missions.firstOrNull { it.id == "duel_1" }?.progress != 1) delay(300)
        }
        assertEquals(50, alpha.claimDailyReward())
        assertTrue(alpha.myPools.value.any { it.id == settled.id })
        println("LIVE: all checks passed")
    }
}
