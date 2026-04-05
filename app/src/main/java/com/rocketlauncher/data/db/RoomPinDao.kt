package com.rocketlauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomPinDao {
    @Query("SELECT * FROM room_pins ORDER BY sortOrder ASC")
    fun observePins(): Flow<List<RoomPinEntity>>

    @Query("SELECT roomId FROM room_pins ORDER BY sortOrder ASC")
    suspend fun getPinnedRoomIdsOrdered(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomPinEntity)

    @Query("DELETE FROM room_pins WHERE roomId = :roomId")
    suspend fun delete(roomId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM room_pins WHERE roomId = :roomId)")
    suspend fun isPinned(roomId: String): Boolean
}
