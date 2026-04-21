package com.rocketlauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rocketlauncher.data.realtime.MessageForegroundService
import com.rocketlauncher.data.repository.SessionPrefs
import com.rocketlauncher.domain.usecase.SyncChatsFromServerUseCase
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootReceiverEntryPoint {
    fun sessionPrefs(): SessionPrefs
    fun syncChatsFromServerUseCase(): SyncChatsFromServerUseCase
}

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            try {
                val entry = EntryPointAccessors.fromApplication(
                    appContext,
                    BootReceiverEntryPoint::class.java
                )
                val sessionPrefs = entry.sessionPrefs()
                val syncChatsFromServerUseCase = entry.syncChatsFromServerUseCase()

                val snap = sessionPrefs.getAll()
                if (snap.authToken == null || snap.userId == null) return@launch
                try {
                    syncChatsFromServerUseCase()
                } catch (_: Exception) { }
                // MessageForegroundService.start() запустит сервис,
                // а тот в onCreate() сам вызовет realtimeService.connect()
                MessageForegroundService.start(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
