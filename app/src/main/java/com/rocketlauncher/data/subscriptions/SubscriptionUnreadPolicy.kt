package com.rocketlauncher.data.subscriptions

/**
 * Rocket.Chat нередко отдаёт в подписке [unread]==0 при [alert]==true;
 * официальный клиент тогда всё равно показывает непрочитанное в списке.
 */
object SubscriptionUnreadPolicy {

    fun effectiveUnread(
        serverUnread: Int?,
        alert: Boolean,
        existingUnread: Int,
        roomDtoUnread: Int? = null
    ): Int {
        if (serverUnread != null) {
            if (serverUnread > 0) return serverUnread
            return if (alert) 1 else 0
        }
        val base = maxOf(existingUnread, roomDtoUnread ?: 0)
        if (base > 0) return base
        return if (alert) 1 else 0
    }
}
