package com.rocketlauncher.presentation.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.rocketlauncher.data.emoji.EmojiStore
import com.rocketlauncher.data.mentions.StoredMention

private val EMOJI_CODE_REGEX = Regex(":([a-zA-Z0-9_+\\-]+):")

fun formatMessage(
    text: String,
    emojiStore: EmojiStore,
    mentions: List<StoredMention> = emptyList(),
    /** Ссылки и @упоминания: для своих сообщений на primaryContainer задавайте onPrimaryContainer из темы. */
    linkColor: Color = Color(0xFF1E88E5),
    mentionColor: Color = Color(0xFF1565C0)
): AnnotatedString {
    val decoded = decodeHtmlEntitiesForMarkdown(text)
    val withEmojis = replaceEmojis(decoded, emojiStore)
    return parseMarkdown(withEmojis, mentions, linkColor, mentionColor)
}

/** Rocket.Chat иногда отдаёт ссылки как `&lt;https://…|…&gt;`. */
private fun decodeHtmlEntitiesForMarkdown(s: String): String {
    if (s.indexOf('&') < 0) return s
    var t = s
    repeat(4) {
        val prev = t
        t = t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        if (t == prev) return t
    }
    return t
}

private fun replaceEmojis(text: String, emojiStore: EmojiStore): String {
    return EMOJI_CODE_REGEX.replace(text) { match ->
        emojiStore.resolve(match.value)
    }
}

private fun parseMarkdown(
    text: String,
    mentions: List<StoredMention> = emptyList(),
    linkColor: Color,
    mentionColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Code block: ```...```
                text.startsWith("```", i) -> {
                    val end = text.indexOf("```", i + 3)
                    if (end != -1) {
                        val code = text.substring(i + 3, end).trimStart('\n').trimEnd('\n')
                        pushStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x20808080)
                        ))
                        append(code)
                        pop()
                        i = end + 3
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Inline code: `...`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x20808080)
                        ))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Bold: *text*
                text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1 && text[end - 1] != ' ') {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        appendFormatted(text.substring(i + 1, end), mentions, mentionColor)
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: _text_
                text[i] == '_' && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = text.indexOf('_', i + 1)
                    if (end != -1 && end > i + 1 && text[end - 1] != ' ') {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        appendFormatted(text.substring(i + 1, end), mentions, mentionColor)
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Strikethrough: ~text~
                text[i] == '~' && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = text.indexOf('~', i + 1)
                    if (end != -1 && end > i + 1 && text[end - 1] != ' ') {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        appendFormatted(text.substring(i + 1, end), mentions, mentionColor)
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Link: [text](url) or plain URLs
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParen)
                            pushStyle(SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            ))
                            pushStringAnnotation("URL", url)
                            appendFormatted(linkText, mentions, mentionColor)
                            pop()
                            pop()
                            i = closeParen + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Slack / Rocket.Chat: <https://url|подпись> или <https://url>
                text[i] == '<' && (
                    text.startsWith("https://", i + 1) || text.startsWith("http://", i + 1)
                ) -> {
                    val closeIdx = text.indexOf('>', i + 1)
                    if (closeIdx != -1) {
                        val inner = text.substring(i + 1, closeIdx)
                        val pipeInInner = inner.indexOf('|')
                        val url: String
                        val label: String?
                        if (pipeInInner != -1) {
                            url = inner.substring(0, pipeInInner)
                            label = inner.substring(pipeInInner + 1).trim()
                        } else {
                            url = inner
                            label = null
                        }
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                            pushStringAnnotation("URL", url)
                            if (!label.isNullOrEmpty()) {
                                appendFormatted(label, mentions, mentionColor)
                            } else {
                                append(url)
                            }
                            pop()
                            pop()
                            i = closeIdx + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Plain URL
                text.startsWith("http://", i) || text.startsWith("https://", i) -> {
                    val end = findUrlEnd(text, i)
                    val url = text.substring(i, end)
                    pushStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ))
                    pushStringAnnotation("URL", url)
                    append(url)
                    pop()
                    pop()
                    i = end
                }
                else -> {
                    if (text[i] == '@' && mentions.isNotEmpty()) {
                        val consumed = consumeMentionForDisplay(text, i, mentions)
                        if (consumed != null) {
                            pushStyle(
                                SpanStyle(
                                    color = mentionColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            append(consumed.first)
                            pop()
                            i = consumed.second
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }
}

/**
 * Заменяет `@username` на `@Отображаемое имя` по массиву mentions с сервера.
 */
private fun consumeMentionForDisplay(
    text: String,
    start: Int,
    mentions: List<StoredMention>
): Pair<String, Int>? {
    if (start >= text.length || text[start] != '@') return null
    val sorted = mentions
        .filter { !it.username.isNullOrBlank() }
        .sortedByDescending { it.username!!.length }
    if (sorted.isEmpty()) return null
    val afterAt = text.substring(start + 1)
    for (m in sorted) {
        val un = m.username!!
        if (afterAt.length < un.length) continue
        if (!afterAt.startsWith(un, ignoreCase = true)) continue
        if (afterAt.length > un.length) {
            val c = afterAt[un.length]
            if (c.isLetterOrDigit() || c == '_' || c == '.') continue
        }
        val label = "@" + (m.name?.takeIf { it.isNotBlank() } ?: un)
        return label to (start + 1 + un.length)
    }
    return null
}

private fun AnnotatedString.Builder.appendFormatted(
    text: String,
    mentions: List<StoredMention>,
    mentionColor: Color
) {
    var j = 0
    while (j < text.length) {
        if (text[j] == '@' && mentions.isNotEmpty()) {
            val consumed = consumeMentionForDisplay(text, j, mentions)
            if (consumed != null) {
                pushStyle(
                    SpanStyle(
                        color = mentionColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                append(consumed.first)
                pop()
                j = consumed.second
            } else {
                append(text[j])
                j++
            }
        } else {
            append(text[j])
            j++
        }
    }
}

private fun findUrlEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && !text[i].isWhitespace() && text[i] != ')' && text[i] != ']') {
        i++
    }
    while (i > start && text[i - 1] in ".,;:!?") i--
    return i
}
