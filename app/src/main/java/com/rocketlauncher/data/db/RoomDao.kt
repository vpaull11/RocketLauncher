package com.rocketlauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY lastMessageTime DESC")
    fun observeAllRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms ORDER BY lastMessageTime DESC")
    suspend fun getAllRooms(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    fun observeRoom(roomId: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoom(roomId: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rooms: List<RoomEntity>)

    @Query(
        "UPDATE rooms SET lastMessageText = :text, lastMessageTime = :time, lastMessageUserId = :userId, " +
            "lastMessageUsername = :username, unreadCount = unreadCount + :unreadDelta, " +
            "userMentions = userMentions + :mentionsDelta, updatedAt = :updatedAt WHERE id = :roomId"
    )
    suspend fun updateLastMessage(
        roomId: String,
        text: String,
        time: String,
        userId: String?,
        username: String?,
        unreadDelta: Int = 1,
        mentionsDelta: Int = 0,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE rooms SET unreadCount = 0, userMentions = 0, threadUnreadCount = 0 WHERE id = :roomId")
    suspend fun markAsRead(roomId: String)

    @Query("UPDATE rooms SET threadUnreadCount = :count WHERE id = :roomId")
    suspend fun updateThreadUnreadCount(roomId: String, count: Int)

    @Query("UPDATE rooms SET unreadCount = :unreadCount, userMentions = :userMentions WHERE id = :roomId")
    suspend fun updateUnreadCounts(roomId: String, unreadCount: Int, userMentions: Int)

    /**
     * Обновить только [RoomEntity.updatedAt] — чтобы [observeRoom] эмитил при изменении
     * только [tunread]/[tunreadUser] в подписке (без смены unread в БД).
     */
    @Query("UPDATE rooms SET updatedAt = :updatedAt WHERE id = :roomId")
    suspend fun touchUpdatedAt(roomId: String, updatedAt: Long)

    /** Макс. локальный [RoomEntity.updatedAt] — для [subscriptions.get]?updatedSince после реконнекта. */
    @Query("SELECT MAX(updatedAt) FROM rooms")
    suspend fun maxRoomsUpdatedAt(): Long?

    @Query("SELECT id FROM rooms")
    suspend fun getAllRoomIds(): List<String>

    @Query("DELETE FROM rooms WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("DELETE FROM rooms")
    suspend fun deleteAll()

    @Query("UPDATE rooms SET notificationsMuted = :muted WHERE id = :roomId")
    suspend fun updateNotificationsMuted(roomId: String, muted: Boolean)

    @Query("UPDATE rooms SET notifySubchats = :notify WHERE id = :roomId")
    suspend fun updateNotifySubchats(roomId: String, notify: Boolean)

    @Query("UPDATE rooms SET isFavorite = :favorite WHERE id = :roomId")
    suspend fun updateFavorite(roomId: String, favorite: Boolean)
}
