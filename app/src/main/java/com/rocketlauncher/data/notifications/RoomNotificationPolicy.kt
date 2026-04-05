package com.rocketlauncher.data.notifications

import com.rocketlauncher.data.db.RoomDao

/**
 * Локальные правила поверх серверного mute: режим «только основной канал» отключает превью/push
 * для **обсуждений**, у которых в БД указан родитель с [notifySubchats] = false.
 */
object RoomNotificationPolicy {

    suspend fun shouldNotifyForRoom(roomDao: RoomDao, roomId: String): Boolean {
        val room = roomDao.getRoom(roomId) ?: return true
        if (room.notificationsMuted) return false
        if (room.isDiscussion) {
            val parentId = room.discussionParentId?.takeIf { it.isNotBlank() } ?: return true
            val parent = roomDao.getRoom(parentId) ?: return true
            if (!parent.notifySubchats) return false
        }
        return true
    }

    /**
     * Push по новому ответу в треде (родительская комната [parentRoomId]):
     * учитываем mute и режим «только основной канал» ([notifySubchats] = false).
     * Дополнительно в [RealtimeMessageService] показ урезается: только @я / @all / @here
     * или участие в треде ([ThreadParticipationPrefs]).
     */
    suspend fun shouldNotifyForThreadReply(roomDao: RoomDao, parentRoomId: String): Boolean {
        val parent = roomDao.getRoom(parentRoomId) ?: return true
        if (parent.notificationsMuted) return false
        if (!parent.notifySubchats) return false
        return true
    }
}
