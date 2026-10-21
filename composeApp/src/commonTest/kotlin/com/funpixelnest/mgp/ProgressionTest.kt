package com.funpixelnest.mgp

import com.funpixelnest.mgp.data.BackendStatus
import com.funpixelnest.mgp.data.FakePlatformRepository
import com.funpixelnest.mgp.data.JoinResult
import com.funpixelnest.mgp.data.Usernames
import com.funpixelnest.mgp.domain.ClaimResult
import com.funpixelnest.mgp.domain.LeaguePoints
import com.funpixelnest.mgp.domain.LeagueTier
import com.funpixelnest.mgp.domain.LevelInfo
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.ui.Navigator
import com.funpixelnest.mgp.ui.Popup
import com.funpixelnest.mgp.ui.Screen
import com.funpixelnest.mgp.ui.Tab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionTest {

    @Test
    fun levelMathMatchesServer() {
        assertEquals(LevelInfo(1, 0L, 250L), LevelInfo.fromXp(0))
        assertEquals(LevelInfo(2, 0L, 500L), LevelInfo.fromXp(250))
        // Mockup: level 18 with 2,850 / 4,500 XP.
        val xpBefore18 = (1 until 18).sumOf { 250L * it }
        assertEquals(LevelInfo(18, 2_850L, 4_500L), LevelInfo.fromXp(xpBefore18 + 2_850))
    }

    @Test
    fun leagueTiersAndPoints() {
        assertEquals(LeagueTier.BRONZE, LeagueTier.forTrophies(999))
        assertEquals(LeagueTier.SILVER, LeagueTier.forTrophies(1_250))
        assertEquals(LeagueTier.CHAMPION, LeagueTier.forTrophies(9_000))
        assertEquals(3, LeagueTier.SILVER.division(1_000))
        assertEquals(1, LeagueTier.SILVER.division(1_999))
        assertNull(LeagueTier.CHAMPION.division(6_000))
        assertEquals(100L, LeaguePoints.forResult(1, isDuel = false))
        assertEquals(80L, LeaguePoints.forResult(1, isDuel = true))
        assertEquals(10L, LeaguePoints.forResult(5, isDuel = false))
        assertEquals(50L, LeaguePoints.xpForResult(1, isDuel = true))
    }

    @Test
    fun usernameRules() {
        assertNull(Usernames.validate("Mohit_18"))
        assertNotNull(Usernames.validate("ab"))
        assertNotNull(Usernames.validate("bad name"))
        assertEquals("mohit@players.funpixelnest.com", Usernames.toEmail(" Mohit "))
    }

    @Test
    fun backOrder() {
        val nav = Navigator()
        assertFalse(nav.wantsBack)
        assertFalse(nav.handleBack(), "nothing to go back to on the Games tab")

        nav.selectTab(Tab.Store)
        nav.push(Screen.Profile)
        nav.drawerOpen = true
        nav.show(Popup.DailyBonus)

        assertTrue(nav.handleBack()); assertNull(nav.popup)
        assertTrue(nav.handleBack()); assertFalse(nav.drawerOpen)
        var paused = false
        nav.gameBackHandler = { if (!paused) { paused = true; true } else false }
        assertTrue(nav.handleBack()); assertTrue(paused, "in a game, Back pauses first")
        assertTrue(nav.handleBack()); assertNull(nav.current)
        assertTrue(nav.handleBack()); assertEquals(Tab.Games, nav.tab)
        assertFalse(nav.handleBack())
    }

    @Test
    fun demoBackendFullLoop() = runTest {
        val repo = FakePlatformRepository(scope = backgroundScope, simulationSpeed = 0.0)
        repo.initialize()
        assertEquals(BackendStatus.SignedOut, repo.status.value)
        assertNull(repo.signUp("Tester", "secret1"))
        assertEquals(BackendStatus.Ready, repo.status.value)
        assertEquals("Tester", repo.player.displayName)

        val joined = assertIs<JoinResult.Joined>(repo.joinTournament("solitaire_starter"))
        repo.submitScore(joined.ticket, GameResult(score = 99_999, durationMillis = 1_000))
        val pool = repo.observePool(joined.ticket.poolId).first { it?.status == PoolStatus.COMPLETED }
        assertNotNull(pool)

        // 25 XP for playing, then 1st of 5: 20 + 30 XP and 100 league points.
        assertEquals(75L, repo.profile.value.xp)
        assertEquals(100L, repo.league.value.trophies)
        assertEquals(1, repo.profile.value.stats.gamesWon)
        val win = repo.challenges.value.challenges.first { it.id == "d_win_1" }
        assertTrue(win.canClaim)
        assertIs<ClaimResult.Granted>(repo.claimChallenge("d_win_1"))
        assertIs<ClaimResult.Failed>(repo.claimChallenge("d_win_1"))

        val gemsBefore = repo.wallet.value.gems
        assertIs<ClaimResult.Granted>(repo.openChest("wooden"))
        assertEquals(gemsBefore - 10, repo.wallet.value.gems)
        assertIs<ClaimResult.Granted>(repo.claimDailyBonus())
        assertIs<ClaimResult.Failed>(repo.claimDailyBonus())
        assertTrue(repo.spinWheel().isSuccess)
        assertTrue(repo.spinWheel().isFailure)

        repo.signOut()
        assertEquals(BackendStatus.SignedOut, repo.status.value)
    }
}
