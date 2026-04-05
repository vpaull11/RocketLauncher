package com.rocketlauncher.data.auth

import com.rocketlauncher.data.api.ApiFactory
import com.rocketlauncher.data.dto.MethodCallAnonRequest
import com.rocketlauncher.data.dto.MethodCallAnonResponse
import org.json.JSONObject
import retrofit2.HttpException
import javax.inject.Inject

/**
 * OAuth login через REST API /api/v1/method.callAnon/login.
 * 1) Первый вызов: params=[{oauth:{credentialToken,credentialSecret}}]
 *    - 200: успех
 *    - 400 + error totp-required: показать форму OTP
 * 2) Второй вызов (с OTP): params=[{oauth:{...},totp:{code:"..."}}]
 */
class OAuthRestLogin @Inject constructor() {

    data class LoginResult(val userId: String, val authToken: String)

    suspend fun login(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        cookies: String? = null
    ): Result<LoginResult> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val api = ApiFactory.create(baseUrl)
            val message = buildLoginMessage(credentialToken, credentialSecret, totpCode = null)
            val request = MethodCallAnonRequest(message = message)
            val response = api.loginMethodCallAnon(request, cookies.takeIf { !it.isNullOrBlank() })
            parseResponse(response)
        } catch (e: HttpException) {
            // HTTP 400 при totp-required — парсим errorBody
            val body = e.response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                try {
                    val wrapper = JSONObject(body)
                    val innerMsg = wrapper.optString("message")
                    if (innerMsg.isNotBlank()) {
                        val parsed = parseMessage(innerMsg)
                        if (parsed != null) return parsed
                    }
                } catch (_: Exception) { /* fall through */ }
            }
            Result.failure(Exception(e.message() ?: "HTTP ${e.code()}"))
        } catch (e: OAuthWebSocketLogin.TotpRequiredException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWith2FA(
        serverUrl: String,
        credentialToken: String,
        credentialSecret: String,
        twoFactorCode: String,
        cookies: String? = null
    ): Result<LoginResult> {
        return try {
            val baseUrl = serverUrl.trimEnd('/') + "/"
            val api = ApiFactory.create(baseUrl)
            val message = buildLoginMessage(credentialToken, credentialSecret, totpCode = twoFactorCode)
            val request = MethodCallAnonRequest(message = message)
            val response = api.loginMethodCallAnon(request, cookies.takeIf { !it.isNullOrBlank() })
            parseResponse(response)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                try {
                    val wrapper = JSONObject(body)
                    val innerMsg = wrapper.optString("message")
                    if (innerMsg.isNotBlank()) {
                        val parsed = parseMessage(innerMsg)
                        if (parsed != null) return parsed
                    }
                } catch (_: Exception) { /* fall through */ }
            }
            Result.failure(Exception(e.message() ?: "HTTP ${e.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseResponse(response: MethodCallAnonResponse): Result<LoginResult> {
        return parseMessage(response.message)
            ?: Result.failure(Exception("Invalid response"))
    }

    private fun parseMessage(message: String): Result<LoginResult>? {
        return try {
            val inner = JSONObject(message)
            val err = inner.optJSONObject("error")
            if (err != null) {
                if (err.optString("error") == "totp-required") {
                    val details = err.optJSONObject("details")
                    val totpDetails = OAuthWebSocketLogin.TotpRequiredDetails(
                        method = details?.optString("method", "email") ?: "email",
                        emailOrUsername = details?.optString("emailOrUsername", "") ?: "",
                        codeGenerated = details?.optBoolean("codeGenerated", false) ?: false,
                        availableMethods = details?.optJSONArray("availableMethods")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: listOf("email")
                    )
                    Result.failure(OAuthWebSocketLogin.TotpRequiredException(totpDetails))
                } else {
                    val msg = err.optString("message").ifBlank {
                        err.optString("reason", err.toString())
                    }
                    Result.failure(Exception(msg))
                }
            } else {
                val result = inner.optJSONObject("result")
                if (result != null) {
                    val userId = result.optString("id")
                    val token = result.optString("token")
                    if (userId.isNotEmpty() && token.isNotEmpty()) {
                        Result.success(LoginResult(userId, token))
                    } else {
                        Result.failure(Exception("Invalid login result"))
                    }
                } else {
                    Result.failure(Exception("No result in response"))
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildLoginMessage(
        credentialToken: String,
        credentialSecret: String,
        totpCode: String?
    ): String {
        val oauth = JSONObject().apply {
            put("credentialToken", credentialToken)
            put("credentialSecret", credentialSecret)
        }
        val paramsObj = JSONObject().apply {
            put("oauth", oauth)
            totpCode?.takeIf { it.isNotBlank() }?.let { code ->
                put("totp", JSONObject().apply { put("code", code) })
            }
        }
        val ddp = JSONObject().apply {
            put("msg", "method")
            put("id", "oauth-rest-${System.currentTimeMillis()}")
            put("method", "login")
            put("params", org.json.JSONArray().put(paramsObj))
        }
        return ddp.toString()
    }
}
