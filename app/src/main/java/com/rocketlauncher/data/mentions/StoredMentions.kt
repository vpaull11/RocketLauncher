package com.rocketlauncher.data.mentions

import com.rocketlauncher.data.dto.MentionDto
import org.json.JSONArray
import org.json.JSONObject

/** Упоминание из сообщения Rocket.Chat (сохранение в БД и разбор для UI). */
data class StoredMention(
    val userId: String?,
    val username: String?,
    val name: String?
)

fun parseStoredMentionsJson(json: String?): List<StoredMention> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    StoredMention(
                        userId = o.optString("_id").takeIf { it.isNotBlank() },
                        username = o.optString("username").takeIf { it.isNotBlank() },
                        name = o.optString("name").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun mentionsArrayToJsonString(mentions: JSONArray?): String? {
    if (mentions == null || mentions.length() == 0) return null
    val out = JSONArray()
    for (i in 0 until mentions.length()) {
        val o = mentions.optJSONObject(i) ?: continue
        val x = JSONObject()
        if (o.has("_id")) x.put("_id", o.optString("_id"))
        if (o.has("username")) x.put("username", o.optString("username"))
        if (o.has("name")) x.put("name", o.optString("name"))
        out.put(x)
    }
    return if (out.length() > 0) out.toString() else null
}

/** Упоминание текущего пользователя или @all / @here. */
fun mentionsArrayContainsCurrentUser(mentions: JSONArray?, myUserId: String?): Boolean {
    if (mentions == null || mentions.length() == 0) return false
    for (i in 0 until mentions.length()) {
        val o = mentions.optJSONObject(i) ?: continue
        val id = o.optString("_id", "")
        if (myUserId != null && id.isNotBlank() && id == myUserId) return true
        when (o.optString("username", "")) {
            "all", "here" -> return true
        }
    }
    return false
}

fun mentionsFromDtoList(mentions: List<MentionDto>?): String? {
    if (mentions.isNullOrEmpty()) return null
    val out = JSONArray()
    mentions.forEach { m ->
        val o = JSONObject()
        o.put("_id", m._id)
        m.username?.takeIf { it.isNotBlank() }?.let { o.put("username", it) }
        m.name?.takeIf { it.isNotBlank() }?.let { o.put("name", it) }
        out.put(o)
    }
    return out.toString()
}
