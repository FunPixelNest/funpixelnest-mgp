package com.funpixelnest.mgp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.audio.LocalUiAudio
import com.funpixelnest.mgp.audio.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.TimeSource

enum class Currency { Coins, Gems }

/**
 * "Coins fly to the wallet": when the coin or gem balance goes up, a burst of coin / gem icons pops out where
 * the player last tapped (the Claim / Spin / Buy button) and flies along a curve into the matching counter in the
 * header. The counter pulses as each one lands and counts up to the new balance. Spending updates at once.
 *
 * Set up once at the app root: [rememberRewardFly], provide it with [LocalRewardFly], put [Modifier.rewardFlyRoot] on
 * the root Box and [RewardFlyOverlay] last inside it, and show [shownAmount] values in the header, whose counters
 * carry [Modifier.rewardTarget].
 */
@Stable
class RewardFlyState {
    internal val targets = mutableStateMapOf<Currency, Rect>()    // counters, window coordinates
    internal val flights = mutableStateListOf<Flight>()
    internal val pulses = mutableStateMapOf<Currency, Int>()
    internal var rootInWindow by mutableStateOf(Offset.Zero)
    internal var rootSize by mutableStateOf(Offset.Zero)
    private var lastTap: Offset? = null                            // root coordinates
    private var lastTapMark = TimeSource.Monotonic.markNow()
    private var nextId = 0L

    internal fun onTap(position: Offset) {
        lastTap = position
        lastTapMark = TimeSource.Monotonic.markNow()
    }

    /** Where a burst starts: the last tap if recent, otherwise the middle of the screen. */
    private fun origin(): Offset {
        val tap = lastTap
        return if (tap != null && lastTapMark.elapsedNow().inWholeSeconds < 10) tap else Offset(rootSize.x / 2, rootSize.y / 2)
    }

    /** Starts a flight for [gained] of [currency]; returns how many icons fly (0 when the counter isn't on screen). */
    internal fun launch(currency: Currency, gained: Long): Int {
        val target = targets[currency] ?: return 0
        val count = particleCount(currency, gained)
        val to = Offset(target.left + target.height / 2, target.center.y) - rootInWindow  // the icon at the pill's left
        flights += Flight(nextId++, currency, origin(), to, count)
        return count
    }

    internal fun landed(currency: Currency) {
        pulses[currency] = (pulses[currency] ?: 0) + 1
    }

    internal fun finished(flight: Flight) {
        flights.remove(flight)
    }

    private fun particleCount(currency: Currency, gained: Long): Int {
        val n = 3 + (ln(gained.coerceAtLeast(1).toDouble()) * 1.3).roundToInt()
        return if (currency == Currency.Coins) n.coerceIn(5, 12) else n.coerceIn(3, 8)
    }
}

internal class Flight(val id: Long, val currency: Currency, val from: Offset, val to: Offset, val count: Int)

val LocalRewardFly = compositionLocalOf<RewardFlyState?> { null }

@Composable
fun rememberRewardFly(): RewardFlyState = remember { RewardFlyState() }

private const val FLY_MS = 680
private const val BURST_MS = 200
private const val STAGGER_MS = 55

/** Root Box: remembers taps (without consuming them) and where the root sits in the window. */
fun Modifier.rewardFlyRoot(state: RewardFlyState): Modifier = this
    .onGloballyPositioned {
        state.rootInWindow = it.positionInWindow()
        state.rootSize = Offset(it.size.width.toFloat(), it.size.height.toFloat())
    }
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { state.onTap(it.position) }
            }
        }
    }

