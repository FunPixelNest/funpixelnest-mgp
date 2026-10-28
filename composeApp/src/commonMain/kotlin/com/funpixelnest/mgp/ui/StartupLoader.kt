package com.funpixelnest.mgp.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.data.BackendStatus
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.ui.components.gameIconResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * The first screen's loading bar, driven by the real start-up work (like an async scene load):
 *   connect to the backend / restore the sign-in → load the player's profile, wallet and progress →
 *   read the game art from disk → 100 %.
 * Each step owns a slice of the bar. While a step runs the bar creeps forward inside its slice (so it never
 * looks frozen), and jumps to the end of the slice when the step finishes. It only moves forward and never
 * restarts; at 100 % the app moves on.
 */
@Stable
class StartupLoader {
    /** 0..1, only ever increases. */
    var progress by mutableFloatStateOf(0f)
        private set

    var label by mutableStateOf("Starting…")
        private set

    /** True once every step is done and the bar reached 100 %. */
    var done by mutableStateOf(false)
        private set

    private var creep: Job? = null

    private companion object {
        const val MIN_STEP_MS = 400L
    }

    /** Runs all start-up steps. Safe to call again after an error (Try again). */
    suspend fun run(repository: PlatformRepository) = coroutineScope {
        done = false
        progress = 0f
        label = "Starting…"

        step(0.05f, 0.45f, "Connecting…") {
            repository.initialize()
            repository.status.first { it != BackendStatus.Loading }
        }
        when (repository.status.value) {
            is BackendStatus.Error -> return@coroutineScope // the splash shows the error and Try again
            BackendStatus.Ready -> step(0.45f, 0.8f, "Loading your profile…") {
                runCatching { repository.refreshProgress() }
            }
            else -> Unit // signed out: the sign-in screen comes next
        }
        step(if (progress < 0.8f) progress else 0.8f, 0.98f, "Preparing games…") {
            preloadGameArt(repository)
        }
        label = "Ready!"
        progress = 1f
        delay(650) // the bar animates to 100 % (350 ms) and stays there a moment
        done = true
    }

    /** One step: the bar creeps from [from] toward [to] while [work] runs, then lands on [to]. */
    private suspend fun CoroutineScope.step(from: Float, to: Float, text: String, work: suspend () -> Unit) {
        label = text
        if (progress < from) progress = from
        creep?.cancel()
        creep = launch {
            // Ease toward 90 % of the slice: fast at first, slower and slower (never reaches the end by itself).
            while (isActive) {
                val target = from + (to - from) * 0.9f
                progress = (progress + (target - progress) * 0.08f).coerceAtLeast(progress)
                delay(50)
            }
        }
        try {
            // Each step stays on screen for a short minimum, so a fast start still reads as a smooth fill
            // rather than a flash; slow steps simply take as long as they need.
            coroutineScope {
                launch { delay(MIN_STEP_MS) }
                work()
            }
        } finally {
            creep?.cancel()
        }
        if (progress < to) progress = to
    }

    /** Reads the game icons from disk in parallel, so the Games tab shows them at once. */
    private suspend fun preloadGameArt(repository: PlatformRepository) = coroutineScope {
        val env = getSystemResourceEnvironment()
        repository.games().map { game ->
            async { runCatching { getDrawableResourceBytes(env, gameIconResource(game.id)) } }
        }.awaitAll()
    }
}
