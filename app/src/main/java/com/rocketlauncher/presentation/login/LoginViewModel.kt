package com.rocketlauncher.presentation.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.auth.OAuthWebSocketLogin.TotpRequiredDetails
import com.rocketlauncher.data.auth.OAuthWebSocketLogin.TotpRequiredException
import com.rocketlauncher.domain.usecase.GetOAuthUrlUseCase
import com.rocketlauncher.domain.usecase.LoginUseCase
import com.rocketlauncher.domain.usecase.LoginWithKeycloakUseCase
import com.rocketlauncher.R
import com.rocketlauncher.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoadingOAuth: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    /** OTP требуется после OAuth (2FA по email). Храним credentials для повторной попытки. */
    val totpRequired: TotpRequiredState? = null
)

data class TotpRequiredState(
    val details: TotpRequiredDetails,
    val serverUrl: String,
    val credentialToken: String,
    val credentialSecret: String,
    val cookies: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val loginWithKeycloakUseCase: LoginWithKeycloakUseCase,
    private val getOAuthUrlUseCase: GetOAuthUrlUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(serverUrl: String, user: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loginUseCase(serverUrl, user, password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: appContext.getString(R.string.login_error_generic)
                        )
                    }
                }
        }
    }

    /** Получить OAuth URL из настроек сервера (GET /api/v1/settings.oauth) */
    suspend fun getOAuthUrl(serverUrl: String): Result<String> = getOAuthUrlUseCase(serverUrl)

    fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }

    fun setLoadingOAuth(loading: Boolean) {
        _uiState.update { it.copy(isLoadingOAuth = loading) }
    }

    /** Обработка результата OAuth (credentialToken/credentialSecret из WebView) */
    fun handleOAuthCredentials(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        cookies: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, totpRequired = null) }
            loginWithKeycloakUseCase(serverUrl, credentialToken, credentialSecret, cookies)
                .onSuccess {
                    AppLog.d("RocketChatOAuth", "handleOAuthCredentials: login success")
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    when (e) {
                        is TotpRequiredException -> {
                            AppLog.d("RocketChatOAuth", "handleOAuthCredentials: totp-required, method=${e.details.method}")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = null,
                                    totpRequired = TotpRequiredState(
                                        details = e.details,
                                        serverUrl = serverUrl,
                                        credentialToken = credentialToken,
                                        credentialSecret = credentialSecret,
                                        cookies = cookies
                                    )
                                )
                            }
                        }
                        else -> {
                            Log.e("RocketChatOAuth", "handleOAuthCredentials: login failed", e)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = e.message ?: appContext.getString(R.string.login_error_oauth)
                                )
                            }
                        }
                    }
                }
        }
    }

    /** Отправка кода OTP (2FA по email) */
    fun submitOtpCode(code: String) {
        val state = _uiState.value.totpRequired ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loginWithKeycloakUseCase.invokeWith2FA(
                state.serverUrl,
                state.credentialToken,
                state.credentialSecret,
                state.details.method,
                code,
                state.cookies
            )
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true, totpRequired = null) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: appContext.getString(R.string.login_error_wrong_otp)
                        )
                    }
                }
        }
    }

    /** Запросить код по email (если codeGenerated=false) */
    fun requestEmailCode(onResult: (Result<Unit>) -> Unit) {
        val state = _uiState.value.totpRequired ?: return
        val emailOrUsername = state.details.emailOrUsername
        if (emailOrUsername.isBlank()) {
            onResult(Result.failure(Exception(appContext.getString(R.string.login_otp_no_email))))
            return
        }
        viewModelScope.launch {
            loginWithKeycloakUseCase.sendEmailCode(state.serverUrl, emailOrUsername)
                .also { onResult(it) }
        }
    }

    fun dismissOtpDialog() {
        _uiState.update { it.copy(totpRequired = null) }
    }
}
