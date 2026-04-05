package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostMessageRequest(
    val roomId: String,
    val text: String? = null,
    val tmid: String? = null,
    /**
     * Как в официальном клиенте RC при пересылке: `true`, чтобы разобрать `[ ](permalink)` в `msg`.
     */
    val parseUrls: Boolean? = null,
    val attachments: List<PostMessageAttachment>? = null
)

/**
 * Внутренний объект в `attachments[].attachments[]` при пересылке с файлом/картинкой
 * (тот же вид, что в ответе/сокете официального клиента).
 */
@Serializable
data class PostMessageNestedFileAttachment(
    val title: String? = null,
    val title_link: String? = null,
    val title_link_download: Boolean? = null,
    val image_url: String? = null,
    val image_type: String? = null,
    val image_size: Long? = null,
    val type: String? = null,
    /** Подпись к файлу/изображению (в REST у оф. клиента здесь, а не во внешнем `text`). */
    val description: String? = null
)

/**
 * Вложение для [PostMessageRequest].
 * Для пересылки с медиа — как у веб-клиента: внешний блок (`message_link`, `ts`, `text` часто `""`)
 * и вложенный [attachments] с одним [PostMessageNestedFileAttachment] (`type: file`, `description`, …).
 */
@Serializable
data class PostMessageAttachment(
    /** Ссылка на исходное сообщение (цитата / пересылка). */
    val message_link: String? = null,
    /**
     * Время исходного сообщения (ISO 8601).
     * Вместе с [message_link] по документации API.
     */
    val ts: String? = null,
    /** Текст цитаты; при пересылке картинки у оф. клиента часто `""`, подпись — во вложенном [description]. */
    val text: String? = null,
    val author_name: String? = null,
    val author_icon: String? = null,
    /** Вложенный файл/картинка (пересылка с медиа). */
    val attachments: List<PostMessageNestedFileAttachment>? = null,
    /** Плоские поля — на случай простых вложений без вложенного массива. */
    val image_url: String? = null,
    val image_type: String? = null,
    val image_size: Long? = null,
    val title: String? = null,
    val title_link: String? = null,
    val title_link_download: Boolean? = null
)
