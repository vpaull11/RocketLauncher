package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MethodCallAnonRequest(
    val message: String
)

@Serializable
data class MethodCallAnonResponse(
    val message: String,
    val success: Boolean
)
