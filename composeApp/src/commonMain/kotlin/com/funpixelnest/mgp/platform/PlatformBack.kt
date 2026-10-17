package com.funpixelnest.mgp.platform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Hooks the platform's own Back into the app:
 * - Android: the system Back button / back gesture.
 * - Web: the browser's Back button (and mouse/keyboard back).
 * - iOS: no system Back; the edge swipe is added with [edgeSwipeBack].
 * [onBack] is only called while [enabled] is true; otherwise the platform does its default (e.g. close the app).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** True on iOS, where "back" is a swipe from the left edge of the screen. */
expect val usesEdgeSwipeBack: Boolean

/** iOS-style back: swipe right starting at the left edge. Observes touches without blocking the content. */
@Composable
fun Modifier.edgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    val callback by rememberUpdatedState(onBack)
    val density = LocalDensity.current
    val edge = with(density) { 24.dp.toPx() }
    val distance = with(density) { 72.dp.toPx() }
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > edge) return@awaitEachGesture
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - down.position.x
                val dy = abs(change.position.y - down.position.y)
                if (!fired && dx > distance && dx > dy * 2) {
                    fired = true
                    callback()
                }
            }
        }
    }
}
