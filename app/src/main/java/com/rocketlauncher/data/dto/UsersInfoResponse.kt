package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UsersInfoResponse(
    val success: Boolean,
    val user: UsersInfoUser? = null
)

@Serializable
data class UsersInfoUser(
    val _id: String,
    val username: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val bio: String? = null,
    /** online, away, busy, offline … */
    val status: String? = null,
    val statusText: String? = null,
    val avatarETag: String? = null,
    val emails: List<UserEmailInfo>? = null,
)

@Serializable
data class UserEmailInfo(
    val address: String? = null,
    val verified: Boolean? = null
)
