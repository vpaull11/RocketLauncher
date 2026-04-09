package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagesResponse(
    val messages: List<MessageDto> = emptyList(),
    val count: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val success: Boolean = false
)

@Serializable
data class MessageDto(
    val _id: String,
    val rid: String,
    val msg: String,
    val ts: String,
    val u: MessageUser,
    val _updatedAt: String? = null,
    val editedAt: String? = null,
    val editedBy: MessageUser? = null,
    val t: String? = null,
    /** Rocket.Chat иногда шлёт `"attachments": [null, {…}]` — элементы nullable. */
    val attachments: List<AttachmentDto?>? = null,
    val replies: List<String>? = null,
    val tcount: Int? = null,
    val tlm: String? = null,
    val tmid: String? = null,
    val drid: String? = null,
    val parent: String? = null,
    val _hidden: Boolean? = null,
    val reactions: Map<String, ReactionInfoDto>? = null,
    val mentions: List<MentionDto>? = null,
    val urls: List<UrlDto>? = null,
    val score: Double? = null,
    /** Время закрепления (для сортировки в [chat.getPinnedMessages]). */
    val pinnedAt: String? = null,
    val pinned: Boolean? = null,
    /**
     * UIKit-блоки Rocket.Chat Apps Engine (опросы, интерактивные сообщения).
     * Присутствует в сообщениях, созданных Poll App / другими приложениями.
     * Хранится в [com.rocketlauncher.data.db.MessageEntity.blocksJson] как JSON-строка.
     */
    val blocks: List<UiKitBlock>? = null
)

@Serializable
data class ThreadsListResponse(
    val threads: List<MessageDto> = emptyList(),
    val count: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val success: Boolean = false
)

@Serializable
data class DiscussionsResponse(
    val discussions: List<RoomDto> = emptyList(),
    val count: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
    val success: Boolean = false
)

@Serializable
data class ReactionInfoDto(
    val usernames: List<String> = emptyList(),
    val names: List<String> = emptyList()
)

@Serializable
data class MessageUser(
    val _id: String,
    val username: String? = null,
    val name: String? = null
)

@Serializable
data class AttachmentDto(
    val title: String? = null,
    val title_link: String? = null,
    val text: String? = null,
    /** Подпись к файлу/картинке (как в официальном клиенте). */
    val description: String? = null,
    val author_name: String? = null,
    val author_icon: String? = null,
    val message_link: String? = null,
    val image_url: String? = null,
    val image_type: String? = null,
    val image_size: Long? = null,
    val audio_url: String? = null,
    val video_url: String? = null,
    val type: String? = null,
    val ts: String? = null,
    /** Вложенные вложения (пересылка с файлом и т.п.); в JSON возможен `null` в массиве. */
    val attachments: List<AttachmentDto?>? = null,
    /** Блоки UIKit, если приложение отправляет их внутри вложения (например, новые версии Poll App) */
    val blocks: List<UiKitBlock>? = null
)

@Serializable
data class MentionDto(
    val _id: String,
    val username: String? = null,
    val name: String? = null
)

@Serializable
data class UrlDto(
    val url: String? = null,
    val meta: UrlMeta? = null
)

@Serializable
data class UrlMeta(
    val pageTitle: String? = null,
    val ogImage: String? = null
)
