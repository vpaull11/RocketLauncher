package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReactRequest(
    val emoji: String,
    val messageId: String,
    val shouldReact: Boolean? = null
)

@Serializable
data class ReactResponse(
    val success: Boolean = false,
    val error: String? = null
)
