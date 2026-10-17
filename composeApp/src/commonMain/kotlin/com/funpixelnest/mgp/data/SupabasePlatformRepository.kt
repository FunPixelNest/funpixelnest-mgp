package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.DailyReward
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.Mission
import com.funpixelnest.mgp.domain.Player
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolEntry
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.RewardsState
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.domain.Wallet
import com.funpixelnest.mgp.domain.WalletTransaction
import com.funpixelnest.mgp.game.GameResult
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The real backend: Supabase (Postgres + Auth + Realtime).
 *
 * All coin-moving rules (entry fees, pools, ranking, payouts, rewards) run on the server as
 * database functions (see /supabase). This class only calls them and maps the JSON to app models.
 * Games, tournaments and store packs are read from [SampleCatalog], which matches the server seed.
 */
class SupabasePlatformRepository(
    supabaseUrl: String,
    supabaseAnonKey: String,
    /** Tests: keep the sign-in in memory only (no device storage, a fresh guest each time). */
    inMemorySession: Boolean = false,
) : PlatformRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = createSupabaseClient(supabaseUrl, supabaseAnonKey) {
        install(Auth) {
            if (inMemorySession) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                enableLifecycleCallbacks = false
            }
        }
        install(Postgrest)
        install(Realtime)
    }

    private val _status = MutableStateFlow<BackendStatus>(BackendStatus.Loading)
    override val status: StateFlow<BackendStatus> = _status.asStateFlow()

    private var _player = Player(id = "", displayName = "")
    override val player: Player get() = _player

    private val _wallet = MutableStateFlow(Wallet(coins = 0, transactions = emptyList()))
    override val wallet: StateFlow<Wallet> = _wallet.asStateFlow()

    private val _myPools = MutableStateFlow<List<Pool>>(emptyList())
    override val myPools: StateFlow<List<Pool>> = _myPools.asStateFlow()

    private val _rewards = MutableStateFlow(
        RewardsState(DailyReward(day = 1, claimedToday = false), 50, 0, emptyList()),
    )
    override val rewards: StateFlow<RewardsState> = _rewards.asStateFlow()

    private var syncJob: Job? = null
    private var channelCounter = 0

    // ---------------------------------------------------------------- startup

    override suspend fun initialize() {
        _status.value = BackendStatus.Loading
        try {
            client.auth.awaitInitialization()
            // First launch: create a guest account. The session is kept on the device afterwards.
            if (client.auth.currentSessionOrNull() == null) client.auth.signInAnonymously()
            val me = rpc("ensure_player", PlayerDto.serializer())
            _player = Player(id = me.playerId, displayName = me.displayName)
            refreshWallet()
            refreshPools()
            refreshRewards()
            startBackgroundSync()
            _status.value = BackendStatus.Ready
        } catch (e: Exception) {
            _status.value = BackendStatus.Error(friendlyMessage(e))
        }
    }

    /** Keeps "My tournaments" and the balance fresh while pools are still running. */
    private fun startBackgroundSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(15_000)
                if (_myPools.value.any { it.status != PoolStatus.COMPLETED }) {
                    runCatching { refreshPools() }
                    runCatching { refreshWallet() }
                }
            }
        }
    }

    // ---------------------------------------------------------------- catalog (local, matches server seed)

    override fun games(): List<Game> = SampleCatalog.games
    override fun game(gameId: String): Game? = SampleCatalog.games.find { it.id == gameId }
    override fun tournamentsFor(gameId: String): List<Tournament> = SampleCatalog.tournaments.filter { it.gameId == gameId }
    override fun tournament(tournamentId: String): Tournament? = SampleCatalog.tournaments.find { it.id == tournamentId }
    override fun featuredTournaments(): List<Tournament> = SampleCatalog.featuredTournamentIds.mapNotNull { tournament(it) }
    override fun newGames(): List<Game> = SampleCatalog.newGameIds.mapNotNull { game(it) }
    override fun storePacks(): List<StorePack> = SampleCatalog.storePacks

    // ---------------------------------------------------------------- tournaments & duels

    override suspend fun joinTournament(tournamentId: String): JoinResult = try {
        val dto = rpc("join_tournament", TicketDto.serializer()) { put("p_tournament_id", tournamentId) }
        refreshSoon()
        JoinResult.Joined(
            MatchTicket(
                ticketId = dto.ticketId,
                poolId = dto.poolId,
                tournamentId = dto.tournamentId,
                gameId = dto.gameId,
                seed = dto.seed.toLongOrNull() ?: 0L,
            ),
        )
    } catch (e: Exception) {
        if (serverCode(e) == "INSUFFICIENT_COINS") JoinResult.InsufficientCoins else JoinResult.Error(friendlyMessage(e))
    }

    override suspend fun submitScore(ticket: MatchTicket, result: GameResult): Pool {
        val dto = rpc("submit_score", PoolDto.serializer()) {
            put("p_ticket_id", ticket.ticketId)
            put("p_score", result.score)
            put("p_duration_ms", result.durationMillis)
        }
        refreshSoon()
        return dto.toPool()
    }

    override fun observePool(poolId: String): Flow<Pool?> = channelFlow {
        var completed = false
        suspend fun fetch() {
            val pool = runCatching { fetchPool(poolId) }.getOrNull() ?: return
            send(pool)
            if (pool.status == PoolStatus.COMPLETED && !completed) {
                completed = true
                refreshSoon() // prizes were just paid
            }
        }
        fetch()

        // Live updates: any change to this pool's entries → reload the pool.
        val channel = client.channel("pool-$poolId-${channelCounter++}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "pool_entries"
            filter("pool_id", FilterOperator.EQ, poolId)
        }
        launch { changes.collect { fetch() } }
        launch { runCatching { channel.subscribe() } }

        // Safety net in case realtime isn't enabled: poll slowly until the pool is finished.
        launch {
            while (isActive && !completed) {
                delay(10_000)
                fetch()
            }
        }

        awaitClose { scope.launch { runCatching { client.realtime.removeChannel(channel) } } }
    }.distinctUntilChanged()

    private suspend fun fetchPool(poolId: String): Pool? {
        val raw = rpcRaw("get_pool") { put("p_pool_id", poolId) }
        if (raw.isBlank() || raw.trim() == "null") return null
        return json.decodeFromString(PoolDto.serializer(), raw).toPool()
    }

    // ---------------------------------------------------------------- rewards

    override suspend fun claimDailyReward(): Long = claim("claim_daily_reward")
    override suspend fun claimFreeCoins(): Long = claim("claim_free_coins")
    override suspend fun claimMission(missionId: String): Long = claim("claim_mission") { put("p_mission_id", missionId) }

    private suspend fun claim(function: String, params: (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = {}): Long {
        val coins = runCatching { rpcRaw(function, params).trim().toLongOrNull() ?: 0L }.getOrDefault(0L)
        runCatching { refreshWallet() }
        runCatching { refreshRewards() }
        return coins
    }

    // ---------------------------------------------------------------- store

    override suspend fun purchasePack(packId: String): PurchaseResult = try {
        val coins = rpcRaw("purchase_pack_demo") { put("p_pack_id", packId) }.trim().toLongOrNull() ?: 0L
        runCatching { refreshWallet() }
        PurchaseResult.Success(coins)
    } catch (e: Exception) {
        PurchaseResult.Error(friendlyMessage(e))
    }

    // ---------------------------------------------------------------- account & push

    override suspend fun setDisplayName(name: String): String? = try {
        rpcRaw("set_display_name") { put("p_name", name.trim()) }
        _player = _player.copy(displayName = name.trim())
        runCatching { refreshPools() }
        null
    } catch (e: Exception) {
        friendlyMessage(e)
    }

    override suspend fun registerPushToken(token: String, platform: String) {
        runCatching {
            rpcRaw("register_push_token") {
                put("p_token", token)
                put("p_platform", platform)
            }
        }
    }

    // ---------------------------------------------------------------- refreshes

    private suspend fun refreshWallet() {
        val dto = rpc("get_wallet", WalletDto.serializer())
        _wallet.value = Wallet(
            coins = dto.coins,
            transactions = dto.transactions.map { WalletTransaction(it.id, it.amount, it.description) },
        )
    }

    private suspend fun refreshPools() {
        val list = rpc("get_my_pools", ListSerializer(PoolDto.serializer()))
        _myPools.value = list.map { it.toPool() }
    }

    private suspend fun refreshRewards() {
        val dto = rpc("get_rewards_state", RewardsDto.serializer())
        _rewards.value = RewardsState(
            daily = DailyReward(day = dto.daily.day, claimedToday = dto.daily.claimedToday, coinsPerDay = dto.daily.coinsPerDay),
            freeCoinsAmount = dto.freeCoinsAmount,
            freeCoinsLeftToday = dto.freeCoinsLeftToday,
            missions = dto.missions.map {
                Mission(it.id, it.title, it.target, it.progress, it.rewardCoins, it.claimed)
            },
        )
    }

    /** Refresh balance, pools and missions in the background after something changed. */
    private fun refreshSoon() {
        scope.launch {
            runCatching { refreshWallet() }
            runCatching { refreshPools() }
            runCatching { refreshRewards() }
        }
    }

    // ---------------------------------------------------------------- RPC helpers

    private suspend fun rpcRaw(function: String, params: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): String {
        val body: JsonObject = buildJsonObject(params)
        return client.postgrest.rpc(function, body).data
    }

    private suspend fun <T> rpc(
        function: String,
        serializer: KSerializer<T>,
        params: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): T = json.decodeFromString(serializer, rpcRaw(function, params))

    /** The server raises errors like 'INSUFFICIENT_COINS'; this returns that code (or null). */
    private fun serverCode(e: Exception): String? =
        (e as? RestException)?.error?.trim()?.takeIf { it.isNotEmpty() && it == it.uppercase() && ' ' !in it }

    private fun friendlyMessage(e: Exception): String = when (serverCode(e)) {
        "INSUFFICIENT_COINS" -> "Not enough coins. Get more in the Store or Rewards."
        "TOURNAMENT_NOT_FOUND" -> "This tournament isn't available right now."
        "TICKET_ALREADY_USED" -> "This match was already submitted."
        "TICKET_NOT_FOUND" -> "We couldn't find this match."
        "INVALID_SCORE" -> "That score couldn't be accepted."
        "INVALID_NAME" -> "Name must be 2 to 20 characters."
        "PURCHASES_DISABLED" -> "The store is closed right now."
        "PACK_NOT_FOUND" -> "This pack isn't available."
        "NOT_AUTHENTICATED" -> "You're signed out. Restart the app to sign in again."
        else -> when {
            e is HttpRequestException -> "Check your internet connection and try again."
            (e.message ?: "").contains("anonymous", ignoreCase = true) ->
                "Guest sign-in is turned off. In Supabase, enable Auth > Sign In / Providers > Allow anonymous sign-ins."
            else -> "Something went wrong. Please try again."
        }
    }
}

