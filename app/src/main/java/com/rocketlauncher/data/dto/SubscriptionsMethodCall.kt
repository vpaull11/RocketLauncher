package com.rocketlauncher.data.dto

import android.util.Log
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * DDP-метод `subscriptions/get` через REST `POST .../method.call/subscriptions:get`
 * (как в веб-клиенте Rocket.Chat; в ответе есть поле избранного `f`).
 */
object SubscriptionsMethodCall {

    private const val TAG = "SubscriptionsMethodCall"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * @param lastUpdatedMs если null — полная выгрузка (`params: []`); иначе EJSON `{"$date": ms}` (дельта).
     */
    fun buildMessage(lastUpdatedMs: Long?): String {
        val o = JSONObject()
        o.put("msg", "method")
        o.put("id", "sub-get-${System.nanoTime()}")
        o.put("method", "subscriptions/get")
        val params = JSONArray()
        if (lastUpdatedMs != null) {
            val dateObj = JSONObject()
            dateObj.put("\$date", lastUpdatedMs)
            params.put(dateObj)
        }
        o.put("params", params)
        return o.toString()
    }

    fun parseResultMessage(message: String): SubscriptionsResponse? {
        return try {
            val obj = JSONObject(message)
            if (obj.optString("msg") != "result") return null
            when (val raw = obj.opt("result")) {
                is JSONObject -> {
                    val update = parseSubscriptionArray(raw.optJSONArray("update"))
                    val remove = parseSubscriptionArray(raw.optJSONArray("remove"))
                    SubscriptionsResponse(update = update, remove = remove, success = true)
                }
                is JSONArray -> {
                    SubscriptionsResponse(
                        update = parseSubscriptionArray(raw),
                        remove = emptyList(),
                        success = true
                    )
                }
                else -> null
            }
        } catch (e: JSONException) {
            Log.e(TAG, "parseResultMessage: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "parseResultMessage: ${e.message}")
            null
        }
    }

    private fun parseSubscriptionArray(arr: JSONArray?): List<SubscriptionDto> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<SubscriptionDto>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            try {
                out.add(json.decodeFromString(SubscriptionDto.serializer(), item.toString()))
            } catch (_: Exception) {
                // минимальные объекты в remove и т.п.
            }
        }
        return out
    }
}
