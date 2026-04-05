package com.rocketlauncher.data.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Логин через Realtime API (WebSocket) с credentialToken/credentialSecret
 * после OAuth redirect от Rocket.Chat.
 * Поддерживает totp-required (2FA по email).
 */
class OAuthWebSocketLogin {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class LoginResult(val userId: String, val authToken: String)

    /** Ошибка totp-required: требуется ввод OTP (email или TOTP) */
    data class TotpRequiredDetails(
        val method: String,
        val emailOrUsername: String,
        val codeGenerated: Boolean,
        val availableMethods: List<String>
    )

    class TotpRequiredException(val details: TotpRequiredDetails) : Exception("TOTP Required [${details.method}]")

    suspend fun login(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        cookies: String? = null
    ): Result<LoginResult> = suspendCancellableCoroutine { cont ->
        val baseUrl = serverUrl.trimEnd('/') + "/"
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "websocket"

        val requestBuilder = Request.Builder().url(wsUrl)
        cookies?.takeIf { it.isNotBlank() }?.let { requestBuilder.addHeader("Cookie", it) }
        val request = requestBuilder.build()
        var ws: WebSocket? = null

        val listener = object : WebSocketListener() {
            var connected = false
            val loginId = "oauth-login-${System.currentTimeMillis()}"

            override fun onOpen(webSocket: WebSocket, response: Response) {
                ws = webSocket
                webSocket.send("""{"msg":"connect","version":"1","support":["1"]}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("msg")) {
                        "connected" -> {
                            if (!connected) {
                                connected = true
                                val oauth = JSONObject().apply {
                                    put("credentialToken", credentialToken)
                                    put("credentialSecret", credentialSecret)
                                }
                                val params = JSONObject().apply { put("oauth", oauth) }
                                val loginMsg = JSONObject().apply {
                                    put("msg", "method")
                                    put("method", "login")
                                    put("id", loginId)
                                    put("params", org.json.JSONArray().put(params))
                                }
                                webSocket.send(loginMsg.toString())
                            }
                        }
                        "result" -> {
                            if (json.optString("id") == loginId) {
                                val err = json.optJSONObject("error")
                                if (err != null) {
                                    if (err.optString("error") == "totp-required") {
                                        val details = err.optJSONObject("details")
                                        val totpDetails = TotpRequiredDetails(
                                            method = details?.optString("method", "email") ?: "email",
                                            emailOrUsername = details?.optString("emailOrUsername", "") ?: "",
                                            codeGenerated = details?.optBoolean("codeGenerated", false) ?: false,
                                            availableMethods = details?.optJSONArray("availableMethods")?.let { arr ->
                                                (0 until arr.length()).map { arr.getString(it) }
                                            } ?: listOf("email")
                                        )
                                        cont.resume(Result.failure(TotpRequiredException(totpDetails)))
                                    } else {
                                        val msg = err.optString("message").ifBlank {
                                            err.optString("reason", err.toString())
                                        }
                                        cont.resume(Result.failure(Exception(msg)))
                                    }
                                } else {
                                    val result = json.optJSONObject("result")
                                    if (result != null) {
                                        val userId = result.optString("id")
                                        val token = result.optString("token")
                                        if (userId.isNotEmpty() && token.isNotEmpty()) {
                                            cont.resume(Result.success(LoginResult(userId, token)))
                                        } else {
                                            cont.resume(Result.failure(Exception("Invalid login result")))
                                        }
                                    } else {
                                        cont.resume(Result.failure(Exception("No result in response")))
                                    }
                                }
                                webSocket.close(1000, "Done")
                            }
                        }
                    }
                } catch (e: Exception) {
                    cont.resume(Result.failure(e))
                    webSocket.close(1000, "Error")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (cont.isActive) {
                    cont.resume(Result.failure(t))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
        }

        val webSocket = client.newWebSocket(request, listener)
        cont.invokeOnCancellation { webSocket.close(1000, "Cancelled") }
    }

    /**
     * Завершает логин с кодом 2FA.
     * Как в веб-версии: тот же method login, но params содержат oauth + totp.code.
     */
    suspend fun loginWith2FA(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        twoFactorMethod: String,
        twoFactorCode: String,
        cookies: String? = null
    ): Result<LoginResult> = suspendCancellableCoroutine { cont ->
        val baseUrl = serverUrl.trimEnd('/') + "/"
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "websocket"

        val requestBuilder = Request.Builder().url(wsUrl)
        cookies?.takeIf { it.isNotBlank() }?.let { requestBuilder.addHeader("Cookie", it) }
        val request = requestBuilder.build()
        val login2FAId = "oauth-2fa-${System.currentTimeMillis()}"

        val listener = object : WebSocketListener() {
            var connected = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"msg":"connect","version":"1","support":["1"]}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.optString("msg")) {
                        "connected" -> {
                            if (!connected) {
                                connected = true
                                val oauth = JSONObject().apply {
                                    put("credentialToken", credentialToken)
                                    put("credentialSecret", credentialSecret)
                                }
                                val totp = JSONObject().apply { put("code", twoFactorCode) }
                                val paramsObj = JSONObject().apply {
                                    put("oauth", oauth)
                                    put("totp", totp)
                                }
                                val loginMsg = JSONObject().apply {
                                    put("msg", "method")
                                    put("method", "login")
                                    put("id", login2FAId)
                                    put("params", JSONArray().put(paramsObj))
                                }
                                webSocket.send(loginMsg.toString())
                            }
                        }
                        "result" -> {
                            if (json.optString("id") == login2FAId) {
                                val err = json.optJSONObject("error")
                                if (err != null) {
                                    val msg = err.optString("message", err.toString())
                                    cont.resume(Result.failure(Exception(msg)))
                                } else {
                                    val result = json.optJSONObject("result")
                                    if (result != null) {
                                        val userId = result.optString("id")
                                        val token = result.optString("token")
                                        if (userId.isNotEmpty() && token.isNotEmpty()) {
                                            cont.resume(Result.success(LoginResult(userId, token)))
                                        } else {
                                            cont.resume(Result.failure(Exception("Invalid login result")))
                                        }
                                    } else {
                                        cont.resume(Result.failure(Exception("No result in response")))
                                    }
                                }
                                webSocket.close(1000, "Done")
                            }
                        }
                    }
                } catch (e: Exception) {
                    cont.resume(Result.failure(e))
                    webSocket.close(1000, "Error")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (cont.isActive) cont.resume(Result.failure(t))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
        }

        val webSocket = client.newWebSocket(request, listener)
        cont.invokeOnCancellation { webSocket.close(1000, "Cancelled") }
    }

    /**
     * Запрос кода 2FA по email (DDP sendEmailCode).
     * Вызывать без авторизации, когда codeGenerated=false.
     */
    suspend fun sendEmailCode(serverUrl: String, emailOrUsername: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val wsUrl = baseUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://") + "websocket"

            val request = Request.Builder().url(wsUrl).build()
            val sendCodeId = "send-email-code-${System.currentTimeMillis()}"

            val listener = object : WebSocketListener() {
                var connected = false

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"msg":"connect","version":"1","support":["1"]}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("msg")) {
                            "connected" -> {
                                if (!connected) {
                                    connected = true
                                    val callMsg = JSONObject().apply {
                                        put("msg", "method")
                                        put("method", "sendEmailCode")
                                        put("id", sendCodeId)
                                        put("params", JSONArray().put(emailOrUsername))
                                    }
                                    webSocket.send(callMsg.toString())
                                }
                            }
                            "result" -> {
                                if (json.optString("id") == sendCodeId) {
                                    val err = json.optJSONObject("error")
                                    if (err != null) {
                                        cont.resume(Result.failure(Exception(err.optString("message", err.toString()))))
                                    } else {
                                        cont.resume(Result.success(Unit))
                                    }
                                    webSocket.close(1000, "Done")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        cont.resume(Result.failure(e))
                        webSocket.close(1000, "Error")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resume(Result.failure(t))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
            }

            val webSocket = client.newWebSocket(request, listener)
            cont.invokeOnCancellation { webSocket.close(1000, "Cancelled") }
        }
}
