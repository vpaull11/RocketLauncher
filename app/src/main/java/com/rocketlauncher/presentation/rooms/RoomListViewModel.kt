package com.rocketlauncher.presentation.rooms

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.db.RoomEntity
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.RoomAutocompleteItemDto
import com.rocketlauncher.data.dto.SpotlightRoomDto
import com.rocketlauncher.data.dto.SpotlightUserDto
import com.rocketlauncher.data.dto.RoomDto
import com.rocketlauncher.data.dto.SubscriptionDto
import com.rocketlauncher.data.realtime.RealtimeMessageService
import com.rocketlauncher.data.realtime.UserPresenceSnapshot
import com.rocketlauncher.data.realtime.UserPresenceStore
import com.rocketlauncher.data.repository.AppUpdateCheckResult
import com.rocketlauncher.data.repository.AppUpdateRepository
import com.rocketlauncher.data.repository.ChatRoomActionsRepository
import com.rocketlauncher.data.repository.AuthRepository
import com.rocketlauncher.data.repository.RoomRepository
import com.rocketlauncher.data.repository.SearchRepository
import com.rocketlauncher.data.repository.SessionPrefs
import com.rocketlauncher.data.notifications.ThreadParticipationPrefs
import com.rocketlauncher.data.repository.RoomListPreferences
import com.rocketlauncher.data.repository.ThemePreferences
import com.rocketlauncher.R
import com.rocketlauncher.domain.model.FavoriteDisplayMode
import com.rocketlauncher.domain.usecase.SyncChatsFromServerUseCase
import com.rocketlauncher.presentation.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import javax.inject.Inject
import androidx.annotation.StringRes

enum class RoomGroup(@StringRes val titleRes: Int, val order: Int) {
    FAVORITES(R.string.room_group_favorites, -1),
    UNREAD(R.string.room_group_unread, 0),
    TEAMS(R.string.room_group_teams, 1),
    DISCUSSIONS(R.string.room_group_discussions, 2),
    CHANNELS(R.string.room_group_channels, 3),
    DM(R.string.room_group_dm, 4)
}

data class GroupedRoom(
    val group: RoomGroup,
    val room: RoomEntity
)

data class SubItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val count: Int,
    val unreadCount: Int = 0,
    val userMentions: Int = 0,
    val type: SubItemType,
    val targetRoomId: String? = null,
    val targetRoomType: String? = null
)

enum class SubItemType { MAIN, THREAD, DISCUSSION }

sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data object Checking : AppUpdateUiState()
    data class Prompt(val remoteVersion: String, val notes: String?, val apkUrl: String) : AppUpdateUiState()
    data class Downloading(val progress: Float?) : AppUpdateUiState()
    data class PendingInstallPermission(val apkFile: File) : AppUpdateUiState()
}

/** В раскрытии: «Основной» + сабчаты, не более [MAX_EXPANDED_SUBCHAT_ROWS] строк всего. */
private const val MAX_EXPANDED_SUBCHAT_ROWS = 7
private const val MAX_CHILD_SUBITEMS = MAX_EXPANDED_SUBCHAT_ROWS - 1

private data class RoomListRoomsAggregate(
    val groupedRooms: List<Pair<RoomGroup, List<RoomEntity>>>,
    val searchQuery: String,
    val presenceSnapshot: UserPresenceSnapshot
)

