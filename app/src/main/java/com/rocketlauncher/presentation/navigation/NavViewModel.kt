package com.rocketlauncher.presentation.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.invite.InviteLinkParser
import com.rocketlauncher.data.notifications.NotificationConstants
import com.rocketlauncher.data.repository.AuthRepository
import com.rocketlauncher.data.repository.RoomRepository
import com.rocketlauncher.data.repository.SessionPrefs
import com.rocketlauncher.data.share.ShareUploadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class PendingOpenChat(
    val roomId: String,
    val roomName: String,
    val roomType: String,
    val avatarPath: String,
    /** Уникален на каждый тап — иначе [MutableStateFlow] не эмитит при том же чате и [LaunchedEffect] не сработает. */
    val openRequestId: Long = System.nanoTime()
)

@HiltViewModel
class NavViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val shareUploadQueue: ShareUploadQueue,
    private val roomRepository: RoomRepository,
    private val sessionPrefs: SessionPrefs
) : ViewModel() {

    private val _pendingOpenChat = MutableStateFlow<PendingOpenChat?>(null)
    val pendingOpenChat: StateFlow<PendingOpenChat?> = _pendingOpenChat.asStateFlow()

    /** URIs из «Поделиться» — пользователь выбирает чат в списке. */
    private val _pendingShareUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingShareUris: StateFlow<List<Uri>> = _pendingShareUris.asStateFlow()

    /** Deep link `https://host/invite/...` — обрабатывается после входа. */
    private val _pendingInviteUri = MutableStateFlow<Uri?>(null)
    val pendingInviteUri: StateFlow<Uri?> = _pendingInviteUri.asStateFlow()

    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    private val inviteMutex = Mutex()

    fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    _pendingShareUris.value = listOf(uri)
                } else {
                    val inviteFromShare = InviteLinkParser.findInviteUriInText(
                        intent.getStringExtra(Intent.EXTRA_TEXT),
                        intent.getStringExtra(Intent.EXTRA_HTML_TEXT),
                        intent.getStringExtra(Intent.EXTRA_SUBJECT),
                        intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                    )
                    if (inviteFromShare != null) {
                        _pendingInviteUri.value = inviteFromShare
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!list.isNullOrEmpty()) {
                    _pendingShareUris.value = list.filterNotNull()
                }
            }
        }

        val fromNotificationTap =
            intent.action == NotificationConstants.ACTION_OPEN_CHAT_FROM_NOTIFICATION ||
                intent.getBooleanExtra(NotificationConstants.EXTRA_FROM_NOTIFICATION, false)
        if (fromNotificationTap) {
            val roomId = intent.getStringExtra(NotificationConstants.EXTRA_OPEN_ROOM_ID) ?: return
            _pendingOpenChat.value = PendingOpenChat(
                roomId = roomId,
                roomName = intent.getStringExtra(NotificationConstants.EXTRA_OPEN_ROOM_NAME) ?: roomId,
                roomType = intent.getStringExtra(NotificationConstants.EXTRA_OPEN_ROOM_TYPE) ?: "c",
                avatarPath = intent.getStringExtra(NotificationConstants.EXTRA_OPEN_AVATAR_PATH) ?: ""
            )
            return
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && InviteLinkParser.looksLikeInviteLink(data)) {
                _pendingInviteUri.value = data
            }
        }
    }

    fun clearInviteError() {
        _inviteError.value = null
    }

    /**
     * Тап по инвайт-ссылке внутри чата (не браузер): та же цепочка, что и для deep link [Intent.ACTION_VIEW].
     */
    fun handleInAppInviteLink(uri: Uri) {
        if (!InviteLinkParser.looksLikeInviteLink(uri)) return
        _pendingInviteUri.value = uri
        tryProcessInviteDeepLink()
    }

    /**
     * Вызов после входа, когда есть [pendingInviteUri]: токен → API → [PendingOpenChat].
     */
    fun tryProcessInviteDeepLink() {
        viewModelScope.launch(Dispatchers.IO) {
            inviteMutex.withLock {
                val uri = _pendingInviteUri.value ?: return@withLock
                try {
                    val token = InviteLinkParser.extractInviteToken(uri)
                    if (token.isNullOrBlank()) {
                        _inviteError.value = "Не удалось разобрать ссылку-приглашение"
                        _pendingInviteUri.value = null
                        return@withLock
                    }
                    val sessionUrl = sessionPrefs.getServerUrl()
                    if (!InviteLinkParser.inviteHostMatchesSession(uri, sessionUrl)) {
                        _inviteError.value =
                            "Ссылка относится к другому серверу, чем текущий вход"
                        _pendingInviteUri.value = null
                        return@withLock
                    }
                    roomRepository.joinRoomViaInviteToken(token).fold(
                        onSuccess = { info ->
                            _pendingOpenChat.value = PendingOpenChat(
                                roomId = info.roomId,
                                roomName = info.roomName,
                                roomType = info.roomType,
                                avatarPath = info.avatarPath
                            )
                            _pendingInviteUri.value = null
                        },
                        onFailure = { e ->
                            _inviteError.value = e.message ?: "Не удалось вступить по ссылке"
                            _pendingInviteUri.value = null
                        }
                    )
                } catch (e: Exception) {
                    _inviteError.value = e.message ?: "Не удалось вступить по ссылке"
                    _pendingInviteUri.value = null
                }
            }
        }
    }

    /** Перед переходом в чат: положить файлы из «Поделиться» в очередь загрузки. */
    fun transferPendingShareToChatQueue() {
        val u = _pendingShareUris.value
        if (u.isNotEmpty()) {
            shareUploadQueue.setPending(u)
            _pendingShareUris.value = emptyList()
        }
    }

    fun consumePendingOpenChat() {
        _pendingOpenChat.value = null
    }

    val isReady = authRepository.isReady.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val authState = authRepository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = authRepository.authState.value
    )

    fun logout() {
        _pendingOpenChat.value = null
        _pendingShareUris.value = emptyList()
        _pendingInviteUri.value = null
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
