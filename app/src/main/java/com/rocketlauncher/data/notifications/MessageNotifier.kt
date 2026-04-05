package com.rocketlauncher.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rocketlauncher.R
import com.rocketlauncher.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MessageNotifier"

@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appForegroundState: AppForegroundState,
    private val openedChatTracker: OpenedChatTracker
) {
    private val nm: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    /** Активные id уведомлений по комнате — чтобы снять все при прочтении. */
    private val notificationIdsByRoom = ConcurrentHashMap<String, MutableSet<Int>>()

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NotificationConstants.CHANNEL_ID_MESSAGES,
                context.getString(R.string.notif_channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_messages_desc)
                enableVibration(true)
                enableLights(true)
            }
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(ch)
        }
    }

    fun notifyNewChatMessage(
        roomId: String,
        roomDisplayName: String,
        roomType: String,
        avatarPath: String,
        senderLabel: String,
        messagePreview: String,
        messageId: String
    ) {
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, context.getString(R.string.notif_disabled_log))
            return
        }
        if (shouldSuppressForOpenChat(roomId)) {
            return
        }
        val open = Intent(context, MainActivity::class.java).apply {
            action = NotificationConstants.ACTION_OPEN_CHAT_FROM_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationConstants.EXTRA_FROM_NOTIFICATION, true)
            putExtra(NotificationConstants.EXTRA_OPEN_ROOM_ID, roomId)
            putExtra(NotificationConstants.EXTRA_OPEN_ROOM_NAME, roomDisplayName)
            putExtra(NotificationConstants.EXTRA_OPEN_ROOM_TYPE, roomType)
            putExtra(NotificationConstants.EXTRA_OPEN_AVATAR_PATH, avatarPath)
        }
        val pi = PendingIntent.getActivity(
            context,
            messageId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = roomDisplayName.ifBlank { context.getString(R.string.default_room_chat) }
        val text = "${senderLabel}: ${messagePreview.take(200)}"
        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setGroup(NotificationConstants.GROUP_KEY_MESSAGES)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val id = (roomId.hashCode() xor messageId.hashCode()) and 0x7fffffff
        synchronized(notificationIdsByRoom) {
            notificationIdsByRoom.getOrPut(roomId) { mutableSetOf() }.add(id)
        }
        nm.notify(id, notification)
        notifySummary()
    }

    /** Снять все push по комнате (открыли/прочитали чат). */
    fun cancelNotificationsForRoom(roomId: String) {
        if (roomId.isBlank()) return
        val ids = synchronized(notificationIdsByRoom) {
            notificationIdsByRoom.remove(roomId)?.toList() ?: emptyList()
        }
        for (nid in ids) {
            try {
                nm.cancel(nid)
            } catch (e: Exception) {
                Log.w(TAG, "cancel $nid: ${e.message}")
            }
        }
    }

    /** Групповое сводное уведомление (Android). */
    private fun notifySummary() {
        val summary = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_summary_new_messages))
            .setGroup(NotificationConstants.GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setStyle(NotificationCompat.InboxStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(SUMMARY_ID, summary)
    }

    /** Не показывать, если пользователь уже смотрит этот чат (приложение на переднем плане). */
    private fun shouldSuppressForOpenChat(roomId: String): Boolean {
        return appForegroundState.isForegroundNow() && openedChatTracker.isViewingRoom(roomId)
    }

    companion object {
        private const val SUMMARY_ID = 100001
    }
}
