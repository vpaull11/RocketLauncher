package com.rocketlauncher.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val displayName: String?,
    val type: String, // c, p, d
    val avatarPath: String? = null,
    val lastMessageText: String?,
    val lastMessageTime: String?,
    val lastMessageUserId: String?,
    val lastMessageUsername: String?,
    val unreadCount: Int = 0,
    /** Непрочитанные в тредах (размер [SubscriptionDto.tunread] на сервере). */
    val threadUnreadCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isDiscussion: Boolean = false,
    val isTeam: Boolean = false,
    val userMentions: Int = 0,
    /** Не показывать push/локальные оповещения (синхронизируется с сервером: disableNotifications / mobilePush). */
    val notificationsMuted: Boolean = false,
    /**
     * Локально: при [false] и не заглушенном чате — не показывать оповещения для **обсуждений** (сабчатов),
     * у которых [discussionParentId] = этот канал/группа. На основной поток комнаты не влияет.
     */
    val notifySubchats: Boolean = true,
    /** Для обсуждений: id родительской комнаты ([RoomDto.prid]); иначе null. */
    val discussionParentId: String? = null
)