// ---------------------------------------------------------------- JSON shapes (see supabase/README.md "Contract")

@Serializable
private data class PlayerDto(
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String,
    val coins: Long = 0,
)

@Serializable
private data class WalletDto(
    val coins: Long,
    val transactions: List<TransactionDto> = emptyList(),
)

@Serializable
private data class TransactionDto(
    val id: String,
    val amount: Long,
    val description: String,
)

@Serializable
private data class TicketDto(
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("pool_id") val poolId: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("game_id") val gameId: String,
    val seed: String,
)

@Serializable
private data class PoolDto(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    val seed: String,
    val status: String,
    val entries: List<EntryDto> = emptyList(),
) {
    fun toPool() = Pool(
        id = id,
        tournamentId = tournamentId,
        seed = seed.toLongOrNull() ?: 0L,
        entries = entries.map {
            PoolEntry(
                playerId = it.playerId,
                displayName = it.displayName,
                score = it.score,
                durationMillis = it.durationMs,
                submitSequence = it.submitSequence,
                finalRank = it.rank,
                finalPrize = if (it.rank != null) it.prize else null,
            )
        },
        status = when (status) {
            "completed" -> PoolStatus.COMPLETED
            "full" -> PoolStatus.FULL
            else -> PoolStatus.OPEN
        },
    )
}

@Serializable
private data class EntryDto(
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String,
    val score: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("submit_sequence") val submitSequence: Long? = null,
    val rank: Int? = null,
    val prize: Long = 0,
)

@Serializable
private data class RewardsDto(
    val daily: DailyDto,
    @SerialName("free_coins_amount") val freeCoinsAmount: Long,
    @SerialName("free_coins_left_today") val freeCoinsLeftToday: Int,
    val missions: List<MissionDto> = emptyList(),
)

@Serializable
private data class DailyDto(
    val day: Int,
    @SerialName("claimed_today") val claimedToday: Boolean,
    @SerialName("coins_per_day") val coinsPerDay: List<Long>,
)

@Serializable
private data class MissionDto(
    val id: String,
    val title: String,
    val target: Int,
    val progress: Int,
    @SerialName("reward_coins") val rewardCoins: Long,
    val claimed: Boolean,
)
