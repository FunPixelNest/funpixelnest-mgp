package com.funpixelnest.mgp.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.funpixelnest.mgp.game.GameSettings

/** Player options (pause menu and Settings). Passed to games when they start and when changed. */
@Stable
class AppSettings {
    var music by mutableStateOf(true)
    var sound by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    var notifications by mutableStateOf(true)

    val forGame: GameSettings get() = GameSettings(music = music, sound = sound, vibration = vibration)
}
