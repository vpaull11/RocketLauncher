package com.rocketlauncher.presentation.rooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rocketlauncher.R

@Composable
fun CreateRoomDialog(
    state: CreateRoomDialogState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onReadOnlyChannelChange: (Boolean) -> Unit,
    onPrivateTeamChange: (Boolean) -> Unit,
    onDiscussionFilterChange: (String) -> Unit,
    onPickDiscussionParent: (com.rocketlauncher.data.db.RoomEntity) -> Unit,
    onSubmit: () -> Unit
) {
    val title = stringResource(
        when (state.kind) {
            CreateRoomKind.CHANNEL -> R.string.create_room_channel_title
            CreateRoomKind.GROUP -> R.string.create_room_group_title
            CreateRoomKind.DISCUSSION -> R.string.create_room_discussion_title
            CreateRoomKind.TEAM -> R.string.create_room_team_title
        }
    )
    val filter = state.discussionParentFilter.trim().lowercase()
    val parents = if (state.kind != CreateRoomKind.DISCUSSION) {
        emptyList()
    } else if (filter.isEmpty()) {
        state.allDiscussionParents.take(40)
    } else {
        state.allDiscussionParents.filter { r ->
            val n = (r.displayName ?: r.name ?: "").lowercase()
            n.contains(filter) || r.id.contains(filter, ignoreCase = true)
        }.take(40)
    }

    AlertDialog(
        onDismissRequest = { if (!state.loading) onDismiss() },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.error != null) {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                when (state.kind) {
                    CreateRoomKind.CHANNEL, CreateRoomKind.GROUP, CreateRoomKind.TEAM -> {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    stringResource(
                                        when (state.kind) {
                                            CreateRoomKind.CHANNEL -> R.string.create_room_channel_name_label
                                            CreateRoomKind.GROUP -> R.string.create_room_group_name_label
                                            else -> R.string.create_room_team_name_label
                                        }
                                    )
                                )
                            },
                            singleLine = true,
                            enabled = !state.loading
                        )
                        if (state.kind == CreateRoomKind.CHANNEL) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.create_room_readonly),
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = state.readOnlyChannel,
                                    onCheckedChange = onReadOnlyChannelChange,
                                    enabled = !state.loading
                                )
                            }
                        }
                        if (state.kind == CreateRoomKind.TEAM) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.create_room_private_team),
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = state.privateTeam,
                                    onCheckedChange = onPrivateTeamChange,
                                    enabled = !state.loading
                                )
                            }
                        }
                    }
                    CreateRoomKind.DISCUSSION -> {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.create_room_discussion_name_label)) },
                            singleLine = true,
                            enabled = !state.loading
                        )
                        Spacer(Modifier.height(8.dp))
                        val parentLabel = state.discussionParentLabel
                            ?: stringResource(R.string.create_room_parent_not_selected)
                        Text(
                            stringResource(R.string.create_room_parent_chat, parentLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.discussionParentFilter,
                            onValueChange = onDiscussionFilterChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.create_room_search_parent)) },
                            singleLine = true,
                            enabled = !state.loading
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(parents, key = { it.id }) { r ->
                                Text(
                                    r.displayName ?: r.name ?: r.id,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !state.loading) { onPickDiscussionParent(r) }
                                        .padding(vertical = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !state.loading
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.loading) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
