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
 * - [FakePlatformRepository]: in-memory demo backend (no account needed).
 * - [SupabasePlatformRepository]: the real backend, used when SUPABASE_URL is configured.
 * The screens don't know which one they're talking to.
 */
interface PlatformRepository {
    /** Loading → Ready (or Error). Screens wait for Ready before reading [player]. */
    val status: StateFlow<BackendStatus>

    /** Connects and signs the player in (guest account on first launch). Safe to call again to retry. */
    suspend fun initialize()

    /** The signed-in player. Only valid once [status] is Ready. */
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

    // ---------------------------------------------------------------- Account & push

    /** Renames the player (2–20 characters). Returns an error message, or null on success. */
    suspend fun setDisplayName(name: String): String?

    /** Saves this device's push token so match results can be sent when the app is closed. */
    suspend fun registerPushToken(token: String, platform: String)
}

sealed interface BackendStatus {
    data object Loading : BackendStatus
    data object Ready : BackendStatus
    data class Error(val message: String) : BackendStatus
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
