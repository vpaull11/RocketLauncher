package com.rocketlauncher.data.notifications

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Какой чат сейчас открыт на экране [ChatScreen].
 * Нужен, чтобы не дублировать push при открытом чате, но показывать уведомления при открытом списке комнат.
 */
@Singleton
class OpenedChatTracker @Inject constructor() {
    private val openRoomId = AtomicReference<String?>(null)

    fun setOpenRoom(roomId: String) {
        openRoomId.set(roomId)
    }

    fun clearIfMatches(roomId: String) {
        openRoomId.compareAndSet(roomId, null)
    }

    fun isViewingRoom(roomId: String): Boolean = openRoomId.get() == roomId
}
