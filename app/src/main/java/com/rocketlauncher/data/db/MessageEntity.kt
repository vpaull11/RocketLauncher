package com.rocketlauncher.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["roomId", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val text: String,
    val timestamp: Long,
    val userId: String,
    val username: String?,
    val displayName: String?,
    val isEdited: Boolean = false,
    val parentId: String? = null,
    val tmid: String? = null,
    /** Комната обсуждения, созданная из этого сообщения (Rocket.Chat `drid`). */
    val drid: String? = null,
    /** Число ответов в треде (`tcount`), только у корня треда. */
    val threadReplyCount: Int? = null,
    val quoteText: String? = null,
    val quoteAuthor: String? = null,
    /** JSON: `[{"author":"…","text":"…"},…]` — полная цепочка вложенных цитат (внешняя → внутренняя). */
    val quoteChainJson: String? = null,
    /** Id цитируемого сообщения (из `message_link` / `?msg=`). */
    val quotedMessageId: String? = null,
    /** Тип сообщения Rocket.Chat (`t`): uj/ul/rm/…; null — обычное сообщение. */
    val msgType: String? = null,
    val imageUrl: String? = null,
    val fullImageUrl: String? = null,
    val fileName: String? = null,
    val fileUrl: String? = null,
    val fileType: String? = null,
    /** Подпись к вложению (Rocket.Chat `description` у file/image). */
    val fileDescription: String? = null,
    /** Размер файла в байтах (`image_size` с сервера). */
    val fileSizeBytes: Long? = null,
    /** JSON: {"emoji":["user1","user2"], ...} */
    val reactions: String? = null,
    /** JSON array: [{"_id","username","name"}, ...] для @упоминаний */
    val mentionsJson: String? = null,
    val syncStatus: Int = SYNC_SYNCED,
    /**
     * Прочитано ли сообщение другими участниками (галочки «прочитано»).
     * null — статус ещё не запрашивали / неизвестен; false — нет квитанций от других; true — есть.
     */
    val readByOthers: Boolean? = null,
    /**
     * JSON-массив UIKit-блоков из поля `blocks` сообщения Rocket.Chat.
     * Присутствует в сообщениях опросов (Poll App) и других интерактивных сообщениях.
     * null — обычное сообщение без блоков. Парсится при рендеринге в [PollBlock].
     */
    val blocksJson: String? = null
) {
    companion object {
        const val SYNC_PENDING = 0
        const val SYNC_SYNCED = 1
        const val SYNC_FAILED = 2
    }
}
