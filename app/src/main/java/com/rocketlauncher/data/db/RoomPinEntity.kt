package com.rocketlauncher.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_pins")
data class RoomPinEntity(
    @PrimaryKey val roomId: String,
    /** Меньшее значение — выше в списке закреплённых */
    val sortOrder: Long = System.currentTimeMillis()
)
