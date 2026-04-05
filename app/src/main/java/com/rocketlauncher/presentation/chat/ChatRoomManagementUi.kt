package com.rocketlauncher.presentation.chat

/** Строка списка участников комнаты. */
data class RoomMemberRowUi(
    val id: String,
    val username: String?,
    val name: String?
)

/** Одноразовый переход в созданную/открытую комнату (обсуждение и т.д.). */
data class PendingOpenRoomChat(
    val roomId: String,
    val title: String,
    val type: String
)

/** Переход в тред с подсветкой сообщения (например после глобального поиска по ответу в треде). */
data class PendingThreadNavigation(
    val tmid: String,
    val threadTitle: String,
    val highlightMessageId: String
)

enum class RoomMemberRoleAction {
    ADD_MODERATOR,
    REMOVE_MODERATOR,
    ADD_OWNER,
    REMOVE_OWNER
}
