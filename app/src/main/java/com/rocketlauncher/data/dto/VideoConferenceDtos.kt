package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Тело POST /api/v1/video-conference.start */
@Serializable
data class VideoConferenceStartRequest(
    val roomId: String,
    val title: String = ""
)

/** Тело POST /api/v1/video-conference.join */
@Serializable
data class VideoConferenceJoinRequest(
    val callId: String
)

@Serializable
data class VideoConferenceStartResponse(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val error: String? = null
)

@Serializable
data class VideoConferenceJoinResponse(
    val success: Boolean = false,
    val url: String? = null,
    val providerName: String? = null,
    val error: String? = null
)
