package com.funpixelnest.mgp.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.funpixelnest.mgp.MainActivity
import com.funpixelnest.mgp.firebase.FirebaseSetup
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Push notifications on Android via Firebase Cloud Messaging.
 *
 * Firebase is set up from BackendConfig (FIREBASE_* values in local.properties), so no
 * google-services.json is needed. Without those values push is simply off.
 */
object AndroidPush {
    const val CHANNEL_ID = "match_results"

    /** Called when Firebase rotates this device's token; MainActivity sends it to the backend. */
    @Volatile
    var onNewToken: ((String) -> Unit)? = null

    val isConfigured: Boolean get() = FirebaseSetup.isConfigured

    /** Sets up Firebase and the notification channel. Returns false when push isn't configured. */
    fun init(context: Context): Boolean {
        if (!FirebaseSetup.init(context)) return false
        createChannel(context)
        return true
    }

    /** This device's push token, or null if Firebase isn't available. */
    suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Match results", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tournament and 1v1 results, prizes and rematches"
        }
        manager.createNotificationChannel(channel)
    }

    /** Shows a notification while the app is open (Android shows them itself when the app is in the background). */
    fun show(context: Context, title: String, body: String, data: Map<String, String>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pending = PendingIntent.getActivity(
            context,
            (data["notification_id"] ?: title).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify((data["notification_id"] ?: title).hashCode(), notification)
        }
    }
}

/** Receives pushes and token changes from Firebase. Registered in AndroidManifest.xml. */
class FpnMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AndroidPush.onNewToken?.invoke(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        AndroidPush.show(applicationContext, title, body, message.data)
    }
}
