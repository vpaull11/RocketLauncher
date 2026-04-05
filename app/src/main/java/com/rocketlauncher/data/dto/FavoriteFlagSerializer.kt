package com.rocketlauncher.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Поле избранного в Rocket.Chat (`f` / `favorite`): boolean или 0/1 в JSON.
 */
object FavoriteFlagSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FavoriteFlag", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("FavoriteFlagSerializer ожидает JSON")
        return decodeElement(jsonDecoder.decodeJsonElement())
    }

    private fun decodeElement(el: JsonElement): Boolean? {
        if (el is JsonNull) return null
        val p = el.jsonPrimitive
        p.booleanOrNull?.let { return it }
        p.intOrNull?.let { return it != 0 }
        val s = p.content
        if (s.isEmpty()) return null
        return s.equals("true", ignoreCase = true) || s == "1"
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) {
            val je = encoder as? kotlinx.serialization.json.JsonEncoder
                ?: throw IllegalStateException("FavoriteFlagSerializer: null только для JSON")
            je.encodeJsonElement(JsonNull)
        } else {
            encoder.encodeBoolean(value)
        }
    }
}
