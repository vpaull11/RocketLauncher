package com.rocketlauncher.data.push

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.dto.PushTokenRegisterRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PushTokenRegistrar"

@Singleton
class PushTokenRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiProvider: ApiProvider
) {

    private fun deviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    /** Запросить текущий FCM-токен и отправить на сервер (после входа). */
    fun enqueueSyncCurrentToken(scope: CoroutineScope) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM getToken failed: ${task.exception?.message}")
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            scope.launch(Dispatchers.IO) {
                registerFcmToken(token)
            }
        }
    }

    /**
     * Регистрация FCM-токена на сервере Rocket.Chat (push уведомления при убитом процессе).
     * Тип `fcm` / `gcm` зависит от версии сервера; пробуем fcm.
     */
    suspend fun registerFcmToken(token: String) {
        val api = apiProvider.getApi() ?: run {
            Log.w(TAG, "registerFcmToken: no API")
            return
        }
        try {
            val body = PushTokenRegisterRequest(
                type = "fcm",
                value = token,
                appName = context.packageName,
                id = deviceId()
            )
            val resp = api.registerPushToken(body)
            if (!resp.success) {
                Log.w(TAG, "push.token failed: ${resp.error}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerFcmToken: ${e.message}", e)
        }
    }
}
