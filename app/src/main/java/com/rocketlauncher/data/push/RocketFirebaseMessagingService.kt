package com.rocketlauncher.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.rocketlauncher.util.AppLog
import com.google.firebase.messaging.RemoteMessage
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.R
import com.rocketlauncher.data.notifications.MessageNotifier
import com.rocketlauncher.data.notifications.RoomNotificationPolicy
import com.rocketlauncher.data.notifications.ThreadParticipationPrefs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "FCM"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FcmEntryPoint {
    fun pushTokenRegistrar(): PushTokenRegistrar
    fun messageNotifier(): MessageNotifier
    fun roomDao(): RoomDao
    fun threadParticipationPrefs(): ThreadParticipationPrefs
}

class RocketFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun entry(): FcmEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, FcmEntryPoint::class.java)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            entry().pushTokenRegistrar().registerFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val rid = data["rid"]
            ?: data["room_id"]
            ?: parseRidFromPayload(data)
            ?: return
        val title = data["title"]
            ?: data["room_name"]
            ?: data["fname"]
            ?: remoteMessage.notification?.title
            ?: getString(R.string.fcm_fallback_title)
        val body = data["message"]
            ?: data["msg"]
            ?: data["text"]
            ?: remoteMessage.notification?.body
            ?: ""
        val sender = data["sender_name"]
            ?: data["name"]
            ?: data["username"]
            ?: ""
        val preview = if (sender.isNotBlank() && body.isNotBlank()) body
        else sender.ifBlank {
            body.ifBlank { getString(R.string.fcm_new_message_fallback) }
        }
        scope.launch {
            try {
                val dao = entry().roomDao()
                if (!RoomNotificationPolicy.shouldNotifyForRoom(dao, rid)) {
                    AppLog.d(TAG, "skip FCM notify: policy room=$rid")
                    return@launch
                }
                val tmid = data["tmid"]?.takeIf { it.isNotBlank() }
                    ?: data["threadId"]?.takeIf { it.isNotBlank() }
                if (tmid != null) {
                    val mentionHint = listOf(
                        data["mention"],
                        data["hasMention"],
                        data["userMentioned"],
                        data["isMention"]
                    ).any { it == "true" || it == "1" }
                    if (!mentionHint) {
                        val participating = entry().threadParticipationPrefs()
                            .isParticipatingInThread(tmid)
                        if (!participating) {
                            AppLog.d(TAG, "skip FCM: thread $tmid without mention/participation")
                            return@launch
                        }
                    }
                }
                entry().messageNotifier().notifyNewChatMessage(
                    roomId = rid,
                    roomDisplayName = title,
                    roomType = data["type"] ?: data["room_type"] ?: "c",
                    avatarPath = "",
                    senderLabel = sender.ifBlank { title },
                    messagePreview = preview,
                    messageId = remoteMessage.messageId ?: data["message_id"] ?: data["msgId"] ?: System.currentTimeMillis().toString()
                )
            } catch (e: Exception) {
                Log.e(TAG, "onMessageReceived: ${e.message}", e)
            }
        }
    }

    /** Разбор rid из нестандартных payload Rocket.Chat / шлюзов. */
    private fun parseRidFromPayload(data: Map<String, String>): String? {
        val jsonLike = data["ejson"] ?: data["payload"] ?: return null
        return try {
            org.json.JSONObject(jsonLike).optString("rid").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
