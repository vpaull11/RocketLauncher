package com.rocketlauncher.presentation.chat

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.db.MessageEntity
import com.rocketlauncher.data.db.RoomEntity
import com.rocketlauncher.data.emoji.EmojiStore
import com.rocketlauncher.data.emoji.RecentEmojiPrefs
import com.rocketlauncher.data.repository.ChatRoomActionsRepository
import com.rocketlauncher.data.repository.AuthRepository
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.ReadReceiptReaderRow
import com.rocketlauncher.data.dto.PinnedBannerItem
import com.rocketlauncher.data.dto.SpotlightUserDto
import com.rocketlauncher.data.repository.FileUploadDebugException
import com.rocketlauncher.data.repository.ForwardMessageDebugException
import com.rocketlauncher.data.repository.MessageRepository
import com.rocketlauncher.data.repository.RoomRepository
import com.rocketlauncher.data.repository.SearchRepository
import com.rocketlauncher.data.repository.SessionPrefs
import com.rocketlauncher.data.repository.VideoConferenceRepository
import com.rocketlauncher.data.realtime.RealtimeMessageService
import com.rocketlauncher.data.realtime.UserPresenceSnapshot
import com.rocketlauncher.data.realtime.UserPresenceStore
import com.rocketlauncher.data.notifications.MessageNotifier
import com.rocketlauncher.data.notifications.OpenedChatTracker
import com.rocketlauncher.data.share.ShareUploadQueue
import com.rocketlauncher.domain.usecase.SendMessageUseCase
import com.rocketlauncher.presentation.navigation.safeUrlDecode
import com.rocketlauncher.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val currentUserId: String? = null,
    val serverUrl: String? = null,
    val roomDisplayName: String = "",
    val roomAvatarUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isThread: Boolean = false,
    val replyTo: MessageEntity? = null,
    /** Режим правки своего сообщения (текст в композере). */
    val editingMessage: MessageEntity? = null,
    val toastMessage: String? = null,
    val authToken: String? = null,
    val authUserId: String? = null,
    val inRoomSearchQuery: String = "",
    val inRoomSearchResults: List<MessageDto> = emptyList(),
    val inRoomSearchLoading: Boolean = false,
    val forwardToMessage: MessageEntity? = null,
    /** Полный отчёт при ошибке пересылки (показ в диалоге + копирование). */
    val forwardErrorReport: String? = null,
    val forwardTargetRooms: List<RoomEntity> = emptyList(),
    val highlightMessageId: String? = null,
    /** Подсказки пользователей при вводе @ (spotlight). */
    val mentionSuggestions: List<SpotlightUserDto> = emptyList(),
    val mentionSearchLoading: Boolean = false,
    /** Загрузка файла в комнату (rooms.upload). */
    val isUploading: Boolean = false,
    /** Отчёт при ошибке загрузки файла (диалог + копирование). */
    val uploadErrorReport: String? = null,
    /** Увеличивается после успешной отправки — список прокручивается вниз. */
    val scrollToBottomNonce: Long = 0L,
    /** Переход к цитируемому сообщению (индекс в списке вычисляет UI). */
    val pendingScrollToMessageId: String? = null,
    /**
     * Сообщение, к которому вернуть по первому нажатию FAB «вниз» после перехода по цитате / закрепу / календарю / поиску.
     * Очищается при возврате через FAB, при ручном скролле к низу ([clearScrollReturnAnchor]) или при новом якоре.
     */
    val scrollReturnAnchorMessageId: String? = null,
    /** Краткая подсветка после перехода по цитате. */
    val quoteHighlightMessageId: String? = null,
    /** Нижний лист профиля собеседника (тап по аватарке / имени). */
    val userProfileSheet: UserProfileSheetUi? = null,
    /** Одноразовый переход в личку после [profileOpenDirectChat]. */
    val navigateToDirectChat: NavigateToDirectChat? = null,
    /** Управление комнатой (канал / группа, не тред и не личка). */
    val roomMembersSheetOpen: Boolean = false,
    val roomMembers: List<RoomMemberRowUi> = emptyList(),
    val roomMembersLoading: Boolean = false,
    val roomMembersError: String? = null,
    val inviteMemberDialogOpen: Boolean = false,
    val inviteMemberQuery: String = "",
    val inviteMemberSuggestions: List<SpotlightUserDto> = emptyList(),
    val inviteMemberLoading: Boolean = false,
    val rolePickerTarget: RoomMemberRowUi? = null,
    /** После успешного выхода из комнаты — закрыть экран. */
    val navigateBackAfterLeave: Boolean = false,
    /** Открыть созданное обсуждение (или др. комнату). */
    val pendingOpenRoom: PendingOpenRoomChat? = null,
    /** Ответ из поиска оказался в треде — открыть тред с подсветкой. */
    val pendingThreadNavigation: PendingThreadNavigation? = null,
    /** Догрузка более старых сообщений при прокрутке к началу истории. */
    val loadingOlderMessages: Boolean = false,
    /** Статусы пользователей (WebSocket user-status + users.info). */
    val presenceSnapshot: UserPresenceSnapshot = UserPresenceSnapshot(),
    /** Закрепления (новее — меньший индекс); [pinnedBannerIndex] — какое превью показать в баннере. */
    val pinnedBannerItems: List<PinnedBannerItem> = emptyList(),
    val pinnedBannerIndex: Int = 0,
    /** Нижний лист «Кто прочитал». */
    val readReceiptsSheet: ReadReceiptsSheetState? = null,
    /** Локальное состояние подписки на уведомления по треду ([tmid]). */
    val threadSubscribed: Boolean = false,
    val threadFollowBusy: Boolean = false
)

