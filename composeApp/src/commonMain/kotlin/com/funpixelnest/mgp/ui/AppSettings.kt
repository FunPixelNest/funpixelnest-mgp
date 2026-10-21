package com.funpixelnest.mgp.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.game.GameSettings
import com.funpixelnest.mgp.platform.LocalPrefs
import com.funpixelnest.mgp.ui.theme.ThemeState

/**
 * Player options (pause menu and Settings), remembered on this device. Music / sound also control the app's
 * own background music and UI sounds; all three are passed to games when they start and when changed.
 */
@Stable
class AppSettings {
    private var musicState by mutableStateOf(loadFlag(KEY_MUSIC, true))
    private var soundState by mutableStateOf(loadFlag(KEY_SOUND, true))
    private var vibrationState by mutableStateOf(loadFlag(KEY_VIBRATION, true))
    private var notificationsState by mutableStateOf(loadFlag(KEY_NOTIFICATIONS, true))

    var music: Boolean
        get() = musicState
        set(value) { musicState = value; saveFlag(KEY_MUSIC, value) }
    var sound: Boolean
        get() = soundState
        set(value) { soundState = value; saveFlag(KEY_SOUND, value) }
    var vibration: Boolean
        get() = vibrationState
        set(value) { vibrationState = value; saveFlag(KEY_VIBRATION, value) }
    var notifications: Boolean
        get() = notificationsState
        set(value) { notificationsState = value; saveFlag(KEY_NOTIFICATIONS, value) }

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
        private const val KEY_MUSIC = "set_music"
        private const val KEY_SOUND = "set_sound"
        private const val KEY_VIBRATION = "set_vibration"
        private const val KEY_NOTIFICATIONS = "set_notifications"

        private fun loadFlag(key: String, default: Boolean): Boolean =
            when (LocalPrefs.get(key)) { "1" -> true; "0" -> false; else -> default }

        private fun saveFlag(key: String, value: Boolean) = LocalPrefs.set(key, if (value) "1" else "0")

        /** Applies the saved theme (call before the first frame). */
        fun loadTheme() {
            ThemeState.dark = LocalPrefs.get(KEY_DARK) == "1"
        }
    }
}
