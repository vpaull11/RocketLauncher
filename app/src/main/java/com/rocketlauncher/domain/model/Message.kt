package com.rocketlauncher.domain.model

data class Message(
    val id: String,
    val roomId: String,
    val text: String,
    val timestamp: Long,
    val userId: String,
    val username: String?,
    val displayName: String?,
    val isEdited: Boolean,
    val isMine: Boolean
)
