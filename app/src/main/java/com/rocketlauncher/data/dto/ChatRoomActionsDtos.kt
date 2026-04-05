package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomIdBody(val roomId: String)

/** [POST /api/v1/rooms.favorite](https://developer.rocket.chat/reference/api/rest-api/endpoints/rooms/rooms-favorite) */
@Serializable
data class RoomFavoriteRequest(
    val roomId: String,
    val favorite: Boolean
)

@Serializable
data class RoomIdUserIdBody(
    val roomId: String,
    val userId: String
)

@Serializable
data class ChannelCreateBody(
    val name: String,
    val readOnly: Boolean? = null
)

@Serializable
data class GroupCreateBody(
    val name: String,
    val members: List<String>? = null
)

@Serializable
data class TeamCreateBody(
    val name: String,
    /** 0 — публичная команда, 1 — приватная (как в REST Rocket.Chat). */
    val type: Int = 1
)

@Serializable
data class DiscussionCreateApiBody(
    val prid: String,
    @SerialName("t_name") val tName: String? = null,
    val pname: String? = null,
    val pmid: String? = null,
    val reply: String? = null,
    val users: List<String>? = null
)

@Serializable
data class ChannelCreateResponse(
    val success: Boolean = false,
    val channel: RoomDto? = null,
    val error: String? = null
)

@Serializable
data class GroupCreateResponse(
    val success: Boolean = false,
    val group: RoomDto? = null,
    val error: String? = null
)

@Serializable
data class TeamCreateResponse(
    val success: Boolean = false,
    val team: TeamInfoDto? = null,
    val error: String? = null
)

@Serializable
data class TeamInfoDto(
    val _id: String,
    val name: String? = null,
    val roomId: String? = null
)

@Serializable
data class DiscussionCreateResponse(
    val success: Boolean = false,
    val discussion: RoomDto? = null,
    val error: String? = null
)

@Serializable
data class RoomMemberDto(
    val _id: String,
    val username: String? = null,
    val name: String? = null,
    val status: String? = null
)

@Serializable
data class RoomMembersResponse(
    val success: Boolean = false,
    val members: List<RoomMemberDto> = emptyList(),
    val count: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val error: String? = null
)
