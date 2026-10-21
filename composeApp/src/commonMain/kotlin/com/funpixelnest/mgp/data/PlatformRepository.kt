package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.ChallengesState
import com.funpixelnest.mgp.domain.ClaimResult
import com.funpixelnest.mgp.domain.DailyBonusState
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.InboxMessage
import com.funpixelnest.mgp.domain.LeagueState
import com.funpixelnest.mgp.domain.LoginMethod
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.Player
import com.funpixelnest.mgp.domain.PlayerProfile
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.domain.ShopState
import com.funpixelnest.mgp.domain.SpinResult
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
    /** Loading → SignedOut (show login) or Ready (or Error). Screens wait for Ready before reading [player]. */
    val status: StateFlow<BackendStatus>

    /** Connects and restores a saved sign-in. Safe to call again to retry. */
    suspend fun initialize()

    // ---------------------------------------------------------------- sign-in

    /** Sign-in options that are switched on for this build (see AUTH_METHODS in config). */
    val loginMethods: List<LoginMethod>

    /** How the current player signed in (shown in Settings). */
    val signedInWith: LoginMethod?

    /** Each returns null on success, or a message to show. On success [status] becomes Ready. */
    suspend fun signInAsGuest(): String?
    suspend fun signUp(username: String, password: String): String?
    suspend fun signIn(username: String, password: String): String?

    /** Google / Apple / Facebook. Opens the provider's page; the app returns signed in. */
    suspend fun signInWithProvider(method: LoginMethod): String?

    /** Mobile number: send a one-time code by SMS, then verify it. Phone in international format (+91…). */
    suspend fun sendPhoneCode(phone: String): String?
    suspend fun verifyPhoneCode(phone: String, code: String): String?

    suspend fun signOut()

    // ---------------------------------------------------------------- player

    /** The signed-in player. Only valid once [status] is Ready. */
    val player: Player
    val profile: StateFlow<PlayerProfile>
    val wallet: StateFlow<Wallet>

    /** Renames the player (2–20 characters). Returns an error message, or null on success. */
    suspend fun setDisplayName(name: String): String?
    suspend fun setAvatar(avatarId: Int): String?

    /** Marks the welcome-bonus popup as seen. */
    suspend fun acknowledgeWelcome()

    // ---------------------------------------------------------------- games & tournaments

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

    // ---------------------------------------------------------------- challenges, leagues, rewards

    val challenges: StateFlow<ChallengesState>
    suspend fun claimChallenge(challengeId: String): ClaimResult

    val league: StateFlow<LeagueState>

    val dailyBonus: StateFlow<DailyBonusState>
    suspend fun claimDailyBonus(): ClaimResult

    val shop: StateFlow<ShopState>

    /** "free" (every 4 hours) or a gem chest: wooden, silver, gold, diamond. */
    suspend fun openChest(chestId: String): ClaimResult

    /** Lucky spin, once a day. Failure carries a message to show (e.g. come back tomorrow). */
    suspend fun spinWheel(): Result<SpinResult>

    /** Free coins after a rewarded video. Returns coins granted (0 if none left today). */
    suspend fun claimFreeCoins(): Long

    /**
     * "Watch a video for 2x": pays [reward] (the reward the player just got) once more.
     * The server only doubles a recent welcome / daily / challenge / chest / spin / level / league reward
     * with exactly these amounts, once. Returns false when there was nothing to double.
     */
    suspend fun doubleReward(reward: RewardGrant): Boolean

    /** Reloads challenges, league, bonus and shop (e.g. when a tab opens). */
    suspend fun refreshProgress()

    // ---------------------------------------------------------------- store

    fun storePacks(): List<StorePack>

    /** Buys a coin pack. Payments aren't connected yet, so this is a demo grant. */
    suspend fun purchasePack(packId: String): PurchaseResult

    // ---------------------------------------------------------------- inbox & push

    val inbox: StateFlow<List<InboxMessage>>
    suspend fun refreshInbox()
    suspend fun markInboxRead()

    /** Saves this device's push token so match results can be sent when the app is closed. */
    suspend fun registerPushToken(token: String, platform: String)
}

sealed interface BackendStatus {
    data object Loading : BackendStatus
    data object SignedOut : BackendStatus
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

/** Usernames become a private sign-in address on the server; only letters, numbers and _ . */
object Usernames {
    const val DOMAIN = "players.funpixelnest.com"

    fun validate(username: String): String? {
        val u = username.trim()
        return when {
            u.length !in 3..20 -> "Username must be 3 to 20 characters."
            !u.all { it.isLetterOrDigit() || it == '_' } -> "Use only letters, numbers and _ in your username."
            else -> null
        }
    }

    fun validatePassword(password: String): String? =
        if (password.length < 6) "Password must be at least 6 characters." else null

    fun toEmail(username: String) = "${username.trim().lowercase()}@$DOMAIN"
}
