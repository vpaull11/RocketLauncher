package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UIKit-блок из поля `blocks` сообщения Rocket.Chat (опросы, интерактивные сообщения).
 * Rocket.Chat Apps Engine отправляет блоки по WebSocket в `stream-room-messages`.
 *
 * Типы блоков:
 *   "section"  — текстовый блок (вопрос опроса или вариант с результатом)
 *   "actions"  — блок с кнопками (кнопки голосования)
 *   "divider"  — разделитель
 *
 * @see https://developer.rocket.chat/docs/uikit
 */
@Serializable
data class UiKitBlock(
    val type: String,
    val appId: String? = null,
    val blockId: String? = null,
    val text: kotlinx.serialization.json.JsonElement? = null,
    val elements: List<UiKitElement>? = null,
    /** Вспомогательное поле некоторых версий Poll App. */
    val accessory: UiKitElement? = null
)

@Serializable
data class UiKitText(
    /** "plain_text" | "mrkdwn" */
    val type: String,
    val text: String = "",
    val emoji: Boolean? = null
)

@Serializable
data class UiKitElement(
    /** "button" | "static_select" | "overflow" и т.д. */
    val type: String,
    val actionId: String? = null,
    val appId: String? = null,
    val blockId: String? = null,
    /** Значение, передаваемое в `ui.blockAction` при нажатии. */
    val value: String? = null,
    val text: kotlinx.serialization.json.JsonElement? = null,
    /** Для кнопки: "primary" | "danger" | null (default). */
    val style: String? = null
)
