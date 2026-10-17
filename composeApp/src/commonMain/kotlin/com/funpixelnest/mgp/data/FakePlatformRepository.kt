package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.AVATAR_COUNT
import com.funpixelnest.mgp.domain.Challenge
import com.funpixelnest.mgp.domain.ChallengeCategory
import com.funpixelnest.mgp.domain.ChallengePeriod
import com.funpixelnest.mgp.domain.ChallengesState
import com.funpixelnest.mgp.domain.ClaimResult
import com.funpixelnest.mgp.domain.DailyBonusState
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.InboxMessage
import com.funpixelnest.mgp.domain.LeaguePoints
import com.funpixelnest.mgp.domain.LeagueRow
import com.funpixelnest.mgp.domain.LeagueState
import com.funpixelnest.mgp.domain.LeagueTier
import com.funpixelnest.mgp.domain.Leaderboard
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * In-memory backend for building and testing the UI without a server.
 *
 * DEVELOPMENT ONLY: to show how a pool fills up, it simulates other players
 * ("Demo" names) joining and submitting. The real backend never does this —
 * every pool contains only real players. Every sign-in method works instantly here.
 */
class FakePlatformRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val random: Random = Random.Default,
    /** Multiplier for simulated waiting times. 0 = instant (useful in tests). */
    private val simulationSpeed: Double = 1.0,
    /** Tests: skip the login screen. */
    startSignedIn: Boolean = false,
) : PlatformRepository {

    private val _status = MutableStateFlow<BackendStatus>(if (startSignedIn) BackendStatus.Ready else BackendStatus.Loading)
    override val status: StateFlow<BackendStatus> = _status.asStateFlow()

    override suspend fun initialize() {
        if (_status.value == BackendStatus.Ready) return
        wait(900) // let the splash screen show
        _status.value = if (signedIn) BackendStatus.Ready else BackendStatus.SignedOut
    }

    // ---------------------------------------------------------------- sign-in (demo: always succeeds)

    private var signedIn = startSignedIn
    override val loginMethods: List<LoginMethod> = LoginMethod.entries.toList()
    override var signedInWith: LoginMethod? = if (startSignedIn) LoginMethod.GUEST else null
        private set

    private fun completeSignIn(method: LoginMethod, name: String?) {
        signedIn = true
        signedInWith = method
        if (name != null) _player = _player.copy(displayName = name)
        _status.value = BackendStatus.Ready
    }

    override suspend fun signInAsGuest(): String? {
        wait(400)
        completeSignIn(LoginMethod.GUEST, "Guest${random.nextInt(1000, 9999)}")
        return null
    }

    override suspend fun signUp(username: String, password: String): String? {
        Usernames.validate(username)?.let { return it }
        Usernames.validatePassword(password)?.let { return it }
        wait(400)
        completeSignIn(LoginMethod.USERNAME, username.trim())
        return null
    }

    override suspend fun signIn(username: String, password: String): String? = signUp(username, password)

    override suspend fun signInWithProvider(method: LoginMethod): String? {
        wait(600)
        completeSignIn(method, "${method.label} Player")
        return null
    }

    override suspend fun sendPhoneCode(phone: String): String? =
        if (phone.filter { it.isDigit() }.length < 10) "Enter a valid mobile number." else null

    override suspend fun verifyPhoneCode(phone: String, code: String): String? {
        if (code.length != 6) return "Enter the 6-digit code."
        completeSignIn(LoginMethod.PHONE, "Player${phone.takeLast(4)}")
        return null
    }

    override suspend fun signOut() {
        signedIn = false
        signedInWith = null
        _status.value = BackendStatus.SignedOut
    }

    // ---------------------------------------------------------------- player

    private var _player = Player(id = "me", displayName = "You")
    override val player: Player get() = _player

    private val _profile = MutableStateFlow(PlayerProfile(welcomePending = true, unreadNotifications = 1))
    override val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    private val mutex = Mutex()
    private var idCounter = 0L
    private var submitSequence = 0L
    private val usedTickets = mutableSetOf<String>()

    /** Insertion-ordered so "my pools" can be shown newest first. */
    private val pools = MutableStateFlow<Map<String, Pool>>(emptyMap())

    private val _wallet = MutableStateFlow(
        Wallet(
            coins = 1_000,
            gems = 25,
            transactions = listOf(WalletTransaction("tx_0", 1_000, "Welcome bonus", gems = 25)),
        ),
    )
    override val wallet: StateFlow<Wallet> = _wallet.asStateFlow()

    private val _myPools = MutableStateFlow<List<Pool>>(emptyList())
    override val myPools: StateFlow<List<Pool>> = _myPools.asStateFlow()

    override suspend fun setDisplayName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.length !in 2..20) return "Name must be 2 to 20 characters."
        mutex.withLock {
            _player = _player.copy(displayName = trimmed)
            pools.value.values.forEach { pool ->
                putPool(pool.copy(entries = pool.entries.map { if (it.playerId == player.id) it.copy(displayName = trimmed) else it }))
            }
            rebuildLeague()
        }
        return null
    }

    override suspend fun setAvatar(avatarId: Int): String? {
        if (avatarId !in 1..AVATAR_COUNT) return "Pick one of the avatars."
        mutex.withLock {
            _profile.value = _profile.value.copy(avatarId = avatarId)
            pools.value.values.forEach { pool ->
                putPool(pool.copy(entries = pool.entries.map { if (it.playerId == player.id) it.copy(avatarId = avatarId) else it }))
            }
            rebuildLeague()
        }
        return null
    }

    override suspend fun acknowledgeWelcome() {
        _profile.value = _profile.value.copy(welcomePending = false)
    }

    // ---------------------------------------------------------------- games & tournaments

    override fun games(): List<Game> = SampleCatalog.games
    override fun game(gameId: String): Game? = SampleCatalog.games.find { it.id == gameId }
    override fun tournamentsFor(gameId: String): List<Tournament> = SampleCatalog.tournaments.filter { it.gameId == gameId }
    override fun tournament(tournamentId: String): Tournament? = SampleCatalog.tournaments.find { it.id == tournamentId }

    override suspend fun joinTournament(tournamentId: String): JoinResult {
        val tournament = tournament(tournamentId) ?: return JoinResult.Error("Tournament not found")
        val ticket = mutex.withLock {
            if (_wallet.value.coins < tournament.entryFee) return JoinResult.InsufficientCoins
            addTransaction(-tournament.entryFee, 0, "Entry: ${label(tournament)}")
            val pool = Pool(
                id = nextId("pool"),
                tournamentId = tournament.id,
                seed = random.nextLong(),
                entries = listOf(PoolEntry(player.id, player.displayName, avatarId = _profile.value.avatarId)),
                status = PoolStatus.OPEN,
            )
            putPool(pool)
            MatchTicket(nextId("ticket"), pool.id, tournament.id, tournament.gameId, pool.seed)
        }
        simulateOtherPlayers(ticket.poolId, tournament)
        return JoinResult.Joined(ticket)
    }

    override suspend fun submitScore(ticket: MatchTicket, result: GameResult): Pool = mutex.withLock {
        check(usedTickets.add(ticket.ticketId)) { "This ticket has already been used" }
        val pool = recordScore(ticket.poolId, player.id, result.score, result.durationMillis)
        rebuildProgress()
        pool
    }

    override fun observePool(poolId: String): Flow<Pool?> = pools.map { it[poolId] }.distinctUntilChanged()

    // ---------------------------------------------------------------- challenges

    private val claimedChallenges = mutableSetOf<String>()
    private val _challenges = MutableStateFlow(ChallengesState())
    override val challenges: StateFlow<ChallengesState> = _challenges.asStateFlow()

    override suspend fun claimChallenge(challengeId: String): ClaimResult = mutex.withLock {
        val challenge = _challenges.value.challenges.find { it.id == challengeId }
            ?: return ClaimResult.Failed("This challenge isn't available.")
        if (challenge.claimed) return ClaimResult.Failed("Already claimed.")
        if (!challenge.isComplete) return ClaimResult.Failed("Finish the challenge first.")
        claimedChallenges += challengeId
        addTransaction(challenge.reward.coins, challenge.reward.gems, "Challenge: ${challenge.title}")
        rebuildProgress()
        ClaimResult.Granted(challenge.reward, challenge.title)
    }

    // ---------------------------------------------------------------- league

    private var trophies = 0L
    private var seasonPoints = 0L
    private val promotedTiers = mutableSetOf<LeagueTier>()
    private val demoLeague = DEMO_NAMES.mapIndexed { i, name ->
        Triple("league_demo_$i", "$name (Demo)", (i % AVATAR_COUNT) + 2) to (5_600L - i * 520L)
    }
    private val _league = MutableStateFlow(LeagueState())
    override val league: StateFlow<LeagueState> = _league.asStateFlow()

    // ---------------------------------------------------------------- daily bonus & shop

    private var bonusDay = 1
    private var bonusClaimedToday = false
    private val _dailyBonus = MutableStateFlow(DailyBonusState())
    override val dailyBonus: StateFlow<DailyBonusState> = _dailyBonus.asStateFlow()

    private var freeChestReady = true
    private var spinReady = true
    private var freeCoinsLeft = 3
    private val _shop = MutableStateFlow(ShopState())
    override val shop: StateFlow<ShopState> = _shop.asStateFlow()

    override suspend fun claimDailyBonus(): ClaimResult = mutex.withLock {
        if (bonusClaimedToday) return ClaimResult.Failed("Come back tomorrow for your next bonus.")
        val day = _dailyBonus.value.rewards[bonusDay - 1]
        val grant = if (day.chest != null) chestContents("epic") else RewardGrant(day.coins, day.gems)
        addTransaction(grant.coins, grant.gems, "Daily bonus: day $bonusDay")
        bonusClaimedToday = true
        rebuildProgress()
        ClaimResult.Granted(grant, if (day.chest != null) "Epic Chest" else "Day $bonusDay bonus")
    }

    override suspend fun openChest(chestId: String): ClaimResult = mutex.withLock {
        if (chestId == "free") {
            if (!freeChestReady) return ClaimResult.Failed("The free chest isn't ready yet.")
            freeChestReady = false
            val grant = chestContents("free")
            addTransaction(grant.coins, grant.gems, "Free chest")
            rebuildProgress()
            return ClaimResult.Granted(grant, "Free Chest")
        }
        val offer = _shop.value.chests.find { it.id == chestId } ?: return ClaimResult.Failed("This chest isn't available.")
        if (_wallet.value.gems < offer.gemCost) return ClaimResult.Failed("Not enough gems. You need ${offer.gemCost}.")
        val grant = chestContents(chestId)
        addTransaction(0, -offer.gemCost, "Opened ${offer.name}")
        addTransaction(grant.coins, grant.gems, "${offer.name} rewards")
        rebuildProgress()
        ClaimResult.Granted(grant, offer.name)
    }

    override suspend fun spinWheel(): Result<SpinResult> = mutex.withLock {
        if (!spinReady) return Result.failure(IllegalStateException("Your next spin is ready tomorrow."))
        spinReady = false
        val weights = listOf(20, 10, 18, 20, 12, 6, 4, 10)
        var roll = random.nextInt(weights.sum())
        val index = weights.indexOfFirst { w -> (roll < w).also { roll -= w } }
        val grant = _shop.value.spinSegments[index]
        addTransaction(grant.coins, grant.gems, "Lucky spin")
        rebuildProgress()
        Result.success(SpinResult(index, grant))
    }

    override suspend fun claimFreeCoins(): Long = mutex.withLock {
        if (freeCoinsLeft <= 0) return 0L
        freeCoinsLeft--
        addTransaction(FREE_COINS, 0, "Free coins (video)")
        rebuildProgress()
        FREE_COINS
    }

    override suspend fun refreshProgress() = mutex.withLock { rebuildProgress() }

    private fun chestContents(id: String): RewardGrant = when (id) {
        "free" -> RewardGrant(random.nextLong(20, 81))
        "wooden" -> RewardGrant(random.nextLong(50, 151))
        "silver" -> RewardGrant(random.nextLong(150, 401), random.nextLong(1, 6))
        "gold" -> RewardGrant(random.nextLong(400, 1_001), random.nextLong(5, 16))
        "diamond" -> RewardGrant(random.nextLong(1_000, 2_501), random.nextLong(15, 41))
        else -> RewardGrant(random.nextLong(800, 2_001), random.nextLong(10, 31)) // epic
    }

    // ---------------------------------------------------------------- store

    override fun storePacks(): List<StorePack> = SampleCatalog.storePacks

    override suspend fun purchasePack(packId: String): PurchaseResult = mutex.withLock {
        val pack = SampleCatalog.storePacks.find { it.id == packId } ?: return PurchaseResult.Error("This pack isn't available.")
        // DEMO: no payment is taken. The real flow goes through Google Play / App Store billing.
        addTransaction(pack.totalCoins, 0, "Store: ${pack.name} (demo, no charge)")
        PurchaseResult.Success(pack.totalCoins)
    }

    // ---------------------------------------------------------------- inbox

    private val _inbox = MutableStateFlow(
        listOf(InboxMessage("n1", "Welcome to FunPixelNest!", "Play tournaments, finish in the top 3 and win coins. Good luck!", read = false)),
    )
    override val inbox: StateFlow<List<InboxMessage>> = _inbox.asStateFlow()
    override suspend fun refreshInbox() = Unit
    override suspend fun markInboxRead() {
        _inbox.value = _inbox.value.map { it.copy(read = true) }
        _profile.value = _profile.value.copy(unreadNotifications = 0)
    }

    private fun notify(title: String, body: String) {
        _inbox.value = listOf(InboxMessage(nextId("n"), title, body, read = false)) + _inbox.value
        _profile.value = _profile.value.copy(unreadNotifications = _inbox.value.count { !it.read })
    }

    override suspend fun registerPushToken(token: String, platform: String) = Unit

    // ---------------------------------------------------------------- derived state (call under mutex)

    init {
        rebuildProgress()
    }

    private data class MyResult(val tournament: Tournament, val pool: Pool, val rank: Int?, val prize: Long, val score: Long?)

    private fun myResults(): List<MyResult> = _myPools.value.mapNotNull { pool ->
        val t = tournament(pool.tournamentId) ?: return@mapNotNull null
        val row = Leaderboard.rank(pool, t).find { it.entry.playerId == player.id } ?: return@mapNotNull null
        MyResult(t, pool, if (pool.status == PoolStatus.COMPLETED) row.rank else null, row.prize, row.entry.score)
    }

    private fun rebuildProgress() {
        val results = myResults()
        val completed = results.filter { it.pool.status == PoolStatus.COMPLETED }
        val wins = completed.count { it.rank == 1 }
        var streak = 0
        for (r in completed) if (r.rank == 1) streak++ else break // newest first
        _profile.value = _profile.value.copy(
            trophies = trophies,
            stats = PlayerStats(
                tournamentsPlayed = results.count { !it.tournament.isDuel },
                matchesPlayed = results.size,
                gamesWon = wins,
                winRate = if (completed.isEmpty()) 0 else wins * 100 / completed.size,
                coinsWon = completed.sumOf { it.prize },
                winStreak = streak,
                daysActive = 1,
            ),
        )
        rebuildChallenges(results)
        rebuildLeague()
        _dailyBonus.value = DailyBonusState(day = bonusDay, claimedToday = bonusClaimedToday, resetsInMillis = DEMO_DAY_LEFT)
        _shop.value = ShopState(
            freeChestReadyInMillis = if (freeChestReady) 0 else 4 * 3_600_000L,
            spinReadyInMillis = if (spinReady) 0 else DEMO_DAY_LEFT,
            freeCoinsAmount = FREE_COINS,
            freeCoinsLeftToday = freeCoinsLeft,
        )
    }

    private fun rebuildChallenges(results: List<MyResult>) {
        val completed = results.filter { it.pool.status == PoolStatus.COMPLETED }
        val pools = results.filter { !it.tournament.isDuel }
        val scores = results.sumOf { it.score ?: 0 }
        val inGame = { id: String -> results.count { it.tournament.gameId == id && it.score != null }.toLong() }
        val myRank = _league.value.myRank
        val weeklyClaimed = claimedChallenges.count { it.startsWith("w_") || it.startsWith("m_") || it.startsWith("d_") }.toLong()
        val specialIds = listOf("w_tourn_3", "w_top3_1", "w_win_1", "w_lp_1000", "w_top10", "w_promote")

        fun c(id: String, period: ChallengePeriod, cat: ChallengeCategory, title: String, sub: String, gameId: String?, target: Long, progress: Long, coins: Long, gems: Long = 0) =
            Challenge(id, period, cat, title, sub, gameId, target, progress.coerceAtMost(target), RewardGrant(coins, gems), id in claimedChallenges)

        val d = ChallengePeriod.DAILY
        val w = ChallengePeriod.WEEKLY
        _challenges.value = ChallengesState(
            dailyResetsInMillis = DEMO_DAY_LEFT,
            weeklyResetsInMillis = DEMO_WEEK_LEFT,
            challenges = listOf(
                c("d_play_3", d, ChallengeCategory.DAILY, "Play 3 Games", "Play any 3 games", null, 3, results.size.toLong(), 100),
                c("d_win_1", d, ChallengeCategory.DAILY, "Win a Tournament", "Finish #1 in any tournament", null, 1, completed.count { it.rank == 1 }.toLong(), 250),
                c("d_score_5000", d, ChallengeCategory.DAILY, "Score 5,000 Points", "Score total 5,000 points in any game", null, 5_000, scores, 150),
                c("w_bingo_3", w, ChallengeCategory.GAME, "Complete 3 Bingo Flash Games", "Play Bingo Flash", "bingo", 3, inGame("bingo"), 200),
                c("w_block_5", w, ChallengeCategory.GAME, "Play 5 Block Puzzle Games", "Play Block Puzzle", "block_puzzle", 5, inGame("block_puzzle"), 150),
                c("w_arrows_5", w, ChallengeCategory.GAME, "Play 5 Arrows Games", "Play Arrows", "arrows", 5, inGame("arrows"), 200),
                c("w_tourn_3", w, ChallengeCategory.TOURNAMENT, "Play 3 Tournaments", "5-player tournaments", null, 3, pools.size.toLong(), 200),
                c("w_top3_1", w, ChallengeCategory.TOURNAMENT, "Finish in Top 3", "In a 5-player tournament", null, 1, completed.count { !it.tournament.isDuel && it.prize > 0 }.toLong(), 250),
                c("w_win_1", w, ChallengeCategory.TOURNAMENT, "Win 1 Tournament", "Finish #1", null, 1, completed.count { it.rank == 1 }.toLong(), 300),
                c("w_lp_1000", w, ChallengeCategory.LEAGUE, "Earn 1,000 League Points", "This season", null, 1_000, seasonPoints, 200),
                c("w_top10", w, ChallengeCategory.LEAGUE, "Reach Top 10", "In the league leaderboard", null, 1, if (myRank != null && myRank <= 10) 1 else 0, 300),
                c("w_promote", w, ChallengeCategory.LEAGUE, "Get Promoted", "Reach the next league", null, 1, promotedTiers.size.toLong(), 500),
                c("m_play_10", w, ChallengeCategory.MILESTONE, "Play 10 Games", "This week", null, 10, results.size.toLong(), 1_000),
                c("m_win_3", w, ChallengeCategory.MILESTONE, "Win 3 Tournaments", "This week", null, 3, completed.count { it.rank == 1 }.toLong(), 0, 20),
                c("m_score_20000", w, ChallengeCategory.MILESTONE, "Score 20,000 Points", "This week", null, 20_000, scores, 1_500),
                c("m_challenges_5", w, ChallengeCategory.MILESTONE, "Complete 5 Challenges", "This week", null, 5, weeklyClaimed, 0, 15),
                c("s_weekly_champ", w, ChallengeCategory.SPECIAL, "Weekly Champion", "Complete all tournament & league challenges", null, 6, specialIds.count { it in claimedChallenges }.toLong(), 500, 50),
            ),
        )
    }

    private fun rebuildLeague() {
        val rows = (demoLeague.map { (who, pts) -> Triple(who, pts, false) } +
            listOf(Triple(Triple(player.id, player.displayName, _profile.value.avatarId), seasonPoints, true)))
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val myIndex = rows.indexOfFirst { it.third }
        _league.value = LeagueState(
            trophies = trophies,
            seasonPoints = seasonPoints,
            seasonEndsInMillis = DEMO_WEEK_LEFT,
            top = rows.take(10).mapIndexed { i, (who, pts, _) ->
                LeagueRow(i + 1, who.first, who.second, who.third, pts, LeagueTier.forTrophies(pts))
            },
            myRank = if (myIndex >= 0) myIndex + 1 else null,
        )
    }

    // ---------------------------------------------------------------- simulation

    private fun simulateOtherPlayers(poolId: String, tournament: Tournament) {
        val names = DEMO_NAMES.shuffled(random).take(tournament.poolSize - 1)
        names.forEachIndexed { index, name ->
            scope.launch {
                wait(random.nextLong(800, 2_500) * (index + 1))
                val demoId = "demo_${poolId}_$index"
                mutex.withLock { addEntry(poolId, PoolEntry(demoId, "$name (Demo)", avatarId = random.nextInt(2, AVATAR_COUNT + 1))) }
                wait(random.nextLong(3_000, 9_000))
                mutex.withLock {
                    recordScore(poolId, demoId, random.nextLong(1_000, 10_000), random.nextLong(60_000, 240_000))
                    rebuildProgress()
                }
            }
        }
    }

    private suspend fun wait(millis: Long) {
        val scaled = (millis * simulationSpeed).toLong()
        if (scaled > 0) delay(scaled)
    }

    // ---------------------------------------------------------------- state changes (call under mutex)

    private fun addEntry(poolId: String, entry: PoolEntry) {
        val pool = pools.value[poolId] ?: return
        val tournament = tournament(pool.tournamentId) ?: return
        if (pool.entries.size >= tournament.poolSize) return
        val entries = pool.entries + entry
        putPool(pool.copy(entries = entries, status = statusFor(entries, tournament.poolSize)))
    }

    private fun recordScore(poolId: String, playerId: String, score: Long, durationMillis: Long): Pool {
        val pool = checkNotNull(pools.value[poolId]) { "Pool not found" }
        val tournament = checkNotNull(tournament(pool.tournamentId))
        val entries = pool.entries.map { entry ->
            if (entry.playerId == playerId && !entry.hasSubmitted) {
                entry.copy(score = score, durationMillis = durationMillis, submitSequence = ++submitSequence)
            } else {
                entry
            }
        }
        val updated = pool.copy(entries = entries, status = statusFor(entries, tournament.poolSize))
        putPool(updated)
        if (pool.status != PoolStatus.COMPLETED && updated.status == PoolStatus.COMPLETED) payOut(updated, tournament)
        return updated
    }

    private fun payOut(pool: Pool, tournament: Tournament) {
        val myRow = Leaderboard.rank(pool, tournament).find { it.entry.playerId == player.id } ?: return
        if (myRow.prize > 0) {
            val what = if (tournament.isDuel) "won ${label(tournament)}" else "#${myRow.rank} in ${label(tournament)}"
            addTransaction(myRow.prize, 0, "Prize: $what")
        }
        // XP and league points, same rules as the server.
        _profile.value = _profile.value.copy(xp = _profile.value.xp + LeaguePoints.xpForResult(myRow.rank, tournament.isDuel))
        val before = LeagueTier.forTrophies(trophies)
        val points = LeaguePoints.forResult(myRow.rank, tournament.isDuel)
        trophies += points
        seasonPoints += points
        LeagueTier.entries.filter { it > before && trophies >= it.minTrophies && it !in promotedTiers }.forEach { tier ->
            promotedTiers += tier
            addTransaction(tier.rewardCoins, tier.rewardGems, "League promotion: ${tier.label}")
            notify("Promoted to ${tier.label}!", "You reached the ${tier.label} league and earned a reward.")
        }
        if (myRow.prize > 0) notify("You won ${myRow.prize} coins!", "You finished #${myRow.rank} in ${label(tournament)}.")
    }

    private fun statusFor(entries: List<PoolEntry>, poolSize: Int): PoolStatus = when {
        entries.size < poolSize -> PoolStatus.OPEN
        entries.all { it.hasSubmitted } -> PoolStatus.COMPLETED
        else -> PoolStatus.FULL
    }

    private fun putPool(pool: Pool) {
        val updated = pools.value + (pool.id to pool)
        pools.value = updated
        _myPools.value = updated.values.filter { p -> p.entries.any { it.playerId == player.id } }.reversed()
    }

    private fun addTransaction(coins: Long, gems: Long, description: String) {
        if (coins == 0L && gems == 0L) return
        val current = _wallet.value
        _wallet.value = current.copy(
            coins = current.coins + coins,
            gems = current.gems + gems,
            transactions = listOf(WalletTransaction(nextId("tx"), coins, description, gems)) + current.transactions,
        )
    }

    private fun nextId(prefix: String) = "${prefix}_${++idCounter}"

    private fun gameName(tournament: Tournament) = game(tournament.gameId)?.name ?: tournament.gameId

    /** "Solitaire Pro" or "Solitaire 1v1 Quick" (same wording as the server). */
    private fun label(tournament: Tournament) =
        if (tournament.isDuel) "${gameName(tournament)} 1v1 ${tournament.title}" else "${gameName(tournament)} ${tournament.title}"

    private companion object {
        const val FREE_COINS = 50L
        const val DEMO_DAY_LEFT = 8 * 3_600_000L + 24 * 60_000L
        const val DEMO_WEEK_LEFT = 6 * 86_400_000L + DEMO_DAY_LEFT
        val DEMO_NAMES = listOf("Aarav", "Maya", "Leo", "Sofia", "Kabir", "Zoe", "Ishaan", "Emma", "Rohan", "Nora")
    }
}
