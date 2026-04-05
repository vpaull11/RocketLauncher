package com.rocketlauncher.presentation.oauth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.rocketlauncher.util.AppLog
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import java.net.URLDecoder

/**
 * OAuth через Rocket.Chat: загружает URL из настроек сервера (GET /api/v1/settings.oauth).
 * Пользователь вводит логин/пароль на форме провайдера (Keycloak и др.).
 * При успехе перехватываем credentialToken/credentialSecret из:
 * - redirect URL (query или hash)
 * - localStorage/sessionStorage (страница "Login completed" при 2FA)
 */
class RocketChatOAuthActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var urlDebugText: android.widget.TextView
    private var pendingOAuthUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val oauthUrl = intent.getStringExtra(EXTRA_OAUTH_URL)
        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL)
        val urlToLoad = oauthUrl ?: serverUrl?.let { it.trimEnd('/') + "/_oauth/keycloak" }
        if (urlToLoad == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val baseServerUrl = serverUrl ?: Uri.parse(oauthUrl).let { "${it.scheme}://${it.host}/" }

        clearOAuthCookies()

        val apiUrl = intent.getStringExtra(EXTRA_DEBUG_API_URL) ?: ""
        urlDebugText = android.widget.TextView(this).apply {
            text = buildString {
                if (apiUrl.isNotEmpty()) append("API: $apiUrl\n")
                append("SSO URL: $urlToLoad\n")
                append("Credentials: проверка...")
            }
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFFE0E0E0.toInt())
            setTextColor(0xFF000000.toInt())
            textSize = 12f
            setSingleLine(false)
            maxLines = 12
        }
        webView = WebView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val closeBtn = Button(this).apply {
            text = "Закрыть"
            setOnClickListener { finish() }
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = 8
            rightMargin = 8
        }
        val contentLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(urlDebugText)
            addView(webView)
        }
        val rootLayout = FrameLayout(this).apply {
            addView(contentLayout)
            addView(closeBtn, params)
        }
        setContentView(rootLayout)

        setupWebView(baseServerUrl)
        webView.clearCache(true)
        webView.clearHistory()
        pendingOAuthUrl = urlToLoad
        webView.loadUrl(baseServerUrl)
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun updateDebugInfo(url: String, credentialStatus: String) {
        if (!::urlDebugText.isInitialized) return
        val api = intent.getStringExtra(EXTRA_DEBUG_API_URL) ?: ""
        urlDebugText.text = buildString {
            if (api.isNotEmpty()) append("API: $api\n")
            append("URL: ${url.take(80)}${if (url.length > 80) "..." else ""}\n")
            append("Credentials: $credentialStatus")
        }
    }

    /** Периодически проверяем hash и localStorage (credentials могут добавиться через JS) */
    private fun pollForCredentials(webView: WebView?, currentUrl: String, serverUrl: String) {
        val delays = listOf(300L, 700L, 1500L)
        delays.forEachIndexed { i, delay ->
            handler.postDelayed({
                if (!isFinishing) {
                    webView?.evaluateJavascript("window.location.href") { result ->
                        result?.trim('"')?.let { fullUrl ->
                            if (extractOAuthCredentials(fullUrl, serverUrl)) {
                                handler.removeCallbacksAndMessages(null)
                            }
                        }
                    }
                    if (!isFinishing) {
                        tryExtractCredentialsFromLocalStorage(webView, currentUrl, serverUrl)
                    }
                }
            }, delay)
        }
    }

    /** Читает credentialToken/credentialSecret из localStorage/sessionStorage (Meteor OAuth при 2FA) */
    private fun tryExtractCredentialsFromLocalStorage(webView: WebView?, currentUrl: String, serverUrl: String) {
        val js = """
            (function() {
                var prefix = 'Meteor.oauth.credentialSecret-';
                var storages = [localStorage, sessionStorage];
                for (var s = 0; s < storages.length; s++) {
                    var storage = storages[s];
                    for (var i = 0; i < storage.length; i++) {
                        var key = storage.key(i);
                        if (key && key.indexOf(prefix) === 0) {
                            var credentialToken = key.substring(prefix.length);
                            var credentialSecret = storage.getItem(key);
                            if (credentialToken && credentialSecret) {
                                return JSON.stringify({credentialToken: credentialToken, credentialSecret: credentialSecret});
                            }
                        }
                    }
                }
                return '';
            })()""".trimIndent()
        webView?.evaluateJavascript(js) { result ->
            if (isFinishing) return@evaluateJavascript
            val raw = result?.trim() ?: return@evaluateJavascript
            val json = raw.removeSurrounding("\"").replace("\\\"", "\"")
            if (json.isEmpty() || json == "null") {
                updateDebugInfo(currentUrl, "localStorage: пусто")
                AppLog.d(TAG, "localStorage: credentials not found")
                return@evaluateJavascript
            }
            try {
                val obj = org.json.JSONObject(json)
                val token = obj.optString("credentialToken").takeIf { it.isNotEmpty() }
                val secret = obj.optString("credentialSecret").takeIf { it.isNotEmpty() }
                if (token != null && secret != null) {
                    val debug = "localStorage: token=${token.take(8)}... len=${token.length}, secret len=${secret.length}"
                    updateDebugInfo(currentUrl, debug)
                    AppLog.d(TAG, "Credentials from localStorage: tokenLen=${token.length}, secretLen=${secret.length}")
                    deliverCredentials(token, secret)
                } else {
                    updateDebugInfo(currentUrl, "localStorage: неполные данные")
                }
            } catch (e: Exception) {
                updateDebugInfo(currentUrl, "localStorage: ошибка парсинга")
                Log.w(TAG, "localStorage parse error", e)
            }
        }
    }

    /** Очищаем cookies, чтобы Keycloak всегда показывал форму логина (а не "Login completed") */
    private fun clearOAuthCookies() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(serverUrl: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (extractOAuthCredentials(url, serverUrl)) return true
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    val oauthToLoad = pendingOAuthUrl
                    if (oauthToLoad != null && it.contains(Uri.parse(serverUrl).host.orEmpty()) && !it.contains("_oauth")) {
                        updateDebugInfo(it, "очистка storage, загрузка OAuth...")
                        pendingOAuthUrl = null
                        view?.evaluateJavascript(
                            "try{localStorage.clear();sessionStorage.clear();}catch(e){}"
                        ) { _ ->
                            if (!isFinishing) view?.loadUrl(oauthToLoad)
                        }
                        return@let
                    }
                    if (extractOAuthCredentials(it, serverUrl)) return@let
                    if (it.contains(Uri.parse(serverUrl).host.orEmpty()) && it.contains("_oauth")) {
                        updateDebugInfo(it, "в URL нет, проверяю localStorage...")
                        tryExtractCredentialsFromLocalStorage(view, it, serverUrl)
                        pollForCredentials(view, it, serverUrl)
                    } else {
                        val host = Uri.parse(serverUrl).host.orEmpty()
                        val status = if (it.contains(host)) "ожидание" else "OAuth провайдер (Keycloak)"
                        updateDebugInfo(it, status)
                    }
                }
            }
        }
    }

    /**
     * Извлекает credentialToken и credentialSecret из URL.
     * Rocket.Chat может передавать их в query (?credentialToken=...&credentialSecret=...)
     * или в hash (#{"credentialToken":"...","credentialSecret":"..."}).
     */
    private fun extractOAuthCredentials(url: String, serverUrl: String): Boolean {
        val host = Uri.parse(serverUrl).host ?: return false
        if (!url.contains(host)) return false

        var credentialToken: String? = null
        var credentialSecret: String? = null

        val hashIndex = url.indexOf('#')
        if (hashIndex >= 0) {
            val hash = url.substring(hashIndex + 1)
            for (raw in listOf(hash, URLDecoder.decode(hash, Charsets.UTF_8.name()))) {
                try {
                    val json = org.json.JSONObject(raw)
                    credentialToken = json.optString("credentialToken").takeIf { it.isNotEmpty() }
                    credentialSecret = json.optString("credentialSecret").takeIf { it.isNotEmpty() }
                    if (credentialToken != null && credentialSecret != null) break
                } catch (_: Exception) { /* пробуем query */ }
            }
        }
        if (credentialToken == null || credentialSecret == null) {
            val uri = Uri.parse(url)
            credentialToken = credentialToken ?: uri.getQueryParameter("credentialToken")
            credentialSecret = credentialSecret ?: uri.getQueryParameter("credentialSecret")
        }

        if (credentialToken != null && credentialSecret != null) {
            val debug = "URL: token=${credentialToken.take(8)}... len=${credentialToken.length}"
            updateDebugInfo(url, debug)
            AppLog.d(TAG, "Credentials from URL: tokenLen=${credentialToken.length}, secretLen=${credentialSecret.length}")
            deliverCredentials(credentialToken, credentialSecret)
            return true
        }
        return false
    }

    private fun deliverCredentials(credentialToken: String, credentialSecret: String) {
        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: ""
        val cookies = if (serverUrl.isNotEmpty()) {
            val url = serverUrl.trimEnd('/').let { if (it.startsWith("http")) it else "https://$it" }
            CookieManager.getInstance().getCookie(url)
        } else null
        val result = Intent().apply {
            putExtra(EXTRA_CREDENTIAL_TOKEN, credentialToken)
            putExtra(EXTRA_CREDENTIAL_SECRET, credentialSecret)
            cookies?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COOKIES, it) }
        }
        AppLog.d(TAG, "deliverCredentials: cookies=${cookies?.take(50)}...")
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        private const val TAG = "RocketChatOAuth"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_OAUTH_URL = "oauth_url"
        const val EXTRA_DEBUG_API_URL = "debug_api_url"
        const val EXTRA_CREDENTIAL_TOKEN = "credential_token"
        const val EXTRA_CREDENTIAL_SECRET = "credential_secret"
        const val EXTRA_COOKIES = "cookies"

        /** @param oauthUrl URL из API settings.oauth, например https://server/_oauth/keycloak */
        fun createIntent(context: Context, serverUrl: String, oauthUrl: String, apiUrl: String = ""): Intent {
            return Intent(context, RocketChatOAuthActivity::class.java).apply {
                putExtra(EXTRA_SERVER_URL, serverUrl.trimEnd('/') + "/")
                putExtra(EXTRA_OAUTH_URL, oauthUrl)
                putExtra(EXTRA_DEBUG_API_URL, apiUrl)
            }
        }
    }
}
