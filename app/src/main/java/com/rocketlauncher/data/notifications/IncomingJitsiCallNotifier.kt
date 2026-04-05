package com.rocketlauncher.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rocketlauncher.R
import com.rocketlauncher.presentation.jitsi.IncomingCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IncomingJitsiCall"

/**
 * Входящий Jitsi-звонок: полноэкранное уведомление (как звонок) + запуск [IncomingCallActivity].
 */
@Singleton
class IncomingJitsiCallNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nm: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NotificationConstants.CHANNEL_ID_CALLS,
                context.getString(R.string.notif_channel_calls_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_calls_desc)
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, null)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(ch)
        }
    }

    fun showIncomingCall(
        roomId: String,
        roomDisplayName: String,
        roomType: String,
        avatarPath: String,
        callerName: String
    ) {
        if (roomId.isBlank()) return
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, "notifications disabled")
        }

        val activityIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingCallActivity.EXTRA_ROOM_ID, roomId)
            putExtra(IncomingCallActivity.EXTRA_ROOM_NAME, roomDisplayName)
            putExtra(IncomingCallActivity.EXTRA_ROOM_TYPE, roomType)
            putExtra(IncomingCallActivity.EXTRA_AVATAR_PATH, avatarPath)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
        }

        val reqCode = notificationIdForRoom(roomId)
        val fullScreenPi = PendingIntent.getActivity(
            context,
            reqCode,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = roomDisplayName.ifBlank { context.getString(R.string.default_room_chat) }
        val text = context.getString(R.string.jitsi_notif_call_text, callerName)

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_CALLS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            nm.notify(reqCode, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "notify: ${e.message}")
        }

        mainHandler.post {
            try {
                context.startActivity(activityIntent)
            } catch (e: Exception) {
                Log.e(TAG, "startActivity: ${e.message}", e)
            }
        }
    }

    fun cancelForRoom(roomId: String) {
        if (roomId.isBlank()) return
        val id = notificationIdForRoom(roomId)
        nm.cancel(id)
        val end = Intent(NotificationConstants.ACTION_JITSI_CALL_ENDED).apply {
            setPackage(context.packageName)
            putExtra(IncomingCallActivity.EXTRA_ROOM_ID, roomId)
        }
        context.sendBroadcast(end)
    }

    fun cancelNotificationOnly(roomId: String) {
        if (roomId.isBlank()) return
        nm.cancel(notificationIdForRoom(roomId))
    }

    private fun notificationIdForRoom(roomId: String): Int =
        210_000 + (roomId.hashCode() and 0xFFFF)
}
