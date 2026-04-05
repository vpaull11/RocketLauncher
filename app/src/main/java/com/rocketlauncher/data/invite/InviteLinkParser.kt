package com.rocketlauncher.data.invite

import android.net.Uri

/**
 * Ссылки приглашения Rocket.Chat: прямой URL, прокси go.rocket.chat (host + path в query),
 * либо hash/token в query.
 */
object InviteLinkParser {

    private const val GO_ROCKET_CHAT = "go.rocket.chat"

    /** Первый http(s)-URL в тексте, похожий на инвайт (для «Поделиться» из браузера и др.). */
    private val HTTP_URL_IN_TEXT =
        Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE)

    fun isGoRocketChatInvite(uri: Uri): Boolean =
        normalizeHost(uri.host) == GO_ROCKET_CHAT

    fun looksLikeInviteLink(uri: Uri): Boolean {
        val path = uri.path ?: return false
        if (path.contains("/invite/", ignoreCase = true)) return true
        if (path.trimEnd('/').endsWith("/invite", ignoreCase = true)) return true
        if (!uri.getQueryParameter("hash").isNullOrBlank()) return true
        if (!uri.getQueryParameter("token").isNullOrBlank()) return true
        if (isGoRocketChatInvite(uri) && !uri.getQueryParameter("path").isNullOrBlank()) return true
        return false
    }

    fun findInviteUriInText(vararg chunks: String?): Uri? {
        val raw = chunks.filterNotNull().joinToString("\n")
        if (raw.isBlank()) return null
        for (match in HTTP_URL_IN_TEXT.findAll(raw)) {
            var candidate = match.value.trimEnd(')', ',', '.', ';', '!', ']', '"', '\'', '»')
            candidate = candidate.trimEnd('/')
            val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: continue
            if (looksLikeInviteLink(uri)) return uri
        }
        val single = raw.trim()
        if (single.startsWith("http", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(single.trimEnd(')', ',', '.', ';')) }.getOrNull()
            if (uri != null && looksLikeInviteLink(uri)) return uri
        }
        return null
    }

    fun extractInviteToken(uri: Uri): String? {
        uri.getQueryParameter("hash")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        uri.getQueryParameter("token")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        if (isGoRocketChatInvite(uri)) {
            tokenAfterInviteSegment(uri.getQueryParameter("path"))?.let { return it }
        }

        val path = uri.path ?: return null
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        val idx = segments.indexOfFirst { it.equals("invite", ignoreCase = true) }
        if (idx >= 0 && idx + 1 < segments.size) {
            val token = segments[idx + 1].trim()
            if (token.isNotEmpty() && token.length <= 64) return token
        }
        return null
    }

    /**
     * Приглашение должно относиться к тому же хосту, что и сохранённый сервер сессии.
     * Для go.rocket.chat сравнивается query host с хостом сессии.
     */
    fun inviteHostMatchesSession(inviteUri: Uri, sessionServerUrl: String?): Boolean {
        if (sessionServerUrl.isNullOrBlank()) return false
        val session = runCatching { Uri.parse(sessionServerUrl.trim()) }.getOrNull() ?: return false
        val sessionHost = normalizeHost(session.host) ?: return false

        if (isGoRocketChatInvite(inviteUri)) {
            val target = inviteUri.getQueryParameter("host")?.trim()?.let { normalizeHostFromParam(it) }
                ?: return false
            return target == sessionHost
        }

        val inviteHost = normalizeHost(inviteUri.host) ?: return false
        return inviteHost == sessionHost
    }

    private fun normalizeHost(host: String?): String? =
        host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }

    /** Значение query `host` может быть без схемы, иногда с путём — берём только хост. */
    private fun normalizeHostFromParam(hostOrUrl: String): String? {
        val trimmed = hostOrUrl.trim()
        if (trimmed.isEmpty()) return null
        val asUri = if (trimmed.contains("://")) {
            runCatching { Uri.parse(trimmed) }.getOrNull()
        } else {
            runCatching { Uri.parse("https://$trimmed") }.getOrNull()
        }
        return normalizeHost(asUri?.host ?: trimmed.substringBefore('/'))
    }

    private fun tokenAfterInviteSegment(pathWithInvite: String?): String? {
        if (pathWithInvite.isNullOrBlank()) return null
        val segments = pathWithInvite.trim().trim('/').split('/').filter { it.isNotEmpty() }
        val idx = segments.indexOfFirst { it.equals("invite", ignoreCase = true) }
        if (idx < 0 || idx + 1 >= segments.size) return null
        val token = segments[idx + 1].trim()
        return token.takeIf { it.isNotEmpty() && it.length <= 64 }
    }
}
