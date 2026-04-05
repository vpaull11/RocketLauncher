package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ответ `POST /api/v1/rooms.media/{rid}` (новые версии Rocket.Chat). */
@Serializable
data class RoomsMediaUploadResponse(
    val success: Boolean = false,
    val file: RoomsMediaUploadedFileRef? = null,
    val error: String? = null
)

@Serializable
data class RoomsMediaUploadedFileRef(
    @SerialName("_id") val id: String,
    val url: String? = null
)

/** Тело `POST /api/v1/rooms.mediaConfirm/{rid}/{fileId}` (JSON). */
@Serializable
data class RoomsMediaConfirmRequest(
    val msg: String? = null,
    val tmid: String? = null,
    val description: String? = null
)
