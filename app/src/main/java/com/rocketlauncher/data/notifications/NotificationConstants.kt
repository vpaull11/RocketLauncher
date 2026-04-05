package com.rocketlauncher.data.notifications

object NotificationConstants {
    /** Явный action у Intent из push — надёжнее, чем только boolean extra при [onNewIntent]. */
    const val ACTION_OPEN_CHAT_FROM_NOTIFICATION = "com.rocketlauncher.action.OPEN_CHAT_FROM_NOTIFICATION"

    /** Канал с повышенной важностью (heads-up в фоне). Старый id оставлен в истории миграций. */
    const val CHANNEL_ID_MESSAGES = "rocket_messages_v2"
    /** Входящие видеозвонки (Jitsi), fullScreenIntent. */
    const val CHANNEL_ID_CALLS = "rocket_calls_v1"
    const val CHANNEL_ID_FOREGROUND = "msg_service"

    /** Завершение звонка в комнате ([jitsi_call_ended]) — закрыть [IncomingCallActivity]. */
    const val ACTION_JITSI_CALL_ENDED = "com.rocketlauncher.action.JITSI_CALL_ENDED"

    const val EXTRA_OPEN_ROOM_ID = "open_room_id"
    const val EXTRA_OPEN_ROOM_NAME = "open_room_name"
    const val EXTRA_OPEN_ROOM_TYPE = "open_room_type"
    const val EXTRA_OPEN_AVATAR_PATH = "open_avatar_path"
    const val EXTRA_FROM_NOTIFICATION = "from_notification"

    const val GROUP_KEY_MESSAGES = "com.rocketlauncher.MESSAGES"
}
