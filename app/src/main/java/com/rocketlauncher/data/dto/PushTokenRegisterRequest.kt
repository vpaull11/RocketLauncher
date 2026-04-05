package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

/** Тело POST /api/v1/push.token (FCM и т.д.) */
@Serializable
data class PushTokenRegisterRequest(
    val type: String,
    val value: String,
    val appName: String,
    val id: String
)
