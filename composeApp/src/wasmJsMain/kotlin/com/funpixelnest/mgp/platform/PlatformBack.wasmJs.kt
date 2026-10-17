@file:OptIn(ExperimentalWasmJsInterop::class)

package com.funpixelnest.mgp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Browser Back: while the app has something to go back to, one extra history entry ("guard") is kept.
 * Pressing Back removes it and fires popstate → [onBack]; the guard is added again if still needed.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val callback by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        backInit { callback() }
        onDispose { }
    }
    LaunchedEffect(enabled) { backSetEnabled(enabled) }
}

actual val usesEdgeSwipeBack: Boolean = false

@JsFun(
    """(cb) => {
  const st = window.__fpnBack || (window.__fpnBack = { enabled: false, guard: false, ignoreNext: false, cb: null, wired: false });
  st.cb = cb;
  if (st.wired) return;
  st.wired = true;
  window.addEventListener('popstate', () => {
    if (st.ignoreNext) { st.ignoreNext = false; return; }
    if (!st.guard) return;
    st.guard = false;
    if (st.cb) st.cb();
    if (st.enabled && !st.guard) { history.pushState({ fpn: 1 }, ''); st.guard = true; }
  });
}""",
)
private external fun backInit(cb: () -> Unit)

@JsFun(
    """(on) => {
  const st = window.__fpnBack;
  if (!st) return;
  st.enabled = on;
  if (on && !st.guard) { history.pushState({ fpn: 1 }, ''); st.guard = true; }
  else if (!on && st.guard) { st.guard = false; st.ignoreNext = true; history.back(); }
}""",
)
private external fun backSetEnabled(on: Boolean)
