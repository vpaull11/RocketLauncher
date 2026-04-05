package com.rocketlauncher.data.message

import com.rocketlauncher.data.db.MessageEntity
import com.rocketlauncher.data.dto.AttachmentDto
import org.json.JSONArray
import org.json.JSONObject

/** Один уровень цитаты (в порядке от внешней к вложенным). */
data class QuoteSegment(val author: String?, val text: String)

fun quoteSegmentsToJson(segments: List<QuoteSegment>): String {
    val arr = JSONArray()
    for (s in segments) {
        val o = JSONObject()
        o.put("author", s.author)
        o.put("text", s.text)
        arr.put(o)
    }
    return arr.toString()
}

fun parseQuoteChainJson(json: String?): List<QuoteSegment> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val out = ArrayList<QuoteSegment>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                QuoteSegment(
                    o.optString("author", "").takeIf { it.isNotEmpty() },
                    o.optString("text", "")
                )
            )
        }
        out
    } catch (_: Exception) {
        emptyList()
    }
}

private fun isQuoteLikeDto(a: AttachmentDto): Boolean {
    if (!a.message_link.isNullOrBlank()) return true
    val t = a.text?.trim().orEmpty()
    val an = a.author_name?.trim()?.takeIf { it.isNotEmpty() }
    if (t.isNotEmpty() && an != null) {
        if (a.image_url != null && a.message_link.isNullOrBlank()) return false
        return true
    }
    return false
}

/**
 * Обходит дерево вложений Rocket.Chat и собирает все блоки цитат в порядке:
 * сначала прямой родитель ответа, затем вложенные цитаты (рекурсивно).
 */
fun collectQuoteSegmentsFromDto(attachments: List<AttachmentDto?>?): List<QuoteSegment> {
    val out = mutableListOf<QuoteSegment>()
    if (attachments.isNullOrEmpty()) return out
    for (a in attachments) {
        val a = a ?: continue
        if (isQuoteLikeDto(a)) {
            out.add(QuoteSegment(a.author_name?.trim()?.takeIf { it.isNotEmpty() }, a.text?.trim().orEmpty()))
            out.addAll(collectQuoteSegmentsFromDto(a.attachments))
        } else {
            out.addAll(collectQuoteSegmentsFromDto(a.attachments))
        }
    }
    return out
}

fun findFirstQuoteAttachmentDto(attachments: List<AttachmentDto?>?): AttachmentDto? {
    if (attachments.isNullOrEmpty()) return null
    for (a in attachments) {
        val a = a ?: continue
        if (isQuoteLikeDto(a)) return a
        findFirstQuoteAttachmentDto(a.attachments)?.let { return it }
    }
    return null
}

private fun isQuoteLikeJson(att: JSONObject): Boolean {
    if (att.optString("message_link", "").isNotBlank()) return true
    val t = att.optString("text", "")
    val an = att.optString("author_name", "")
    if (t.isNotBlank() && an.isNotBlank()) {
        if (att.has("image_url") && att.optString("message_link", "").isBlank()) return false
        return true
    }
    return false
}

fun collectQuoteSegmentsFromJsonArray(arr: JSONArray?): List<QuoteSegment> {
    val out = mutableListOf<QuoteSegment>()
    if (arr == null) return out
    for (i in 0 until arr.length()) {
        val att = arr.optJSONObject(i) ?: continue
        if (isQuoteLikeJson(att)) {
            out.add(
                QuoteSegment(
                    att.optString("author_name", "").takeIf { it.isNotBlank() },
                    att.optString("text", "")
                )
            )
            out.addAll(collectQuoteSegmentsFromJsonArray(att.optJSONArray("attachments")))
        } else {
            out.addAll(collectQuoteSegmentsFromJsonArray(att.optJSONArray("attachments")))
        }
    }
    return out
}

fun findFirstQuoteAttachmentJson(arr: JSONArray?): JSONObject? {
    if (arr == null) return null
    for (i in 0 until arr.length()) {
        val att = arr.optJSONObject(i) ?: continue
        if (isQuoteLikeJson(att)) return att
        findFirstQuoteAttachmentJson(att.optJSONArray("attachments"))?.let { return it }
    }
    return null
}

/** Цепочка для UI: новый JSON или устаревшие поля [MessageEntity.quoteText]. */
fun quoteSegmentsForMessageEntity(message: MessageEntity): List<QuoteSegment> {
    val fromJson = parseQuoteChainJson(message.quoteChainJson)
    if (fromJson.isNotEmpty()) return fromJson
    val qt = message.quoteText ?: return emptyList()
    return listOf(QuoteSegment(message.quoteAuthor, qt))
}
