package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatSearchResponse(
    val messages: List<MessageDto> = emptyList(),
    val success: Boolean = false
)

@Serializable
data class SpotlightResponse(
    val users: List<SpotlightUserDto> = emptyList(),
    val rooms: List<SpotlightRoomDto> = emptyList(),
    val success: Boolean = false
)

@Serializable
data class SpotlightUserDto(
    val _id: String,
    val name: String? = null,
    val username: String? = null,
    val status: String? = null
)

@Serializable
data class SpotlightRoomDto(
    val _id: String,
    val name: String? = null,
    val fname: String? = null,
    val t: String? = null
)

@Serializable
data class RoomAutocompleteResponse(
    val items: List<RoomAutocompleteItemDto> = emptyList(),
    val success: Boolean = false
)

@Serializable
data class RoomAutocompleteItemDto(
    val _id: String,
    val name: String? = null,
    val fname: String? = null,
    val t: String? = null
)

@Serializable
data class ImCreateRequest(
    val username: String
)

@Serializable
data class ImCreateResponse(
    val room: RoomDto? = null,
    val success: Boolean = false,
    val error: String? = null
)
