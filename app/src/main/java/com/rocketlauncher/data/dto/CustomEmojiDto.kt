package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomEmojiResponse(
    val emojis: CustomEmojiContainer? = null,
    val success: Boolean = false
)

@Serializable
data class CustomEmojiContainer(
    val update: List<CustomEmojiDto> = emptyList(),
    val remove: List<CustomEmojiDto> = emptyList()
)

@Serializable
data class CustomEmojiDto(
    val _id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val extension: String = "png"
)
