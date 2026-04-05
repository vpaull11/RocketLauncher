package com.rocketlauncher.domain.usecase

import com.rocketlauncher.data.repository.AuthRepository
import javax.inject.Inject

/**
 * OAuth-вход через Keycloak (и др.), настроенный на сервере Rocket.Chat.
 * credentialToken/credentialSecret получаются из WebView redirect.
 * При 2FA возвращает Result.failure(TotpRequiredException).
 */
class LoginWithKeycloakUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        cookies: String? = null
    ): Result<Unit> = authRepository.loginWithOAuthCredentials(
        serverUrl, credentialToken, credentialSecret, cookies
    )

    suspend fun invokeWith2FA(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        twoFactorMethod: String,
        twoFactorCode: String,
        cookies: String? = null
    ): Result<Unit> = authRepository.loginWithOAuth2FA(
        serverUrl, credentialToken, credentialSecret, twoFactorMethod, twoFactorCode, cookies
    )

    suspend fun sendEmailCode(serverUrl: String, emailOrUsername: String): Result<Unit> =
        authRepository.sendEmailCode(serverUrl, emailOrUsername)
}
