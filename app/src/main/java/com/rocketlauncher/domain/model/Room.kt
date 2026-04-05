package com.rocketlauncher.domain.model

data class Room(
    val id: String,
    val name: String?,
    val displayName: String,
    val type: RoomType,
    val lastMessage: String?,
    val lastMessageTime: String?,
    val lastMessageAuthor: String?,
    val unreadCount: Int
)

enum class RoomType {
    CHANNEL,
    PRIVATE,
    DIRECT
}
