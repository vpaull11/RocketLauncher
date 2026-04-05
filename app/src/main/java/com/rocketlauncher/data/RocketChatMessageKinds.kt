package com.rocketlauncher.data

/**
 * Типы сообщений Rocket.Chat ([MessageDto.t] / DDP `t`).
 * Сервисные события: не увеличивают unread, не обновляют превью комнаты, без push.
 */
object RocketChatMessageKinds {

    private val ROOM_EVENT_TYPES = setOf(
        "uj", "ul", "au", "ru", "ult", "ut", "wm",
        "ujt",
        "message_pinned", "message_unpinned",
        "discussion-created",
        "jitsi_call_started", "jitsi_call_ended",
        "rm",
        "e2e",
        // Команды (см. packages/core-typings IMessage TeamMessageTypesValues)
        "added-user-to-team",
        "removed-user-from-team",
        "user-converted-to-team",
        "user-converted-to-channel",
        "user-removed-room-from-team",
        "user-deleted-room-from-team",
        "user-added-room-to-team",
    )

    fun isRoomEventOrService(msgType: String?): Boolean {
        val t = msgType?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (t in ROOM_EVENT_TYPES) return true
        if (t.startsWith("room_")) return true
        if (t.startsWith("room-")) return true
        if (t.startsWith("subscription_")) return true
        if (t.startsWith("livechat_")) return true
        if (t.startsWith("user_")) return true
        if (t.startsWith("added-user") || t.startsWith("removed-user")) return true
        return false
    }
}
