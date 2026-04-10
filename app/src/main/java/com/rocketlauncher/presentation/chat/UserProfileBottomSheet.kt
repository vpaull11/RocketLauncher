package com.rocketlauncher.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rocketlauncher.R
import com.rocketlauncher.data.realtime.UserPresenceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileBottomSheet(
    state: UserProfileSheetUi,
    livePresence: UserPresenceStatus,
    serverUrl: String?,
    onDismiss: () -> Unit,
    onOpenDirectChat: () -> Unit,
    onVideoCall: () -> Unit,
    /** Показывать кнопку «Личные сообщения» (скрываем в уже открытой личке с этим человеком). */
    showDirectChatButton: Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val username = state.details?.username ?: state.usernameHint
    val avatarUrl = if (!username.isNullOrBlank() && serverUrl != null) {
        serverUrl.trimEnd('/') + "/avatar/" + username + "?format=png"
    } else null
    val ringPresence =
        if (livePresence != UserPresenceStatus.UNKNOWN) livePresence
        else UserPresenceStatus.fromRestApi(state.details?.status)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.loading) {
                BoxCenteredLoading()
            } else if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    PresenceRingAsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        size = 120.dp,
                        presence = ringPresence
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val title = state.details?.name?.takeIf { it.isNotBlank() }
                        ?: state.details?.nickname?.takeIf { it.isNotBlank() }
                        ?: state.fallbackName?.takeIf { it.isNotBlank() }
                        ?: username
                        ?: stringResource(R.string.default_username_fallback)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!username.isNullOrBlank()) {
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                val d = state.details
                if (d != null) {
                    ProfileField(stringResource(R.string.profile_field_status), listOfNotNull(d.status, d.statusText?.takeIf { it.isNotBlank() }).joinToString(stringResource(R.string.default_separator)).takeIf { it.isNotBlank() })
                    ProfileField(stringResource(R.string.profile_field_bio), d.bio?.takeIf { it.isNotBlank() })
                    d.emails?.firstOrNull()?.address?.takeIf { it.isNotBlank() }?.let { ProfileField("Email", it) }
                    d.nickname?.takeIf { it.isNotBlank() && it != d.name }?.let { ProfileField(stringResource(R.string.profile_field_nickname), it) }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showDirectChatButton) {
                        OutlinedButton(
                            onClick = onOpenDirectChat,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.profile_action_message), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(
                        onClick = onVideoCall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_action_call))
                    }
                }
                if (!showDirectChatButton) {
                    Text(
                        text = stringResource(R.string.profile_already_open_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxCenteredLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.profile_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProfileField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
