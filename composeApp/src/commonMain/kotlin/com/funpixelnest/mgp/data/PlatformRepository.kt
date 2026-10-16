package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.Player
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.RewardsState
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.domain.Wallet
import com.funpixelnest.mgp.game.GameResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the app needs from the backend.
 *
 * Today: [FakePlatformRepository] (in-memory, for building the UI).
 * Next: a Nakama-backed implementation — the screens won't change.
 */
interface PlatformRepository {
    val player: Player
    val wallet: StateFlow<Wallet>

    /** Pools the current player has joined, newest first. */
    val myPools: StateFlow<List<Pool>>

    fun games(): List<Game>
    fun game(gameId: String): Game?
    fun tournamentsFor(gameId: String): List<Tournament>
    fun tournament(tournamentId: String): Tournament?

    /** Deducts the entry fee and places the player in an open pool (or a new one). */
    suspend fun joinTournament(tournamentId: String): JoinResult

    /** Submits the final score for a ticket. Each ticket can be used only once. */
    suspend fun submitScore(ticket: MatchTicket, result: GameResult): Pool

    /** Live updates for one pool's leaderboard. */
    fun observePool(poolId: String): Flow<Pool?>

    // ---------------------------------------------------------------- Featured

    /** Highlighted tournaments for the Featured tab. */
    fun featuredTournaments(): List<Tournament>

    /** Games marked as new on the Featured tab. */
    fun newGames(): List<Game>

    // ---------------------------------------------------------------- Rewards

    val rewards: StateFlow<RewardsState>

    /** Claims today's login-streak reward. Returns coins granted (0 if already claimed). */
    suspend fun claimDailyReward(): Long

    /** Grants free coins after a rewarded video. Returns coins granted (0 if none left today). */
    suspend fun claimFreeCoins(): Long

    /** Claims a completed mission. Returns coins granted (0 if not claimable). */
    suspend fun claimMission(missionId: String): Long

    // ---------------------------------------------------------------- Store

    fun storePacks(): List<StorePack>

    /** Buys a coin pack. Payments aren't connected yet, so the fake backend grants it as a demo. */
    suspend fun purchasePack(packId: String): PurchaseResult
}

sealed interface PurchaseResult {
    data class Success(val coinsAdded: Long) : PurchaseResult
    data class Error(val message: String) : PurchaseResult
}

sealed interface JoinResult {
    data class Joined(val ticket: MatchTicket) : JoinResult
    data object InsufficientCoins : JoinResult
    data class Error(val message: String) : JoinResult
}
