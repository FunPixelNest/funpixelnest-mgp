package com.funpixelnest.mgp.platform

import androidx.compose.runtime.Composable

/** iOS has no Back button; App adds [edgeSwipeBack] instead. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual val usesEdgeSwipeBack: Boolean = true
