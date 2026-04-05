package com.rocketlauncher.data.repository

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.dto.VideoConferenceJoinRequest
import com.rocketlauncher.data.dto.VideoConferenceStartRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VideoConfRepo"

@Singleton
class VideoConferenceRepository @Inject constructor(
    private val apiProvider: ApiProvider
) {

    /**
     * Старт звонка (Jitsi через VideoConf на сервере).
     * Возвращает URL для открытия в браузере / Jitsi Meet.
     */
    suspend fun startCallAndGetJoinUrl(roomId: String): Result<String> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val start = api.videoConferenceStart(
                VideoConferenceStartRequest(roomId = roomId, title = "")
            )
            if (!start.success) {
                return Result.failure(
                    IllegalStateException(start.error ?: "video-conference.start failed")
                )
            }
            val data = start.data
            var url = findUrlInJson(data)
            var callId = findStringInJson(data, "callId")
            if (url == null && callId == null) {
                callId = findStringInJson(data, "id")
            }
            if (url == null && callId != null) {
                val join = api.videoConferenceJoin(VideoConferenceJoinRequest(callId = callId))
                if (join.success && !join.url.isNullOrBlank()) {
                    url = join.url
                } else {
                    return Result.failure(
                        IllegalStateException(join.error ?: "video-conference.join failed")
                    )
                }
            }
            if (url.isNullOrBlank()) {
                Log.e(TAG, "No URL in start response: $data")
                return Result.failure(
                    IllegalStateException("Сервер не вернул ссылку на звонок (проверьте Jitsi / права call-management)")
                )
            }
            Result.success(url)
        } catch (e: Exception) {
            Log.e(TAG, "startCall: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun findUrlInJson(element: JsonElement?): String? {
        when (element) {
            null, JsonNull -> return null
            is JsonPrimitive -> {
                if (element.isString) {
                    val s = element.content
                    if (s.startsWith("http://") || s.startsWith("https://")) return s
                }
                return null
            }
            is JsonObject -> {
                element["url"]?.let { u ->
                    if (u is JsonPrimitive && u.isString) return u.content
                }
                element.values.forEach { v ->
                    findUrlInJson(v)?.let { return it }
                }
                return null
            }
            is JsonArray -> {
                element.forEach { findUrlInJson(it)?.let { return it } }
                return null
            }
        }
    }

    private fun findStringInJson(element: JsonElement?, key: String): String? {
        when (element) {
            null -> return null
            is JsonObject -> {
                element[key]?.let { v ->
                    if (v is JsonPrimitive && v.isString) return v.content
                }
                element.values.forEach { findStringInJson(it, key)?.let { return it } }
                return null
            }
            is JsonArray -> {
                element.forEach { findStringInJson(it, key)?.let { return it } }
                return null
            }
            else -> return null
        }
    }
}
