package com.funpixelnest.mgp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.funpixelnest.mgp.data.BackendStatus
import com.funpixelnest.mgp.data.createPlatformRepository
import com.funpixelnest.mgp.push.AndroidPush
import com.funpixelnest.mgp.ui.Navigator
import com.funpixelnest.mgp.ui.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Pool to open when the app is started from a result notification. */
    private val pendingPoolId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light background → dark status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        pendingPoolId.value = intent.poolIdExtra()
        val pushEnabled = AndroidPush.init(applicationContext)

        setContent {
            val navigator = remember { Navigator() }
            val repository = remember { createPlatformRepository() }
            val status by repository.status.collectAsState()
            val scope = rememberCoroutineScope()

            // System Back pops pushed screens; it's blocked mid-game (use Quit instead).
            BackHandler(enabled = navigator.canGoBack) {
                if (navigator.current !is Screen.PlayGame) navigator.back()
            }

            // Push: ask for permission (Android 13+), then save this device's token for the signed-in player.
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(status) {
                if (status != BackendStatus.Ready || !pushEnabled) return@LaunchedEffect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                AndroidPush.onNewToken = { token -> scope.launch { repository.registerPushToken(token, "android") } }
                AndroidPush.currentToken()?.let { repository.registerPushToken(it, "android") }
            }

            // Opened from a result notification → show that leaderboard.
            val poolId by pendingPoolId
            LaunchedEffect(status, poolId) {
                val id = poolId
                if (status == BackendStatus.Ready && !id.isNullOrBlank()) {
                    navigator.push(Screen.PoolLeaderboard(id))
                    pendingPoolId.value = null
                }
            }

            // TODO(unity): pass gameLauncher = UnityGameLauncher(this) once Unity as a Library is added.
            App(repository = repository, navigator = navigator)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.poolIdExtra()?.let { pendingPoolId.value = it }
    }

    private fun Intent?.poolIdExtra(): String? = this?.getStringExtra("pool_id")?.takeIf { it.isNotBlank() }
}
