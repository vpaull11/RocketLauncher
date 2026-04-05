package com.rocketlauncher.data.repository

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.dto.UpdateOwnBasicInfoData
import com.rocketlauncher.data.dto.UpdateOwnBasicInfoRequest
import com.rocketlauncher.data.dto.UsersInfoUser
import com.rocketlauncher.data.realtime.UserPresenceStore
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserProfileRepo"

@Singleton
class UserProfileRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val sessionPrefs: SessionPrefs,
    private val authRepository: AuthRepository,
    private val userPresenceStore: UserPresenceStore
) {

    suspend fun loadMyProfile(): Result<UsersInfoUser> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val userId = sessionPrefs.getUserId() ?: return Result.failure(IllegalStateException("Нет userId"))
        return try {
            val resp = api.getUsersInfo(userId)
            if (resp.success && resp.user != null) Result.success(resp.user)
            else Result.failure(IllegalStateException("users.info: нет данных"))
        } catch (e: Exception) {
            Log.e(TAG, "loadMyProfile: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfile(data: UpdateOwnBasicInfoData): Result<UsersInfoUser> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val resp = api.updateOwnBasicInfo(UpdateOwnBasicInfoRequest(data = data))
            if (resp.success && resp.user != null) {
                authRepository.updateSessionFromUserProfile(resp.user)
                userPresenceStore.applyFromUserInfo(resp.user)
                Result.success(resp.user)
            } else {
                Result.failure(IllegalStateException(resp.error?.takeIf { it.isNotBlank() } ?: "Не удалось сохранить"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateOwnProfile: ${e.message}", e)
            Result.failure(e)
        }
    }
}
