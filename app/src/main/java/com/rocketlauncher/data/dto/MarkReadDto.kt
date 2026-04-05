package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

/**
 * Тело для `POST /api/v1/subscriptions.read` — [rid] (id комнаты).
 * [readThreads] — пометить непрочитанные треды прочитанными (Rocket.Chat: `readThreads`, по умолчанию false).
 */
@Serializable
data class MarkReadRequest(
    val rid: String,
    val readThreads: Boolean? = null
)

@Serializable
data class MarkReadResponse(val success: Boolean = false)
