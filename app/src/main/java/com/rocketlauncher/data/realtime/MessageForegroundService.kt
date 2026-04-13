package com.rocketlauncher.data.realtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rocketlauncher.R
import com.rocketlauncher.presentation.MainActivity
import com.rocketlauncher.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MsgFgService"

@AndroidEntryPoint
class MessageForegroundService : Service() {

    @Inject lateinit var realtimeService: RealtimeMessageService

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.e(TAG, "onCreate: Foreground service started")
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notif_ws_connecting))
        )
        realtimeService.connect()
        scope.launch {
            realtimeService.connectionState.collect { state ->
                val text = when (state) {
                    RealtimeMessageService.ConnectionState.CONNECTED ->
                        getString(R.string.notif_ws_connected)
                    RealtimeMessageService.ConnectionState.CONNECTING ->
                        getString(R.string.notif_ws_connecting)
                    RealtimeMessageService.ConnectionState.RECONNECTING ->
                        getString(R.string.notif_ws_reconnecting)
                    RealtimeMessageService.ConnectionState.DISCONNECTED ->
                        getString(R.string.notif_ws_disconnected)
                }
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppLog.e(TAG, "onStartCommand: explicit STOP action")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            null -> {
                // intent == null означает, что Android перезапустил сервис после убийства.
                // Это ключевой момент для диагностики — фиксируем в лог.
                AppLog.e(TAG, "onStartCommand: restarted by system (intent=null), reconnecting WS")
                realtimeService.connect()
            }
            else -> AppLog.e(TAG, "onStartCommand: action=${intent.action}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.e(TAG, "onDestroy: Foreground service is being destroyed")
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Пользователь смахнул приложение из Recent Apps.
        // Фиксируем в лог для диагностики, но не останавливаем сервис.
        AppLog.e(TAG, "onTaskRemoved: app swiped from recent apps")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_ws_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_ws_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "msg_service"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.rocketlauncher.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, MessageForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageForegroundService::class.java))
        }
    }
}
