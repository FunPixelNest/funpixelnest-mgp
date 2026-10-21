package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.config.BackendConfig
import com.funpixelnest.mgp.domain.Challenge
import com.funpixelnest.mgp.domain.ChallengeCategory
import com.funpixelnest.mgp.domain.ChallengePeriod
import com.funpixelnest.mgp.domain.ChallengesState
import com.funpixelnest.mgp.domain.ChestOffer
import com.funpixelnest.mgp.domain.ClaimResult
import com.funpixelnest.mgp.domain.DailyBonusDay
import com.funpixelnest.mgp.domain.DailyBonusState
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.InboxMessage
import com.funpixelnest.mgp.domain.LeagueRow
import com.funpixelnest.mgp.domain.LeagueState
import com.funpixelnest.mgp.domain.LeagueTier
import com.funpixelnest.mgp.domain.LoginMethod
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.domain.Player
import com.funpixelnest.mgp.domain.PlayerProfile
import com.funpixelnest.mgp.domain.PlayerStats
import com.funpixelnest.mgp.domain.Pool
import com.funpixelnest.mgp.domain.PoolEntry
import com.funpixelnest.mgp.domain.PoolStatus
import com.funpixelnest.mgp.domain.RewardGrant
import com.funpixelnest.mgp.domain.ShopState
import com.funpixelnest.mgp.domain.SpinResult
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.domain.Tournament
import com.funpixelnest.mgp.domain.Wallet
import com.funpixelnest.mgp.domain.WalletTransaction
import com.funpixelnest.mgp.game.GameResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * The real backend: Supabase (Postgres + Auth + Realtime).
 *
 * All coin-moving rules (entry fees, pools, ranking, payouts, rewards, chests, leagues) run on the server
 * as database functions (see /supabase). This class only calls them and maps the JSON to app models.
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

    /** Exposed so platforms can hand sign-in redirects (deep links) back to Supabase. */
    val client: SupabaseClient = createSupabaseClient(supabaseUrl, supabaseAnonKey) {
        install(Auth) {
            // Google / Apple / Facebook come back to the app through funpixelnest://login
            scheme = "funpixelnest"
            host = "login"
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

    private val _profile = MutableStateFlow(PlayerProfile())
    override val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    private val _wallet = MutableStateFlow(Wallet(coins = 0, transactions = emptyList()))
    override val wallet: StateFlow<Wallet> = _wallet.asStateFlow()

    private val _myPools = MutableStateFlow<List<Pool>>(emptyList())
    override val myPools: StateFlow<List<Pool>> = _myPools.asStateFlow()

    private val _challenges = MutableStateFlow(ChallengesState())
    override val challenges: StateFlow<ChallengesState> = _challenges.asStateFlow()

    private val _league = MutableStateFlow(LeagueState())
    override val league: StateFlow<LeagueState> = _league.asStateFlow()

    private val _dailyBonus = MutableStateFlow(DailyBonusState())
    override val dailyBonus: StateFlow<DailyBonusState> = _dailyBonus.asStateFlow()

    private val _shop = MutableStateFlow(ShopState())
    override val shop: StateFlow<ShopState> = _shop.asStateFlow()

    private val _inbox = MutableStateFlow<List<InboxMessage>>(emptyList())
    override val inbox: StateFlow<List<InboxMessage>> = _inbox.asStateFlow()

    private var syncJob: Job? = null
    private var sessionWatch: Job? = null
    private var channelCounter = 0
    private val loadLock = Mutex()

    // ---------------------------------------------------------------- startup & sign-in

    override val loginMethods: List<LoginMethod> = BackendConfig.AUTH_METHODS
        .split(',')
        .mapNotNull { id -> LoginMethod.entries.firstOrNull { it.id == id.trim().lowercase() } }

    override val signedInWith: LoginMethod?
        get() {
            val user = client.auth.currentUserOrNull() ?: return null
            if (user.isAnonymous == true) return LoginMethod.GUEST
            return when ((user.appMetadata?.get("provider") as? JsonPrimitive)?.contentOrNull) {
                "google" -> LoginMethod.GOOGLE
                "apple" -> LoginMethod.APPLE
                "facebook" -> LoginMethod.FACEBOOK
                "phone" -> LoginMethod.PHONE
                "email" -> LoginMethod.USERNAME
                else -> null
            }
        }

    override suspend fun initialize() {
        _status.value = BackendStatus.Loading
        try {
            client.auth.awaitInitialization()
            watchSession()
            if (client.auth.currentSessionOrNull() == null) {
                _status.value = BackendStatus.SignedOut
            } else {
                loadPlayer(displayName = null)
            }
        } catch (e: Exception) {
            _status.value = BackendStatus.Error(friendlyMessage(e))
        }
    }

    /** Google/Apple/Facebook finish outside the app; when their session arrives, load the player. */
    private fun watchSession() {
        if (sessionWatch != null) return
        sessionWatch = scope.launch {
            client.auth.sessionStatus.collect { s ->
                if (s is SessionStatus.Authenticated && _status.value == BackendStatus.SignedOut) {
                    runCatching { loadPlayer(displayName = null) }
                }
            }
        }
    }

    /** After any sign-in: create the player on first visit, then load everything. */
    private suspend fun loadPlayer(displayName: String?) = loadLock.withLock {
        if (_status.value == BackendStatus.Ready) return@withLock
        val me = rpc("ensure_player", PlayerDto.serializer()) { displayName?.let { put("p_display_name", it) } }
        _player = Player(id = me.playerId, displayName = me.displayName)
        refreshProfile()
        refreshWallet()
        refreshPools()
        refreshProgress()
        runCatching { refreshInbox() }
        startBackgroundSync()
        _status.value = BackendStatus.Ready
    }

    private suspend fun attempt(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: Exception) {
        authMessage(e)
    }

    override suspend fun signInAsGuest(): String? = attempt {
        client.auth.signInAnonymously()
        loadPlayer(displayName = null)
    }

    override suspend fun signUp(username: String, password: String): String? {
        Usernames.validate(username)?.let { return it }
        Usernames.validatePassword(password)?.let { return it }
        return attempt {
            client.auth.signUpWith(Email) {
                email = Usernames.toEmail(username)
                this.password = password
            }
            if (client.auth.currentSessionOrNull() == null) {
                throw IllegalStateException(
                    "Account created, but email confirmation is on. In Supabase turn off Authentication > Sign In / Providers > Email > Confirm email.",
                )
            }
            loadPlayer(displayName = username.trim())
        }
    }

    override suspend fun signIn(username: String, password: String): String? {
        if (username.isBlank() || password.isBlank()) return "Enter your username and password."
        return attempt {
            client.auth.signInWith(Email) {
                email = Usernames.toEmail(username)
                this.password = password
            }
            loadPlayer(displayName = null)
        }
    }

    override suspend fun signInWithProvider(method: LoginMethod): String? = attempt {
        when (method) {
            LoginMethod.GOOGLE -> client.auth.signInWith(Google)
            LoginMethod.APPLE -> client.auth.signInWith(Apple)
            LoginMethod.FACEBOOK -> client.auth.signInWith(Facebook)
            else -> error("Unsupported sign-in method")
        }
        // The session arrives later through the redirect; watchSession() finishes the sign-in.
    }

    override suspend fun sendPhoneCode(phone: String): String? = attempt {
        client.auth.signInWith(OTP) { this.phone = normalizePhone(phone) }
    }

    override suspend fun verifyPhoneCode(phone: String, code: String): String? = attempt {
        client.auth.verifyPhoneOtp(OtpType.Phone.SMS, normalizePhone(phone), code.trim())
        loadPlayer(displayName = null)
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() || it == '+' }
        return if (digits.startsWith("+")) digits else "+$digits"
    }

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
        syncJob?.cancel()
        _player = Player(id = "", displayName = "")
        _myPools.value = emptyList()
        _status.value = BackendStatus.SignedOut
    }

    /** Keeps "My matches" and the balance fresh while pools are still running. */
    private fun startBackgroundSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(15_000)
                if (_myPools.value.any { it.status != PoolStatus.COMPLETED }) {
                    runCatching { refreshPools() }
                    runCatching { refreshWallet() }
                    runCatching { refreshProfile() }
                }
            }
        }
    }

    // ---------------------------------------------------------------- catalog (local, matches server seed)

    override fun games(): List<Game> = SampleCatalog.visibleGames
    override fun game(gameId: String): Game? = SampleCatalog.games.find { it.id == gameId }
    override fun tournamentsFor(gameId: String): List<Tournament> = SampleCatalog.tournaments.filter { it.gameId == gameId }
    override fun tournament(tournamentId: String): Tournament? = SampleCatalog.tournaments.find { it.id == tournamentId }
    override fun storePacks(): List<StorePack> = SampleCatalog.storePacks

    // ---------------------------------------------------------------- player

    override suspend fun setDisplayName(name: String): String? = try {
        rpcRaw("set_display_name") { put("p_name", name.trim()) }
        _player = _player.copy(displayName = name.trim())
        refreshSoon()
        null
    } catch (e: Exception) {
        friendlyMessage(e)
    }

    override suspend fun setAvatar(avatarId: Int): String? = try {
        rpcRaw("set_avatar") { put("p_avatar_id", avatarId) }
        _profile.value = _profile.value.copy(avatarId = avatarId)
        refreshSoon()
        null
    } catch (e: Exception) {
        friendlyMessage(e)
    }

    override suspend fun acknowledgeWelcome() {
        _profile.value = _profile.value.copy(welcomePending = false)
        runCatching { rpcRaw("ack_welcome") }
    }

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
                refreshSoon() // prizes, XP and trophies were just awarded
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

    // ---------------------------------------------------------------- challenges, league, bonus, shop

    override suspend fun claimChallenge(challengeId: String): ClaimResult = grant("claim_challenge", {
        put("p_challenge_id", challengeId)
    }) { _challenges.value.challenges.find { it.id == challengeId }?.title ?: "Challenge" }

    override suspend fun claimDailyBonus(): ClaimResult {
        val day = _dailyBonus.value.day
        return try {
            val dto = rpc("claim_daily_bonus", DailyClaimDto.serializer())
            afterClaim()
            ClaimResult.Granted(RewardGrant(dto.coins, dto.gems), if (dto.chest != null) "Epic Chest" else "Day ${dto.day.takeIf { it > 0 } ?: day} bonus")
        } catch (e: Exception) {
            ClaimResult.Failed(friendlyMessage(e))
        }
    }

    override suspend fun openChest(chestId: String): ClaimResult = grant("open_chest", { put("p_chest_id", chestId) }) {
        _shop.value.chests.find { it.id == chestId }?.name ?: "Free Chest"
    }

    override suspend fun spinWheel(): Result<SpinResult> = try {
        val dto = rpc("spin_wheel", SpinDto.serializer())
        afterClaim()
        Result.success(SpinResult(dto.segment, RewardGrant(dto.coins, dto.gems)))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(friendlyMessage(e)))
    }

    override suspend fun claimFreeCoins(): Long {
        val coins = runCatching { rpcRaw("claim_free_coins").trim().toLongOrNull() ?: 0L }.getOrDefault(0L)
        afterClaim()
        return coins
    }

    private suspend fun grant(function: String, params: JsonObjectBuilder.() -> Unit, title: () -> String): ClaimResult = try {
        val dto = rpc(function, GrantDto.serializer(), params)
        val name = title()
        afterClaim()
        ClaimResult.Granted(RewardGrant(dto.coins, dto.gems), name)
    } catch (e: Exception) {
        ClaimResult.Failed(friendlyMessage(e))
    }

    private suspend fun afterClaim() {
        runCatching { refreshWallet() }
        runCatching { refreshProgress() }
    }

    override suspend fun refreshProgress() {
        runCatching { refreshChallenges() }
        runCatching { refreshLeague() }
        runCatching { refreshDailyBonus() }
        runCatching { refreshShop() }
    }

    // ---------------------------------------------------------------- store

    override suspend fun purchasePack(packId: String): PurchaseResult = try {
        val coins = rpcRaw("purchase_pack_demo") { put("p_pack_id", packId) }.trim().toLongOrNull() ?: 0L
        runCatching { refreshWallet() }
        PurchaseResult.Success(coins)
    } catch (e: Exception) {
        PurchaseResult.Error(friendlyMessage(e))
    }

    // ---------------------------------------------------------------- inbox & push

    override suspend fun refreshInbox() {
        val list = rpc("get_notifications", ListSerializer(NotificationDto.serializer()))
        _inbox.value = list.map { InboxMessage(it.id, it.title, it.body ?: "", it.read) }
    }

    override suspend fun markInboxRead() {
        runCatching { rpcRaw("mark_notifications_read") }
        _inbox.value = _inbox.value.map { it.copy(read = true) }
        _profile.value = _profile.value.copy(unreadNotifications = 0)
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

    private suspend fun refreshProfile() {
        val dto = rpc("get_profile", ProfileDto.serializer())
        _player = _player.copy(displayName = dto.displayName)
        _profile.value = PlayerProfile(
            avatarId = dto.avatarId,
            xp = dto.xp,
            trophies = dto.trophies,
            welcomePending = dto.welcomePending,
            welcomeCoins = dto.welcomeCoins,
            unreadNotifications = dto.unreadNotifications,
            stats = PlayerStats(
                tournamentsPlayed = dto.stats.tournamentsPlayed,
                matchesPlayed = dto.stats.matchesPlayed,
                gamesWon = dto.stats.gamesWon,
                winRate = dto.stats.winRate,
                coinsWon = dto.stats.coinsWon,
                winStreak = dto.stats.winStreak,
                daysActive = dto.stats.daysActive,
            ),
        )
    }

    private suspend fun refreshWallet() {
        val dto = rpc("get_wallet", WalletDto.serializer())
        _wallet.value = Wallet(
            coins = dto.coins,
            gems = dto.gems,
            transactions = dto.transactions.map { WalletTransaction(it.id, it.amount, it.description, it.gems) },
        )
    }

    private suspend fun refreshPools() {
        val list = rpc("get_my_pools", ListSerializer(PoolDto.serializer()))
        _myPools.value = list.map { it.toPool() }
    }

    private suspend fun refreshChallenges() {
        val dto = rpc("get_challenges", ChallengesDto.serializer())
        _challenges.value = ChallengesState(
            dailyResetsInMillis = dto.dailyResetsInMs,
            weeklyResetsInMillis = dto.weeklyResetsInMs,
            challenges = dto.challenges.map {
                Challenge(
                    id = it.id,
                    period = if (it.period == "daily") ChallengePeriod.DAILY else ChallengePeriod.WEEKLY,
                    category = ChallengeCategory.entries.firstOrNull { c -> c.name.equals(it.category, ignoreCase = true) }
                        ?: ChallengeCategory.SPECIAL,
                    title = it.title,
                    subtitle = it.subtitle ?: "",
                    gameId = it.gameId,
                    target = it.target,
                    progress = it.progress,
                    reward = RewardGrant(it.rewardCoins, it.rewardGems),
                    claimed = it.claimed,
                )
            },
        )
    }

    private suspend fun refreshLeague() {
        val dto = rpc("get_league", LeagueDto.serializer())
        _league.value = LeagueState(
            trophies = dto.trophies,
            seasonPoints = dto.seasonPoints,
            seasonEndsInMillis = dto.seasonEndsInMs,
            top = dto.top.map { LeagueRow(it.rank, it.playerId, it.displayName, it.avatarId, it.points, LeagueTier.fromId(it.tier)) },
            myRank = dto.me?.rank,
        )
        _profile.value = _profile.value.copy(trophies = dto.trophies)
    }

    private suspend fun refreshDailyBonus() {
        val dto = rpc("get_daily_bonus", DailyBonusDto.serializer())
        _dailyBonus.value = DailyBonusState(
            day = dto.day,
            claimedToday = dto.claimedToday,
            resetsInMillis = dto.resetsInMs,
            rewards = dto.rewards.map { DailyBonusDay(it.day, it.coins, it.gems, it.chest) }.ifEmpty { DailyBonusState.DEFAULT_DAYS },
        )
    }

    private suspend fun refreshShop() {
        val dto = rpc("get_shop_state", ShopDto.serializer())
        _shop.value = ShopState(
            freeChestReadyInMillis = dto.freeChestReadyInMs,
            spinReadyInMillis = dto.spinReadyInMs,
            freeCoinsAmount = dto.freeCoinsAmount,
            freeCoinsLeftToday = dto.freeCoinsLeftToday,
            chests = dto.chests.map { ChestOffer(it.id, it.name, it.gemCost, it.description) }.ifEmpty { ShopState.DEFAULT_CHESTS },
            spinSegments = dto.spinSegments.map { RewardGrant(it.coins, it.gems) }.ifEmpty { ShopState.DEFAULT_SEGMENTS },
        )
    }

    /** Refresh balance, pools and progress in the background after something changed. */
    private fun refreshSoon() {
        scope.launch {
            runCatching { refreshWallet() }
            runCatching { refreshPools() }
            runCatching { refreshProfile() }
            refreshProgress()
        }
    }

    // ---------------------------------------------------------------- RPC helpers

    private suspend fun rpcRaw(function: String, params: JsonObjectBuilder.() -> Unit = {}): String {
        val body: JsonObject = buildJsonObject(params)
        return client.postgrest.rpc(function, body).data
    }

    private suspend fun <T> rpc(
        function: String,
        serializer: KSerializer<T>,
        params: JsonObjectBuilder.() -> Unit = {},
    ): T = json.decodeFromString(serializer, rpcRaw(function, params))

    /** The server raises errors like 'INSUFFICIENT_COINS'; this returns that code (or null). */
    private fun serverCode(e: Exception): String? =
        (e as? RestException)?.error?.trim()?.takeIf { it.isNotEmpty() && it == it.uppercase() && ' ' !in it }

    private fun authMessage(e: Exception): String {
        if (e is IllegalStateException && e.message != null) return e.message!!
        val code = (e as? AuthRestException)?.errorCode
        return when (code) {
            AuthErrorCode.InvalidCredentials -> "Wrong username or password."
            AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "That username is taken. Try another one."
            AuthErrorCode.WeakPassword -> "Choose a stronger password (at least 6 characters)."
            AuthErrorCode.EmailNotConfirmed ->
                "Email confirmation is on. In Supabase turn off Authentication > Sign In / Providers > Email > Confirm email."
            AuthErrorCode.PhoneProviderDisabled, AuthErrorCode.SmsSendFailed ->
                "Mobile number sign-in isn't set up yet (it needs an SMS provider in Supabase)."
            AuthErrorCode.OtpExpired -> "That code has expired. Send a new one."
            AuthErrorCode.ProviderDisabled -> "This sign-in option isn't switched on yet."
            AuthErrorCode.AnonymousProviderDisabled ->
                "Guest sign-in is turned off. In Supabase, enable Auth > Sign In / Providers > Allow anonymous sign-ins."
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverSmsSendRateLimit -> "Too many tries. Please wait a minute."
            else -> friendlyMessage(e)
        }
    }

    private fun friendlyMessage(e: Exception): String = when (serverCode(e)) {
        "INSUFFICIENT_COINS" -> "Not enough coins. Get more in the Store or Rewards."
        "NOT_ENOUGH_GEMS" -> "Not enough gems for this chest."
        "CHEST_NOT_READY" -> "This chest isn't ready yet."
        "UNKNOWN_CHEST" -> "This chest isn't available."
        "SPIN_NOT_READY" -> "Your next spin is ready tomorrow."
        "ALREADY_CLAIMED" -> "Already claimed. Come back later!"
        "CHALLENGE_NOT_COMPLETE" -> "Finish the challenge first."
        "UNKNOWN_CHALLENGE" -> "This challenge isn't available."
        "INVALID_AVATAR" -> "Pick one of the avatars."
        "TOURNAMENT_NOT_FOUND" -> "This tournament isn't available right now."
        "TICKET_ALREADY_USED" -> "This match was already submitted."
        "TICKET_NOT_FOUND" -> "We couldn't find this match."
        "INVALID_SCORE" -> "That score couldn't be accepted."
        "INVALID_NAME" -> "Name must be 2 to 20 characters."
        "PURCHASES_DISABLED" -> "The store is closed right now."
        "PACK_NOT_FOUND" -> "This pack isn't available."
        "NOT_AUTHENTICATED" -> "You're signed out. Please sign in again."
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
    val gems: Long = 0,
    val transactions: List<TransactionDto> = emptyList(),
)

@Serializable
private data class TransactionDto(
    val id: String,
    val amount: Long,
    val description: String,
    val gems: Long = 0,
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
                avatarId = it.avatarId,
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
    @SerialName("avatar_id") val avatarId: Int = 1,
)

@Serializable
private data class ProfileDto(
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: Int = 1,
    val xp: Long = 0,
    val trophies: Long = 0,
    @SerialName("welcome_pending") val welcomePending: Boolean = false,
    @SerialName("welcome_coins") val welcomeCoins: Long = 1_000,
    @SerialName("unread_notifications") val unreadNotifications: Int = 0,
    val stats: StatsDto = StatsDto(),
)

@Serializable
private data class StatsDto(
    @SerialName("tournaments_played") val tournamentsPlayed: Int = 0,
    @SerialName("matches_played") val matchesPlayed: Int = 0,
    @SerialName("games_won") val gamesWon: Int = 0,
    @SerialName("win_rate") val winRate: Int = 0,
    @SerialName("coins_won") val coinsWon: Long = 0,
    @SerialName("win_streak") val winStreak: Int = 0,
    @SerialName("days_active") val daysActive: Int = 1,
)

@Serializable
private data class GrantDto(val coins: Long = 0, val gems: Long = 0)

@Serializable
private data class SpinDto(val segment: Int = 0, val coins: Long = 0, val gems: Long = 0)

@Serializable
private data class DailyClaimDto(val day: Int = 0, val coins: Long = 0, val gems: Long = 0, val chest: String? = null)

@Serializable
private data class ChallengesDto(
    @SerialName("daily_resets_in_ms") val dailyResetsInMs: Long = 0,
    @SerialName("weekly_resets_in_ms") val weeklyResetsInMs: Long = 0,
    val challenges: List<ChallengeDto> = emptyList(),
)

@Serializable
private data class ChallengeDto(
    val id: String,
    val period: String,
    val category: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("game_id") val gameId: String? = null,
    val target: Long,
    val progress: Long,
    @SerialName("reward_coins") val rewardCoins: Long = 0,
    @SerialName("reward_gems") val rewardGems: Long = 0,
    val claimed: Boolean = false,
)

@Serializable
private data class LeagueDto(
    val trophies: Long = 0,
    @SerialName("season_points") val seasonPoints: Long = 0,
    @SerialName("season_ends_in_ms") val seasonEndsInMs: Long = 0,
    val top: List<LeagueRowDto> = emptyList(),
    val me: LeagueMeDto? = null,
)

@Serializable
private data class LeagueRowDto(
    val rank: Int,
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: Int = 1,
    val points: Long,
    val tier: String? = null,
)

@Serializable
private data class LeagueMeDto(val rank: Int? = null, val points: Long = 0)

@Serializable
private data class DailyBonusDto(
    val day: Int = 1,
    @SerialName("claimed_today") val claimedToday: Boolean = false,
    @SerialName("resets_in_ms") val resetsInMs: Long = 0,
    val rewards: List<DailyBonusDayDto> = emptyList(),
)

@Serializable
private data class DailyBonusDayDto(val day: Int, val coins: Long = 0, val gems: Long = 0, val chest: String? = null)

@Serializable
private data class ShopDto(
    @SerialName("free_chest_ready_in_ms") val freeChestReadyInMs: Long = 0,
    @SerialName("spin_ready_in_ms") val spinReadyInMs: Long = 0,
    @SerialName("free_coins_amount") val freeCoinsAmount: Long = 50,
    @SerialName("free_coins_left_today") val freeCoinsLeftToday: Int = 0,
    val chests: List<ChestDto> = emptyList(),
    @SerialName("spin_segments") val spinSegments: List<GrantDto> = emptyList(),
)

@Serializable
private data class ChestDto(
    val id: String,
    val name: String,
    @SerialName("gem_cost") val gemCost: Long,
    val description: String = "",
)

@Serializable
private data class NotificationDto(
    val id: String,
    val title: String,
    val body: String? = null,
    val read: Boolean = false,
)
