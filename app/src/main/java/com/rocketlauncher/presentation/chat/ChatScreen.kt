package com.rocketlauncher.presentation.chat

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TagFaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketlauncher.presentation.jitsi.JitsiMeetLauncher
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.rocketlauncher.R
import com.rocketlauncher.data.RocketChatMessageKinds
import com.rocketlauncher.data.db.MessageEntity
import com.rocketlauncher.data.db.RoomEntity
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.SpotlightUserDto
import com.rocketlauncher.data.emoji.EmojiEntry
import com.rocketlauncher.data.emoji.EmojiPickerSearchRow
import com.rocketlauncher.data.realtime.UserPresenceStatus
import com.rocketlauncher.data.emoji.EmojiStore
import com.rocketlauncher.data.message.quoteSegmentsForMessageEntity
import com.rocketlauncher.data.message.stripRocketQuotePermalinkMarkdown
import com.rocketlauncher.data.mentions.StoredMention
import com.rocketlauncher.data.emoji.TwemojiUrls
import com.rocketlauncher.data.mentions.parseStoredMentionsJson
import androidx.hilt.navigation.compose.hiltViewModel
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    roomName: String,
    @Suppress("UNUSED_PARAMETER") roomType: String,
    @Suppress("UNUSED_PARAMETER") avatarPath: String,
    onBack: () -> Unit,
    /** Переход в тред по корневому сообщению (tmid = id сообщения); [highlightMsg] — подсветка ответа и т.п. */
    onNavigateToThread: (tmid: String, threadTitle: String, highlightMsg: String) -> Unit = { _, _, _ -> },
    /** Переход в комнату обсуждения по [MessageEntity.drid]. */
    onNavigateToDiscussion: (discussionRoomId: String, title: String, discussionRoomType: String) -> Unit =
        { _, _, _ -> },
    /** Открыть личный чат из профиля пользователя (после im.create). */
    onOpenDirectChat: (roomId: String, roomName: String, avatarPath: String) -> Unit = { _, _, _ -> },
    /** После выхода из канала/группы — вернуться назад (например pop стека). */
    onLeaveChat: () -> Unit = {},
    /**
     * Обработка URL по тапу: вернуть `true`, если ссылка обработана в приложении (инвайт в канал и т.п.).
     * Иначе откроется системный обработчик ([LocalUriHandler]).
     */
    onUrlClick: (String) -> Boolean = { false },
    viewModel: ChatViewModel = hiltViewModel()
) {
    val defaultThreadTitle = stringResource(R.string.default_thread)
    val defaultDiscussionTitle = stringResource(R.string.default_discussion)
    val defaultRoomTitle = stringResource(R.string.default_room_chat)
    var composer by remember { mutableStateOf(TextFieldValue("")) }
    var composerLinkDialog by remember { mutableStateOf<ComposerLinkDialogState?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    var showInRoomSearch by remember { mutableStateOf(false) }
    var showChatOverflowMenu by remember { mutableStateOf(false) }
    var showLeaveRoomConfirm by remember { mutableStateOf(false) }
    var showNewDiscussionDialog by remember { mutableStateOf(false) }
    var newDiscussionTitleInput by remember { mutableStateOf("") }
    var showCreatePollSheet by remember { mutableStateOf(false) }
    var showForwardPicker by remember { mutableStateOf(false) }
    var contextMenuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var reactMessageId by remember { mutableStateOf<String?>(null) }
    var showEmojiKeyboard by remember { mutableStateOf(false) }
    val recentEmojiCodes by viewModel.recentEmojiCodes.collectAsState()
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scrollBottomThresholdPx = remember(density) { with(density) { 12.dp.roundToPx() } }
    val showScrollToBottomFab by remember {
        derivedStateOf {
            if (uiState.messages.isEmpty()) false
            else {
                val total = listState.layoutInfo.totalItemsCount
                if (total == 0) false
                else {
                    val atBottom = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset <= scrollBottomThresholdPx
                    !atBottom
                }
            }
        }
    }
    val scrollFabBadgeLabel by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            when {
                idx <= 0 -> null
                idx >= 100 -> "99+"
                else -> "$idx"
            }
        }
    }
    val isAtBottom by remember {
        derivedStateOf {
            if (uiState.messages.isEmpty()) true
            else {
                val total = listState.layoutInfo.totalItemsCount
                if (total == 0) true
                else {
                    listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset <= scrollBottomThresholdPx
                }
            }
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) viewModel.clearScrollReturnAnchor()
    }

    fun updateComposer(newVal: TextFieldValue) {
        composer = newVal
        val q = findActiveMentionQuery(newVal.text, newVal.selection.end)?.second
        viewModel.onMentionQueryChange(q)
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val caption = composer.text.trim().takeIf { it.isNotEmpty() }
            viewModel.uploadAttachment(uri, caption)
            updateComposer(TextFieldValue(""))
            viewModel.clearMentionSuggestions()
        }
    }

    val votedPollOptionsMap by viewModel.votedPollOptions.collectAsState()

    LaunchedEffect(roomId) { viewModel.loadMessages() }

    LaunchedEffect(uiState.editingMessage?.id) {
        val em = uiState.editingMessage
        if (em != null) {
            updateComposer(TextFieldValue(em.text, TextRange(em.text.length)))
        }
    }

    /** Прокрутка к самым старым сообщениям (верх списка при reverseLayout) — догрузка с сервера. */
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            lastVisible to total
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 2) {
                    viewModel.loadOlderMessages()
                }
            }
    }

    LaunchedEffect(uiState.scrollToBottomNonce) {
        if (uiState.scrollToBottomNonce > 0L) {
            delay(50)
            if (uiState.messages.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(uiState.pendingScrollToMessageId, uiState.messages) {
        val targetId = uiState.pendingScrollToMessageId ?: return@LaunchedEffect
        val idx = uiState.messages.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            smoothScrollLazyToItem(listState, idx, durationMillis = 220)
            viewModel.consumePendingScrollToMessage()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.chat_message_not_in_history),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.consumePendingScrollToMessage()
        }
    }

    LaunchedEffect(uiState.pendingThreadNavigation) {
        val p = uiState.pendingThreadNavigation ?: return@LaunchedEffect
        onNavigateToThread(p.tmid, p.threadTitle, p.highlightMessageId)
        viewModel.consumePendingThreadNavigation()
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(uiState.navigateToDirectChat) {
        val n = uiState.navigateToDirectChat ?: return@LaunchedEffect
        onOpenDirectChat(n.roomId, n.title, n.avatarPath)
        viewModel.consumeNavigateToDirectChat()
    }

    LaunchedEffect(uiState.navigateBackAfterLeave) {
        if (uiState.navigateBackAfterLeave) {
            onLeaveChat()
            viewModel.consumeNavigateBackAfterLeave()
        }
    }

    LaunchedEffect(uiState.pendingOpenRoom) {
        val p = uiState.pendingOpenRoom ?: return@LaunchedEffect
        onNavigateToDiscussion(p.roomId, p.title, p.type)
        viewModel.consumePendingOpenRoom()
    }

    if (uiState.roomMembersSheetOpen) {
        ChatRoomMembersBottomSheet(
            serverUrl = uiState.serverUrl,
            presenceSnapshot = uiState.presenceSnapshot,
            members = uiState.roomMembers,
            loading = uiState.roomMembersLoading,
            error = uiState.roomMembersError,
            currentUserId = uiState.currentUserId,
            inviteDialogOpen = uiState.inviteMemberDialogOpen,
            inviteQuery = uiState.inviteMemberQuery,
            inviteSuggestions = uiState.inviteMemberSuggestions,
            inviteLoading = uiState.inviteMemberLoading,
            roleTarget = uiState.rolePickerTarget,
            onDismiss = { viewModel.dismissRoomMembersSheet() },
            onRefresh = { viewModel.refreshRoomMembers() },
            onOpenInvite = { viewModel.openInviteMemberDialog() },
            onInviteQueryChange = { viewModel.onInviteMemberQueryChange(it) },
            onPickInviteUser = { viewModel.inviteMember(it._id) },
            onDismissInvite = { viewModel.dismissInviteMemberDialog() },
            onMemberRolesClick = { viewModel.openRolePicker(it) },
            onDismissRolePicker = { viewModel.dismissRolePicker() },
            onRoleAction = { viewModel.applyMemberRole(it) }
        )
    }

    if (showLeaveRoomConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveRoomConfirm = false },
            title = { Text(stringResource(R.string.chat_leave_title)) },
            text = { Text(stringResource(R.string.chat_leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveRoomConfirm = false
                    viewModel.leaveCurrentRoom()
                }) { Text(stringResource(R.string.action_exit)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveRoomConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showNewDiscussionDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewDiscussionDialog = false
                newDiscussionTitleInput = ""
            },
            title = { Text(stringResource(R.string.chat_new_discussion_title)) },
            text = {
                OutlinedTextField(
                    value = newDiscussionTitleInput,
                    onValueChange = { newDiscussionTitleInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.chat_field_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = newDiscussionTitleInput.trim()
                        showNewDiscussionDialog = false
                        newDiscussionTitleInput = ""
                        viewModel.createDiscussionFromMenu(t)
                    }
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewDiscussionDialog = false
                    newDiscussionTitleInput = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    uiState.userProfileSheet?.let { profile ->
        UserProfileBottomSheet(
            state = profile,
            livePresence = uiState.presenceSnapshot.resolve(profile.userId, profile.usernameHint),
            serverUrl = uiState.serverUrl,
            onDismiss = { viewModel.dismissUserProfile() },
            onOpenDirectChat = { viewModel.profileOpenDirectChat() },
            onVideoCall = {
                viewModel.profileStartVideoCall { url ->
                    try {
                        JitsiMeetLauncher.launch(context, url)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            e.message ?: context.getString(R.string.toast_call_open_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            showDirectChatButton = viewModel.shouldShowProfileDirectChatButton(profile.userId)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val headerPresence =
                            if (roomType == "d" && avatarPath.isNotBlank() && !avatarPath.startsWith("room/")) {
                                uiState.presenceSnapshot.resolve(null, avatarPath)
                            } else {
                                UserPresenceStatus.UNKNOWN
                            }
                        PresenceRingAsyncImage(
                            model = uiState.roomAvatarUrl,
                            contentDescription = null,
                            size = 36.dp,
                            presence = headerPresence
                        )
                        Text(uiState.roomDisplayName.ifEmpty { roomName.ifEmpty { defaultRoomTitle } })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (viewModel.isThread) {
                        TextButton(
                            onClick = {
                                if (uiState.threadSubscribed) {
                                    viewModel.unsubscribeFromThread()
                                } else {
                                    viewModel.subscribeToThread()
                                }
                            },
                            enabled = !uiState.threadFollowBusy
                        ) {
                            Text(
                                if (uiState.threadSubscribed) {
                                    stringResource(R.string.chat_unsubscribe)
                                } else {
                                    stringResource(R.string.chat_subscribe)
                                }
                            )
                        }
                    }
                    if (viewModel.supportsRoomManagement()) {
                        Box {
                            IconButton(onClick = { showChatOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(
                                expanded = showChatOverflowMenu,
                                onDismissRequest = { showChatOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_menu_members)) },
                                    onClick = {
                                        showChatOverflowMenu = false
                                        viewModel.openRoomMembersSheet()
                                    }
                                )
                                if (viewModel.supportsInviteLink()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_menu_invite_link)) },
                                        onClick = {
                                            showChatOverflowMenu = false
                                            viewModel.copyRoomInviteLinkToClipboard()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_menu_new_discussion)) },
                                    onClick = {
                                        showChatOverflowMenu = false
                                        showNewDiscussionDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_menu_leave)) },
                                    onClick = {
                                        showChatOverflowMenu = false
                                        showLeaveRoomConfirm = true
                                    }
                                )
                            }
                        }
                    }
                    if (!viewModel.isThread) {
                        IconButton(onClick = {
                            viewModel.startVideoCall { url ->
                                try {
                                    JitsiMeetLauncher.launch(context, url)
                                } catch (e: Exception) {
                                    Toast.makeText(
                            context,
                            e.message ?: context.getString(R.string.toast_call_open_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.cd_video_call))
                        }
                        IconButton(onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val anchorId =
                                        uiState.messages.getOrNull(listState.firstVisibleItemIndex)?.id
                                    viewModel.jumpToStartOfDay(
                                        LocalDate.of(y, m + 1, d),
                                        anchorId
                                    )
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        }) {
                            Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.cd_jump_to_day))
                        }
                        IconButton(onClick = { showInRoomSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_in_room_search))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!viewModel.isThread && uiState.pinnedBannerItems.isNotEmpty()) {
                val pIdx = uiState.pinnedBannerIndex.coerceIn(0, uiState.pinnedBannerItems.lastIndex)
                val bannerItem = uiState.pinnedBannerItems[pIdx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val anchorId =
                                uiState.messages.getOrNull(listState.firstVisibleItemIndex)?.id
                            viewModel.onPinnedBannerClick(anchorId)
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.cd_pinned),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = bannerItem.previewText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (uiState.isUploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (uiState.loadingOlderMessages) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            uiState.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                reverseLayout = true,
                state = listState
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    if (!msg.msgType.isNullOrBlank() &&
                        RocketChatMessageKinds.isRoomEventOrService(msg.msgType)
                    ) {
                        SystemEventRow(message = msg)
                    } else {
                    MessageBubble(
                        message = msg,
                        isMine = msg.userId == uiState.currentUserId,
                        isThreadContext = uiState.isThread,
                        serverUrl = uiState.serverUrl,
                        peerPresence = uiState.presenceSnapshot.resolve(msg.userId, msg.username),
                        emojiStore = viewModel.emojiStore,
                        highlight = msg.id == uiState.highlightMessageId ||
                            msg.id == uiState.quoteHighlightMessageId,
                        onQuoteClick = msg.quotedMessageId?.let { qid ->
                            { viewModel.scrollToQuotedMessage(qid, anchorSourceMessageId = msg.id) }
                        },
                        onPeerProfileClick =
                            if (msg.userId != uiState.currentUserId) {
                                {
                                    viewModel.openUserProfile(
                                        userId = msg.userId,
                                        username = msg.username,
                                        fallbackName = msg.displayName ?: msg.username
                                    )
                                }
                            } else null,
                        onLongClick = { contextMenuMessage = msg },
                        onImageClick = { url -> fullScreenImageUrl = url },
                        onFileDownload = { url, name -> viewModel.downloadFile(url, name) },
                        onFileCopyLink = { url ->
                            clipboard.setText(AnnotatedString(url))
                            Toast.makeText(context, context.getString(R.string.toast_link_copied), Toast.LENGTH_SHORT).show()
                        },
                        onReactionClick = { emoji -> viewModel.react(msg.id, emoji) },
                        onVotePoll = { appId, blockId, actionId, value ->
                            viewModel.votePoll(msg.id, msg.roomId, appId, blockId, actionId, value)
                        },
                        votedPollValues = votedPollOptionsMap.filter { it.startsWith("${msg.id}:") }.map { it.substringAfter(":") }.toSet(),
                        onOpenThread =
                            if (!uiState.isThread &&
                                msg.tmid.isNullOrBlank() &&
                                msg.threadReplyCount != null &&
                                msg.threadReplyCount > 0
                            ) {
                                {
                                    onNavigateToThread(
                                        msg.id,
                                        msg.text.trim().take(48).ifBlank { defaultThreadTitle },
                                        ""
                                    )
                                }
                            } else null,
                        onOpenDiscussion = msg.drid?.takeIf { it.isNotBlank() }?.let { drid ->
                            {
                                onNavigateToDiscussion(
                                    drid,
                                    msg.text.trim().take(48).ifBlank { defaultDiscussionTitle },
                                    "p"
                                )
                            }
                        },
                        onUrlClick = onUrlClick
                    )
                    }
                }
            }
            if (showScrollToBottomFab) {
                FloatingActionButton(
                    onClick = { viewModel.requestScrollToBottom() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 6.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    BadgedBox(
                        badge = {
                            val label = scrollFabBadgeLabel
                            if (label != null) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_scroll_to_latest)
                        )
                    }
                }
            }
            }

            if (uiState.editingMessage != null) {
                EditMessageBar(
                    previewText = uiState.editingMessage!!.text,
                    onDismiss = {
                        viewModel.cancelEditing()
                        updateComposer(TextFieldValue(""))
                    }
                )
            } else {
                uiState.replyTo?.let { reply ->
                    ReplyBar(
                        replyTo = reply,
                        onDismiss = { viewModel.clearReplyTo() }
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                if (uiState.mentionSuggestions.isNotEmpty() || uiState.mentionSearchLoading) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 4.dp)
                            .heightIn(max = 220.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        if (uiState.mentionSearchLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 220.dp)
                            ) {
                                items(
                                    uiState.mentionSuggestions,
                                    key = { it._id }
                                ) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                updateComposer(insertMentionAtCursor(composer, user))
                                                viewModel.clearMentionSuggestions()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = user.name?.trim().orEmpty()
                                                .ifEmpty { user.username ?: "?" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val un = user.username
                                        if (!un.isNullOrBlank()) {
                                            Text(
                                                text = " @$un",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                        enabled = !uiState.isUploading
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = stringResource(R.string.cd_attach_file),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showCreatePollSheet = true },
                        enabled = !uiState.isUploading
                    ) {
                        Icon(
                            Icons.Default.Poll,
                            contentDescription = "Создать опрос",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showEmojiKeyboard = true },
                        enabled = !uiState.isUploading
                    ) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = stringResource(R.string.cd_emoji),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ComposerWithSelectionToolbar(
                            composer = composer,
                            onComposerChange = { updateComposer(it) },
                            enabled = !uiState.isUploading,
                            modifier = Modifier.fillMaxWidth(),
                            onRequestLinkDialog = { st -> composerLinkDialog = st },
                            hintText = if (uiState.editingMessage != null) {
                                stringResource(R.string.composer_editing)
                            } else {
                                stringResource(R.string.composer_hint)
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            val text = composer.text.trim()
                            if (text.isNotEmpty()) {
                                viewModel.sendMessage(text)
                                updateComposer(TextFieldValue(""))
                                viewModel.clearMentionSuggestions()
                            }
                        },
                        enabled = !uiState.isUploading
                    ) {
                        Text("➤", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }

    if (contextMenuMessage != null) {
        MessageContextMenu(
            message = contextMenuMessage!!,
            onDismiss = { contextMenuMessage = null },
            onReply = {
                viewModel.setReplyTo(contextMenuMessage!!)
                contextMenuMessage = null
            },
            onForward = {
                viewModel.setForwardMessage(contextMenuMessage!!)
                showForwardPicker = true
                contextMenuMessage = null
            },
            onStartDiscussion = {
                viewModel.createDiscussion(contextMenuMessage!!.id, contextMenuMessage!!.text)
                contextMenuMessage = null
            },
            onPin = {
                viewModel.pinMessage(contextMenuMessage!!.id)
                contextMenuMessage = null
            },
            onCopyLink = {
                val link = viewModel.buildPermalink(contextMenuMessage!!.id)
                clipboard.setText(AnnotatedString(link))
                Toast.makeText(context, context.getString(R.string.toast_link_copied), Toast.LENGTH_SHORT).show()
                contextMenuMessage = null
            },
            onCopyText = {
                clipboard.setText(AnnotatedString(contextMenuMessage!!.text))
                Toast.makeText(context, context.getString(R.string.toast_text_copied), Toast.LENGTH_SHORT).show()
                contextMenuMessage = null
            },
            onReadReceipts = {
                viewModel.loadReadReceipts(contextMenuMessage!!.id)
                contextMenuMessage = null
            },
            onEdit = if (
                contextMenuMessage!!.userId == uiState.currentUserId &&
                contextMenuMessage!!.msgType.isNullOrBlank()
            ) {
                {
                    viewModel.startEditing(contextMenuMessage!!)
                    contextMenuMessage = null
                }
            } else null,
            onAddReaction = {
                reactMessageId = contextMenuMessage!!.id
                contextMenuMessage = null
            },
            onDelete = if (
                contextMenuMessage!!.userId == uiState.currentUserId &&
                contextMenuMessage!!.msgType.isNullOrBlank()
            ) {
                {
                    viewModel.deleteMessage(contextMenuMessage!!.id)
                    contextMenuMessage = null
                }
            } else null
        )
    }

    uiState.readReceiptsSheet?.let { readSheet ->
        ModalBottomSheet(onDismissRequest = { viewModel.dismissReadReceiptsSheet() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(stringResource(R.string.chat_read_receipts_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                when {
                    readSheet.loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(36.dp))
                        }
                    }
                    readSheet.error != null -> {
                        Text(
                            readSheet.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    readSheet.readers.isEmpty() -> {
                        Text(
                            stringResource(R.string.chat_read_receipts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            items(readSheet.readers, key = { it.userId }) { row ->
                                Text(
                                    row.displayName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInRoomSearch && !viewModel.isThread) {
        InRoomSearchSheet(
            query = uiState.inRoomSearchQuery,
            loading = uiState.inRoomSearchLoading,
            results = uiState.inRoomSearchResults,
            onQueryChange = viewModel::onInRoomSearchQueryChange,
            onResultClick = { messageId ->
                val anchorId = uiState.messages.getOrNull(listState.firstVisibleItemIndex)?.id
                viewModel.scrollToInRoomSearchResult(messageId, anchorId)
                showInRoomSearch = false
            },
            onDismiss = {
                showInRoomSearch = false
                viewModel.clearInRoomSearch()
            }
        )
    }

    if (showForwardPicker) {
        ForwardRoomPickerSheet(
            rooms = uiState.forwardTargetRooms,
            serverUrl = uiState.serverUrl,
            onSelect = { roomId ->
                viewModel.forwardToRoom(roomId)
                showForwardPicker = false
            },
            onDismiss = {
                showForwardPicker = false
                viewModel.setForwardMessage(null)
            }
        )
    }

    if (showCreatePollSheet) {
        CreatePollSheet(
            onDismiss = { showCreatePollSheet = false },
            onCreate = { question, options, anonymous, multipleChoice ->
                viewModel.createPoll(question, options, anonymous, multipleChoice)
            }
        )
    }

    val forwardErrorReport = uiState.forwardErrorReport
    if (forwardErrorReport != null) {
        val scrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = { viewModel.clearForwardErrorReport() },
            title = { Text(stringResource(R.string.chat_forward_error_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.chat_error_report_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = forwardErrorReport,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(scrollState)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(forwardErrorReport))
                        Toast.makeText(context, context.getString(R.string.toast_report_copied), Toast.LENGTH_SHORT).show()
                        viewModel.clearForwardErrorReport()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(stringResource(R.string.action_copy))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearForwardErrorReport() }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    val uploadErrorReport = uiState.uploadErrorReport
    if (uploadErrorReport != null) {
        val uploadScroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { viewModel.clearUploadErrorReport() },
            title = { Text(stringResource(R.string.chat_upload_error_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.chat_error_report_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uploadErrorReport,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(uploadScroll)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(uploadErrorReport))
                        Toast.makeText(context, context.getString(R.string.toast_report_copied), Toast.LENGTH_SHORT).show()
                        viewModel.clearUploadErrorReport()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(stringResource(R.string.action_copy))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearUploadErrorReport() }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (reactMessageId != null) {
        EmojiPickerSheet(
            emojiStore = viewModel.emojiStore,
            serverUrl = uiState.serverUrl,
            recentCodes = recentEmojiCodes,
            onEmojiSelected = { code ->
                viewModel.recordEmojiPicked(code)
                reactMessageId?.let { viewModel.react(it, code) }
                reactMessageId = null
            },
            onDismiss = { reactMessageId = null }
        )
    }

    if (showEmojiKeyboard) {
        EmojiPickerSheet(
            emojiStore = viewModel.emojiStore,
            serverUrl = uiState.serverUrl,
            recentCodes = recentEmojiCodes,
            onEmojiSelected = { code ->
                viewModel.recordEmojiPicked(code)
                val add = viewModel.emojiStore.unicodeForComposerInsert(code)
                val newText = composer.text + add
                updateComposer(TextFieldValue(newText, TextRange(newText.length)))
                showEmojiKeyboard = false
            },
            onDismiss = { showEmojiKeyboard = false }
        )
    }

    composerLinkDialog?.let { st ->
        var url by remember(st) { mutableStateOf("https://") }
        var label by remember(st) { mutableStateOf(st.initialLabel) }
        AlertDialog(
            onDismissRequest = { composerLinkDialog = null },
            title = { Text(stringResource(R.string.chat_link_dialog_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.chat_link_text_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.chat_link_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateComposer(
                            insertMarkdownLink(composer, label, url, st.replaceStart, st.replaceEnd)
                        )
                        composerLinkDialog = null
                    }
                ) { Text(stringResource(R.string.action_insert)) }
            },
            dismissButton = {
                TextButton(onClick = { composerLinkDialog = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (fullScreenImageUrl != null) {
        val imageUrl = fullScreenImageUrl!!
        val fileName = extractFileName(imageUrl)
        FullScreenImageViewer(
            imageUrl = imageUrl,
            fileName = fileName,
            onDownload = { viewModel.downloadFile(imageUrl, fileName) },
            onCopyLink = {
                clipboard.setText(AnnotatedString(imageUrl))
                Toast.makeText(context, context.getString(R.string.toast_link_copied), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { fullScreenImageUrl = null }
        )
    }
}

private fun extractFileName(url: String): String {
    val cleaned = url.substringAfterLast('/').substringBefore('?')
    return cleaned.ifBlank { "file" }
}

/** Диапазон «@query» до курсора (включая позицию символа @). */
private data class MentionContext(val startIndex: Int)

/**
 * Если курсор стоит сразу после «@» и фрагмента имени (без пробелов), возвращает контекст и строку запроса.
 */
private fun findActiveMentionQuery(text: String, cursor: Int): Pair<MentionContext, String>? {
    if (cursor <= 0) return null
    val beforeCursor = text.substring(0, cursor)
    val at = beforeCursor.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0) {
        val prev = text[at - 1]
        if (!prev.isWhitespace() && prev != '(') return null
    }
    val afterAt = beforeCursor.substring(at + 1)
    if (afterAt.any { it.isWhitespace() || it == '\n' }) return null
    return MentionContext(at) to afterAt
}

private fun insertMentionAtCursor(composer: TextFieldValue, user: SpotlightUserDto): TextFieldValue {
    val text = composer.text
    val cursor = composer.selection.end
    val parsed = findActiveMentionQuery(text, cursor) ?: return composer
    val (mentionCtx, _) = parsed
    val username = user.username?.trim().orEmpty().ifEmpty {
        user.name?.trim()?.replace("\\s+".toRegex(), "_") ?: user._id
    }
    val before = text.substring(0, mentionCtx.startIndex)
    val after = text.substring(cursor)
    val inserted = "@$username "
    val newText = before + inserted + after
    val newPos = (before + inserted).length
    return TextFieldValue(newText, TextRange(newPos))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InRoomSearchSheet(
    query: String,
    loading: Boolean,
    results: List<MessageDto>,
    onQueryChange: (String) -> Unit,
    onResultClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.chat_in_room_search_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.chat_search_min_chars)) },
                singleLine = true
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(results, key = { it._id }) { m ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(m._id) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            m.u.name ?: m.u.username ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            m.msg.take(300).let { if (m.msg.length > 300) "$it…" else it },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardRoomPickerSheet(
    rooms: List<RoomEntity>,
    @Suppress("UNUSED_PARAMETER") serverUrl: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(rooms, searchQuery) {
        filterRoomsBySearchQuery(rooms, searchQuery)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = 24.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.chat_forward_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.chat_forward_search_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = if (rooms.isEmpty()) {
                            stringResource(R.string.chat_forward_no_rooms)
                        } else {
                            stringResource(R.string.chat_forward_no_results)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { room ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(room.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            room.displayName ?: room.name ?: room.id,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

private fun filterRoomsBySearchQuery(rooms: List<RoomEntity>, query: String): List<RoomEntity> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return rooms
    return rooms.filter { room ->
        listOf(room.displayName, room.name, room.id)
            .any { field -> field?.lowercase()?.contains(q) == true }
    }
}

@Composable
private fun EditMessageBar(previewText: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_context_edit_bar),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ReplyBar(replyTo: MessageEntity, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 36.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        ) {
            Text(
                text = replyTo.displayName ?: replyTo.username ?: stringResource(R.string.default_username_fallback),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = replyTo.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageContextMenu(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onStartDiscussion: () -> Unit,
    onPin: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyText: () -> Unit,
    onReadReceipts: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onAddReaction: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = message.text.take(60).let { if (message.text.length > 60) "$it..." else it },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 2
            )
            MenuAction(Icons.Default.Reply, stringResource(R.string.chat_context_reply), onReply)
            MenuAction(Icons.Default.Forward, stringResource(R.string.chat_context_forward), onForward)
            MenuAction(Icons.Default.Forum, stringResource(R.string.chat_context_start_discussion), onStartDiscussion)
            MenuAction(Icons.Default.PushPin, stringResource(R.string.chat_context_pin), onPin)
            MenuAction(Icons.Default.Link, stringResource(R.string.chat_context_copy_link), onCopyLink)
            MenuAction(Icons.Default.ContentCopy, stringResource(R.string.chat_context_copy_text), onCopyText)
            MenuAction(Icons.Default.Visibility, stringResource(R.string.chat_context_read_receipts), onReadReceipts)
            if (onEdit != null) {
                MenuAction(Icons.Default.Edit, stringResource(R.string.chat_context_edit), onEdit)
            }
            MenuAction(Icons.Default.TagFaces, stringResource(R.string.chat_context_add_reaction), onAddReaction)
            if (onDelete != null) {
                MenuAction(Icons.Default.Delete, stringResource(R.string.chat_context_delete), onDelete)
            }
        }
    }
}

@Composable
private fun SystemEventRow(message: MessageEntity) {
    val text = roomEventDisplayText(message)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Плавный переход к элементу без [androidx.compose.foundation.lazy.animateScrollToItem]
 * (в текущем Compose BOM расширение недоступно): интерполяция индекса + [LazyListState.scrollToItem].
 */
private suspend fun smoothScrollLazyToItem(
    listState: LazyListState,
    targetIndex: Int,
    durationMillis: Int = 220
) {
    val count = listState.layoutInfo.totalItemsCount
    if (count == 0) return
    val target = targetIndex.coerceIn(0, count - 1)
    val start = listState.firstVisibleItemIndex.coerceIn(0, count - 1)
    if (start == target) {
        listState.scrollToItem(target)
        return
    }
    val frameMs = 16L
    val steps = (durationMillis / frameMs.toInt()).coerceIn(5, 14)
    for (s in 1..steps) {
        val t = s / steps.toFloat()
        val eased = FastOutSlowInEasing.transform(t)
        val idx = (start + (target - start) * eased).roundToInt().coerceIn(0, count - 1)
        listState.scrollToItem(idx)
        delay(frameMs)
    }
    listState.scrollToItem(target)
}

@Composable
private fun roomEventDisplayText(message: MessageEntity): String {
    val defaultParticipant = stringResource(R.string.default_participant)
    val body = message.text.trim()
    val who = message.displayName ?: message.username ?: defaultParticipant
    val actorLabel = buildString {
        val name = message.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val un = message.username?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }
        when {
            name != null && un != null -> append("$name $un")
            name != null -> append(name)
            un != null -> append(un)
            else -> append(defaultParticipant)
        }
    }
    when (message.msgType) {
        "added-user-to-team" -> {
            val target = body.removePrefix("@").trim().takeIf { it.isNotEmpty() } ?: "…"
            return stringResource(R.string.room_event_added_user_to_team, actorLabel, target)
        }
        "removed-user-from-team" -> {
            val target = body.removePrefix("@").trim().takeIf { it.isNotEmpty() } ?: "…"
            return stringResource(R.string.room_event_removed_user_from_team, actorLabel, target)
        }
        "user-added-room-to-team" -> {
            return if (body.isNotEmpty()) {
                stringResource(R.string.room_event_actor_body, actorLabel, body)
            } else {
                stringResource(R.string.room_event_user_added_room_to_team, actorLabel)
            }
        }
        "user-removed-room-from-team", "user-deleted-room-from-team" -> {
            return if (body.isNotEmpty()) {
                stringResource(R.string.room_event_actor_body, actorLabel, body)
            } else {
                stringResource(R.string.room_event_user_removed_room_from_team, actorLabel)
            }
        }
        "user-converted-to-team" -> {
            return if (body.isNotEmpty()) body else stringResource(R.string.room_event_user_converted_to_team, who)
        }
        "user-converted-to-channel" -> {
            return if (body.isNotEmpty()) body else stringResource(R.string.room_event_user_converted_to_channel, who)
        }
        "ujt" -> return stringResource(R.string.room_event_who_joined_team, who)
    }
    if (body.isNotEmpty()) return body
    return when (message.msgType) {
        "uj" -> stringResource(R.string.room_event_uj, who)
        "ul" -> stringResource(R.string.room_event_ul, who)
        "au" -> stringResource(R.string.room_event_au, who)
        "ru" -> stringResource(R.string.room_event_ru, who)
        "ult" -> stringResource(R.string.room_event_ult, who)
        "ut" -> stringResource(R.string.room_event_ut, who)
        "rm" -> stringResource(R.string.system_msg_deleted)
        "message_pinned" -> stringResource(R.string.system_msg_pinned)
        "message_unpinned" -> stringResource(R.string.system_msg_unpinned)
        "discussion-created" -> stringResource(R.string.system_discussion_created)
        "jitsi_call_started" -> stringResource(R.string.system_call_started)
        "jitsi_call_ended" -> stringResource(R.string.system_call_ended)
        else -> message.msgType?.let { t -> stringResource(R.string.system_event_prefix, t) } ?: body
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun FullScreenImageViewer(
    imageUrl: String,
    fileName: String,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    /**
     * Иначе панорама при зуме ощущается заметно медленнее жеста: [panChange] в координатах экрана,
     * а сдвиг применяется к уже отмасштабированному слою — усиливаем пропорционально [scale].
     */
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(0.5f, 5f)
        scale = newScale
        val panBoost = newScale.coerceAtLeast(1f)
        offset += panChange * panBoost
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close),
                    tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.chat_download),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDownload)
                )
                androidx.compose.material3.Text(
                    text = stringResource(R.string.chat_context_copy_link),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onCopyLink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentEmojiRow(
    codes: List<String>,
    emojiStore: EmojiStore,
    onEmojiSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chat_recent_emoji),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(codes, key = { it }) { code ->
                val customUrl = emojiStore.getCustomEmojiUrl(code)
                val unicode = emojiStore.unicodeForReactionOrKey(code)
                val tw = if (customUrl == null && unicode.isNotBlank() && !unicode.startsWith(":")) {
                    TwemojiUrls.png72Url(unicode)
                } else {
                    null
                }
                when {
                    customUrl != null -> AsyncImage(
                        model = customUrl,
                        contentDescription = code,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { onEmojiSelected(code) }
                            .padding(4.dp)
                    )
                    tw != null -> AsyncImage(
                        model = tw,
                        contentDescription = code,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { onEmojiSelected(code) }
                            .padding(4.dp)
                    )
                    else -> Text(
                        text = unicode.ifBlank { code },
                        fontSize = 26.sp,
                        modifier = Modifier
                            .clickable { onEmojiSelected(code) }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(
    emojiStore: EmojiStore,
    serverUrl: String?,
    recentCodes: List<String>,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val customEmojis by emojiStore.customEmojis.collectAsState()
    val categories = remember { emojiStore.getStandardByCategory() }
    val searchRows = remember { emojiStore.getStandardEmojiSearchRows() }
    var searchQuery by remember { mutableStateOf("") }
    val queryTrimmed = searchQuery.trim()
    val isSearchActive = queryTrimmed.isNotEmpty()
    val searchTokens = remember(queryTrimmed) {
        queryTrimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    val filteredStandard = remember(searchTokens, searchRows) {
        if (searchTokens.isEmpty()) emptyList<EmojiPickerSearchRow>()
        else searchRows.filter { row -> searchTokens.all { row.searchText.contains(it) } }
    }
    val filteredCustom = remember(searchTokens, customEmojis) {
        if (searchTokens.isEmpty()) emptyList()
        else customEmojis.filter { e ->
            val name = e.code.trim(':', ':').lowercase(Locale.ROOT)
            val hay = "$name ${e.code.lowercase(Locale.ROOT)}"
            searchTokens.all { hay.contains(it) }
        }
    }
    val customTabLabel = stringResource(R.string.chat_emoji_custom_tab)
    val tabNames = remember(customEmojis, customTabLabel) {
        val list = mutableListOf<String>()
        if (customEmojis.isNotEmpty()) list.add(customTabLabel)
        list.addAll(categories.keys)
        list
    }
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.chat_emoji_search_placeholder), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                textStyle = TextStyle(fontSize = 14.sp)
            )

            if (recentCodes.isNotEmpty() && !isSearchActive) {
                RecentEmojiRow(
                    codes = recentCodes,
                    emojiStore = emojiStore,
                    onEmojiSelected = onEmojiSelected
                )
            }

            if (isSearchActive) {
                val merged = remember(filteredCustom, filteredStandard) {
                    buildList {
                        filteredCustom.forEach { add(SearchPickItem.Custom(it)) }
                        filteredStandard.forEach { add(SearchPickItem.Std(it.shortcode, it.unicode)) }
                    }
                }
                if (merged.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.chat_emoji_nothing_found), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        items(merged, key = { it.stableKey }) { item ->
                            when (item) {
                                is SearchPickItem.Custom -> {
                                    val url = item.entry.imageUrl ?: return@items
                                    AsyncImage(
                                        model = url,
                                        contentDescription = item.entry.code,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable { onEmojiSelected(item.entry.code) }
                                            .padding(4.dp)
                                    )
                                }
                                is SearchPickItem.Std -> {
                                    val tw = TwemojiUrls.png72Url(item.unicode)
                                    if (tw != null) {
                                        AsyncImage(
                                            model = tw,
                                            contentDescription = item.code,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clickable { onEmojiSelected(item.code) }
                                                .padding(4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = item.unicode,
                                            fontSize = 26.sp,
                                            modifier = Modifier
                                                .clickable { onEmojiSelected(item.code) }
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabNames.forEachIndexed { index, name ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(name, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }

                val hasCustom = customEmojis.isNotEmpty()
                val isCustomTab = hasCustom && selectedTab == 0
                val stdCatIndex = if (hasCustom) selectedTab - 1 else selectedTab

                if (isCustomTab) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        items(customEmojis, key = { it.code }) { emoji ->
                            val url = emoji.imageUrl ?: return@items
                            AsyncImage(
                                model = url,
                                contentDescription = emoji.code,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { onEmojiSelected(emoji.code) }
                                    .padding(4.dp)
                            )
                        }
                    }
                } else {
                    val catKey = categories.keys.toList().getOrNull(stdCatIndex)
                    val emojis = catKey?.let { categories[it] } ?: emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        items(emojis, key = { it.first }) { (code, unicode, _) ->
                            val tw = TwemojiUrls.png72Url(unicode)
                            if (tw != null) {
                                AsyncImage(
                                    model = tw,
                                    contentDescription = code,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable { onEmojiSelected(code) }
                                        .padding(4.dp)
                                )
                            } else {
                                Text(
                                    text = unicode,
                                    fontSize = 26.sp,
                                    modifier = Modifier
                                        .clickable { onEmojiSelected(code) }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class SearchPickItem {
    abstract val stableKey: String

    data class Custom(val entry: EmojiEntry) : SearchPickItem() {
        override val stableKey: String get() = "c:${entry.code}"
    }

    data class Std(val code: String, val unicode: String) : SearchPickItem() {
        override val stableKey: String get() = "s:$code"
    }
}

@Composable
private fun ReactionChip(
    reactionKey: String,
    count: Int,
    emojiStore: EmojiStore,
    onClick: () -> Unit
) {
    val customUrl = emojiStore.customEmojiUrlForReactionKey(reactionKey)
    val unicode = emojiStore.unicodeForReactionOrKey(reactionKey)
    val twUrl =
        if (customUrl == null && unicode.isNotBlank() && !unicode.startsWith(":")) {
            TwemojiUrls.png72Url(unicode)
        } else {
            null
        }
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            customUrl != null -> AsyncImage(
                model = customUrl,
                contentDescription = reactionKey,
                modifier = Modifier.size(18.dp)
            )
            twUrl != null -> AsyncImage(
                model = twUrl,
                contentDescription = reactionKey,
                modifier = Modifier.size(18.dp)
            )
            else -> Text(
                text = unicode.ifBlank { reactionKey },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(text = count.toString(), fontSize = 13.sp)
    }
}

@Composable
private fun FormattedMessageMarkdown(
    formatted: AnnotatedString,
    style: TextStyle,
    color: Color,
    onUrlClick: (String) -> Boolean = { false }
) {
    val uriHandler = LocalUriHandler.current
    val clickable = formatted.getStringAnnotations("URL", 0, formatted.length)
    if (clickable.isEmpty()) {
        Text(text = formatted, style = style, color = color)
    } else {
        @Suppress("DEPRECATION")
        androidx.compose.foundation.text.ClickableText(
            text = formatted,
            style = style.copy(color = color),
            onClick = { offset ->
                formatted.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.let { annotation ->
                        if (!onUrlClick(annotation.item)) {
                            try {
                                uriHandler.openUri(annotation.item)
                            } catch (_: Exception) {
                            }
                        }
                    }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    isMine: Boolean,
    isThreadContext: Boolean = false,
    serverUrl: String?,
    peerPresence: UserPresenceStatus = UserPresenceStatus.UNKNOWN,
    emojiStore: EmojiStore,
    highlight: Boolean = false,
    onQuoteClick: (() -> Unit)? = null,
    /** Тап по аватарке / имени (чужие сообщения). */
    onPeerProfileClick: (() -> Unit)? = null,
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onFileDownload: (String, String) -> Unit,
    onFileCopyLink: (String) -> Unit,
    onReactionClick: (String) -> Unit,
    onVotePoll: ((appId: String, blockId: String, actionId: String, value: String) -> Unit)? = null,
    votedPollValues: Set<String> = emptySet(),
    onOpenThread: (() -> Unit)? = null,
    onOpenDiscussion: (() -> Unit)? = null,
    onUrlClick: (String) -> Boolean = { false }
) {
    val defaultUserName = stringResource(R.string.default_username_fallback)
    val downloadLabel = stringResource(R.string.chat_download)
    val copyFileLinkLabel = stringResource(R.string.chat_file_copy_link)
    val discussionLabel = stringResource(R.string.chat_discussion_label)
    val avatarUrl = if (!isMine && message.username != null && serverUrl != null) {
        serverUrl.trimEnd('/') + "/avatar/" + message.username + "?format=png"
    } else null

    val reactions = parseReactions(message.reactions)
    val mentions = remember(message.mentionsJson) { parseStoredMentionsJson(message.mentionsJson) }
    val scheme = MaterialTheme.colorScheme
    val linkColor = if (isMine) scheme.onPrimaryContainer else Color(0xFF1E88E5)
    val mentionColor = if (isMine) scheme.onPrimaryContainer else Color(0xFF1565C0)
    val caption = message.fileDescription?.trim()?.takeIf { it.isNotEmpty() }
    val bodyRaw = message.text.trim()
    /** Не дублировать текст под медиа, если он совпадает с телом сообщения; подпись под картой/файлом — форматировать ссылками. */
    val showMainTextBody = bodyRaw.isNotEmpty()
    val showCaptionUnderMedia = caption != null && (bodyRaw.isEmpty() || caption != bodyRaw)
    val formattedText = remember(message.text, message.mentionsJson, linkColor, mentionColor) {
        formatMessage(
            stripRocketQuotePermalinkMarkdown(message.text),
            emojiStore,
            mentions,
            linkColor = linkColor,
            mentionColor = mentionColor
        )
    }
    val formattedCaption = remember(caption, message.mentionsJson, linkColor, mentionColor) {
        if (caption.isNullOrBlank()) null
        else formatMessage(
            stripRocketQuotePermalinkMarkdown(caption),
            emojiStore,
            mentions,
            linkColor = linkColor,
            mentionColor = mentionColor
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            PresenceRingAsyncImage(
                model = avatarUrl,
                contentDescription = null,
                size = 32.dp,
                presence = peerPresence,
                onClick = onPeerProfileClick
            )
        }
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .widthIn(max = 300.dp)
                    .wrapContentWidth(if (isMine) Alignment.End else Alignment.Start),
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .wrapContentWidth(if (isMine) Alignment.End else Alignment.Start)
                        .background(
                            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 16.dp
                            )
                        )
                        .then(
                            if (highlight) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.tertiary,
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(12.dp),
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
            ) {
                if (!isMine) {
                    Text(
                        text = message.displayName ?: message.username ?: defaultUserName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = if (onPeerProfileClick != null) {
                            Modifier.clickable { onPeerProfileClick() }
                        } else {
                            Modifier
                        }
                    )
                }

                val quoteSegments = remember(message.quoteChainJson, message.quoteText, message.quoteAuthor) {
                    quoteSegmentsForMessageEntity(message)
                }
                if (quoteSegments.isNotEmpty()) {
                    val quoteModifier = if (message.quotedMessageId != null && onQuoteClick != null) {
                        Modifier.clickable { onQuoteClick() }
                    } else {
                        Modifier
                    }
                    Column(modifier = quoteModifier.widthIn(max = 276.dp)) {
                        quoteSegments.forEachIndexed { index, seg ->
                            QuoteBubble(
                                quoteAuthor = seg.author,
                                quoteText = seg.text,
                                emojiStore = emojiStore,
                                mentions = mentions,
                                isMine = isMine,
                                modifier = Modifier.padding(start = (index * 8).dp),
                                onUrlClick = onUrlClick
                            )
                            if (index < quoteSegments.lastIndex) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (showMainTextBody) {
                    FormattedMessageMarkdown(
                        formatted = formattedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMine) scheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        onUrlClick = onUrlClick
                    )
                }

                if (message.imageUrl != null) {
                    val base = serverUrl?.trimEnd('/') ?: ""
                    val previewUrl = if (message.imageUrl.startsWith("http")) message.imageUrl
                        else "$base${message.imageUrl}"
                    val fullUrl = (message.fullImageUrl ?: message.imageUrl).let { raw ->
                        if (raw.startsWith("http")) raw else "$base$raw"
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = caption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(fullUrl) },
                        contentScale = ContentScale.FillWidth
                    )
                    if (showCaptionUnderMedia && formattedCaption != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FormattedMessageMarkdown(
                            formatted = formattedCaption,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMine) scheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            onUrlClick = onUrlClick
                        )
                    }
                } else if (message.fileUrl != null) {
                    val base = serverUrl?.trimEnd('/') ?: ""
                    val fileUrlAbs = if (message.fileUrl.startsWith("http")) message.fileUrl
                    else "$base${message.fileUrl}"
                    val fileName = message.fileName?.ifBlank { null } ?: extractFileName(fileUrlAbs)
                    val fileType = message.fileType?.ifBlank { null }.orEmpty()

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .background(
                                color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (fileType.isNotBlank()) {
                                Text(
                                    text = fileType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (showCaptionUnderMedia && formattedCaption != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                FormattedMessageMarkdown(
                                    formatted = formattedCaption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isMine) scheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onUrlClick = onUrlClick
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = downloadLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onFileDownload(fileUrlAbs, fileName) }
                            )
                            Text(
                                text = copyFileLinkLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onFileCopyLink(fileUrlAbs) }
                            )
                        }
                    }
                }

                if (!message.blocksJson.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PollBlock(
                        blocksJson = message.blocksJson,
                        onVote = { appId, blockId, actionId, value ->
                            onVotePoll?.invoke(appId, blockId, actionId, value)
                        },
                        votedValues = votedPollValues
                    )
                }

                if (!isThreadContext && message.tmid.isNullOrBlank()) {
                    if (onOpenThread != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenThread() }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(
                                    R.string.chat_thread_replies,
                                    message.threadReplyCount ?: 0
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (onOpenDiscussion != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = discussionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenDiscussion() }
                                .padding(vertical = 2.dp)
                        )
                    }
                }

                if (isMine) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMessageTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageDeliveryTicks(
                            readByOthers = message.readByOthers,
                            syncStatus = message.syncStatus
                        )
                    }
                } else {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (reactions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    reactions.forEach { (emoji, count) ->
                        ReactionChip(
                            reactionKey = emoji,
                            count = count,
                            emojiStore = emojiStore,
                            onClick = { onReactionClick(emoji) }
                        )
                    }
                }
            }
        }
        if (isMine) {
            Box(modifier = Modifier.size(32.dp))
        }
    }
}

private val MessageTickBlue = Color(0xFF2196F3)

@Composable
private fun MessageDeliveryTicks(
    readByOthers: Boolean?,
    syncStatus: Int
) {
    val blue = MessageTickBlue
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        syncStatus == MessageEntity.SYNC_PENDING -> Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = stringResource(R.string.cd_message_pending),
            modifier = Modifier.size(14.dp),
            tint = muted
        )
        readByOthers == true -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = stringResource(R.string.cd_message_read),
            modifier = Modifier.size(15.dp),
            tint = blue
        )
        else -> Icon(
            imageVector = Icons.Default.Done,
            contentDescription = stringResource(R.string.cd_message_sent),
            modifier = Modifier.size(15.dp),
            tint = blue
        )
    }
}

private fun parseReactions(json: String?): List<Pair<String, Int>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().map { key ->
            val arr = obj.optJSONArray(key)
            key to (arr?.length() ?: 0)
        }.filter { it.second > 0 }.toList()
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun QuoteBubble(
    quoteAuthor: String?,
    quoteText: String,
    emojiStore: EmojiStore,
    mentions: List<StoredMention>,
    isMine: Boolean = false,
    modifier: Modifier = Modifier,
    onUrlClick: (String) -> Boolean = { false }
) {
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val linkColor = if (isMine) scheme.onPrimaryContainer else Color(0xFF1E88E5)
    val mentionColor = if (isMine) scheme.onPrimaryContainer else Color(0xFF1565C0)
    val formattedQuote = remember(quoteText, mentions, linkColor, mentionColor) {
        formatMessage(
            stripRocketQuotePermalinkMarkdown(quoteText),
            emojiStore,
            mentions,
            linkColor = linkColor,
            mentionColor = mentionColor
        )
    }
    val quoteStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Column(
        modifier = modifier
            .widthIn(max = 276.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        if (quoteAuthor != null) {
            Text(
                text = quoteAuthor,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val clickable = formattedQuote.getStringAnnotations("URL", 0, formattedQuote.length)
        if (clickable.isEmpty()) {
            Text(
                text = formattedQuote,
                style = quoteStyle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            @Suppress("DEPRECATION")
            androidx.compose.foundation.text.ClickableText(
                text = formattedQuote,
                style = quoteStyle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                onClick = { offset ->
                    formattedQuote.getStringAnnotations("URL", offset, offset)
                        .firstOrNull()?.let { annotation ->
                            if (!onUrlClick(annotation.item)) {
                                try {
                                    uriHandler.openUri(annotation.item)
                                } catch (_: Exception) {
                                }
                            }
                        }
                }
            )
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(timestamp)
    val messageDay = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return if (messageDay == today) {
        DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(instant)
    } else {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(instant)
    }
}