/** A header counter that flights land on. Also gives it a small pulse when an icon lands. */
fun Modifier.rewardTarget(currency: Currency): Modifier = composed {
    val state = LocalRewardFly.current ?: return@composed this
    DisposableEffect(state, currency) { onDispose { state.targets.remove(currency) } }
    val pulse = state.pulses[currency] ?: 0
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            scale.snapTo(1.12f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    this
        .onGloballyPositioned { state.targets[currency] = it.boundsInWindow() }
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * The balance to show in the header for [actual]: equal to it, except while a reward is flying in, when it stays at
 * the old value until the first icon lands and then counts up. Keep this above anything that hides the header, so a
 * reward earned while the header is hidden (e.g. in a game) flies in when the header comes back. [holder] returns what
 * holds a reward back (the open pop-up); the flight waits until that changes.
 */
@Composable
fun shownAmount(actual: Long, currency: Currency, state: RewardFlyState, holder: () -> Any? = { null }): Long {
    var shown by remember { mutableLongStateOf(actual) }
    var ready by remember { mutableStateOf(false) }
    // The first moments after start are the wallet loading, not rewards.
    LaunchedEffect(Unit) { delay(1500); ready = true }
    // When the holder (pop-up) last changed: a reward that lands just after a pop-up closed came from that pop-up.
    var holderChanged by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var holderBefore by remember { mutableStateOf<Any?>(null) }   // what held rewards before the last change
    LaunchedEffect(Unit) {
        var last: Any? = holder()
        snapshotFlow { holder() }.collect { now ->
            if (now != last) { holderBefore = last; last = now; holderChanged = TimeSource.Monotonic.markNow() }
        }
    }
    LaunchedEffect(actual, ready) {
        val from = shown
        if (!ready || actual <= from) {
            shown = actual
            return@LaunchedEffect
        }
        // Wait for the counter to be on screen (it's hidden during games and some screens) and for the reward's
        // pop-up to close (the icons then start from its Claim / Close button). A reward often arrives together with its pop-up (chest, lucky spin): give that pop-up a moment to appear,
        // then hold the flight until it closes. If a pop-up had just closed and another opened (Welcome -> Daily),
        // the reward came from the closed one, so fly now.
        delay(250)
        val justSwapped = holderChanged.elapsedNow().inWholeMilliseconds < 850 && holderBefore != null
        val heldBy = if (justSwapped) null else holder()
        snapshotFlow { state.targets[currency] != null && (heldBy == null || holder() != heldBy) }.first { it }
        delay(120) // let the header settle into place
        val icons = state.launch(currency, actual - from)
        if (icons > 0) {
            delay((BURST_MS + FLY_MS).toLong())
            val countMs = (icons - 1) * STAGGER_MS + 160
            animate(from.toFloat(), actual.toFloat(), animationSpec = tween(countMs, easing = LinearEasing)) { v, _ ->
                shown = v.toLong()
            }
        }
        shown = actual
    }
    return shown
}

/** Draws the flying icons. Put it last in the root Box (above pages and pop-ups). */
@Composable
fun BoxScope.RewardFlyOverlay(state: RewardFlyState) {
    Box(Modifier.matchParentSize()) {
        state.flights.forEach { flight ->
            key(flight.id) { FlightIcons(flight, state) }
        }
    }
}

@Composable
private fun FlightIcons(flight: Flight, state: RewardFlyState) {
    val audio = LocalUiAudio.current
    val density = LocalDensity.current
    val spread = with(density) { 70.dp.toPx() }
    val half = with(density) { 15.dp.toPx() }
    val total = BURST_MS + FLY_MS + (flight.count - 1) * STAGGER_MS
    val clock = remember { Animatable(0f) }
    val rnd = remember { Random(flight.id) }
    // Each icon: a burst direction and a side for its curve.
    val bursts = remember {
        List(flight.count) { i ->
            val angle = (i.toFloat() / flight.count) * 2 * PI.toFloat() + rnd.nextFloat() * 0.6f
            val r = spread * (0.55f + rnd.nextFloat() * 0.45f)
            Offset(cos(angle) * r, sin(angle) * r)
        }
    }
    var landedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(flight.id) {
        audio.play(Sfx.Reward)
        clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) {
            // Count landings for the counter pulse and a light tick.
            val landedNow = (0 until flight.count).count { i -> value >= i * STAGGER_MS + BURST_MS + FLY_MS }
            while (landedCount < landedNow) {
                landedCount++
                state.landed(flight.currency)
                if (landedCount == 1 || landedCount == flight.count) audio.tick()
            }
        }
        state.finished(flight)
    }

    val t = clock.value
    for (i in 0 until flight.count) {
        val local = t - i * STAGGER_MS
        if (local < 0f) continue
        val burstEnd = flight.from + bursts[i]
        val pos: Offset
        val scale: Float
        if (local < BURST_MS) {
            val p = FastOutSlowInEasing.transform(local / BURST_MS)
            pos = flight.from + bursts[i] * p
            scale = 0.4f + 0.75f * p
        } else {
            val p = ((local - BURST_MS) / FLY_MS).coerceIn(0f, 1f)
            if (p >= 1f) continue                                 // landed
            val e = FastOutSlowInEasing.transform(p)
            // Quadratic curve that bows outward, so the icons fan in rather than flying in a straight line.
            val mid = (burstEnd + flight.to) / 2f
            val bow = Offset(-(flight.to.y - burstEnd.y), flight.to.x - burstEnd.x) * (if (i % 2 == 0) 0.18f else -0.12f)
            val ctrl = mid + bow
            val a = 1 - e
            pos = burstEnd * (a * a) + ctrl * (2 * a * e) + flight.to * (e * e)
            scale = 1.15f - 0.4f * e
        }
        Box(
            Modifier
                .offset { IntOffset((pos.x - half).roundToInt(), (pos.y - half).roundToInt()) }
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            if (flight.currency == Currency.Coins) CoinIcon(30.dp) else GemIcon(30.dp)
        }
    }
}
