package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class GenericResponse(
    val success: Boolean = false,
    val error: String? = null
)
