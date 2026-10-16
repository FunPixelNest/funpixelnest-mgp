package com.funpixelnest.mgp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.funpixelnest.mgp.ui.Navigator
import com.funpixelnest.mgp.ui.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light background → dark status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val navigator = remember { Navigator() }

            // System Back pops pushed screens; it's blocked mid-game (use Quit instead).
            BackHandler(enabled = navigator.canGoBack) {
                if (navigator.current !is Screen.PlayGame) navigator.back()
            }

            // TODO(unity): pass gameLauncher = UnityGameLauncher(this) once Unity as a Library is added.
            App(navigator = navigator)
        }
    }
}
