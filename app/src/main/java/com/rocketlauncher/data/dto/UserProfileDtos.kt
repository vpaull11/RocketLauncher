package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

/**
 * POST /api/v1/users.updateOwnBasicInfo
 * @see https://developer.rocket.chat/apidocs/update-own-basic-information
 */
@Serializable
data class UpdateOwnBasicInfoRequest(
    val data: UpdateOwnBasicInfoData
)

@Serializable
data class UpdateOwnBasicInfoData(
    val name: String? = null,
    val nickname: String? = null,
    val bio: String? = null,
    /** online, busy, away, offline */
    val statusType: String? = null,
    val statusText: String? = null,
)

@Serializable
data class UpdateOwnBasicInfoResponse(
    val success: Boolean = false,
    val user: UsersInfoUser? = null,
    val error: String? = null,
)
