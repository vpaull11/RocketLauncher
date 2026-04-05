package com.rocketlauncher.presentation.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.rocketlauncher.R
import com.rocketlauncher.data.realtime.UserPresenceStatus
import com.rocketlauncher.presentation.chat.PresenceRingSubcomposeAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    onBack: () -> Unit,
    onMainChat: (roomId: String, roomName: String, roomType: String, avatarPath: String) -> Unit,
    onThread: (roomId: String, roomName: String, roomType: String, avatarPath: String, tmid: String, threadTitle: String) -> Unit,
    onDiscussion: (roomId: String, roomName: String, roomType: String, avatarPath: String) -> Unit,
    viewModel: ThreadListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl = uiState.serverUrl
    val avatarUrl = viewModel.avatarPath.takeIf { it.isNotBlank() }?.let { path ->
        serverUrl?.trimEnd('/')?.let { "$it/avatar/$path?format=png" }
    }
    val headerPresence =
        if (viewModel.roomType == "d" && viewModel.avatarPath.isNotBlank() &&
            !viewModel.avatarPath.startsWith("room/")
        ) {
            uiState.presenceSnapshot.resolve(null, viewModel.avatarPath)
        } else {
            UserPresenceStatus.UNKNOWN
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PresenceRingSubcomposeAsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            size = 36.dp,
                            presence = headerPresence,
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = viewModel.roomName.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = viewModel.roomName.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        )
                        Text(
                            viewModel.roomName.ifEmpty {
                                stringResource(R.string.threads_default_title)
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.items.size == 1 && uiState.items[0].type == ThreadItemType.MAIN && !uiState.isLoading -> {
                    LaunchedEffect(Unit) {
                        onMainChat(viewModel.roomId, viewModel.roomName, viewModel.roomType, viewModel.avatarPath)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            ThreadListItem(
                                item = item,
                                onClick = {
                                    when (item.type) {
                                        ThreadItemType.MAIN -> onMainChat(
                                            viewModel.roomId, viewModel.roomName,
                                            viewModel.roomType, viewModel.avatarPath
                                        )
                                        ThreadItemType.THREAD -> onThread(
                                            viewModel.roomId, viewModel.roomName,
                                            viewModel.roomType, viewModel.avatarPath,
                                            item.id, item.title
                                        )
                                        ThreadItemType.DISCUSSION -> onDiscussion(
                                            item.targetRoomId ?: item.id,
                                            item.title,
                                            item.targetRoomType ?: "p",
                                            ""
                                        )
                                    }
                                }
                            )
                            Divider(thickness = 0.5.dp)
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ThreadListItem(item: ThreadItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (item.type) {
            ThreadItemType.MAIN -> Icons.Default.Chat to MaterialTheme.colorScheme.primary
            ThreadItemType.THREAD -> Icons.Default.QuestionAnswer to MaterialTheme.colorScheme.tertiary
            ThreadItemType.DISCUSSION -> Icons.Default.Forum to MaterialTheme.colorScheme.secondary
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (item.type == ThreadItemType.MAIN) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.type != ThreadItemType.MAIN && item.replyCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${item.replyCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
