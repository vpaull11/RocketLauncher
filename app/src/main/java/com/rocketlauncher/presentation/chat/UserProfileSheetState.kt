package com.rocketlauncher.presentation.chat

import com.rocketlauncher.data.dto.UsersInfoUser

data class UserProfileSheetUi(
    val userId: String,
    val usernameHint: String?,
    val fallbackName: String?,
    val loading: Boolean = true,
    val details: UsersInfoUser? = null,
    val error: String? = null
)

data class NavigateToDirectChat(
    val roomId: String,
    val title: String,
    val avatarPath: String
)
