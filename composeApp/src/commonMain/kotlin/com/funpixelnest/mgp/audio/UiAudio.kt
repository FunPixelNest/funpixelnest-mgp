package com.funpixelnest.mgp.audio

import androidx.compose.runtime.staticCompositionLocalOf

/** Short UI sounds (all original, generated for the app: tools/sound/synth.py). */
enum class Sfx { Tap, Tab, Popup, Close, Reward, LevelUp, Error, Toggle }

/**
 * Background music + UI sounds of the app itself (not the games: games play their own audio).
 * Android plays them; other platforms are silent for now.
 */
interface UiAudio {
    fun play(sfx: Sfx)

    /** Calm background music on / off (fades). */
    fun setMusic(on: Boolean)

    /** A very short, light vibration for a tap (buttons, tabs, switches). */
    fun tick() {}
}

object NoAudio : UiAudio {
    override fun play(sfx: Sfx) = Unit
    override fun setMusic(on: Boolean) = Unit
}

/** UI sounds for any composable (GlossyButton, tabs, switches, pop-ups). */
val LocalUiAudio = staticCompositionLocalOf<UiAudio> { NoAudio }

/** Plays sounds only while the Sound setting is on, and tap vibrations only while Vibration is on. */
class SettingsGatedAudio(
    private val inner: UiAudio,
    private val soundOn: () -> Boolean,
    private val vibrationOn: () -> Boolean = { true },
) : UiAudio {
    override fun play(sfx: Sfx) {
        if (soundOn()) inner.play(sfx)
    }

    override fun tick() {
        if (vibrationOn()) inner.tick()
    }

    override fun setMusic(on: Boolean) = inner.setMusic(on)
}
