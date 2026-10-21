package com.funpixelnest.mgp.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    /** Space the app's HUD covers at the top / system bar at the bottom (dp), for the game to keep clear. */
    val hudInsetTopDp: Int = 0,
    val hudInsetBottomDp: Int = 0,
    /** Height under the game view taken by the app (banner ad + system bar), dp; the game view is that much shorter. */
    val belowGameDp: Int = 0,
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
            hudTop = hudInsetTopDp,
            hudBottom = hudInsetBottomDp,
        ),
    )
}

/** Player options from the pause menu / Settings, passed to the game. */
data class GameSettings(val music: Boolean = true, val sound: Boolean = true, val vibration: Boolean = true)

/**
 * Live values for the app's in-game HUD, reported by the game while it runs (Unity: FpnPlatform.UpdateHud).
 * [secondsLeft] is null for games without a timer, [lives] null for games without lives (shown disabled).
 * [reported] stays false until the game sends anything.
 */
data class GameHud(
    val score: Long = 0,
    val secondsLeft: Int? = null,
    val lives: Int? = null,
    /** A game's own count shown instead of lives, e.g. arrows left in Arrow Duel (null = none). */
    val counter: Int? = null,
    val reported: Boolean = false,
) {
    companion object {
        /** Bridge values: score, secondsLeft, lives, counter (< 0 = the game doesn't have it). */
        fun parse(score: Double, secondsLeft: Double, lives: Double = -1.0, counter: Double = -1.0): GameHud = GameHud(
            score = score.toLong().coerceAtLeast(0),
            secondsLeft = if (secondsLeft.isNaN() || secondsLeft < 0) null else secondsLeft.toInt(),
            lives = if (lives.isNaN() || lives < 0) null else lives.toInt(),
            counter = if (counter.isNaN() || counter < 0) null else counter.toInt(),
            reported = true,
        )
    }
}

/** Height of the app's HUD row over every game, below the status bar. */
val GAME_HUD_HEIGHT = 64.dp

private val noHud: StateFlow<GameHud> = MutableStateFlow(GameHud()).asStateFlow()

/** Sent back Unity → native when the match ends. */
data class GameResult(
    val score: Long,
    val durationMillis: Long,
    /** Move/timestamp log for replays and anti-cheat review on the server. */
    val eventLog: List<String> = emptyList(),
)

/** One row of a game's score board on the Game Over screen, e.g. "Time Bonus" 1,250. */
data class ScoreLine(val label: String, val value: Long)

/**
 * The game's score rows (FunPixelNest SDK FpnPlatform.AddScoreLine), sent inside the event log as
 * "score:<label>=<value>". Empty when the game sends none (the Game Over screen then shows one Score row).
 */
fun GameResult.scoreLines(): List<ScoreLine> = eventLog.mapNotNull { e ->
    if (!e.startsWith("score:")) return@mapNotNull null
    val body = e.removePrefix("score:")
    val eq = body.lastIndexOf('=')
    if (eq <= 0) return@mapNotNull null
    val value = body.substring(eq + 1).trim().toLongOrNull() ?: return@mapNotNull null
    ScoreLine(body.substring(0, eq).trim(), value)
}.take(6)

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

    /** Score / time left of the running game, for the app's HUD. */
    val hud: StateFlow<GameHud> get() = noHud

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
    @SerialName("hudTop") val hudTop: Int = 0,
    @SerialName("hudBottom") val hudBottom: Int = 0,
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
