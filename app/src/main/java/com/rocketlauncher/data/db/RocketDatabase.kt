package com.rocketlauncher.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RoomEntity::class, MessageEntity::class, RoomPinEntity::class],
    version = 20,
    exportSchema = false
)
abstract class RocketDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun messageDao(): MessageDao
    abstract fun roomPinDao(): RoomPinDao
}
