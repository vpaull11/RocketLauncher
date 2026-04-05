package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

/**
 * POST api/v1/rooms.saveNotification
 * Значения полей — строки (см. [saveNotificationSettings] на сервере Rocket.Chat).
 */
@Serializable
data class SaveRoomNotificationRequest(
    val roomId: String,
    val notifications: Map<String, String>
)
