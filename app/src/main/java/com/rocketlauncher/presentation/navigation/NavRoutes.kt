package com.rocketlauncher.presentation.navigation

object NavRoutes {
    const val LOGIN = "login"
    const val ROOMS = "rooms"
    const val GLOBAL_SEARCH = "global_search"
    const val MY_PROFILE = "my_profile"
    const val CHAT =
        "chat/{roomId}/{roomName}/{roomType}/{avatarPath}?tmid={tmid}&threadTitle={threadTitle}&highlightMsg={highlightMsg}"

    fun chat(
        roomId: String,
        roomName: String,
        roomType: String,
        avatarPath: String = "",
        tmid: String = "",
        threadTitle: String = "",
        highlightMsg: String = ""
    ) = "chat/${encode(roomId)}/${encode(roomName)}/$roomType/${encode(avatarPath)}" +
        "?tmid=${encode(tmid)}&threadTitle=${encode(threadTitle)}&highlightMsg=${encode(highlightMsg)}"

    /** Через Java: на API &lt; 33 нет {@code URLEncoder.encode(String, Charset)}. */
    private fun encode(s: String) = UrlFormEncode.utf8(s)
}
