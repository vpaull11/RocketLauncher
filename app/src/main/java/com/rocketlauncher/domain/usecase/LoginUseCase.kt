package com.rocketlauncher.domain.usecase

import com.rocketlauncher.data.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        serverUrl: String,
        user: String,
        password: String
    ): Result<Unit> = authRepository.login(serverUrl, user, password)
}
