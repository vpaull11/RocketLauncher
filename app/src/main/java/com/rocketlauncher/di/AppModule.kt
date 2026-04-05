package com.rocketlauncher.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rocketlauncher.data.auth.OAuthWebSocketLogin
import com.rocketlauncher.data.db.MessageDao
import com.rocketlauncher.data.db.RocketDatabase
import com.rocketlauncher.data.db.RoomDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rooms ADD COLUMN avatarPath TEXT")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN quoteText TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN quoteAuthor TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN imageUrl TEXT")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reactions TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN tmid TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rooms ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE rooms ADD COLUMN isDiscussion INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE rooms ADD COLUMN isTeam INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rooms ADD COLUMN userMentions INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN fullImageUrl TEXT")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN fileName TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN fileUrl TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN fileType TEXT")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN mentionsJson TEXT")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_pins (" +
                    "roomId TEXT NOT NULL PRIMARY KEY, " +
                    "sortOrder INTEGER NOT NULL" +
                    ")"
            )
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN readByOthers INTEGER")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rooms ADD COLUMN notificationsMuted INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN drid TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN threadReplyCount INTEGER")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN fileDescription TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN fileSizeBytes INTEGER")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN quotedMessageId TEXT")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN msgType TEXT")
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rooms ADD COLUMN notifySubchats INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE rooms ADD COLUMN discussionParentId TEXT")
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN quoteChainJson TEXT")
        }
    }

    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rooms ADD COLUMN threadUnreadCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RocketDatabase {
        return Room.databaseBuilder(
            context,
            RocketDatabase::class.java,
            "rocket.db"
        ).addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
            MIGRATION_18_19, MIGRATION_19_20
        ).build()
    }

    @Provides
    @Singleton
    fun provideRoomDao(database: RocketDatabase): RoomDao = database.roomDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: RocketDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun provideOAuthWebSocketLogin(): OAuthWebSocketLogin = OAuthWebSocketLogin()
}
