package com.rocketlauncher.data.message

/** Пустая метка `[ ](url)` — типичная разметка цитаты Rocket.Chat. */
private val ROCKET_EMPTY_QUOTE_LINK = Regex("""\[\s*\]\([^)]+\)""")

/**
 * Markdown-ссылки на permalink сообщения: `[любой текст](...?msg=...)` (вложенные цитаты).
 */
private val ROCKET_MESSAGE_PERMALINK_LINK = Regex("""\[[^\]]*]\([^)]*[?&]msg=[^)]*\)""")

/**
 * Убирает из текста сообщения или тела цитаты markdown-ссылки на чат-сообщения,
 * чтобы они не отображались как обычные кликабельные ссылки и не ломали вложенное цитирование.
 */
fun stripRocketQuotePermalinkMarkdown(raw: String): String {
    var s = raw
    repeat(8) {
        val next = ROCKET_EMPTY_QUOTE_LINK.replace(s, "")
            .let { ROCKET_MESSAGE_PERMALINK_LINK.replace(it, "") }
        if (next == s) return s.trim()
        s = next
    }
    return s.trim()
}
