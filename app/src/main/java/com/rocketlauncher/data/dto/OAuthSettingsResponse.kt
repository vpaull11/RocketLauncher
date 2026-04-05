package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthSettingsResponse(
    val success: Boolean,
    val services: List<OAuthService> = emptyList()
)

@Serializable
data class OAuthService(
    @SerialName("_id") val id: String? = null,
    val name: String = "",
    val service: String? = null,  // custom OAuth может использовать service вместо name
    @SerialName("serverURL") val serverURL: String? = null,
    @SerialName("authorizePath") val authorizePath: String? = null,
    @SerialName("clientId") val clientId: String? = null,
    val scope: String? = null,
    @SerialName("buttonLabelText") val buttonLabelText: String? = null,
    val custom: Boolean = false
) {
    /** Имя сервиса для URL: name или service */
    fun serviceName(): String = name.ifBlank { service ?: "" }
}
