package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val status: String,
    val data: LoginData? = null,
    val message: String? = null
)

@Serializable
data class LoginData(
    val authToken: String,
    val userId: String,
    val me: UserMe
)

@Serializable
data class UserMe(
    val _id: String,
    val name: String,
    val username: String,
    val status: String? = null,
    val avatarUrl: String? = null,
    val emails: List<EmailInfo>? = null
)

@Serializable
data class EmailInfo(
    val address: String,
    val verified: Boolean = false
)
