package com.rocketlauncher.presentation.jitsi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions

private const val TAG = "JitsiMeetLauncher"

/**
 * Запуск встроенного Jitsi Meet SDK.
 * [joinUrl] — полный URL конференции, как возвращает Rocket.Chat (как для браузера), в т.ч. с `#jwt=...`.
 */
object JitsiMeetLauncher {

    fun launch(context: Context, joinUrl: String) {
        val trimmed = joinUrl.trim()
        if (trimmed.isEmpty()) {
            Log.e(TAG, "empty join URL")
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            Log.e(TAG, "no Activity in context")
            return
        }
        try {
            val options = JitsiMeetConferenceOptions.Builder()
                .setRoom(trimmed)
                .setFeatureFlag("welcomepage.enabled", false)
                .setFeatureFlag("pip.enabled", true)
                .build()
            JitsiMeetActivity.launch(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "launch failed: ${e.message}", e)
            throw e
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