data class RoomListUiState(
    val groupedRooms: List<Pair<RoomGroup, List<RoomEntity>>> = emptyList(),
    val serverUrl: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val debugInfo: String = "",
    val expandedRoomId: String? = null,
    val subItems: List<SubItem> = emptyList(),
    val subItemsLoading: Boolean = false,
    val collapsedGroups: Set<RoomGroup> = emptySet(),
    /** Результаты spotlight / autocomplete на сервере (при вводе в поиске) */
    val remoteSearchLoading: Boolean = false,
    val spotlightUsers: List<SpotlightUserDto> = emptyList(),
    val spotlightRooms: List<SpotlightRoomDto> = emptyList(),
    val autocompleteRooms: List<RoomAutocompleteItemDto> = emptyList(),
    val createRoomDialog: CreateRoomDialogState? = null,
    val pendingOpenCreatedRoom: PendingOpenCreatedRoom? = null,
    val toastMessage: String? = null,
    val presenceSnapshot: UserPresenceSnapshot = UserPresenceSnapshot(),
    val appUpdate: AppUpdateUiState = AppUpdateUiState.Idle
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RoomListViewModel @Inject constructor(
    private val syncChatsFromServerUseCase: SyncChatsFromServerUseCase,
    private val roomRepository: RoomRepository,
    private val roomDao: RoomDao,
    private val searchRepository: SearchRepository,
    private val themePreferences: ThemePreferences,
    private val roomListPreferences: RoomListPreferences,
    private val sessionPrefs: SessionPrefs,
    private val authRepository: AuthRepository,
    private val realtimeService: RealtimeMessageService,
    private val apiProvider: ApiProvider,
    private val chatRoomActionsRepository: ChatRoomActionsRepository,
    private val userPresenceStore: UserPresenceStore,
    private val threadParticipationPrefs: ThreadParticipationPrefs,
    private val appUpdateRepository: AppUpdateRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private var appUpdateJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(RoomListUiState())
    val uiState: StateFlow<RoomListUiState> = _uiState.asStateFlow()

    /** Текущий режим темы (для меню в списке чатов) */
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    /** Как показывать избранное: отдельной группой или сверху внутри групп. */
    val favoriteDisplayMode: StateFlow<FavoriteDisplayMode> =
        roomListPreferences.favoriteDisplayMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FavoriteDisplayMode.INLINE_IN_GROUPS
        )

    fun setFavoriteDisplayMode(mode: FavoriteDisplayMode) {
        viewModelScope.launch(Dispatchers.IO) {
            roomListPreferences.setFavoriteDisplayMode(mode)
        }
    }

    /** Состояние WebSocket к серверу чатов (индикатор в шапке списка). */
    val wsConnectionState: StateFlow<RealtimeMessageService.ConnectionState> =
        realtimeService.connectionState

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(Dispatchers.IO) {
            themePreferences.setThemeMode(mode)
        }
    }

    /** Уведомления по чату: цикл «все → только основной канал → выкл». */
    fun cycleRoomNotificationPolicy(roomId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                roomRepository.cycleRoomNotificationPolicy(roomId)
            } catch (e: Exception) {
                Log.e("RoomListVM", "cycleRoomNotificationPolicy: ${e.message}")
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(serverUrl = sessionPrefs.getServerUrl()) }
        }
        viewModelScope.launch {
            _uiState
                .map { it.expandedRoomId }
                .distinctUntilChanged()
                .flatMapLatest { expandedId ->
                    if (expandedId == null) flowOf(null)
                    else roomRepository.observeRoom(expandedId).debounce(400L)
                }
                .collect { room ->
                    room?.let { applySubscriptionPatch(it) }
                }
        }

        viewModelScope.launch {
            combine(
                roomRepository.observeRooms(),
                _searchQuery,
                userPresenceStore.snapshot,
                favoriteDisplayMode
            ) { rooms, query, presenceSnap, favMode ->
                val filtered = if (query.isBlank()) rooms
                else {
                    val q = query.lowercase()
                    rooms.filter { room ->
                        (room.displayName ?: "").lowercase().contains(q) ||
                            (room.name ?: "").lowercase().contains(q)
                    }
                }
                RoomListRoomsAggregate(
                    groupedRooms = groupAndSort(filtered, favMode),
                    searchQuery = query,
                    presenceSnapshot = presenceSnap
                )
            }
            .catch { e -> _uiState.update { it.copy(error = e.message) } }
            .collect { agg ->
                _uiState.update {
                    it.copy(
                        groupedRooms = agg.groupedRooms,
                        searchQuery = agg.searchQuery,
                        presenceSnapshot = agg.presenceSnapshot
                    )
                }
            }
        }

        /** Статусы для личек: users.info сразу после появления комнат в БД (не только после открытия чата). */
        viewModelScope.launch(Dispatchers.IO) {
            roomRepository.observeRooms()
                .map { rooms -> dmPeerUsernamesFromRooms(rooms) }
                .distinctUntilChanged()
                .debounce(350L)
                .collect { usernames ->
                    prefetchDmPeersPresence(usernames)
                }
        }

        viewModelScope.launch {
            _searchQuery
                .debounce(400L)
                .collect { q ->
                    if (q.length < 2) {
                        _uiState.update {
                            it.copy(
                                remoteSearchLoading = false,
                                spotlightUsers = emptyList(),
                                spotlightRooms = emptyList(),
                                autocompleteRooms = emptyList()
                            )
                        }
                        return@collect
                    }
                    _uiState.update { it.copy(remoteSearchLoading = true) }
                    val spot = withContext(Dispatchers.IO) { searchRepository.spotlight(q) }
                    val auto = withContext(Dispatchers.IO) { searchRepository.autocompleteRooms(q) }
                    _uiState.update {
                        it.copy(
                            remoteSearchLoading = false,
                            spotlightUsers = spot?.users ?: emptyList(),
                            spotlightRooms = spot?.rooms ?: emptyList(),
                            autocompleteRooms = auto?.items ?: emptyList()
                        )
                    }
                }
        }
    }

    private fun groupAndSort(
        rooms: List<RoomEntity>,
        favoriteMode: FavoriteDisplayMode
    ): List<Pair<RoomGroup, List<RoomEntity>>> {
        val byTime = Comparator<RoomEntity> { a, b ->
            val ta = a.lastMessageTime ?: ""
            val tb = b.lastMessageTime ?: ""
            tb.compareTo(ta)
        }

        if (favoriteMode == FavoriteDisplayMode.SEPARATE_GROUP) {
            return groupAndSortSeparateFavorites(rooms, byTime)
        }

        val unread = mutableListOf<RoomEntity>()
        val teams = mutableListOf<RoomEntity>()
        val discussions = mutableListOf<RoomEntity>()
        val channels = mutableListOf<RoomEntity>()
        val dm = mutableListOf<RoomEntity>()

        for (room in rooms) {
            when {
                roomHasUnread(room) -> unread.add(room)
                room.isTeam -> teams.add(room)
                room.isDiscussion -> discussions.add(room)
                room.type == "d" -> dm.add(room)
                else -> channels.add(room)
            }
        }

        fun sortBucket(list: List<RoomEntity>) =
            sortRoomsInBucket(list, byTime, pinFavoritesFirst = true)

        val sections = listOf(
            RoomGroup.UNREAD to sortBucket(unread),
            RoomGroup.TEAMS to sortBucket(teams),
            RoomGroup.DISCUSSIONS to sortBucket(discussions),
            RoomGroup.CHANNELS to sortBucket(channels),
            RoomGroup.DM to sortBucket(dm)
        )
        return sections.filter { it.second.isNotEmpty() }
    }

    /**
     * Избранное отдельной группой:
     * 1) **Непрочитанные** — все чаты с непрочитанным (включая избранные);
     * 2) **Избранное** — только избранные **без** непрочитанного;
     * 3) Остальные группы — без непрочитанных и без избранного (они уже в п.1–2).
     */
    private fun groupAndSortSeparateFavorites(
        rooms: List<RoomEntity>,
        byTime: Comparator<RoomEntity>
    ): List<Pair<RoomGroup, List<RoomEntity>>> {
        val unreadAll = rooms.filter(::roomHasUnread).sortedWith(byTime)
        val favoritesReadOnly = rooms.filter { it.isFavorite && !roomHasUnread(it) }.sortedWith(byTime)

        val teams = mutableListOf<RoomEntity>()
        val discussions = mutableListOf<RoomEntity>()
        val channels = mutableListOf<RoomEntity>()
        val dm = mutableListOf<RoomEntity>()
        for (room in rooms) {
            if (roomHasUnread(room)) continue
            if (room.isFavorite) continue
            when {
                room.isTeam -> teams.add(room)
                room.isDiscussion -> discussions.add(room)
                room.type == "d" -> dm.add(room)
                else -> channels.add(room)
            }
        }
        fun byTimeOnly(list: List<RoomEntity>) = list.sortedWith(byTime)

        val sections = mutableListOf<Pair<RoomGroup, List<RoomEntity>>>()
        if (unreadAll.isNotEmpty()) sections.add(RoomGroup.UNREAD to unreadAll)
        if (favoritesReadOnly.isNotEmpty()) sections.add(RoomGroup.FAVORITES to favoritesReadOnly)
        sections.add(RoomGroup.TEAMS to byTimeOnly(teams))
        sections.add(RoomGroup.DISCUSSIONS to byTimeOnly(discussions))
        sections.add(RoomGroup.CHANNELS to byTimeOnly(channels))
        sections.add(RoomGroup.DM to byTimeOnly(dm))
        return sections.filter { it.second.isNotEmpty() }
    }

    private fun roomHasUnread(room: RoomEntity): Boolean =
        room.unreadCount > 0 || room.userMentions > 0 || room.threadUnreadCount > 0

    /**
     * Внутри группы: при [pinFavoritesFirst] — сначала избранные ([RoomEntity.isFavorite]), затем по времени;
     * иначе только по времени.
     */
    private fun sortRoomsInBucket(
        rooms: List<RoomEntity>,
        byTime: Comparator<RoomEntity>,
        pinFavoritesFirst: Boolean
    ): List<RoomEntity> {
        if (!pinFavoritesFirst) {
            return rooms.sortedWith(byTime)
        }
        return rooms.sortedWith { a, b ->
            when {
                a.isFavorite != b.isFavorite -> if (a.isFavorite) -1 else 1
                else -> byTime.compare(a, b)
            }
        }
    }

    /** Переключить избранное (звезда) на сервере и в БД; long-press в списке чатов. */
    fun togglePin(roomId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val room = roomDao.getRoom(roomId) ?: return@launch
                roomRepository.setRoomFavorite(roomId, !room.isFavorite)
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                toastMessage = e.message
                                    ?: appContext.getString(R.string.room_vm_favorite_toggle_failed)
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("RoomListVM", "togglePin: ${e.message}")
            }
        }
    }

    suspend fun createDirectMessage(username: String): Result<String> =
        searchRepository.createDirectMessage(username)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleGroupCollapse(group: RoomGroup) {
        _uiState.update { state ->
            val collapsed = state.collapsedGroups.toMutableSet()
            if (collapsed.contains(group)) collapsed.remove(group) else collapsed.add(group)
            state.copy(collapsedGroups = collapsed)
        }
    }

    fun toggleExpand(room: RoomEntity) {
        val current = _uiState.value.expandedRoomId
        if (current == room.id) {
            _uiState.update { it.copy(expandedRoomId = null, subItems = emptyList()) }
        } else {
            _uiState.update { it.copy(expandedRoomId = room.id, subItems = emptyList(), subItemsLoading = true) }
            loadSubItems(room)
        }
    }

    private fun loadSubItems(room: RoomEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val api = apiProvider.getApi()
            if (api == null) {
                _uiState.update { it.copy(subItemsLoading = false) }
                return@launch
            }

            val subsMap: Map<String, SubscriptionDto> = try {
                roomRepository.getAllSubscriptionsMap()
            } catch (_: Exception) {
                emptyMap()
            }

            val subRoom = subsMap[room.id]
            val mainItem = SubItem(
                id = "main_${room.id}",
                title = appContext.getString(R.string.room_sub_main),
                subtitle = room.displayName ?: room.name ?: "",
                count = 0,
                unreadCount = subRoom?.unread ?: room.unreadCount,
                userMentions = subRoom?.userMentions ?: room.userMentions,
                type = SubItemType.MAIN
            )

            val roomsById = try {
                roomDao.getAllRooms().associateBy { it.id }
            } catch (_: Exception) {
                emptyMap()
            }

            data class ChildMeta(val item: SubItem, val lastMsgMs: Long, val subscribed: Boolean)

            val childMetas = mutableListOf<ChildMeta>()

            try {
                val threads = api.getThreadsList(room.id)
                if (threads.success) {
                    for (dto in threads.threads) {
                        val lastMs = rocketChatTimeToMillis(
                            dto.tlm ?: dto.ts ?: dto._updatedAt
                        )
                        val subscribed = threadParticipationPrefs.isParticipatingInThread(dto._id)
                        childMetas.add(
                            ChildMeta(
                                item = dto.toSubItem(room.id, subsMap),
                                lastMsgMs = lastMs,
                                subscribed = subscribed
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("RoomListVM", "getThreadsList: ${e.message}")
            }

            if (room.type != "d") {
                try {
                    val discussions = when (room.type) {
                        "p" -> api.getGroupDiscussions(room.id)
                        else -> api.getChannelDiscussions(room.id)
                    }
                    if (discussions.success) {
                        for (dto in discussions.discussions) {
                            val sub = subsMap[dto._id]
                            val dbRoom = roomsById[dto._id]
                            val lastMs = rocketChatTimeToMillis(
                                dto.lm ?: dto.lastMessage?.ts ?: dto._updatedAt ?: dto.ts
                            )
                            val subscribed = sub?.f == true
                            childMetas.add(
                                ChildMeta(
                                    item = dto.toDiscussionSubItem(
                                        unread = sub?.unread ?: dto.unread ?: dbRoom?.unreadCount ?: 0,
                                        mentions = sub?.userMentions ?: dbRoom?.userMentions ?: 0
                                    ),
                                    lastMsgMs = lastMs,
                                    subscribed = subscribed
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RoomListVM", "getDiscussions: ${e.message}")
                }
            }

            val byLastMsgDesc = compareByDescending<ChildMeta> { it.lastMsgMs }
            val subscribedSorted = childMetas.filter { it.subscribed }.sortedWith(byLastMsgDesc)
            val restSorted = childMetas.filter { !it.subscribed }.sortedWith(byLastMsgDesc)
            val limitedSubchats = (subscribedSorted + restSorted)
                .take(MAX_CHILD_SUBITEMS)
                .map { it.item }

            val result = listOf(mainItem) + limitedSubchats

            _uiState.update {
                if (it.expandedRoomId == room.id) it.copy(subItems = result, subItemsLoading = false)
                else it.copy(subItemsLoading = false)
            }
        }
    }

    /**
     * Обновляет счётчики в раскрытом списке по [subscriptions.getAll] (источник истины — сервер).
     */
    private suspend fun applySubscriptionPatch(room: RoomEntity) {
        val subsMap: Map<String, SubscriptionDto> = try {
            roomRepository.getAllSubscriptionsMap()
        } catch (e: Exception) {
            Log.e("RoomListVM", "applySubscriptionPatch subs: ${e.message}")
            return
        }
        val subRoom = subsMap[room.id]
        _uiState.update { state ->
            if (state.expandedRoomId != room.id || state.subItems.isEmpty()) return@update state
            val patched = state.subItems.map { item ->
                when (item.type) {
                    SubItemType.MAIN -> item.copy(
                        unreadCount = subRoom?.unread ?: room.unreadCount,
                        userMentions = subRoom?.userMentions ?: room.userMentions
                    )
                    SubItemType.THREAD -> {
                        val tun = subRoom?.tunread ?: emptyList()
                        val tunUser = subRoom?.tunreadUser ?: emptyList()
                        val hasMen = tunUser.any { it == item.id || it.trim() == item.id.trim() }
                        val n = tun.count { it == item.id || it.trim() == item.id.trim() }
                        item.copy(
                            unreadCount = n,
                            userMentions = if (hasMen) 1 else 0
                        )
                    }
                    SubItemType.DISCUSSION -> {
                        val sid = item.targetRoomId ?: return@map item
                        val s = subsMap[sid]
                        item.copy(
                            unreadCount = s?.unread ?: item.unreadCount,
                            userMentions = s?.userMentions ?: item.userMentions
                        )
                    }
                }
            }
            state.copy(subItems = patched)
        }
    }

    private fun MessageDto.toSubItem(parentRoomId: String, subsMap: Map<String, SubscriptionDto>): SubItem {
        val preview = msg.take(100).let { if (msg.length > 100) "$it..." else it }
        val sub = subsMap[parentRoomId]
        val tun = sub?.tunread ?: emptyList()
        val tunUser = sub?.tunreadUser ?: emptyList()
        val hasMen = tunUser.any { it == _id || it.trim() == _id.trim() }
        /** Количество вхождений id треда в tunread (обычно 0 или 1). */
        val unreadInThread = tun.count { it == _id || it.trim() == _id.trim() }
        return SubItem(
            id = _id,
            title = preview.ifBlank { appContext.getString(R.string.room_thread_no_text) },
            subtitle = appContext.getString(
                R.string.room_thread_subtitle,
                u.name ?: u.username ?: "?",
                tcount ?: 0
            ),
            count = tcount ?: 0,
            unreadCount = unreadInThread,
            userMentions = if (hasMen) 1 else 0,
            type = SubItemType.THREAD
        )
    }

    private fun RoomDto.toDiscussionSubItem(unread: Int, mentions: Int): SubItem {
        return SubItem(
            id = _id,
            title = fname ?: name ?: _id,
            subtitle = appContext.getString(R.string.room_discussion_msgs_suffix, msgs),
            count = msgs,
            unreadCount = unread,
            userMentions = mentions,
            type = SubItemType.DISCUSSION,
            targetRoomId = _id,
            targetRoomType = t ?: "p"
        )
    }

    fun buildDebugInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(debugInfo = appContext.getString(R.string.room_list_debug_loading)) }
            val auth = authRepository.authState.value
            val srvUrl = sessionPrefs.getServerUrl()
            val sb = StringBuilder()
            sb.appendLine(appContext.getString(R.string.room_list_debug_header))
            sb.appendLine("serverUrl: $srvUrl")
            sb.appendLine("authToken: ${auth.authToken?.take(12) ?: "NULL"}...")
            sb.appendLine("userId: ${auth.userId ?: "NULL"}")
            sb.appendLine("isLoggedIn: ${auth.isLoggedIn}")
            sb.appendLine()

            sb.appendLine(realtimeService.getDiagnosticInfo())

            sb.appendLine("--- Rooms ---")
            val groups = _uiState.value.groupedRooms
            val totalRooms = groups.sumOf { it.second.size }
            sb.appendLine("total rooms: $totalRooms")
            groups.forEach { (group, rooms) ->
                sb.appendLine("${appContext.getString(group.titleRes)} (${rooms.size}):")
                rooms.take(3).forEach { r ->
                    sb.appendLine("  ${r.displayName ?: r.name}: type=${r.type}, unread=${r.unreadCount}, mentions=${r.userMentions}")
                }
                if (rooms.size > 3) {
                    sb.appendLine("  ${appContext.getString(R.string.room_list_debug_more, rooms.size - 3)}")
                }
            }

            sb.appendLine()
            sb.appendLine("--- API Test ---")
            try {
                if (apiProvider.getApi() != null) {
                    val subMap = roomRepository.getAllSubscriptionsMap()
                    sb.appendLine("subscriptions (paginated map): count=${subMap.size}")
                    subMap.values.take(3).forEach { s ->
                        sb.appendLine("  rid=${s.rid.take(8)}.. unread=${s.unread} f=${s.f} mentions=${s.userMentions}")
                    }
                } else {
                    sb.appendLine("API: null (no session)")
                }
            } catch (e: Exception) {
                sb.appendLine("API error: ${e.message}")
            }

            sb.appendLine()
            try {
                sb.appendLine(roomRepository.getFavoriteDiagnosticsReport())
            } catch (e: Exception) {
                sb.appendLine("--- Избранное (диагностика) ---")
                sb.appendLine(e.message)
            }

            _uiState.update { it.copy(debugInfo = sb.toString()) }
        }
    }

    fun loadRooms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) { syncChatsFromServerUseCase() }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun showCreateRoomDialog(kind: CreateRoomKind) {
        viewModelScope.launch(Dispatchers.IO) {
            val parents = if (kind == CreateRoomKind.DISCUSSION) {
                roomDao.getAllRooms().filter { it.type == "c" || it.type == "p" }
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    createRoomDialog = CreateRoomDialogState(
                        kind = kind,
                        allDiscussionParents = parents
                    )
                )
            }
        }
    }

    fun dismissCreateRoomDialog() {
        _uiState.update { it.copy(createRoomDialog = null) }
    }

    fun updateCreateRoomName(name: String) {
        _uiState.update { s ->
            val d = s.createRoomDialog ?: return@update s
            s.copy(createRoomDialog = d.copy(name = name, error = null))
        }
    }

    fun updateCreateReadOnlyChannel(readOnly: Boolean) {
        _uiState.update { s ->
            val d = s.createRoomDialog ?: return@update s
            s.copy(createRoomDialog = d.copy(readOnlyChannel = readOnly))
        }
    }

    fun updateCreatePrivateTeam(privateTeam: Boolean) {
        _uiState.update { s ->
            val d = s.createRoomDialog ?: return@update s
            s.copy(createRoomDialog = d.copy(privateTeam = privateTeam))
        }
    }

    fun updateDiscussionParentFilter(q: String) {
        _uiState.update { s ->
            val d = s.createRoomDialog ?: return@update s
            s.copy(createRoomDialog = d.copy(discussionParentFilter = q))
        }
    }

    fun selectDiscussionParent(room: RoomEntity) {
        _uiState.update { s ->
            val d = s.createRoomDialog ?: return@update s
            val label = room.displayName ?: room.name ?: room.id
            s.copy(
                createRoomDialog = d.copy(
                    discussionParentId = room.id,
                    discussionParentLabel = label,
                    error = null
                )
            )
        }
    }

    fun submitCreateRoom() {
        if (_uiState.value.createRoomDialog == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { s ->
                val d = s.createRoomDialog ?: return@update s
                s.copy(createRoomDialog = d.copy(loading = true, error = null))
            }
            val d = _uiState.value.createRoomDialog ?: return@launch
            when (d.kind) {
                CreateRoomKind.CHANNEL -> {
                    chatRoomActionsRepository.createChannel(d.name, d.readOnlyChannel).fold(
                        onSuccess = { ch ->
                            roomRepository.syncRooms()
                            _uiState.update {
                                it.copy(
                                    createRoomDialog = null,
                                    pendingOpenCreatedRoom = PendingOpenCreatedRoom(
                                        roomId = ch._id,
                                        title = ch.fname ?: ch.name ?: d.name,
                                        type = ch.t ?: "c"
                                    ),
                                    toastMessage = appContext.getString(R.string.room_vm_channel_created)
                                )
                            }
                        },
                        onFailure = { e ->
                            _uiState.update { s ->
                                val cur = s.createRoomDialog ?: return@update s
                                s.copy(
                                    createRoomDialog = cur.copy(loading = false, error = e.message)
                                )
                            }
                        }
                    )
                }
                CreateRoomKind.GROUP -> {
                    chatRoomActionsRepository.createGroup(d.name).fold(
                        onSuccess = { g ->
                            roomRepository.syncRooms()
                            _uiState.update {
                                it.copy(
                                    createRoomDialog = null,
                                    pendingOpenCreatedRoom = PendingOpenCreatedRoom(
                                        roomId = g._id,
                                        title = g.fname ?: g.name ?: d.name,
                                        type = g.t ?: "p"
                                    ),
                                    toastMessage = appContext.getString(R.string.room_vm_group_created)
                                )
                            }
                        },
                        onFailure = { e ->
                            _uiState.update { s ->
                                val cur = s.createRoomDialog ?: return@update s
                                s.copy(
                                    createRoomDialog = cur.copy(loading = false, error = e.message)
                                )
                            }
                        }
                    )
                }
                CreateRoomKind.DISCUSSION -> {
                    val pid = d.discussionParentId
                    if (pid.isNullOrBlank()) {
                        _uiState.update { s ->
                            val cur = s.createRoomDialog ?: return@update s
                            s.copy(
                                createRoomDialog = cur.copy(
                                    loading = false,
                                    error = appContext.getString(R.string.room_vm_pick_parent)
                                )
                            )
                        }
                        return@launch
                    }
                    val resp = chatRoomActionsRepository.createDiscussion(pid, d.name)
                    if (resp.success) {
                        roomRepository.syncRooms()
                        val disc = resp.discussion
                        val pending = if (disc != null) {
                            PendingOpenCreatedRoom(
                                roomId = disc._id,
                                title = disc.fname ?: disc.name ?: d.name.ifBlank {
                                    appContext.getString(R.string.default_discussion)
                                },
                                type = disc.t ?: "p"
                            )
                        } else null
                        _uiState.update {
                            it.copy(
                                createRoomDialog = null,
                                pendingOpenCreatedRoom = pending,
                                toastMessage = if (pending != null) {
                                    appContext.getString(R.string.room_vm_discussion_created)
                                } else {
                                    appContext.getString(R.string.room_vm_discussion_created_refresh)
                                }
                            )
                        }
                    } else {
                        _uiState.update { s ->
                            val cur = s.createRoomDialog ?: return@update s
                            s.copy(
                                createRoomDialog = cur.copy(
                                    loading = false,
                                    error = resp.error ?: appContext.getString(R.string.error_generic)
                                )
                            )
                        }
                    }
                }
                CreateRoomKind.TEAM -> {
                    chatRoomActionsRepository.createTeam(d.name, d.privateTeam).fold(
                        onSuccess = { (_, mainRoomId) ->
                            roomRepository.syncRooms()
                            if (!mainRoomId.isNullOrBlank()) {
                                _uiState.update {
                                    it.copy(
                                        createRoomDialog = null,
                                        pendingOpenCreatedRoom = PendingOpenCreatedRoom(
                                            roomId = mainRoomId,
                                            title = d.name.trim(),
                                            type = "p"
                                        ),
                                        toastMessage = appContext.getString(R.string.room_vm_team_created)
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        createRoomDialog = null,
                                        toastMessage = appContext.getString(R.string.room_vm_team_created_sync)
                                    )
                                }
                            }
                        },
                        onFailure = { e ->
                            _uiState.update { s ->
                                val cur = s.createRoomDialog ?: return@update s
                                s.copy(
                                    createRoomDialog = cur.copy(loading = false, error = e.message)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    fun consumePendingOpenCreatedRoom() {
        _uiState.update { it.copy(pendingOpenCreatedRoom = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun checkForAppUpdate() {
        appUpdateJob?.cancel()
        appUpdateJob = viewModelScope.launch {
            _uiState.update { it.copy(appUpdate = AppUpdateUiState.Checking) }
            when (val r = appUpdateRepository.checkForUpdate()) {
                is AppUpdateCheckResult.UpToDate -> {
                    _uiState.update {
                        it.copy(
                            appUpdate = AppUpdateUiState.Idle,
                            toastMessage = appContext.getString(R.string.app_update_toast_up_to_date)
                        )
                    }
                }
                is AppUpdateCheckResult.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            appUpdate = AppUpdateUiState.Prompt(
                                remoteVersion = r.remoteVersion,
                                notes = r.releaseNotes,
                                apkUrl = r.apkUrl
                            )
                        )
                    }
                }
                is AppUpdateCheckResult.Error -> {
                    _uiState.update {
                        it.copy(
                            appUpdate = AppUpdateUiState.Idle,
                            toastMessage = r.message
                        )
                    }
                }
            }
        }
    }

    fun dismissAppUpdateFlow() {
        appUpdateJob?.cancel()
        appUpdateJob = null
        _uiState.update { it.copy(appUpdate = AppUpdateUiState.Idle) }
    }

    fun confirmAppUpdateInstall() {
        val state = _uiState.value.appUpdate
        if (state !is AppUpdateUiState.Prompt) return
        appUpdateJob?.cancel()
        appUpdateJob = viewModelScope.launch {
            val apkUrl = state.apkUrl
            _uiState.update { it.copy(appUpdate = AppUpdateUiState.Downloading(0f)) }
            val result = appUpdateRepository.downloadApk(apkUrl) { p ->
                _uiState.update { cur ->
                    cur.copy(
                        appUpdate = AppUpdateUiState.Downloading(
                            if (p < 0f) null else p
                        )
                    )
                }
            }
            result.fold(
                onSuccess = { file -> tryInstallApk(file) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            appUpdate = AppUpdateUiState.Idle,
                            toastMessage = e.message
                                ?: appContext.getString(R.string.app_update_error_download_http, -1)
                        )
                    }
                }
            )
        }
    }

    private fun tryInstallApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            _uiState.update {
                it.copy(appUpdate = AppUpdateUiState.PendingInstallPermission(file))
            }
            return
        }
        try {
            appContext.startActivity(appUpdateRepository.installApkIntent(file))
        } catch (e: Exception) {
            Log.e("RoomListVM", "tryInstallApk: ${e.message}")
            _uiState.update {
                it.copy(
                    appUpdate = AppUpdateUiState.Idle,
                    toastMessage = e.message
                )
            }
            return
        }
        _uiState.update { it.copy(appUpdate = AppUpdateUiState.Idle) }
    }

    fun openUnknownSourcesSettings() {
        appContext.startActivity(appUpdateRepository.unknownSourcesSettingsIntent())
    }

    fun retryInstallAfterPermission() {
        val state = _uiState.value.appUpdate
        if (state !is AppUpdateUiState.PendingInstallPermission) return
        tryInstallApk(state.apkFile)
    }

    private fun dmPeerUsernamesFromRooms(rooms: List<RoomEntity>): Set<String> =
        rooms.asSequence()
            .filter { it.type == "d" }
            .mapNotNull { r ->
                r.avatarPath?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("room/") }
            }
            .toSet()

    private suspend fun prefetchDmPeersPresence(usernames: Set<String>) {
        if (usernames.isEmpty()) return
        for (un in usernames) {
            searchRepository.getUserByUsername(un).onSuccess { user ->
                userPresenceStore.applyFromUserInfo(user)
            }
        }
    }

    /** ISO-8601 от Rocket.Chat → epoch ms для сортировки сабчатов по последней активности. */
    private fun rocketChatTimeToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            Instant.parse(iso.trim()).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