data class ReadReceiptsSheetState(
    val messageId: String,
    val loading: Boolean = true,
    val readers: List<ReadReceiptReaderRow> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val sessionPrefs: SessionPrefs,
    private val realtimeService: RealtimeMessageService,
    private val sendMessageUseCase: SendMessageUseCase,
    private val apiProvider: ApiProvider,
    private val searchRepository: SearchRepository,
    private val roomRepository: RoomRepository,
    private val chatRoomActionsRepository: ChatRoomActionsRepository,
    private val videoConferenceRepository: VideoConferenceRepository,
    private val openedChatTracker: OpenedChatTracker,
    private val messageNotifier: MessageNotifier,
    private val shareUploadQueue: ShareUploadQueue,
    @ApplicationContext private val appContext: Context,
    val emojiStore: EmojiStore,
    private val recentEmojiPrefs: RecentEmojiPrefs,
    private val userPresenceStore: UserPresenceStore
) : ViewModel() {

    /** Последние смайлики из пикера (глобально по приложению). */
    val recentEmojiCodes: StateFlow<List<String>> = recentEmojiPrefs.recentFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun recordEmojiPicked(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            recentEmojiPrefs.record(code)
        }
    }

    val roomId: String = savedStateHandle.get<String>("roomId") ?: ""
    private val roomType: String = savedStateHandle.get<String>("roomType") ?: "c"
    private val roomNameArg: String = safeUrlDecode(savedStateHandle.get<String>("roomName"))
    private val avatarPathArg: String = safeUrlDecode(savedStateHandle.get<String>("avatarPath"))
    private val tmid: String = savedStateHandle.get<String>("tmid") ?: ""
    private val threadTitleArg: String = safeUrlDecode(savedStateHandle.get<String>("threadTitle"))
    private val highlightMsgArg: String = safeUrlDecode(savedStateHandle.get<String>("highlightMsg"))

    val isThread: Boolean = tmid.isNotBlank()

    private val _uiState = MutableStateFlow(ChatUiState(
        currentUserId = authRepository.authState.value.userId,
        roomDisplayName = if (isThread) threadTitleArg.take(50) else roomNameArg,
        isThread = isThread,
        highlightMessageId = highlightMsgArg.takeIf { it.isNotBlank() }
    ))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var refreshReadReceiptsJob: Job? = null
    private var mentionSearchJob: Job? = null
    private var inviteSearchJob: Job? = null
    private var quoteHighlightJob: Job? = null

    /** Следующий offset для [MessageRepository.loadMessagesPage] / [loadThreadMessagesPage]. */
    private var nextRoomHistoryOffset = 0
    private var roomHistoryExhausted = true
    private var nextThreadHistoryOffset = 0
    private var threadHistoryExhausted = true
    private val olderMessagesMutex = Mutex()

    /** Техническое имя комнаты с сервера ([RoomEntity.name]) для permalink; надёжнее, чем displayName из навигации. */
    @Volatile
    private var cachedRoomSlug: String? = null

    /** Для DM: username собеседника из [RoomEntity.avatarPath] (не путать с channel `room/…`). */
    @Volatile
    private var cachedDmPeerUsername: String? = null

    private fun scheduleRefreshReadReceipts(messages: List<MessageEntity>) {
        refreshReadReceiptsJob?.cancel()
        refreshReadReceiptsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            val uid = _uiState.value.currentUserId ?: return@launch
            messageRepository.refreshReadReceiptsForOwnMessages(roomId, uid, messages)
        }
    }

    init {
        _uiState.update {
            it.copy(
                currentUserId = authRepository.authState.value.userId,
                roomDisplayName = if (isThread) {
                    val title = threadTitleArg.ifEmpty { appContext.getString(R.string.default_thread) }
                    if (title.length > 50) title.take(50) + "..." else title
                } else {
                    roomNameArg.ifEmpty { appContext.getString(R.string.default_room_chat) }
                }
            )
        }
        viewModelScope.launch {
            userPresenceStore.snapshot.collect { snap ->
                _uiState.update { it.copy(presenceSnapshot = snap) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionPrefs.getAll()
            val srvUrl = session.serverUrl
            val avatarUrl = if (avatarPathArg.isNotBlank() && srvUrl != null) {
                srvUrl.trimEnd('/') + "/avatar/" + avatarPathArg + "?format=png"
            } else null
            _uiState.update {
                it.copy(
                    serverUrl = srvUrl,
                    roomAvatarUrl = avatarUrl,
                    authToken = session.authToken,
                    authUserId = session.userId
                )
            }
            if (srvUrl != null) emojiStore.loadCustomEmojis(srvUrl)
        }
        realtimeService.subscribe(roomId)
        viewModelScope.launch(Dispatchers.IO) {
            messageNotifier.cancelNotificationsForRoom(roomId)
            messageRepository.markAsRead(roomId, readThreads = isThread)
        }
        if (roomId.isNotBlank()) {
            openedChatTracker.setOpenRoom(roomId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (isThread && tmid.isNotBlank()) {
                val sub = messageRepository.isThreadFollowedLocally(tmid)
                _uiState.update { it.copy(threadSubscribed = sub) }
            }
            val r = roomRepository.getRoom(roomId)
            cachedRoomSlug = r?.name?.trim()?.takeIf { it.isNotEmpty() }
            if (roomType == "d") {
                cachedDmPeerUsername = r?.avatarPath?.trim()?.takeIf { peer ->
                    peer.isNotEmpty() && !peer.startsWith("room/")
                }
                val peerUn = cachedDmPeerUsername
                    ?: avatarPathArg.trim().takeIf { it.isNotEmpty() && !it.startsWith("room/") }
                if (!peerUn.isNullOrEmpty()) {
                    searchRepository.getUserByUsername(peerUn).onSuccess { user ->
                        userPresenceStore.applyFromUserInfo(user)
                    }
                }
            }
        }
        if (isThread) {
            viewModelScope.launch {
                messageRepository.observeThreadMessages(tmid)
                    .catch { e -> _uiState.update { it.copy(error = e.message) } }
                    .collect { messages ->
                        _uiState.update { it.copy(messages = messages) }
                        scheduleRefreshReadReceipts(messages)
                    }
            }
        } else {
            viewModelScope.launch {
                messageRepository.observeMessages(roomId)
                    .catch { e -> _uiState.update { it.copy(error = e.message) } }
                    .collect { messages ->
                        _uiState.update { it.copy(messages = messages) }
                        scheduleRefreshReadReceipts(messages)
                    }
            }
        }
        viewModelScope.launch {
            messageRepository.observeAllRooms()
                .catch { e -> Log.e("ChatVM", "rooms: ${e.message}") }
                .collect { rooms: List<RoomEntity> ->
                    _uiState.update { it.copy(forwardTargetRooms = rooms.filter { r -> r.id != roomId }) }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val pending = shareUploadQueue.pollPending()
            if (pending.isEmpty() || roomId.isBlank()) return@launch
            _uiState.update { it.copy(isUploading = true) }
            var ok = 0
            var errors = 0
            var lastFailureReport: String? = null
            for (uri in pending) {
                val r = messageRepository.uploadFile(
                    roomId = roomId,
                    uri = uri,
                    fileNameOverride = null,
                    message = null,
                    description = null,
                    threadMessageId = if (isThread) tmid else null
                )
                if (r.isSuccess) ok++
                else {
                    errors++
                    lastFailureReport = r.exceptionOrNull()?.let { failureReportForUpload(it) }
                }
            }
            _uiState.update {
                val bump = if (ok > 0) it.scrollToBottomNonce + 1L else it.scrollToBottomNonce
                it.copy(
                    isUploading = false,
                    scrollToBottomNonce = bump,
                    toastMessage = when {
                        errors > 0 && ok == 0 -> appContext.getString(R.string.chat_vm_files_send_failed)
                        errors > 0 -> appContext.getString(R.string.chat_vm_files_sent_partial, ok, ok + errors)
                        else -> appContext.getString(R.string.chat_vm_files_sent_count, ok)
                    },
                    uploadErrorReport = if (errors > 0 && ok == 0) lastFailureReport else null
                )
            }
        }
        if (!isThread && highlightMsgArg.isNotBlank() && roomId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(250)
                val ok = messageRepository.ensureMessageInRoomLoaded(roomId, roomType, highlightMsgArg)
                val msg = messageRepository.getMessageById(highlightMsgArg)
                withContext(Dispatchers.Main) {
                    when {
                        msg == null || msg.roomId != roomId -> {
                            _uiState.update {
                                it.copy(
                                    toastMessage = if (!ok) {
                                        appContext.getString(R.string.chat_vm_load_message_history_failed)
                                    } else {
                                        appContext.getString(R.string.chat_vm_message_not_in_chat)
                                    },
                                    highlightMessageId = null
                                )
                            }
                        }
                        !msg.tmid.isNullOrBlank() -> {
                            val title = msg.text.trim().take(48).ifBlank {
                                appContext.getString(R.string.default_thread)
                            }
                            _uiState.update {
                                it.copy(
                                    pendingThreadNavigation = PendingThreadNavigation(
                                        tmid = msg.tmid!!,
                                        threadTitle = title,
                                        highlightMessageId = highlightMsgArg
                                    ),
                                    highlightMessageId = null
                                )
                            }
                        }
                        else -> {
                            viewModelScope.launch {
                                delay(150)
                                _uiState.update { it.copy(pendingScrollToMessageId = highlightMsgArg) }
                            }
                        }
                    }
                }
            }
        }
        if (isThread && highlightMsgArg.isNotBlank() && roomId.isNotBlank() && tmid.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(250)
                val ok = messageRepository.ensureMessageInThreadLoaded(tmid, roomId, highlightMsgArg)
                val entity = messageRepository.getMessageById(highlightMsgArg)
                withContext(Dispatchers.Main) {
                    if (entity != null) {
                        viewModelScope.launch {
                            delay(150)
                            _uiState.update { it.copy(pendingScrollToMessageId = highlightMsgArg) }
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                toastMessage = if (!ok) {
                                    appContext.getString(R.string.chat_vm_load_thread_message_failed)
                                } else {
                                    appContext.getString(R.string.chat_vm_message_not_found)
                                },
                                highlightMessageId = null
                            )
                        }
                    }
                }
            }
        }
    }

    /** Файл из чата (кнопка «Прикрепить») или одноразово; [messageText] — подпись к файлу (поле msg на сервере). */
    fun uploadAttachment(uri: Uri, messageText: String? = null) {
        if (roomId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isUploading = true, editingMessage = null) }
            val r = messageRepository.uploadFile(
                roomId = roomId,
                uri = uri,
                fileNameOverride = null,
                message = messageText?.trim()?.takeIf { it.isNotEmpty() },
                description = null,
                threadMessageId = if (isThread) tmid else null
            )
            val ex = r.exceptionOrNull()
            _uiState.update {
                val bump = if (r.isSuccess) it.scrollToBottomNonce + 1L else it.scrollToBottomNonce
                it.copy(
                    isUploading = false,
                    scrollToBottomNonce = bump,
                    threadSubscribed = if (r.isSuccess && isThread) true else it.threadSubscribed,
                    toastMessage = if (r.isSuccess) {
                        appContext.getString(R.string.chat_vm_file_sent)
                    } else ex?.message,
                    uploadErrorReport = if (r.isFailure) ex?.let { failureReportForUpload(it) } else null
                )
            }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val n = withContext(Dispatchers.IO) {
                    if (isThread) messageRepository.loadThreadMessages(tmid, roomId)
                    else messageRepository.loadMessages(roomId, roomType)
                }
                if (isThread) {
                    nextThreadHistoryOffset = n
                    threadHistoryExhausted = n < 50
                } else {
                    nextRoomHistoryOffset = n
                    roomHistoryExhausted = n < 50
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "loadMessages: ${e.message}")
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
            if (!isThread && roomId.isNotBlank()) {
                val pinned = withContext(Dispatchers.IO) {
                    messageRepository.loadPinnedBannerItems(roomId)
                }
                _uiState.update {
                    it.copy(pinnedBannerItems = pinned, pinnedBannerIndex = 0)
                }
            }
        }
    }

    /**
     * Догрузка более старых сообщений с сервера (прокрутка к верху списка — к старым).
     */
    fun loadOlderMessages() {
        if (isThread) {
            if (tmid.isBlank() || roomId.isBlank() || threadHistoryExhausted) return
            viewModelScope.launch(Dispatchers.IO) {
                if (!olderMessagesMutex.tryLock()) return@launch
                try {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(loadingOlderMessages = true) }
                    }
                    val n = messageRepository.loadThreadMessagesPage(tmid, roomId, nextThreadHistoryOffset)
                    if (n == 0) {
                        threadHistoryExhausted = true
                    } else {
                        nextThreadHistoryOffset += n
                        if (n < 50) threadHistoryExhausted = true
                    }
                } finally {
                    olderMessagesMutex.unlock()
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(loadingOlderMessages = false) }
                    }
                }
            }
            return
        }
        if (roomId.isBlank() || roomHistoryExhausted) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!olderMessagesMutex.tryLock()) return@launch
            try {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(loadingOlderMessages = true) }
                }
                val n = messageRepository.loadMessagesPage(roomId, roomType, nextRoomHistoryOffset)
                if (n == 0) {
                    roomHistoryExhausted = true
                } else {
                    nextRoomHistoryOffset += n
                    if (n < 50) roomHistoryExhausted = true
                }
            } finally {
                olderMessagesMutex.unlock()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(loadingOlderMessages = false) }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val editing = _uiState.value.editingMessage
        if (editing != null) {
            viewModelScope.launch(Dispatchers.IO) {
                messageRepository.updateMessage(roomId, editing.id, trimmed)
                    .onSuccess {
                        _uiState.update { s ->
                            s.copy(
                                editingMessage = null,
                                toastMessage = appContext.getString(R.string.chat_vm_message_edited)
                            )
                        }
                    }
                    .onFailure { e ->
                        Log.e("ChatVM", "updateMessage: ${e.message}")
                        _uiState.update {
                            it.copy(
                                toastMessage = e.message
                                    ?: appContext.getString(R.string.chat_vm_save_failed)
                            )
                        }
                    }
            }
            return
        }
        val replyTo = _uiState.value.replyTo
        val finalText = if (replyTo != null) {
            val permalink = buildPermalink(replyTo.id)
            "[ ]($permalink) $trimmed"
        } else trimmed

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (isThread) {
                messageRepository.sendMessage(roomId, finalText, tmid)
            } else {
                sendMessageUseCase(roomId, finalText)
            }
            result.onSuccess {
                _uiState.update { s ->
                    s.copy(
                        scrollToBottomNonce = s.scrollToBottomNonce + 1L,
                        threadSubscribed = if (isThread) true else s.threadSubscribed
                    )
                }
            }
            result.onFailure { e ->
                Log.e("ChatVM", "sendMessage: ${e.message}")
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(replyTo = null) }
        }
    }

    fun subscribeToThread() {
        if (!isThread || tmid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(threadFollowBusy = true) }
            messageRepository.followThread(tmid)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            threadSubscribed = true,
                            threadFollowBusy = false,
                            toastMessage = appContext.getString(R.string.chat_vm_thread_subscribed)
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            threadFollowBusy = false,
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_thread_subscribe_failed)
                        )
                    }
                }
        }
    }

    fun unsubscribeFromThread() {
        if (!isThread || tmid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(threadFollowBusy = true) }
            messageRepository.unfollowThread(tmid)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            threadSubscribed = false,
                            threadFollowBusy = false,
                            toastMessage = appContext.getString(R.string.chat_vm_thread_unsubscribed)
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            threadFollowBusy = false,
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_thread_unsubscribe_failed)
                        )
                    }
                }
        }
    }

    fun startEditing(message: MessageEntity) {
        _uiState.update { it.copy(editingMessage = message, replyTo = null) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessage = null) }
    }

    fun deleteMessage(messageId: String) {
        if (roomId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessage(roomId, messageId)
                .onFailure { e ->
                    Log.e("ChatVM", "deleteMessage: ${e.message}")
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_delete_failed)
                        )
                    }
                }
        }
    }

    fun react(messageId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.reactToMessage(messageId, emoji)
                .onSuccess {
                    val n = if (isThread) {
                        messageRepository.loadThreadMessages(tmid, roomId)
                    } else {
                        messageRepository.loadMessages(roomId, roomType)
                    }
                    if (isThread) {
                        nextThreadHistoryOffset = n
                        threadHistoryExhausted = n < 50
                    } else {
                        nextRoomHistoryOffset = n
                        roomHistoryExhausted = n < 50
                    }
                }
                .onFailure { e -> Log.e("ChatVM", "react: ${e.message}") }
        }
    }

    fun setReplyTo(message: MessageEntity) {
        _uiState.update { it.copy(replyTo = message, editingMessage = null) }
    }

    fun clearReplyTo() {
        _uiState.update { it.copy(replyTo = null) }
    }

    /**
     * FAB «вниз»: если есть [ChatUiState.scrollReturnAnchorMessageId] — сначала прокрутка к этому сообщению
     * (как в Telegram после цитаты/перехода), иначе — к последнему сообщению.
     */
    fun requestScrollToBottom() {
        _uiState.update { s ->
            val anchor = s.scrollReturnAnchorMessageId?.takeIf { it.isNotBlank() }
            if (anchor != null) {
                s.copy(
                    scrollReturnAnchorMessageId = null,
                    pendingScrollToMessageId = anchor
                )
            } else {
                s.copy(scrollToBottomNonce = s.scrollToBottomNonce + 1L)
            }
        }
    }

    /** Сброс якоря возврата (например, пользователь вручную доскроллил до низа ленты). */
    fun clearScrollReturnAnchor() {
        _uiState.update {
            if (it.scrollReturnAnchorMessageId != null) {
                it.copy(scrollReturnAnchorMessageId = null)
            } else it
        }
    }

    /**
     * Переход к цитируемому сообщению.
     * @param anchorSourceMessageId id сообщения, с которого ушли (бабл с цитатой) — первое нажатие FAB вернёт сюда.
     */
    fun scrollToQuotedMessage(quotedMessageId: String, anchorSourceMessageId: String? = null) {
        if (quotedMessageId.isBlank()) return
        quoteHighlightJob?.cancel()
        val anchor = anchorSourceMessageId?.takeIf { it.isNotBlank() && it != quotedMessageId }
        _uiState.update {
            it.copy(
                pendingScrollToMessageId = quotedMessageId,
                quoteHighlightMessageId = quotedMessageId,
                scrollReturnAnchorMessageId = anchor ?: it.scrollReturnAnchorMessageId
            )
        }
        quoteHighlightJob = viewModelScope.launch {
            delay(2500)
            _uiState.update { it.copy(quoteHighlightMessageId = null) }
        }
    }

    fun consumePendingScrollToMessage() {
        _uiState.update { it.copy(pendingScrollToMessageId = null) }
    }

    /**
     * Результат поиска по чату: прокрутка к сообщению, якорь — текущая позиция ленты до перехода.
     */
    fun scrollToInRoomSearchResult(messageId: String, viewportAnchorMessageId: String?) {
        if (messageId.isBlank() || isThread) return
        val anchor = viewportAnchorMessageId?.takeIf { it.isNotBlank() && it != messageId }
        _uiState.update {
            it.copy(
                pendingScrollToMessageId = messageId,
                scrollReturnAnchorMessageId = anchor ?: it.scrollReturnAnchorMessageId,
                inRoomSearchQuery = "",
                inRoomSearchResults = emptyList(),
                inRoomSearchLoading = false
            )
        }
    }

    /**
     * Прокрутка к первому сообщению выбранного календарного дня; при необходимости догружает историю с API.
     * @param viewportAnchorMessageId сообщение у верхнего края видимой области до перехода — для возврата по FAB.
     */
    fun jumpToStartOfDay(localDate: LocalDate, viewportAnchorMessageId: String? = null) {
        if (isThread) {
            _uiState.update { it.copy(toastMessage = appContext.getString(R.string.chat_vm_calendar_not_in_thread)) }
            return
        }
        if (roomId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            if (localDate.isAfter(today)) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(toastMessage = appContext.getString(R.string.chat_vm_calendar_future_day))
                    }
                }
                return@launch
            }
            val start = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            suspend fun findWithPaging(): String? {
                var id = messageRepository.findEarliestMessageIdOnCalendarDay(roomId, start, end)
                if (id != null) return id
                var offset = 50
                repeat(400) {
                    val n = messageRepository.loadMessagesPage(roomId, roomType, offset)
                    if (n == 0) return null
                    delay(80)
                    id = messageRepository.findEarliestMessageIdOnCalendarDay(roomId, start, end)
                    if (id != null) return id
                    val oldest = messageRepository.getOldestStoredMessageTimestamp(roomId) ?: return null
                    if (oldest < start) return null
                    offset += 50
                }
                return null
            }

            val found = findWithPaging()
            withContext(Dispatchers.Main) {
                if (found != null) {
                    val anchor = viewportAnchorMessageId?.takeIf { it.isNotBlank() && it != found }
                    _uiState.update {
                        it.copy(
                            pendingScrollToMessageId = found,
                            scrollReturnAnchorMessageId = anchor ?: it.scrollReturnAnchorMessageId
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(toastMessage = appContext.getString(R.string.chat_vm_calendar_no_messages))
                    }
                }
            }
        }
    }

    fun pinMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = apiProvider.getApi() ?: return@launch
                val resp = api.pinMessage(mapOf("messageId" to messageId))
                if (resp.success) {
                    _uiState.update { it.copy(toastMessage = appContext.getString(R.string.chat_vm_pin_success)) }
                    if (!isThread && roomId.isNotBlank()) {
                        val pinned = messageRepository.loadPinnedBannerItems(roomId)
                        _uiState.update {
                            it.copy(pinnedBannerItems = pinned, pinnedBannerIndex = 0)
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(toastMessage = resp.error ?: appContext.getString(R.string.error_generic))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        toastMessage = appContext.getString(
                            R.string.error_generic_with_message,
                            e.message ?: ""
                        )
                    )
                }
            }
        }
    }

    /**
     * Тап по баннеру закрепления: прокрутка к сообщению, затем превью — следующее (старее), по кругу.
     * @param viewportAnchorMessageId id сообщения у верхнего края видимой ленты до перехода (для FAB «назад»).
     */
    fun onPinnedBannerClick(viewportAnchorMessageId: String? = null) {
        if (isThread || roomId.isBlank()) return
        val items = _uiState.value.pinnedBannerItems
        if (items.isEmpty()) return
        val idx = _uiState.value.pinnedBannerIndex.coerceIn(0, items.lastIndex)
        val targetId = items[idx].messageId
        val anchor = viewportAnchorMessageId?.takeIf { it.isNotBlank() && it != targetId }
        quoteHighlightJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.ensureMessageInRoomLoaded(roomId, roomType, targetId)
            val n = items.size
            val nextIdx = (idx + 1) % n
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        pendingScrollToMessageId = targetId,
                        quoteHighlightMessageId = targetId,
                        scrollReturnAnchorMessageId = anchor ?: it.scrollReturnAnchorMessageId,
                        pinnedBannerIndex = nextIdx
                    )
                }
            }
            quoteHighlightJob = viewModelScope.launch {
                delay(2500)
                _uiState.update { it.copy(quoteHighlightMessageId = null) }
            }
        }
    }

    fun createDiscussion(messageId: String, messageText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val resp = chatRoomActionsRepository.createDiscussionFromMessage(roomId, messageId, messageText)
            if (resp.success) {
                roomRepository.syncRooms()
                val disc = resp.discussion
                val pending = if (disc != null) {
                    PendingOpenRoomChat(
                        disc._id,
                        disc.fname ?: disc.name ?: appContext.getString(R.string.default_discussion),
                        disc.t ?: "p"
                    )
                } else null
                _uiState.update {
                    it.copy(
                        toastMessage = appContext.getString(R.string.chat_vm_discussion_created),
                        pendingOpenRoom = pending
                    )
                }
            } else {
                _uiState.update {
                    it.copy(toastMessage = resp.error ?: appContext.getString(R.string.error_generic))
                }
            }
        }
    }

    fun supportsRoomManagement(): Boolean = !isThread && (roomType == "c" || roomType == "p")

    /** Инвайт по ссылке — для приватных групп (`p`). */
    fun supportsInviteLink(): Boolean = !isThread && roomType == "p"

    fun copyRoomInviteLinkToClipboard() {
        if (!supportsInviteLink()) return
        viewModelScope.launch(Dispatchers.IO) {
            chatRoomActionsRepository.getOrCreateRoomInviteLink(roomId, roomType).fold(
                onSuccess = { url ->
                    withContext(Dispatchers.Main) {
                        val cm =
                            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(
                            ClipData.newPlainText(
                                appContext.getString(R.string.chat_vm_invite_clip_label),
                                url
                            )
                        )
                        _uiState.update {
                            it.copy(toastMessage = appContext.getString(R.string.chat_vm_invite_copied))
                        }
                    }
                },
                onFailure = { e ->
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                toastMessage = e.message
                                    ?: appContext.getString(R.string.chat_vm_invite_link_failed)
                            )
                        }
                    }
                }
            )
        }
    }

    fun openRoomMembersSheet() {
        if (!supportsRoomManagement()) return
        _uiState.update {
            it.copy(
                roomMembersSheetOpen = true,
                roomMembersLoading = true,
                roomMembers = emptyList(),
                roomMembersError = null
            )
        }
        reloadRoomMembers()
    }

    fun dismissRoomMembersSheet() {
        inviteSearchJob?.cancel()
        _uiState.update {
            it.copy(
                roomMembersSheetOpen = false,
                roomMembers = emptyList(),
                roomMembersLoading = false,
                roomMembersError = null,
                inviteMemberDialogOpen = false,
                inviteMemberQuery = "",
                inviteMemberSuggestions = emptyList(),
                inviteMemberLoading = false,
                rolePickerTarget = null
            )
        }
    }

    fun refreshRoomMembers() {
        if (!_uiState.value.roomMembersSheetOpen || !supportsRoomManagement()) return
        reloadRoomMembers()
    }

    private fun reloadRoomMembers() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(roomMembersLoading = true) }
            chatRoomActionsRepository.loadAllMembers(roomId, roomType).fold(
                onSuccess = { list ->
                    val rows = list.map { m ->
                        RoomMemberRowUi(m._id, m.username, m.name)
                    }
                    _uiState.update {
                        it.copy(
                            roomMembers = rows,
                            roomMembersLoading = false,
                            roomMembersError = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            roomMembersLoading = false,
                            roomMembersError = e.message
                                ?: appContext.getString(R.string.chat_vm_members_load_error)
                        )
                    }
                }
            )
        }
    }

    fun openInviteMemberDialog() {
        _uiState.update {
            it.copy(
                inviteMemberDialogOpen = true,
                inviteMemberQuery = "",
                inviteMemberSuggestions = emptyList()
            )
        }
    }

    fun dismissInviteMemberDialog() {
        inviteSearchJob?.cancel()
        _uiState.update {
            it.copy(
                inviteMemberDialogOpen = false,
                inviteMemberQuery = "",
                inviteMemberSuggestions = emptyList(),
                inviteMemberLoading = false
            )
        }
    }

    fun onInviteMemberQueryChange(q: String) {
        _uiState.update { it.copy(inviteMemberQuery = q) }
        inviteSearchJob?.cancel()
        val query = q.trim()
        if (query.length < 1) {
            _uiState.update {
                it.copy(inviteMemberSuggestions = emptyList(), inviteMemberLoading = false)
            }
            return
        }
        _uiState.update { it.copy(inviteMemberLoading = true) }
        inviteSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            if (!isActive) return@launch
            try {
                val resp = searchRepository.spotlight(query)
                if (!isActive) return@launch
                val users = resp?.users?.take(15) ?: emptyList()
                _uiState.update {
                    it.copy(inviteMemberSuggestions = users, inviteMemberLoading = false)
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "invite spotlight: ${e.message}")
                if (isActive) {
                    _uiState.update {
                        it.copy(inviteMemberSuggestions = emptyList(), inviteMemberLoading = false)
                    }
                }
            }
        }
    }

    fun inviteMember(userId: String) {
        if (!supportsRoomManagement()) return
        viewModelScope.launch(Dispatchers.IO) {
            chatRoomActionsRepository.inviteUser(roomId, roomType, userId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            toastMessage = appContext.getString(R.string.chat_vm_invite_sent),
                            inviteMemberDialogOpen = false,
                            inviteMemberQuery = "",
                            inviteMemberSuggestions = emptyList(),
                            inviteMemberLoading = false
                        )
                    }
                    reloadRoomMembers()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_invite_failed)
                        )
                    }
                }
            )
        }
    }

    fun openRolePicker(member: RoomMemberRowUi) {
        _uiState.update { it.copy(rolePickerTarget = member) }
    }

    fun dismissRolePicker() {
        _uiState.update { it.copy(rolePickerTarget = null) }
    }

    fun applyMemberRole(action: RoomMemberRoleAction) {
        val target = _uiState.value.rolePickerTarget ?: return
        if (target.id == _uiState.value.currentUserId) {
            _uiState.update {
                it.copy(
                    toastMessage = appContext.getString(R.string.chat_vm_role_self),
                    rolePickerTarget = null
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val r = when (action) {
                RoomMemberRoleAction.ADD_MODERATOR ->
                    chatRoomActionsRepository.setModerator(roomId, roomType, target.id, true)
                RoomMemberRoleAction.REMOVE_MODERATOR ->
                    chatRoomActionsRepository.setModerator(roomId, roomType, target.id, false)
                RoomMemberRoleAction.ADD_OWNER ->
                    chatRoomActionsRepository.setOwner(roomId, roomType, target.id, true)
                RoomMemberRoleAction.REMOVE_OWNER ->
                    chatRoomActionsRepository.setOwner(roomId, roomType, target.id, false)
            }
            r.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            toastMessage = appContext.getString(R.string.chat_vm_done),
                            rolePickerTarget = null
                        )
                    }
                    reloadRoomMembers()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message ?: appContext.getString(R.string.error_generic),
                            rolePickerTarget = null
                        )
                    }
                }
            )
        }
    }

    fun leaveCurrentRoom() {
        if (!supportsRoomManagement()) return
        viewModelScope.launch(Dispatchers.IO) {
            chatRoomActionsRepository.leaveRoom(roomId, roomType).fold(
                onSuccess = {
                    realtimeService.unsubscribe(roomId)
                    roomRepository.syncRooms()
                    _uiState.update { it.copy(navigateBackAfterLeave = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_leave_failed)
                        )
                    }
                }
            )
        }
    }

    fun consumeNavigateBackAfterLeave() {
        _uiState.update { it.copy(navigateBackAfterLeave = false) }
    }

    fun consumePendingOpenRoom() {
        _uiState.update { it.copy(pendingOpenRoom = null) }
    }

    fun consumePendingThreadNavigation() {
        _uiState.update { it.copy(pendingThreadNavigation = null) }
    }

    fun createDiscussionFromMenu(title: String) {
        if (!supportsRoomManagement()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resp = chatRoomActionsRepository.createDiscussion(roomId, title)
            if (resp.success) {
                roomRepository.syncRooms()
                val disc = resp.discussion
                val pending = if (disc != null) {
                    PendingOpenRoomChat(
                        disc._id,
                        disc.fname ?: disc.name ?: title.trim().ifBlank {
                            appContext.getString(R.string.default_discussion)
                        },
                        disc.t ?: "p"
                    )
                } else null
                _uiState.update {
                    it.copy(
                        toastMessage = appContext.getString(R.string.chat_vm_discussion_created),
                        pendingOpenRoom = pending
                    )
                }
            } else {
                _uiState.update {
                    it.copy(toastMessage = resp.error ?: appContext.getString(R.string.error_generic))
                }
            }
        }
    }

    /**
     * Permalink как в веб-клиенте Rocket.Chat — иначе [chat.postMessage] с [message_link]
     * не строит превью (в т.ч. для картинок без текста) и в целевом чате видно «пустое» сообщение.
     * Форматы: `/channel/name`, `/group/name`, для лички — `/direct/{rid}` (как у веб-клиента), не username.
     */
    fun buildPermalink(messageId: String): String {
        return buildPermalinkImpl(messageId, permalinkPathSegment(null))
    }

    /**
     * Сегмент пути после `/direct/` | `/channel/` | `/group/`.
     *
     * Для **лички** официальный клиент использует `/direct/{roomName}` где `roomName` — **техническое имя DM с сервера**
     * (часто конкатенация идентификаторов, см. пример с `Wm5e8xn...XhgcYq5...`), а не username в чистом виде.
     * Поэтому сначала [RoomEntity.name] / кэш, и только потом fallback на username ([avatarPath]).
     */
    private fun permalinkPathSegment(room: RoomEntity?): String {
        if (roomType == "d") {
            // chat.postMessage + message_link: сервер валидирует href — для лички нужен rid/тех. id комнаты
            // (как в веб-клиенте: /direct/Wm5e8xn...XhgcYq5...), иначе часто HTTP 400 «Invalid href value provided».
            // Username в пути (/direct/a.dvorchenko) здесь не подходит, даже если так в Room.name.
            roomId.trim().takeIf { it.isNotEmpty() }?.let { return it }
            val dmSlug = room?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: cachedRoomSlug?.takeIf { it.isNotEmpty() }
            if (!dmSlug.isNullOrEmpty()) return dmSlug
            val fromRoom = room?.avatarPath?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("room/") }
            val fromNav = avatarPathArg.trim().takeIf { it.isNotEmpty() && !it.startsWith("room/") }
            val peer = fromRoom ?: fromNav ?: cachedDmPeerUsername?.trim()?.takeIf { it.isNotEmpty() }
            if (!peer.isNullOrEmpty()) return peer.lowercase(Locale.ROOT)
        }
        return (room?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: cachedRoomSlug?.takeIf { it.isNotEmpty() }
            ?: roomNameArg.trim())
    }

    private fun buildPermalinkImpl(messageId: String, roomPathSegment: String): String {
        val srv = _uiState.value.serverUrl?.trimEnd('/') ?: ""
        val pathPrefix = when (roomType) {
            "c" -> "channel"
            "p" -> "group"
            "d" -> "direct"
            else -> "channel"
        }
        return if (roomPathSegment.isNotEmpty()) {
            val enc = Uri.encode(roomPathSegment, null)
            "$srv/$pathPrefix/$enc?msg=$messageId"
        } else {
            "$srv/$pathPrefix/${Uri.encode(roomId, null)}?msg=$messageId"
        }
    }

    fun downloadFile(url: String, fileName: String) {
        try {
            val state = _uiState.value
            val fullUrl = if (url.startsWith("http")) url
                else (state.serverUrl?.trimEnd('/') ?: "") + url

            val request = DownloadManager.Request(Uri.parse(fullUrl)).apply {
                setTitle(fileName)
                setDescription(appContext.getString(R.string.chat_vm_upload_download_title))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                state.authToken?.let { addRequestHeader("X-Auth-Token", it) }
                state.authUserId?.let { addRequestHeader("X-User-Id", it) }
            }
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            _uiState.update {
                it.copy(toastMessage = appContext.getString(R.string.chat_vm_upload_progress, fileName))
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    toastMessage = appContext.getString(
                        R.string.chat_vm_upload_error,
                        e.message ?: ""
                    )
                )
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun loadReadReceipts(messageId: String) {
        _uiState.update {
            it.copy(
                readReceiptsSheet = ReadReceiptsSheetState(messageId = messageId, loading = true)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = messageRepository.getReadReceiptReaders(messageId)
            _uiState.update { s ->
                val sheet = s.readReceiptsSheet ?: return@update s
                if (sheet.messageId != messageId) return@update s
                result.fold(
                    onSuccess = { readers ->
                        s.copy(
                            readReceiptsSheet = sheet.copy(
                                loading = false,
                                readers = readers,
                                error = null
                            )
                        )
                    },
                    onFailure = { e ->
                        s.copy(
                            readReceiptsSheet = sheet.copy(
                                loading = false,
                                error = e.message ?: appContext.getString(R.string.error_generic)
                            )
                        )
                    }
                )
            }
        }
    }

    fun dismissReadReceiptsSheet() {
        _uiState.update { it.copy(readReceiptsSheet = null) }
    }

    fun clearForwardErrorReport() {
        _uiState.update { it.copy(forwardErrorReport = null) }
    }

    fun clearUploadErrorReport() {
        _uiState.update { it.copy(uploadErrorReport = null) }
    }

    private fun failureReportForUpload(e: Throwable): String = when (e) {
        is FileUploadDebugException -> e.debugReport
        else -> buildString {
            appendLine(appContext.getString(R.string.chat_upload_debug_header))
            appendLine("${e.javaClass.name}: ${e.message}")
        }
    }

    /**
     * [query] — текст после «@» до курсора; null если курсор не в режиме упоминания.
     */
    fun onMentionQueryChange(query: String?) {
        mentionSearchJob?.cancel()
        if (query == null) {
            _uiState.update { it.copy(mentionSuggestions = emptyList(), mentionSearchLoading = false) }
            return
        }
        if (query.length < 1) {
            _uiState.update { it.copy(mentionSuggestions = emptyList(), mentionSearchLoading = false) }
            return
        }
        _uiState.update { it.copy(mentionSearchLoading = true) }
        mentionSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            if (!isActive) return@launch
            try {
                val resp = searchRepository.spotlight(query)
                if (!isActive) return@launch
                val users = resp?.users?.take(15) ?: emptyList()
                _uiState.update {
                    it.copy(mentionSuggestions = users, mentionSearchLoading = false)
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "mention spotlight: ${e.message}")
                if (isActive) {
                    _uiState.update { it.copy(mentionSuggestions = emptyList(), mentionSearchLoading = false) }
                }
            }
        }
    }

    fun clearMentionSuggestions() {
        mentionSearchJob?.cancel()
        _uiState.update { it.copy(mentionSuggestions = emptyList(), mentionSearchLoading = false) }
    }

    fun onInRoomSearchQueryChange(q: String) {
        _uiState.update { it.copy(inRoomSearchQuery = q) }
        val trimmed = q.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(inRoomSearchResults = emptyList(), inRoomSearchLoading = false) }
            return
        }
        if (isThread) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(inRoomSearchLoading = true) }
            val r = searchRepository.searchMessagesInRoom(roomId, trimmed)
            r.onSuccess { list ->
                _uiState.update { it.copy(inRoomSearchResults = list, inRoomSearchLoading = false) }
            }
            r.onFailure { e ->
                _uiState.update { it.copy(inRoomSearchLoading = false, toastMessage = e.message) }
            }
        }
    }

    fun clearInRoomSearch() {
        _uiState.update { it.copy(inRoomSearchQuery = "", inRoomSearchResults = emptyList()) }
    }

    fun setForwardMessage(message: MessageEntity?) {
        _uiState.update { it.copy(forwardToMessage = message) }
    }

    fun forwardToRoom(targetRoomId: String) {
        val msg = _uiState.value.forwardToMessage ?: return
        if (targetRoomId == roomId) {
            _uiState.update {
                it.copy(
                    toastMessage = appContext.getString(R.string.chat_vm_forward_pick_other),
                    forwardToMessage = null
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val room = roomRepository.getRoom(roomId)
            val segment = permalinkPathSegment(room)
            val link = buildPermalinkImpl(msg.id, segment)
            val result = messageRepository.forwardMessage(
                targetRoomId,
                msg,
                link,
                _uiState.value.serverUrl?.trimEnd('/')
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        toastMessage = appContext.getString(R.string.chat_vm_forward_success),
                        forwardToMessage = null
                    )
                }
            }
            result.onFailure { e ->
                val report = when (e) {
                    is ForwardMessageDebugException -> e.debugReport
                    else -> buildString {
                        appendLine(appContext.getString(R.string.chat_forward_debug_header))
                        appendLine("exception=${e.javaClass.name}")
                        appendLine("message=${e.message}")
                    }
                }
                Log.e("ChatViewModel", "forwardToRoom failed:\n$report", e)
                _uiState.update {
                    it.copy(
                        forwardErrorReport = report,
                        forwardToMessage = null
                    )
                }
            }
        }
    }

    fun startVideoCall(onOpenUrl: (String) -> Unit) {
        if (isThread) {
            _uiState.update {
                it.copy(toastMessage = appContext.getString(R.string.chat_vm_call_unavailable_in_thread))
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val r = videoConferenceRepository.startCallAndGetJoinUrl(roomId)
            r.onSuccess { url ->
                withContext(Dispatchers.Main) { onOpenUrl(url) }
            }
            r.onFailure { e ->
                _uiState.update {
                    it.copy(
                        toastMessage = e.message
                            ?: appContext.getString(R.string.chat_vm_call_start_failed)
                    )
                }
            }
        }
    }

    fun openUserProfile(userId: String, username: String?, fallbackName: String?) {
        if (userId.isBlank() || userId == _uiState.value.currentUserId) return
        _uiState.update {
            it.copy(
                userProfileSheet = UserProfileSheetUi(
                    userId = userId,
                    usernameHint = username,
                    fallbackName = fallbackName
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            searchRepository.getUserById(userId).fold(
                onSuccess = { u ->
                    userPresenceStore.applyFromUserInfo(u)
                    _uiState.update { s ->
                        val sheet = s.userProfileSheet ?: return@update s
                        if (sheet.userId != userId) return@update s
                        s.copy(userProfileSheet = sheet.copy(loading = false, details = u, error = null))
                    }
                },
                onFailure = { e ->
                    _uiState.update { s ->
                        val sheet = s.userProfileSheet ?: return@update s
                        if (sheet.userId != userId) return@update s
                        s.copy(
                            userProfileSheet = sheet.copy(
                                loading = false,
                                error = e.message
                                    ?: appContext.getString(R.string.chat_vm_profile_load_failed)
                            )
                        )
                    }
                }
            )
        }
    }

    fun dismissUserProfile() {
        _uiState.update { it.copy(userProfileSheet = null) }
    }

    fun consumeNavigateToDirectChat() {
        _uiState.update { it.copy(navigateToDirectChat = null) }
    }

    fun profileOpenDirectChat() {
        val sheet = _uiState.value.userProfileSheet ?: return
        val un = sheet.details?.username ?: sheet.usernameHint
        if (un.isNullOrBlank()) {
            _uiState.update {
                it.copy(toastMessage = appContext.getString(R.string.chat_vm_dm_no_username))
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            searchRepository.createDirectMessage(un).fold(
                onSuccess = { rid ->
                    if (rid == roomId && roomType == "d") {
                        _uiState.update {
                            it.copy(
                                userProfileSheet = null,
                                toastMessage = appContext.getString(R.string.chat_vm_dm_already_in_chat)
                            )
                        }
                        return@launch
                    }
                    val u = sheet.details
                    val title = sequenceOf(u?.name, u?.nickname, sheet.fallbackName, un)
                        .mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() } }
                        .firstOrNull() ?: un
                    _uiState.update {
                        it.copy(
                            userProfileSheet = null,
                            navigateToDirectChat = NavigateToDirectChat(
                                roomId = rid,
                                title = title,
                                avatarPath = un
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_dm_open_failed)
                        )
                    }
                }
            )
        }
    }

    fun profileStartVideoCall(onOpenUrl: (String) -> Unit) {
        if (isThread) {
            _uiState.update {
                it.copy(toastMessage = appContext.getString(R.string.chat_vm_call_unavailable_in_thread))
            }
            return
        }
        val sheet = _uiState.value.userProfileSheet ?: return
        val peerId = sheet.userId
        val un = sheet.details?.username ?: sheet.usernameHint
        if (un.isNullOrBlank()) {
            _uiState.update {
                it.copy(toastMessage = appContext.getString(R.string.chat_vm_call_no_username))
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val targetRoom = if (roomType == "d" && isDirectChatWithPeer(peerId)) {
                roomId
            } else {
                searchRepository.createDirectMessage(un).getOrElse { e ->
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_call_prepare_failed)
                        )
                    }
                    return@launch
                }
            }
            videoConferenceRepository.startCallAndGetJoinUrl(targetRoom).fold(
                onSuccess = { url ->
                    withContext(Dispatchers.Main) { onOpenUrl(url) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            toastMessage = e.message
                                ?: appContext.getString(R.string.chat_vm_call_start_failed)
                        )
                    }
                }
            )
        }
    }

    private fun isDirectChatWithPeer(peerUserId: String): Boolean {
        if (roomType != "d") return false
        val my = _uiState.value.currentUserId ?: return false
        if (peerUserId.isBlank() || peerUserId == my) return false
        return _uiState.value.messages.any { it.userId == peerUserId }
    }

    /** В уже открытой личке с этим пользователем кнопку «Личные сообщения» не показываем. */
    fun shouldShowProfileDirectChatButton(peerUserId: String): Boolean {
        if (roomType != "d") return true
        return !isDirectChatWithPeer(peerUserId)
    }

    // ── Опросы ──────────────────────────────────────────────────────────────

    /**
     * Множество ключей `"$msgId:$value"` — варианты, за которые пользователь
     * проголосовал в текущей сессии. Хранится в памяти (сбрасывается при выходе из чата).
     * Сервер обновит сообщение через WebSocket, и прогресс-бары обновятся автоматически.
     */
    private val _votedPollOptions = MutableStateFlow<Set<String>>(emptySet())
    val votedPollOptions: StateFlow<Set<String>> = _votedPollOptions.asStateFlow()

    /**
     * Отправляет голос за вариант опроса через REST `POST /api/apps/ui.interaction/{appId}`.
     * Ключ оптимистичного состояния: `"$msgId:$value"`.
     */
    fun votePoll(
        msgId: String,
        votedRoomId: String,
        appId: String,
        blockId: String,
        actionId: String,
        value: String
    ) {
        // Оптимистично помечаем вариант как проголосованный
        _votedPollOptions.value = _votedPollOptions.value + "$msgId:$value"
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.sendBlockActionRest(
                appId = appId,
                actionId = actionId,
                blockId = blockId,
                value = value,
                msgId = msgId,
                roomId = votedRoomId
            ).onFailure { e ->
                // Откатываем оптимистичный голос при ошибке
                _votedPollOptions.value = _votedPollOptions.value - "$msgId:$value"
                _uiState.update { it.copy(toastMessage = e.message ?: "Не удалось проголосовать") }
            }
        }
    }


    /**
     * Создаёт опрос через двухшаговый поток:
     * 1. Вызывает `/poll` через REST → сервер присылает `modal.open` через WebSocket
     * 2. Из WebSocket-события берём реальные `triggerId` и `viewId`
     * 3. Отправляем `viewSubmit` с введёнными данными — Poll App создаёт опрос
     */
    fun createPoll(
        question: String,
        options: List<String>,
        anonymous: Boolean,
        multipleChoice: Boolean
    ) {
        if (roomId.isBlank() || question.isBlank() || options.size < 2) return
        viewModelScope.launch(Dispatchers.IO) {
            // Генерируем свой triggerId для запуска команды (как делает веб-клиент)
            val expectedTriggerId = java.util.UUID.randomUUID().toString().replace("-", "").take(17)

            // НАЧИНАЕМ СЛУШАТЬ ДО ОТПРАВКИ КОМАНДЫ (Race Condition Fix)
            val eventDeferred = async {
                withTimeoutOrNull(10_000L) {
                    realtimeService.uiInteractionEvents.first { it.triggerId == expectedTriggerId }
                }
            }

            // Шаг 1: запускаем /poll с нашим triggerId — Poll App откроет диалог через WebSocket
            messageRepository.runSlashCommand(roomId, "poll", "", tmid = null, triggerId = expectedTriggerId)
                .onFailure { e ->
                    _uiState.update { it.copy(toastMessage = e.message ?: "Не удалось запустить /poll") }
                    eventDeferred.cancel()
                    return@launch
                }

            // Шаг 2: ждём событие modal.open от Poll App с нашим triggerId (до 10 секунд)
            val event = eventDeferred.await()
            if (event == null) {
                _uiState.update { it.copy(toastMessage = "Таймаут: сервер не ответил модальным окном") }
                return@launch
            }

            // Шаг 3: отправляем viewSubmit с реальным triggerId
            messageRepository.submitPollModal(
                appId = event.triggerId.let { messageRepository.pollAppId },
                triggerId = event.triggerId,
                viewId = event.viewId,
                serverViewJson = event.viewJson,
                roomId = roomId,
                question = question,
                options = options,
                anonymous = anonymous,
                multipleChoice = multipleChoice
            ).onFailure { e ->
                _uiState.update { it.copy(toastMessage = e.message ?: "Не удалось создать опрос") }
            }
        }
    }


    override fun onCleared() {
        quoteHighlightJob?.cancel()
        inviteSearchJob?.cancel()
        if (roomId.isNotBlank()) {
            openedChatTracker.clearIfMatches(roomId)
        }
        super.onCleared()
    }
}

