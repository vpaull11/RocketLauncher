package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class RoomsResponse(
    val update: List<RoomDto> = emptyList(),
    val remove: List<RoomDto> = emptyList(),
    val success: Boolean = false
)

@Serializable
data class ChannelsListResponse(
    val channels: List<RoomDto> = emptyList(),
    val count: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val success: Boolean = false
)

@Serializable
data class ImListResponse(
    val ims: List<RoomDto> = emptyList(),
    val count: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val success: Boolean = false
)

@Serializable
data class RoomDto(
    val _id: String,
    val name: String? = null,
    val fname: String? = null,
    val t: String? = null, // c=channel, p=private, d=dm
    val usernames: List<String>? = null,
    val uids: List<String>? = null,
    val msgs: Int = 0,
    val usersCount: Int? = null,
    val u: RoomUser? = null,
    val topic: String? = null,
    val lastMessage: LastMessage? = null,
    val lm: String? = null,
    val ts: String? = null,
    val _updatedAt: String? = null,
    @Serializable(with = FavoriteFlagSerializer::class)
    @JsonNames("f", "favorite")
    val f: Boolean? = null,
    val prid: String? = null,
    val teamMain: Boolean? = null,
    val teamId: String? = null,
    val unread: Int? = null,
    val alert: Boolean? = null
)

@Serializable
data class RoomUser(
    val _id: String,
    val username: String? = null,
    val name: String? = null
)

@Serializable
data class LastMessage(
    val _id: String? = null,
    val msg: String? = null,
    val ts: String? = null,
    val u: RoomUser? = null
)
