package com.rocketlauncher.data.emoji

import java.util.Locale

/**
 * PNG 72×72 с CDN Twemoji — единый вид эмодзи на всех устройствах (как картинки).
 * @see https://github.com/twitter/twemoji
 */
object TwemojiUrls {

    private const val CDN_72 =
        "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72"

    /**
     * Строит имя файла по скалярам Unicode (как в Twemoji).
     */
    fun filenameForUnicode(emoji: String): String? {
        if (emoji.isEmpty()) return null
        val parts = ArrayList<String>()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            parts.add(Integer.toHexString(cp).lowercase(Locale.US))
            i += Character.charCount(cp)
        }
        if (parts.isEmpty()) return null
        return parts.joinToString("-")
    }

    fun png72Url(emoji: String): String? {
        val fn = filenameForUnicode(emoji) ?: return null
        return "$CDN_72/$fn.png"
    }
}
