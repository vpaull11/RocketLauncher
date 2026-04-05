package com.rocketlauncher.presentation.rooms

import com.rocketlauncher.data.db.RoomEntity

enum class CreateRoomKind {
    CHANNEL,
    GROUP,
    DISCUSSION,
    TEAM
}

data class CreateRoomDialogState(
    val kind: CreateRoomKind,
    val name: String = "",
    val readOnlyChannel: Boolean = false,
    val privateTeam: Boolean = true,
    val allDiscussionParents: List<RoomEntity> = emptyList(),
    val discussionParentFilter: String = "",
    val discussionParentId: String? = null,
    val discussionParentLabel: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class PendingOpenCreatedRoom(
    val roomId: String,
    val title: String,
    val type: String,
    val avatarPath: String = ""
)
