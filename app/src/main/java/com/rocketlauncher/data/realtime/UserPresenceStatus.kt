package com.rocketlauncher.data.realtime

import java.util.Locale

/** Статус присутствия Rocket.Chat (REST и realtime). */
enum class UserPresenceStatus {
    ONLINE,
    AWAY,
    BUSY,
    OFFLINE,
    UNKNOWN;

    companion object {
        /** DDP `user-status`: число 0..3 или строка. */
        fun fromRocketRealtime(raw: Any?): UserPresenceStatus {
            return when (raw) {
                is Number -> when (raw.toInt()) {
                    0 -> OFFLINE
                    1 -> ONLINE
                    2 -> AWAY
                    3 -> BUSY
                    else -> UNKNOWN
                }
                is String -> when (raw.lowercase(Locale.ROOT)) {
                    "online" -> ONLINE
                    "away" -> AWAY
                    "busy" -> BUSY
                    "offline", "invisible" -> OFFLINE
                    else -> UNKNOWN
                }
                else -> UNKNOWN
            }
        }

        fun fromRestApi(status: String?): UserPresenceStatus {
            if (status.isNullOrBlank()) return UNKNOWN
            return when (status.lowercase(Locale.ROOT)) {
                "online" -> ONLINE
                "away" -> AWAY
                "busy" -> BUSY
                "offline", "invisible" -> OFFLINE
                else -> UNKNOWN
            }
        }
    }
}
