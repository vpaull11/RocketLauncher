package com.rocketlauncher.data.repository

import android.content.Context
import android.util.Log
import com.rocketlauncher.data.api.ApiFactory
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.api.RocketChatApi
import com.rocketlauncher.data.realtime.MessageForegroundService
import com.rocketlauncher.data.realtime.RealtimeMessageService
import com.rocketlauncher.data.auth.OAuthRestLogin
import com.rocketlauncher.data.auth.OAuthWebSocketLogin
import com.rocketlauncher.data.auth.OAuthWebSocketLogin.TotpRequiredException
import com.rocketlauncher.data.dto.LoginRequest
import com.rocketlauncher.data.dto.UsersInfoUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.rocketlauncher.data.notifications.ThreadParticipationPrefs
import com.rocketlauncher.data.push.PushTokenRegistrar
import com.rocketlauncher.domain.usecase.SyncChatsFromServerUseCase

private const val TAG = "AuthRepository"

data class AuthState(
    val userId: String? = null,
    val authToken: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: SessionPrefs,
    private val apiProvider: ApiProvider,
    private val realtimeService: RealtimeMessageService,
    private val oauthRestLogin: OAuthRestLogin,
    private val oauthWebSocketLogin: OAuthWebSocketLogin,
    private val syncChatsFromServerUseCase: SyncChatsFromServerUseCase,
    private val pushTokenRegistrar: PushTokenRegistrar,
    private val threadParticipationPrefs: ThreadParticipationPrefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        scope.launch { loadSession() }
    }

    private suspend fun loadSession() {
        val snap = prefs.getAll()
        if (snap.authToken != null && snap.userId != null) {
            _authState.value = AuthState(
                userId = snap.userId,
                authToken = snap.authToken,
                username = snap.username,
                displayName = snap.displayName,
                avatarUrl = snap.avatarUrl,
                isLoggedIn = true
            )
            try {
                syncChatsFromServerUseCase()
            } catch (e: Exception) {
                Log.e(TAG, "sync chats on startup: ${e.message}", e)
            }
            realtimeService.connect()
            MessageForegroundService.start(appContext)
            pushTokenRegistrar.enqueueSyncCurrentToken(scope)
        }
        _isReady.value = true
    }

    suspend fun login(serverUrl: String, user: String, password: String): Result<Unit> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val api = ApiFactory.create(baseUrl)
            val response = api.login(LoginRequest(user = user, password = password))
            if (response.status == "success" && response.data != null) {
                val data = response.data
                prefs.saveSession(
                    serverUrl = baseUrl,
                    authToken = data.authToken,
                    userId = data.userId,
                    username = data.me.username,
                    displayName = data.me.name,
                    avatarUrl = data.me.avatarUrl
                )
                _authState.update {
                    AuthState(
                        userId = data.userId,
                        authToken = data.authToken,
                        username = data.me.username,
                        displayName = data.me.name,
                        avatarUrl = data.me.avatarUrl,
                        isLoggedIn = true
                    )
                }
                try {
                    syncChatsFromServerUseCase()
                } catch (e: Exception) {
                    Log.e(TAG, "sync chats after login: ${e.message}", e)
                }
                realtimeService.connect()
                MessageForegroundService.start(appContext)
                pushTokenRegistrar.enqueueSyncCurrentToken(scope)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * OAuth login через REST /api/v1/method.callAnon/login.
     * Первый вызов без OTP. При 400 + totp-required — TotpRequiredException, вызовите loginWithOAuth2FA.
     */
    suspend fun loginWithOAuthCredentials(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        cookies: String? = null
    ): Result<Unit> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val loginResult = oauthRestLogin.login(baseUrl, credentialToken, credentialSecret, cookies)
                .getOrElse { throw it }
            saveOAuthSession(baseUrl, loginResult)
            Result.success(Unit)
        } catch (e: TotpRequiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Завершает OAuth-логин с кодом 2FA.
     * Второй вызов method.callAnon/login с oauth + totp.code в params.
     */
    suspend fun loginWithOAuth2FA(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        twoFactorMethod: String,
        twoFactorCode: String,
        cookies: String? = null
    ): Result<Unit> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val loginResult = oauthRestLogin.loginWith2FA(
                baseUrl, credentialToken, credentialSecret, twoFactorCode, cookies
            ).getOrThrow()
            saveOAuthSession(baseUrl, loginResult)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveOAuthSession(
        baseUrl: String,
        loginResult: OAuthRestLogin.LoginResult
    ) {
        val api = ApiFactory.create(baseUrl, loginResult.authToken, loginResult.userId)
        val userInfo = api.getUsersInfo(loginResult.userId)
        val username = userInfo.user?.username ?: ""
        val displayName = userInfo.user?.name ?: username
        val avatarUrl = baseUrl + "avatar/" + username
        prefs.saveSession(
            serverUrl = baseUrl,
            authToken = loginResult.authToken,
            userId = loginResult.userId,
            username = username,
            displayName = displayName,
            avatarUrl = avatarUrl
        )
        _authState.update {
            AuthState(
                userId = loginResult.userId,
                authToken = loginResult.authToken,
                username = username,
                displayName = displayName,
                avatarUrl = avatarUrl,
                isLoggedIn = true
            )
        }
        try {
            syncChatsFromServerUseCase()
        } catch (e: Exception) {
            Log.e(TAG, "sync chats after OAuth: ${e.message}", e)
        }
        realtimeService.connect()
        MessageForegroundService.start(appContext)
        pushTokenRegistrar.enqueueSyncCurrentToken(scope)
    }

    suspend fun sendEmailCode(serverUrl: String, emailOrUsername: String): Result<Unit> =
        oauthWebSocketLogin.sendEmailCode(serverUrl, emailOrUsername)

    /** После users.updateOwnBasicInfo — обновить отображаемое имя и аватар в сессии. */
    suspend fun updateSessionFromUserProfile(user: UsersInfoUser) {
        val snap = prefs.getAll()
        val baseUrl = snap.serverUrl ?: return
        val token = snap.authToken ?: return
        val username = user.username ?: snap.username ?: return
        val displayName = user.name?.trim()?.takeIf { it.isNotEmpty() } ?: username
        val avatarUrl = "${baseUrl.trimEnd('/')}/avatar/$username?format=png"
        prefs.saveSession(
            serverUrl = baseUrl,
            authToken = token,
            userId = user._id,
            username = username,
            displayName = displayName,
            avatarUrl = avatarUrl
        )
        _authState.update {
            it.copy(
                userId = user._id,
                username = username,
                displayName = displayName,
                avatarUrl = avatarUrl
            )
        }
    }

    suspend fun logout() {
        MessageForegroundService.stop(appContext)
        prefs.clearSession()
        threadParticipationPrefs.clear()
        apiProvider.invalidate()
        realtimeService.disconnect()
        _authState.value = AuthState()
    }

    fun getAuthToken(): String? = _authState.value.authToken
    fun getUserId(): String? = _authState.value.userId
}
