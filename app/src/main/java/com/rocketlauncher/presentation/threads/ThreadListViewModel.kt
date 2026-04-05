package com.rocketlauncher.presentation.threads

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.RoomDto
import com.rocketlauncher.data.realtime.UserPresenceSnapshot
import com.rocketlauncher.data.realtime.UserPresenceStore
import com.rocketlauncher.data.repository.SessionPrefs
import com.rocketlauncher.presentation.navigation.safeUrlDecode
import com.rocketlauncher.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ThreadItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val replyCount: Int,
    val lastActivity: String?,
    val type: ThreadItemType,
    val targetRoomId: String? = null,
    val targetRoomType: String? = null
)

enum class ThreadItemType { MAIN, THREAD, DISCUSSION }

data class ThreadListUiState(
    val items: List<ThreadItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverUrl: String? = null,
    val presenceSnapshot: UserPresenceSnapshot = UserPresenceSnapshot()
)

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiProvider: ApiProvider,
    private val sessionPrefs: SessionPrefs,
    private val userPresenceStore: UserPresenceStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val roomId: String = savedStateHandle.get<String>("roomId") ?: ""
    val roomType: String = savedStateHandle.get<String>("roomType") ?: "c"
    val roomName: String = safeUrlDecode(savedStateHandle.get<String>("roomName"))
    val avatarPath: String = safeUrlDecode(savedStateHandle.get<String>("avatarPath"))

    private val _uiState = MutableStateFlow(ThreadListUiState())
    val uiState: StateFlow<ThreadListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(serverUrl = sessionPrefs.getServerUrl()) }
        }
        viewModelScope.launch {
            userPresenceStore.snapshot.collect { snap ->
                _uiState.update { it.copy(presenceSnapshot = snap) }
            }
        }
        loadThreadsAndDiscussions()
    }

    fun loadThreadsAndDiscussions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = withContext(Dispatchers.IO) { fetchAll() }
                _uiState.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                Log.e("ThreadListVM", "load failed: ${e.message}")
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private suspend fun fetchAll(): List<ThreadItem> {
        val api = apiProvider.getApi() ?: return listOf(mainItem())
        val result = mutableListOf(mainItem())

        try {
            val threads = api.getThreadsList(roomId)
            if (threads.success) {
                result.addAll(threads.threads.map { it.toThreadItem() })
            }
        } catch (e: Exception) {
            Log.e("ThreadListVM", "getThreadsList: ${e.message}")
        }

        if (roomType != "d") {
            try {
                val discussions = when (roomType) {
                    "p" -> api.getGroupDiscussions(roomId)
                    else -> api.getChannelDiscussions(roomId)
                }
                if (discussions.success) {
                    result.addAll(discussions.discussions.map { it.toDiscussionItem() })
                }
            } catch (e: Exception) {
                Log.e("ThreadListVM", "getDiscussions: ${e.message}")
            }
        }

        return result
    }

    private fun mainItem() = ThreadItem(
        id = "main",
        title = appContext.getString(R.string.room_sub_main),
        subtitle = roomName,
        replyCount = 0,
        lastActivity = null,
        type = ThreadItemType.MAIN
    )

    private fun MessageDto.toThreadItem(): ThreadItem {
        val preview = msg.take(120).let { if (msg.length > 120) "$it..." else it }
        return ThreadItem(
            id = _id,
            title = preview.ifBlank { appContext.getString(R.string.room_thread_no_text) },
            subtitle = appContext.getString(
                R.string.room_thread_subtitle,
                u.name ?: u.username ?: "?",
                tcount ?: 0
            ),
            replyCount = tcount ?: 0,
            lastActivity = tlm,
            type = ThreadItemType.THREAD
        )
    }

    private fun RoomDto.toDiscussionItem(): ThreadItem {
        return ThreadItem(
            id = _id,
            title = fname ?: name ?: _id,
            subtitle = appContext.getString(R.string.thread_discussion_msgs_count, msgs),
            replyCount = msgs,
            lastActivity = lm ?: _updatedAt,
            type = ThreadItemType.DISCUSSION,
            targetRoomId = _id,
            targetRoomType = t ?: "p"
        )
    }
}
