package com.rocketlauncher.data.websocket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Rocket.Chat Realtime API (DDP over WebSocket)
 * @see https://developer.rocket.chat/apidocs/realtimeapi
 *
 * Подключение: wss://[server]/websocket
 * Формат сообщений: {"msg": "type", "id": "unique-id", ...}
 */
class RocketWebSocket(
    private val serverUrl: String,
    private val authToken: String?,
    private val userId: String?
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect(listener: WebSocketListener): WebSocket {
        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/websocket"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, listener)
        return webSocket!!
    }

    fun disconnect() {
        webSocket?.close(1000, "Closing")
        webSocket = null
    }

    fun sendConnect() {
        val msg = """{"msg":"connect","version":"1","support":["1"]}"""
        webSocket?.send(msg)
    }

    fun sendPong() {
        webSocket?.send("""{"msg":"pong"}""")
    }
}
