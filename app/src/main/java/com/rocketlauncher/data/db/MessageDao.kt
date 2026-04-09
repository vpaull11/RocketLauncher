package com.rocketlauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /** Сообщения основной ветки: ответы треда (tmid != null) не показываем. */
    @Query(
        "SELECT * FROM messages WHERE roomId = :roomId AND (tmid IS NULL OR tmid = '') " +
            "ORDER BY timestamp DESC"
    )
    fun observeMessages(roomId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE roomId = :roomId AND (tmid IS NULL OR tmid = '') " +
            "ORDER BY timestamp ASC"
    )
    suspend fun getMessages(roomId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE tmid = :tmid OR id = :tmid ORDER BY timestamp DESC")
    fun observeThreadMessages(tmid: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query(
        "SELECT MAX(timestamp) FROM messages WHERE roomId = :roomId AND (tmid IS NULL OR tmid = '')"
    )
    suspend fun getLatestTimestamp(roomId: String): Long?

    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM messages WHERE syncStatus = :status")
    suspend fun getPendingMessages(status: Int): List<MessageEntity>

    @Query("UPDATE messages SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Query("UPDATE messages SET readByOthers = :readByOthers WHERE id = :id")
    suspend fun updateReadByOthers(id: String, readByOthers: Boolean?)

    @Query("UPDATE messages SET text = :text, isEdited = :edited WHERE id = :id")
    suspend fun updateMessageText(id: String, text: String, edited: Boolean)

    /** Обновляет только поле blocksJson (для синхронизации результатов голосования из WebSocket/REST). */
    @Query("UPDATE messages SET blocksJson = :blocksJson WHERE id = :id")
    suspend fun updateBlocksJson(id: String, blocksJson: String?)
}
