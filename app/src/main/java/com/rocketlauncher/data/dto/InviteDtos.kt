package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FindOrCreateInviteRequest(
    val rid: String,
    val days: Int = 0,
    val maxUses: Int = 0
)

@Serializable
data class FindOrCreateInviteResponse(
    val success: Boolean = false,
    @SerialName("_id") val inviteId: String? = null,
    val rid: String? = null,
    /** Полная ссылка, если сервер её отдаёт (новые версии Rocket.Chat). */
    val url: String? = null,
    val error: String? = null,
    val message: String? = null,
    val errorType: String? = null
)

@Serializable
data class UseInviteTokenRequest(
    val token: String
)

@Serializable
data class UseInviteTokenResponse(
    val success: Boolean = false,
    val error: String? = null,
    val room: InviteRoomDto? = null
)

@Serializable
data class InviteRoomDto(
    val rid: String,
    val prid: String? = null,
    val fname: String? = null,
    val name: String? = null,
    val t: String
)

/** Результат вступления по invite-токену для навигации в чат. */
data class InviteJoinResult(
    val roomId: String,
    val roomName: String,
    val roomType: String,
    val avatarPath: String
)
