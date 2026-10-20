package com.funpixelnest.mgp.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * How many players are in each game right now, shown on the game cards.
 *
 * PLACEHOLDER: there are no real players yet, so each game gets a believable random count that drifts up
 * and down every few seconds. To show real numbers later, feed [counts] from the backend instead
 * (for example a Supabase Realtime presence channel per game, or a count of matches started in the
 * last few minutes) and remove [simulate].
 */
object LivePlayers {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    /** Runs until cancelled: gives every game a starting count, then nudges each one every 3–6 seconds. */
    suspend fun simulate(gameIds: List<String>) {
        if (gameIds.isEmpty()) return
        val random = Random.Default
        val current = _counts.value.toMutableMap()
        gameIds.forEach { id -> current.getOrPut(id) { startingCount(id, random) } }
        _counts.value = current.toMap()
        while (true) {
            delay(3_000L + random.nextLong(3_000L))
            val next = _counts.value.toMutableMap()
            gameIds.forEach { id ->
                val now = next[id] ?: startingCount(id, random)
                // Small change (about ±3%), a little more likely up than down, never below 40.
                val step = (now * 0.03).toInt().coerceAtLeast(2)
                next[id] = (now + random.nextInt(-step, step + 2)).coerceIn(40, 25_000)
            }
            _counts.value = next.toMap()
        }
    }

    /** A few hundred to a few thousand; popular games start higher. */
    private fun startingCount(id: String, random: Random): Int {
        val popular = id in setOf("block_puzzle", "solitaire", "bubble_shooter", "bingo", "mahjong")
        return if (popular) random.nextInt(1_800, 6_500) else random.nextInt(300, 2_400)
    }
}
