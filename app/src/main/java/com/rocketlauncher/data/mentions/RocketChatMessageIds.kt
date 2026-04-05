package com.rocketlauncher.data.mentions

/** Извлечение id сообщения из permalink Rocket.Chat (`?msg=`). */
object RocketChatMessageIds {

    private val msgParamRegex = Regex("""[?&]msg=([A-Za-z0-9]+)""")
    private val bracketLinkRegex = Regex("""\[[ ]*]\([^)]*?\bmsg=([A-Za-z0-9]+)[^)]*\)""")

    fun fromMessageLink(link: String?): String? {
        if (link.isNullOrBlank()) return null
        return msgParamRegex.find(link)?.groupValues?.get(1)
    }

    /** Текст сообщения до очистки цитаты: `[ ](url?msg=…)` */
    fun fromRawMessageText(rawMsg: String?): String? {
        if (rawMsg.isNullOrBlank()) return null
        return bracketLinkRegex.find(rawMsg)?.groupValues?.get(1)
            ?: msgParamRegex.find(rawMsg)?.groupValues?.get(1)
    }

    fun resolveQuotedId(messageLink: String?, rawMsg: String?): String? =
        fromMessageLink(messageLink) ?: fromRawMessageText(rawMsg)
}
