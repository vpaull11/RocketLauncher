package com.rocketlauncher.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rocketlauncher.data.dto.SpotlightUserDto
import com.rocketlauncher.data.realtime.UserPresenceSnapshot
import com.rocketlauncher.data.realtime.UserPresenceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomMembersBottomSheet(
    serverUrl: String?,
    presenceSnapshot: UserPresenceSnapshot,
    members: List<RoomMemberRowUi>,
    loading: Boolean,
    error: String?,
    currentUserId: String?,
    inviteDialogOpen: Boolean,
    inviteQuery: String,
    inviteSuggestions: List<SpotlightUserDto>,
    inviteLoading: Boolean,
    roleTarget: RoomMemberRowUi?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenInvite: () -> Unit,
    onInviteQueryChange: (String) -> Unit,
    onPickInviteUser: (SpotlightUserDto) -> Unit,
    onDismissInvite: () -> Unit,
    onMemberRolesClick: (RoomMemberRowUi) -> Unit,
    onDismissRolePicker: () -> Unit,
    onRoleAction: (RoomMemberRoleAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (roleTarget != null) {
        AlertDialog(
            onDismissRequest = onDismissRolePicker,
            title = { Text(roleTarget.name ?: roleTarget.username ?: "Участник") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "@${roleTarget.username ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Доступно при наличии прав на сервере.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onRoleAction(RoomMemberRoleAction.ADD_MODERATOR) }) {
                        Text("Сделать модератором")
                    }
                    TextButton(onClick = { onRoleAction(RoomMemberRoleAction.REMOVE_MODERATOR) }) {
                        Text("Снять модератора")
                    }
                    TextButton(onClick = { onRoleAction(RoomMemberRoleAction.ADD_OWNER) }) {
                        Text("Сделать владельцем")
                    }
                    TextButton(onClick = { onRoleAction(RoomMemberRoleAction.REMOVE_OWNER) }) {
                        Text("Снять владельца")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRolePicker) { Text("Закрыть") }
            }
        )
    }

    if (inviteDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissInvite,
            title = { Text("Пригласить пользователя") },
            text = {
                Column {
                    OutlinedTextField(
                        value = inviteQuery,
                        onValueChange = onInviteQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Имя или @username") },
                        singleLine = true
                    )
                    if (inviteLoading) {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(inviteSuggestions, key = { it._id }) { u ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickInviteUser(u) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    u.name ?: u.username ?: u._id,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!u.username.isNullOrBlank()) {
                                    Text(
                                        " @${u.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissInvite) { Text("Закрыть") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Участники", style = MaterialTheme.typography.titleLarge)
                Row {
                    IconButton(onClick = onOpenInvite) {
                        Icon(Icons.Default.Add, contentDescription = "Пригласить")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading && members.isEmpty() -> {
                    Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(members, key = { it.id }) { m ->
                            MemberRow(
                                member = m,
                                serverUrl = serverUrl,
                                presence = presenceSnapshot.resolve(m.id, m.username),
                                showRoleButton = m.id != currentUserId,
                                onRolesClick = { onMemberRolesClick(m) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: RoomMemberRowUi,
    serverUrl: String?,
    presence: UserPresenceStatus,
    showRoleButton: Boolean,
    onRolesClick: () -> Unit
) {
    val un = member.username
    val avatarUrl = if (!un.isNullOrBlank() && serverUrl != null) {
        serverUrl.trimEnd('/') + "/avatar/" + un + "?format=png"
    } else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PresenceRingAsyncImage(
            model = avatarUrl,
            contentDescription = null,
            size = 40.dp,
            presence = presence
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                member.name ?: member.username ?: member.id,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!member.username.isNullOrBlank()) {
                Text(
                    "@${member.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showRoleButton) {
            IconButton(onClick = onRolesClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Права")
            }
        }
    }
}
