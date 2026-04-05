package com.rocketlauncher.presentation.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.dto.UpdateOwnBasicInfoData
import com.rocketlauncher.data.repository.UserProfileRepository
import com.rocketlauncher.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
    /** Только отображение */
    val username: String = "",
    val name: String = "",
    val nickname: String = "",
    val bio: String = "",
    /** online, away, busy, offline */
    val statusType: String = "online",
    val statusText: String = "",
)

@HiltViewModel
class MyProfileViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyProfileUiState())
    val uiState: StateFlow<MyProfileUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, loadError = null, saveError = null, saveSuccess = false)
            }
            userProfileRepository.loadMyProfile().fold(
                onSuccess = { u ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = null,
                            username = u.username.orEmpty(),
                            name = u.name.orEmpty(),
                            nickname = u.nickname.orEmpty(),
                            bio = u.bio.orEmpty(),
                            statusType = normalizeStatusType(u.status),
                            statusText = u.statusText.orEmpty()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = e.message ?: appContext.getString(R.string.profile_load_error)
                        )
                    }
                }
            )
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v) }
    fun onNicknameChange(v: String) = _uiState.update { it.copy(nickname = v) }
    fun onBioChange(v: String) = _uiState.update { it.copy(bio = v) }
    fun onStatusTypeChange(v: String) = _uiState.update { it.copy(statusType = v) }
    fun onStatusTextChange(v: String) = _uiState.update { it.copy(statusText = v) }

    fun consumeSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }

    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, saveError = null, saveSuccess = false) }
            val data = UpdateOwnBasicInfoData(
                name = s.name.trim().takeIf { it.isNotEmpty() },
                nickname = s.nickname.trim().takeIf { it.isNotEmpty() },
                bio = s.bio.trim().takeIf { it.isNotEmpty() },
                statusType = s.statusType.takeIf { it.isNotBlank() },
                statusText = s.statusText.trim().takeIf { it.isNotEmpty() }
            )
            userProfileRepository.updateOwnProfile(data).fold(
                onSuccess = { u ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            saveSuccess = true,
                            name = u.name.orEmpty(),
                            nickname = u.nickname.orEmpty(),
                            bio = u.bio.orEmpty(),
                            statusType = normalizeStatusType(u.status),
                            statusText = u.statusText.orEmpty()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            saveError = e.message ?: appContext.getString(R.string.profile_save_error)
                        )
                    }
                }
            )
        }
    }

    private fun normalizeStatusType(raw: String?): String {
        return when (raw?.lowercase()) {
            "online" -> "online"
            "away" -> "away"
            "busy" -> "busy"
            "offline" -> "offline"
            else -> "online"
        }
    }
}
