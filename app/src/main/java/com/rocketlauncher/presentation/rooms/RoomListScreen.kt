package com.rocketlauncher.presentation.rooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rocketlauncher.data.db.RoomEntity
import com.rocketlauncher.data.realtime.RealtimeMessageService
import com.rocketlauncher.data.realtime.UserPresenceStatus
import com.rocketlauncher.presentation.chat.PresenceRingSubcomposeAsyncImage
import com.rocketlauncher.data.dto.RoomAutocompleteItemDto
import com.rocketlauncher.data.dto.SpotlightRoomDto
import com.rocketlauncher.data.dto.SpotlightUserDto
import com.rocketlauncher.domain.model.FavoriteDisplayMode
import com.rocketlauncher.presentation.theme.ThemeMode
import com.rocketlauncher.R
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    /** Файлы из «Поделиться» — показать подсказку выбора чата. */
    pendingShareUris: List<Uri> = emptyList(),
    onOpenMyProfile: () -> Unit = {},
    onOpenGlobalMessageSearch: () -> Unit,
    onRoomClick: (id: String, name: String, type: String, avatarPath: String) -> Unit,
    onThreadClick: (roomId: String, roomName: String, roomType: String, avatarPath: String, tmid: String, threadTitle: String) -> Unit,
    onDiscussionClick: (roomId: String, roomName: String, roomType: String, avatarPath: String) -> Unit,
    onLogout: () -> Unit,
    viewModel: RoomListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val favoriteDisplayMode by viewModel.favoriteDisplayMode.collectAsState()
    val wsConnectionState by viewModel.wsConnectionState.collectAsState()
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadRooms() }

    LaunchedEffect(uiState.pendingOpenCreatedRoom) {
        val p = uiState.pendingOpenCreatedRoom ?: return@LaunchedEffect
        onRoomClick(p.roomId, p.title, p.type, p.avatarPath)
        viewModel.consumePendingOpenCreatedRoom()
    }

    LaunchedEffect(uiState.toastMessage) {
        val t = uiState.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
        viewModel.clearToastMessage()
    }

    uiState.createRoomDialog?.let { dialogState ->
        CreateRoomDialog(
            state = dialogState,
            onDismiss = { viewModel.dismissCreateRoomDialog() },
            onNameChange = { viewModel.updateCreateRoomName(it) },
            onReadOnlyChannelChange = { viewModel.updateCreateReadOnlyChannel(it) },
            onPrivateTeamChange = { viewModel.updateCreatePrivateTeam(it) },
            onDiscussionFilterChange = { viewModel.updateDiscussionParentFilter(it) },
            onPickDiscussionParent = { viewModel.selectDiscussionParent(it) },
            onSubmit = { viewModel.submitCreateRoom() }
        )
    }

    when (val au = uiState.appUpdate) {
        is AppUpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAppUpdateFlow() },
                title = { Text(stringResource(R.string.app_update_checking)) },
                text = { CircularProgressIndicator() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissAppUpdateFlow() }) {
                        Text(stringResource(R.string.app_update_cancel))
                    }
                }
            )
        }
        is AppUpdateUiState.Prompt -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAppUpdateFlow() },
                title = { Text(stringResource(R.string.app_update_dialog_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.app_update_dialog_message, au.remoteVersion))
                        au.notes?.let { notes ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.app_update_notes_header),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                notes,
                                modifier = Modifier
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmAppUpdateInstall() }) {
                        Text(stringResource(R.string.app_update_install))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissAppUpdateFlow() }) {
                        Text(stringResource(R.string.app_update_cancel))
                    }
                }
            )
        }
        is AppUpdateUiState.Downloading -> {
            Dialog(onDismissRequest = {}) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.app_update_downloading),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        val prog = au.progress
                        if (prog == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = prog,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        is AppUpdateUiState.PendingInstallPermission -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAppUpdateFlow() },
                title = { Text(stringResource(R.string.app_update_permission_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.app_update_permission_body))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.retryInstallAfterPermission() }) {
                                Text(stringResource(R.string.app_update_install_retry))
                            }
                            TextButton(onClick = { viewModel.openUnknownSourcesSettings() }) {
                                Text(stringResource(R.string.app_update_open_settings))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissAppUpdateFlow() }) {
                        Text(stringResource(R.string.app_update_cancel))
                    }
                }
            )
        }
        else -> {}
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text(stringResource(R.string.rooms_search_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WsConnectionIndicator(wsConnectionState)
                            IconButton(onClick = {
                                showSearch = false
                                viewModel.onSearchQueryChanged("")
                            }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close)) }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.rooms_title)) },
                    navigationIcon = {
                        WsConnectionIndicator(wsConnectionState)
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_channel)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.showCreateRoomDialog(CreateRoomKind.CHANNEL)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_private_group)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.showCreateRoomDialog(CreateRoomKind.GROUP)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_discussion)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.showCreateRoomDialog(CreateRoomKind.DISCUSSION)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_team)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.showCreateRoomDialog(CreateRoomKind.TEAM)
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_profile)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenMyProfile()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.rooms_menu_theme_section),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                ThemeMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.titleRes)) },
                                        onClick = {
                                            viewModel.setThemeMode(mode)
                                            showOverflowMenu = false
                                        },
                                        leadingIcon = if (themeMode == mode) {
                                            {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        } else null
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.rooms_menu_favorites_section),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                FavoriteDisplayMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes)) },
                                        onClick = {
                                            viewModel.setFavoriteDisplayMode(mode)
                                            showOverflowMenu = false
                                        },
                                        leadingIcon = if (favoriteDisplayMode == mode) {
                                            {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        } else null
                                    )
                                }
                                Divider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_global_search)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenGlobalMessageSearch()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FilterList, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_check_updates)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.checkForAppUpdate()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_debug)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.buildDebugInfo()
                                        showDebug = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Info, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_menu_logout)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onLogout()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Logout, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.groupedRooms.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.groupedRooms.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.rooms_empty_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    if (pendingShareUris.isNotEmpty()) {
                        item(key = "share_hint") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.rooms_share_hint, pendingShareUris.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    if (showSearch && uiState.searchQuery.length >= 2) {
                        item(key = "remote_search") {
                            RemoteSearchSection(
                                loading = uiState.remoteSearchLoading,
                                spotlightUsers = uiState.spotlightUsers,
                                spotlightRooms = uiState.spotlightRooms,
                                autocompleteRooms = uiState.autocompleteRooms,
                                onOpenUser = { username, name ->
                                    scope.launch {
                                        val r = viewModel.createDirectMessage(username)
                                        r.onSuccess { roomId ->
                                            onRoomClick(roomId, name ?: username, "d", username)
                                        }
                                        r.onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                e.message ?: context.getString(R.string.rooms_dm_error),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onOpenRoom = { id, title, type ->
                                    onRoomClick(id, title, type, "")
                                }
                            )
                        }
                    }
                    uiState.groupedRooms.forEach { (group, rooms) ->
                        val isGroupCollapsed = uiState.collapsedGroups.contains(group)

                        item(key = "header_${group.name}") {
                            GroupHeader(
                                group = group,
                                count = rooms.size,
                                isCollapsed = isGroupCollapsed,
                                onClick = { viewModel.toggleGroupCollapse(group) }
                            )
                        }

                        if (!isGroupCollapsed) {
                            items(rooms, key = { it.id }) { room ->
                                val isExpanded = uiState.expandedRoomId == room.id
                                val dmPresence = if (room.type == "d") {
                                    val un = room.avatarPath?.trim()
                                        ?.takeIf { it.isNotEmpty() && !it.startsWith("room/") }
                                    uiState.presenceSnapshot.resolve(null, un)
                                } else {
                                    UserPresenceStatus.UNKNOWN
                                }
                                Column {
                                    RoomItem(
                                        room = room,
                                        serverUrl = uiState.serverUrl,
                                        peerPresence = dmPresence,
                                        isExpanded = isExpanded,
                                        isPinned = room.isFavorite,
                                        onOpenRoom = {
                                            onRoomClick(
                                                room.id,
                                                room.displayName ?: room.name ?: "",
                                                room.type,
                                                room.avatarPath ?: ""
                                            )
                                        },
                                        onExpandClick = { viewModel.toggleExpand(room) },
                                        onLongClick = { viewModel.togglePin(room.id) },
                                        onNotificationToggle = {
                                            viewModel.cycleRoomNotificationPolicy(room.id)
                                        }
                                    )
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            if (uiState.subItemsLoading) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                }
                                            } else {
                                                uiState.subItems.forEach { sub ->
                                                    SubItemRow(
                                                        item = sub,
                                                        onClick = {
                                                            when (sub.type) {
                                                                SubItemType.MAIN -> onRoomClick(
                                                                    room.id,
                                                                    room.displayName ?: room.name ?: "",
                                                                    room.type,
                                                                    room.avatarPath ?: ""
                                                                )
                                                                SubItemType.THREAD -> onThreadClick(
                                                                    room.id,
                                                                    room.displayName ?: room.name ?: "",
                                                                    room.type,
                                                                    room.avatarPath ?: "",
                                                                    sub.id,
                                                                    sub.title
                                                                )
                                                                SubItemType.DISCUSSION -> onDiscussionClick(
                                                                    sub.targetRoomId ?: sub.id,
                                                                    sub.title,
                                                                    sub.targetRoomType ?: "p",
                                                                    ""
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDebug) {
        DebugInfoDialog(info = uiState.debugInfo, onDismiss = { showDebug = false })
    }
}

@Composable
private fun RemoteSearchSection(
    loading: Boolean,
    spotlightUsers: List<SpotlightUserDto>,
    spotlightRooms: List<SpotlightRoomDto>,
    autocompleteRooms: List<RoomAutocompleteItemDto>,
    onOpenUser: (username: String, name: String?) -> Unit,
    onOpenRoom: (id: String, title: String, type: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.rooms_remote_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        spotlightUsers.forEach { u ->
            val uname = u.username ?: return@forEach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUser(uname, u.name) }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Text("@$uname", fontWeight = FontWeight.Bold)
                u.name?.let {
                    Text(" · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        spotlightRooms.forEach { r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpenRoom(r._id, r.fname ?: r.name ?: r._id, r.t ?: "c")
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Text("# ${r.fname ?: r.name ?: r._id}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        autocompleteRooms.forEach { r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpenRoom(r._id, r.fname ?: r.name ?: r._id, r.t ?: "c")
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Text("⌂ ${r.fname ?: r.name ?: r._id}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!loading && spotlightUsers.isEmpty() && spotlightRooms.isEmpty() && autocompleteRooms.isEmpty()) {
            Text(
                stringResource(R.string.rooms_remote_no_matches),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupHeader(
    group: RoomGroup,
    count: Int,
    isCollapsed: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(group.titleRes),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomItem(
    room: RoomEntity,
    serverUrl: String?,
    peerPresence: UserPresenceStatus,
    isExpanded: Boolean,
    isPinned: Boolean,
    onOpenRoom: () -> Unit,
    onExpandClick: () -> Unit,
    onLongClick: () -> Unit,
    onNotificationToggle: () -> Unit
) {
    val avatarUrl = room.avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
        serverUrl?.trimEnd('/')?.let { "$it/avatar/$path?format=png" }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onOpenRoom,
                    onLongClick = onLongClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
        PresenceRingSubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = null,
            size = 44.dp,
            presence = peerPresence,
            loading = { AvatarPlaceholder(room.displayName ?: room.name) },
            error = { AvatarPlaceholder(room.displayName ?: room.name) }
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.rooms_cd_pinned),
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    text = room.displayName ?: room.name ?: room.id,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (room.unreadCount > 0 || room.threadUnreadCount > 0 || room.userMentions > 0) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                room.lastMessageTime?.let { time ->
                    Text(
                        text = formatTimeLabel(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                room.lastMessageText?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))

                val totalUnread = room.unreadCount + room.threadUnreadCount
                if (totalUnread > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val badgeColor = if (room.userMentions > 0) Color(0xFFE53935) else Color(0xFF9E9E9E)
                    Box(
                        modifier = Modifier
                            .background(badgeColor, CircleShape)
                            .size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (totalUnread > 99) "99+" else "$totalUnread",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        }
        IconButton(
            onClick = onExpandClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) {
                    stringResource(R.string.rooms_cd_collapse_subchats)
                } else {
                    stringResource(R.string.rooms_cd_expand_subchats)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onNotificationToggle,
            modifier = Modifier.size(44.dp)
        ) {
            val (notifIcon, notifDesc, notifTint) = when {
                room.notificationsMuted ->
                    Triple(
                        Icons.Default.NotificationsOff,
                        stringResource(R.string.rooms_notif_muted),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                !room.notifySubchats ->
                    Triple(
                        Icons.Filled.Notifications,
                        stringResource(R.string.rooms_notif_main_only),
                        MaterialTheme.colorScheme.secondary
                    )
                else ->
                    Triple(
                        Icons.Filled.NotificationsActive,
                        stringResource(R.string.rooms_notif_all),
                        MaterialTheme.colorScheme.primary
                    )
            }
            Icon(
                imageVector = notifIcon,
                contentDescription = notifDesc,
                tint = notifTint
            )
        }
    }
}

@Composable
private fun SubItemRow(item: SubItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (item.type) {
            SubItemType.MAIN -> Icons.Default.Chat to MaterialTheme.colorScheme.primary
            SubItemType.THREAD -> Icons.Default.QuestionAnswer to MaterialTheme.colorScheme.tertiary
            SubItemType.DISCUSSION -> Icons.Default.Forum to MaterialTheme.colorScheme.secondary
        }
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (item.unreadCount > 0) FontWeight.Bold
                        else if (item.type == SubItemType.MAIN) FontWeight.Bold
                        else FontWeight.Normal
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.type != SubItemType.MAIN && item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (item.unreadCount > 0) {
            val badgeColor = if (item.userMentions > 0) Color(0xFFE53935) else Color(0xFF9E9E9E)
            Box(
                modifier = Modifier
                    .background(badgeColor, CircleShape)
                    .size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.unreadCount > 99) "99+" else "${item.unreadCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun DebugInfoDialog(info: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rooms_debug_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = info.ifEmpty { stringResource(R.string.rooms_debug_loading_body) },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(info)) }) {
                Text(stringResource(R.string.action_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

/** Индикатор WebSocket: зелёный — онлайн, жёлтый — подключение/переподключение, красный — офлайн. */
@Composable
private fun WsConnectionIndicator(state: RealtimeMessageService.ConnectionState) {
    val (color, desc) = when (state) {
        RealtimeMessageService.ConnectionState.CONNECTED ->
            Color(0xFF43A047) to stringResource(R.string.rooms_ws_connected)
        RealtimeMessageService.ConnectionState.DISCONNECTED ->
            Color(0xFFD32F2F) to stringResource(R.string.rooms_ws_disconnected)
        RealtimeMessageService.ConnectionState.CONNECTING,
        RealtimeMessageService.ConnectionState.RECONNECTING ->
            Color(0xFFF9A825) to stringResource(R.string.rooms_ws_connecting)
    }
    Box(
        modifier = Modifier
            .padding(start = 4.dp, end = 8.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = desc }
    )
}

@Composable
private fun AvatarPlaceholder(name: String?) {
    val letter = name?.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = letter, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun formatTimeLabel(iso: String): String {
    val res = LocalContext.current.resources
    return try {
        val instant = java.time.Instant.parse(iso)
        val now = java.time.Instant.now()
        val diff = java.time.Duration.between(instant, now)
        when {
            diff.toDays() > 0 -> res.getString(R.string.rooms_time_days, diff.toDays())
            diff.toHours() > 0 -> res.getString(R.string.rooms_time_hours, diff.toHours())
            diff.toMinutes() > 0 -> res.getString(R.string.rooms_time_minutes, diff.toMinutes())
            else -> res.getString(R.string.rooms_time_now)
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}
