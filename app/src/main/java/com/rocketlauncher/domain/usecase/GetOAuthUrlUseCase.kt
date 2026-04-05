package com.rocketlauncher.domain.usecase

import android.util.Log
import com.rocketlauncher.data.api.ApiFactory
import com.rocketlauncher.util.AppLog
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject

/**
 * Получает URL для OAuth из настроек сервера Rocket.Chat.
 * /_oauth/keycloak не делает 302 — показывает "Login completed". Поэтому строим
 * прямой URL Keycloak из API (serverURL, authorizePath, clientId). redirect_uri
 * остаётся Rocket.Chat для получения credentialToken.
 */
class GetOAuthUrlUseCase @Inject constructor() {

    suspend operator fun invoke(serverUrl: String): Result<String> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val api = ApiFactory.create(baseUrl)
            val response = api.getOAuthSettings()
            AppLog.d(TAG, "Response: success=${response.success}, services=${response.services.size}")
            if (!response.success || response.services.isEmpty()) {
                return Result.failure(Exception("OAuth не настроен на сервере"))
            }
            val service = response.services
                .firstOrNull { it.serviceName().isNotBlank() }
                ?: response.services.first()
            val name = service.serviceName()
            if (name.isBlank()) {
                return Result.failure(Exception("OAuth сервис без имени"))
            }
            val redirectUri = baseUrl + "_oauth/" + name
            val result = when {
                service.serverURL != null && service.authorizePath != null && !service.clientId.isNullOrBlank() -> {
                    val keycloakUrl = buildKeycloakUrl(
                        serverURL = service.serverURL!!.trimEnd('/'),
                        authorizePath = service.authorizePath!!.trimStart('/'),
                        clientId = service.clientId!!,
                        redirectUri = redirectUri,
                        scope = service.scope?.ifBlank { "openid" } ?: "openid"
                    )
                    AppLog.d(TAG, "Using direct Keycloak URL: $keycloakUrl")
                    keycloakUrl
                }
                else -> {
                    AppLog.d(TAG, "Fallback (no API config): $redirectUri")
                    redirectUri
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "OAuth failed", e)
            Result.failure(e)
        }
    }

    private fun buildKeycloakUrl(
        serverURL: String,
        authorizePath: String,
        clientId: String,
        redirectUri: String,
        scope: String
    ): String {
        val base = serverURL.trimEnd('/')
        val path = authorizePath.trimStart('/')
        val authUrl = "$base/$path"
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to scope,
            "state" to UUID.randomUUID().toString()
        )
        val query = params.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8.name())}"
        }
        return "$authUrl?$query"
    }

    companion object {
        private const val TAG = "GetOAuthUrlUseCase"
    }
}
