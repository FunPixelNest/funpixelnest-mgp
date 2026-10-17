package com.funpixelnest.mgp.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** What kind of match the game is being opened for. Sent to Unity as "practice" / "tournament" / "duel". */
enum class LaunchMode(val wireName: String) {
    PRACTICE("practice"),
    TOURNAMENT("tournament"),
    DUEL("duel"),
}

/** Everything Unity needs to start a match. Sent native → Unity when Play is tapped. */
data class GameLaunchRequest(
    val game: Game,
    val ticket: MatchTicket,
    val playerId: String,
    val displayName: String,
    val mode: LaunchMode,
    val settings: GameSettings = GameSettings(),
) {
    /**
     * The launch data the Unity bridge reads (`FpnPlatform.LaunchData`).
     * The seed is sent as a string because JavaScript numbers can't hold a full 64-bit value.
     */
    fun toLaunchJson(): String = launchJson.encodeToString(
        LaunchDataDto.serializer(),
        LaunchDataDto(
            gameId = game.id,
            gameName = game.name,
            mode = mode.wireName,
            seed = ticket.seed.toString(),
            ticketId = ticket.ticketId,
            playerId = playerId,
            displayName = displayName,
            music = settings.music,
            sound = settings.sound,
            vibration = settings.vibration,
        ),
    )
}

/** Player options from the pause menu / Settings, passed to the game. */
data class GameSettings(val music: Boolean = true, val sound: Boolean = true, val vibration: Boolean = true)

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
 * - Web: loads the game's Unity WebGL build over the app (see WebGlGameLauncher).
 * - Android / iOS: Unity as a Library, added during native integration.
 *
 * When a game isn't available on this platform yet, the app shows a placeholder game screen instead.
 */
interface GameLauncher {
    /** True when this game can be played for real on this platform right now. */
    fun isAvailable(gameId: String): Boolean

    /** Runs the game and suspends until it ends. Returns null if the player quit. */
    suspend fun launch(request: GameLaunchRequest): GameResult?

    /** The player pressed Back/Pause inside the running game: the app shows its pause menu. */
    fun setPauseRequestListener(listener: (() -> Unit)?) {}

    /** Freeze the running game and hide it so the app's pause menu is visible. */
    fun pause() {}

    /** Show the game again and continue. */
    fun resume() {}

    /** End the running game now (the pending [launch] returns null). */
    fun forfeit() {}

    /** Music / sound / vibration changed while the game runs. */
    fun applySettings(settings: GameSettings) {}

    /**
     * Where the running game is drawn inside the app (Android: a WebView). Platforms that draw the game
     * themselves on top of the app (web) leave this empty.
     */
    @Composable
    fun GameView(modifier: Modifier) {}
}

@Serializable
private data class LaunchDataDto(
    @SerialName("gameId") val gameId: String,
    @SerialName("gameName") val gameName: String = "",
    @SerialName("mode") val mode: String,
    @SerialName("seed") val seed: String,
    @SerialName("ticketId") val ticketId: String,
    @SerialName("playerId") val playerId: String,
    @SerialName("displayName") val displayName: String,
    @SerialName("music") val music: Boolean = true,
    @SerialName("sound") val sound: Boolean = true,
    @SerialName("vibration") val vibration: Boolean = true,
)

/** Settings JSON sent to a running game: {"music":true,"sound":true,"vibration":false}. */
fun GameSettings.toJson(): String = """{"music":$music,"sound":$sound,"vibration":$vibration}"""

/** Result payload the web host and native bridges send back: {"score":…, "durationMs":…, "eventLog":"[…]"}. */
@Serializable
private data class GameResultDto(
    val score: Double = 0.0,
    val durationMs: Double = 0.0,
    val eventLog: String? = null,
)

private val launchJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Parses a bridge result; empty / "quit" / invalid → null (player left without finishing). */
fun parseGameResult(raw: String?): GameResult? {
    if (raw.isNullOrBlank() || raw == "quit") return null
    return runCatching {
        val dto = launchJson.decodeFromString(GameResultDto.serializer(), raw)
        val events = dto.eventLog
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { launchJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }
            .orEmpty()
        GameResult(
            score = dto.score.toLong().coerceAtLeast(0),
            durationMillis = dto.durationMs.toLong().coerceAtLeast(0),
            eventLog = events,
        )
    }.getOrNull()
}
