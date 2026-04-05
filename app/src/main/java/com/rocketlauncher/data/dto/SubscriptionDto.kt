package com.rocketlauncher.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class SubscriptionsResponse(
    val update: List<SubscriptionDto> = emptyList(),
    val remove: List<SubscriptionDto> = emptyList(),
    val success: Boolean = false
)

@Serializable
data class SubscriptionDto(
    val _id: String,
    val rid: String,
    /** null = поле не пришло в JSON; не подставлять 0 — иначе затираются реальные непрочитанные при частичном ответе */
    val unread: Int? = null,
    /** null — поле не пришло в JSON (дельта подписок); не затирать избранное локально. */
    @Serializable(with = FavoriteFlagSerializer::class)
    @JsonNames("f", "favorite")
    val f: Boolean? = null,
    val alert: Boolean = false,
    val open: Boolean = false,
    val name: String? = null,
    val fname: String? = null,
    val t: String? = null,
    val userMentions: Int? = null,
    val groupMentions: Int = 0,
    /** Полностью отключить уведомления по этой подписке (сервер). */
    val disableNotifications: Boolean? = null,
    /** Мобильные push: all / mentions / nothing / default. */
    val mobilePushNotifications: String? = null,
    /**
     * ID корневых сообщений тредов с непрочитанным (Rocket.Chat).
     * null — поля не было в JSON (частичная дельта); не затирать [threadUnreadCount] в merge.
     */
    val tunread: List<String>? = null,
    val tunreadUser: List<String>? = null,
    val tunreadGroup: List<String>? = null
)
