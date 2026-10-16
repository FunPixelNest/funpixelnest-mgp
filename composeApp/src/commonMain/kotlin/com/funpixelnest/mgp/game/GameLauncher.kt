package com.funpixelnest.mgp.game

import com.funpixelnest.mgp.domain.MatchTicket

/** Everything Unity needs to start a match. Sent native → Unity when Play is tapped. */
data class GameLaunchRequest(
    val gameId: String,
    val ticket: MatchTicket,
    val playerId: String,
    /** False for free practice rounds (no ticket is consumed). */
    val isTournament: Boolean = true,
)

/** Sent back Unity → native when the match ends. */
data class GameResult(
    val score: Long,
    val durationMillis: Long,
    /** Move/timestamp log for replays and anti-cheat review on the server. */
    val eventLog: List<String> = emptyList(),
)

/**
 * The single seam between the platform app and the games.
 *
 * - Android: will open Unity as a Library (separate process) — see docs/UNITY_INTEGRATION.md.
 * - iOS: will present the UnityFramework view controller.
 * - Web: will load the Unity WebGL build.
 *
 * Until Unity is integrated, the app shows an in-app placeholder game screen instead
 * (App is called with gameLauncher = null).
 */
interface GameLauncher {
    /** Runs the game and suspends until it ends. Returns null if the player quit. */
    suspend fun launch(request: GameLaunchRequest): GameResult?
}
