package com.funpixelnest.mgp.data

import com.funpixelnest.mgp.domain.DailyReward
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.Leaderboard
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
 * every pool contains only real players.
 */
class FakePlatformRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val random: Random = Random.Default,
    /** Multiplier for simulated waiting times. 0 = instant (useful in tests). */
    private val simulationSpeed: Double = 1.0,
) : PlatformRepository {

    override val player = Player(id = "me", displayName = "You")

    private val mutex = Mutex()
    private var idCounter = 0L
    private var submitSequence = 0L
    private val usedTickets = mutableSetOf<String>()

    /** Insertion-ordered so "my pools" can be shown newest first. */
    private val pools = MutableStateFlow<Map<String, Pool>>(emptyMap())

    private val _wallet = MutableStateFlow(
        Wallet(
            coins = 1_000,
            transactions = listOf(WalletTransaction("tx_0", 1_000, "Welcome bonus")),
        ),
    )
    override val wallet: StateFlow<Wallet> = _wallet.asStateFlow()

    private val _myPools = MutableStateFlow<List<Pool>>(emptyList())
    override val myPools: StateFlow<List<Pool>> = _myPools.asStateFlow()

    // Rewards state (in production the server tracks dates, streak resets and limits).
    private var dailyDay = 1
    private var dailyClaimedToday = false
    private var freeCoinsLeft = 3
    private val claimedMissions = mutableSetOf<String>()

    private val _rewards = MutableStateFlow(buildRewards())
    override val rewards: StateFlow<RewardsState> = _rewards.asStateFlow()

    override fun games(): List<Game> = SampleCatalog.games
    override fun game(gameId: String): Game? = SampleCatalog.games.find { it.id == gameId }
    override fun tournamentsFor(gameId: String): List<Tournament> =
        SampleCatalog.tournaments.filter { it.gameId == gameId }

    override fun tournament(tournamentId: String): Tournament? =
        SampleCatalog.tournaments.find { it.id == tournamentId }

    override suspend fun joinTournament(tournamentId: String): JoinResult {
        val tournament = tournament(tournamentId) ?: return JoinResult.Error("Tournament not found")

        val ticket = mutex.withLock {
            if (_wallet.value.coins < tournament.entryFee) return JoinResult.InsufficientCoins
            addTransaction(-tournament.entryFee, "Entry: ${gameName(tournament)} ${tournament.title}")

            val pool = Pool(
                id = nextId("pool"),
                tournamentId = tournament.id,
                seed = random.nextLong(),
                entries = listOf(PoolEntry(player.id, player.displayName)),
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
        recordScore(ticket.poolId, player.id, result.score, result.durationMillis)
    }

    override fun observePool(poolId: String): Flow<Pool?> =
        pools.map { it[poolId] }.distinctUntilChanged()

    // ---------------------------------------------------------------- featured

    override fun featuredTournaments(): List<Tournament> =
        SampleCatalog.featuredTournamentIds.mapNotNull { tournament(it) }

    override fun newGames(): List<Game> = SampleCatalog.newGameIds.mapNotNull { game(it) }

    // ---------------------------------------------------------------- rewards

    override suspend fun claimDailyReward(): Long = mutex.withLock {
        if (dailyClaimedToday) return 0L
        val coins = _rewards.value.daily.todayCoins
        addTransaction(coins, "Daily reward · Day $dailyDay")
        dailyClaimedToday = true
        refreshRewards()
        coins
    }

    override suspend fun claimFreeCoins(): Long = mutex.withLock {
        if (freeCoinsLeft <= 0) return 0L
        freeCoinsLeft--
        addTransaction(FREE_COINS, "Free coins (video)")
        refreshRewards()
        FREE_COINS
    }

    override suspend fun claimMission(missionId: String): Long = mutex.withLock {
        val mission = _rewards.value.missions.find { it.id == missionId }
        if (mission == null || !mission.canClaim) return 0L
        claimedMissions += missionId
        addTransaction(mission.rewardCoins, "Mission: ${mission.title}")
        refreshRewards()
        mission.rewardCoins
    }

    // ---------------------------------------------------------------- store

    override fun storePacks(): List<StorePack> = SampleCatalog.storePacks

    override suspend fun purchasePack(packId: String): PurchaseResult = mutex.withLock {
        val pack = SampleCatalog.storePacks.find { it.id == packId }
            ?: return PurchaseResult.Error("This pack isn't available.")
        // DEMO: no payment is taken. The real flow goes through Google Play / App Store billing.
        addTransaction(pack.totalCoins, "Store: ${pack.name} (demo, no charge)")
        PurchaseResult.Success(pack.totalCoins)
    }

    private fun buildRewards(): RewardsState {
        val mine = pools.value.values.filter { p -> p.entries.any { it.playerId == player.id } }
        val myRows = mine
            .filter { it.status == PoolStatus.COMPLETED }
            .mapNotNull { pool ->
                val t = tournament(pool.tournamentId) ?: return@mapNotNull null
                Leaderboard.rank(pool, t).find { it.entry.playerId == player.id }
            }
        val distinctGames = mine.mapNotNull { tournament(it.tournamentId)?.gameId }.distinct().size

        fun mission(id: String, title: String, target: Int, progress: Int, reward: Long) =
            Mission(id, title, target, progress.coerceAtMost(target), reward, claimed = id in claimedMissions)

        return RewardsState(
            daily = DailyReward(day = dailyDay, claimedToday = dailyClaimedToday),
            freeCoinsAmount = FREE_COINS,
            freeCoinsLeftToday = freeCoinsLeft,
            missions = listOf(
                mission("play_3", "Play 3 tournaments", 3, mine.size, 100),
                mission("variety_3", "Play 3 different games", 3, distinctGames, 200),
                mission("top_3", "Finish in the top 3", 1, myRows.count { it.prize > 0 }, 150),
                mission("win_1", "Win a tournament", 1, myRows.count { it.rank == 1 }, 250),
            ),
        )
    }

    private fun refreshRewards() {
        _rewards.value = buildRewards()
    }

    // ---------------------------------------------------------------- simulation

    private fun simulateOtherPlayers(poolId: String, tournament: Tournament) {
        val names = DEMO_NAMES.shuffled(random).take(tournament.poolSize - 1)
        names.forEachIndexed { index, name ->
            scope.launch {
                wait(random.nextLong(800, 2_500) * (index + 1))
                val demoId = "demo_${poolId}_$index"
                mutex.withLock { addEntry(poolId, PoolEntry(demoId, "$name (Demo)")) }

                wait(random.nextLong(3_000, 9_000))
                mutex.withLock {
                    recordScore(
                        poolId = poolId,
                        playerId = demoId,
                        score = random.nextLong(1_000, 10_000),
                        durationMillis = random.nextLong(60_000, 240_000),
                    )
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

        if (pool.status != PoolStatus.COMPLETED && updated.status == PoolStatus.COMPLETED) {
            payOut(updated, tournament)
        }
        return updated
    }

    private fun payOut(pool: Pool, tournament: Tournament) {
        val myRow = Leaderboard.rank(pool, tournament).find { it.entry.playerId == player.id } ?: return
        if (myRow.prize > 0) {
            addTransaction(myRow.prize, "Prize: #${myRow.rank} in ${gameName(tournament)} ${tournament.title}")
        }
    }

    private fun statusFor(entries: List<PoolEntry>, poolSize: Int): PoolStatus = when {
        entries.size < poolSize -> PoolStatus.OPEN
        entries.all { it.hasSubmitted } -> PoolStatus.COMPLETED
        else -> PoolStatus.FULL
    }

    private fun putPool(pool: Pool) {
        val updated = pools.value + (pool.id to pool)
        pools.value = updated
        _myPools.value = updated.values
            .filter { p -> p.entries.any { it.playerId == player.id } }
            .reversed()
        refreshRewards()
    }

    private fun addTransaction(amount: Long, description: String) {
        val current = _wallet.value
        _wallet.value = current.copy(
            coins = current.coins + amount,
            transactions = listOf(WalletTransaction(nextId("tx"), amount, description)) + current.transactions,
        )
    }

    private fun nextId(prefix: String) = "${prefix}_${++idCounter}"

    private fun gameName(tournament: Tournament) = game(tournament.gameId)?.name ?: tournament.gameId

    private companion object {
        const val FREE_COINS = 50L
        val DEMO_NAMES = listOf(
            "Aarav", "Maya", "Leo", "Sofia", "Kabir", "Zoe", "Ishaan", "Emma", "Rohan", "Nora",
        )
    }
}
