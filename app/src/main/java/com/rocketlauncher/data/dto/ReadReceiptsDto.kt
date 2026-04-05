package com.rocketlauncher.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReadReceiptsResponse(
    val success: Boolean = false,
    val receipts: List<ReadReceiptItemDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class ReadReceiptItemDto(
    @SerialName("userId")
    val userId: String? = null,
    val user: ReadReceiptUserDto? = null
)

@Serializable
data class ReadReceiptUserDto(
    val _id: String? = null,
    val username: String? = null,
    val name: String? = null
)

/** Строка для UI: кто прочитал сообщение. */
data class ReadReceiptReaderRow(
    val userId: String,
    val displayName: String
)
