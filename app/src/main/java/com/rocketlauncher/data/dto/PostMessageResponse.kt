package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostMessageResponse(
    val success: Boolean = false,
    val ts: Long? = null,
    val channel: String? = null,
    val message: MessageDto? = null,
    val error: String? = null,
    val errorType: String? = null
)
