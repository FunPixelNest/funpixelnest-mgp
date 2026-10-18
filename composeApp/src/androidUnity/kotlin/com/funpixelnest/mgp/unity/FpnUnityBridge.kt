package com.funpixelnest.mgp.unity

/**
 * Called by the Unity game (FunPixelNest SDK, `FpnNativeBridge` Android) through JNI:
 *   onScore(gameId, ticketId, score, durationMs, eventLogJson) · onQuit(ticketId)
 *   onHud(score, secondsLeft, lives, counter) · getInset(bottom) → share of the screen height to keep clear.
 * Calls arrive on Unity's thread. The class name is fixed in the SDK (`AndroidBridgeClass`).
 */
object FpnUnityBridge {

    interface Listener {
        fun onScore(score: Long, durationMs: Long, eventLogJson: String)
        fun onQuit()
        fun onHud(score: Long, secondsLeft: Int, lives: Int, counter: Int)
    }

    @Volatile
    var listener: Listener? = null

    /** Launch JSON of the current match (the SDK asks for it when Unity starts). */
    @Volatile
    var currentLaunch: String? = null

    /** Share (0..0.5) of the screen height covered by the app's HUD (top) / system bar (bottom). */
    @Volatile
    var insetTop: Float = 0f

    @Volatile
    var insetBottom: Float = 0f

    @JvmStatic
    fun onScore(gameId: String, ticketId: String, score: Long, durationMs: Long, eventLogJson: String) {
        listener?.onScore(score, durationMs, eventLogJson)
    }

    @JvmStatic
    fun onQuit(ticketId: String) {
        listener?.onQuit()
    }

    /** Older game builds (no counter). */
    @JvmStatic
    fun onHud(score: Long, secondsLeft: Int, lives: Int) {
        listener?.onHud(score, secondsLeft, lives, -1)
    }

    @JvmStatic
    fun onHud(score: Long, secondsLeft: Int, lives: Int, counter: Int) {
        listener?.onHud(score, secondsLeft, lives, counter)
    }

    @JvmStatic
    fun getInset(bottom: Boolean): Float = if (bottom) insetBottom else insetTop

    @JvmStatic
    fun getLaunchData(): String? = currentLaunch
}
