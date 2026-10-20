package com.funpixelnest.mgp.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.game.GameSettings
import com.funpixelnest.mgp.platform.LocalPrefs
import com.funpixelnest.mgp.ui.theme.ThemeState

/** Player options (pause menu and Settings). Passed to games when they start and when changed. */
@Stable
class AppSettings {
    var music by mutableStateOf(true)
    var sound by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var notifications by mutableStateOf(true)

    /** Dark or light look for the whole app; remembered on this device. */
    var darkMode: Boolean
        get() = ThemeState.dark
        set(value) {
            ThemeState.dark = value
            LocalPrefs.set(KEY_DARK, if (value) "1" else "0")
        }

    val forGame: GameSettings get() = GameSettings(music = music, sound = sound, vibration = vibration)

    companion object {
        private const val KEY_DARK = "dark_mode"

        /** Applies the saved theme (call before the first frame). */
        fun loadTheme() {
            ThemeState.dark = LocalPrefs.get(KEY_DARK) == "1"
        }
    }
}
