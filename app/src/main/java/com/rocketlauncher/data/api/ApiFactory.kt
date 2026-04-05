package com.rocketlauncher.data.api

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rocketlauncher.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiFactory {

    private const val HTTP_LOG_TAG = "RocketHttp"

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor { message -> Log.d(HTTP_LOG_TAG, message) }
                    logging.level = HttpLoggingInterceptor.Level.BODY
                    addInterceptor(logging)
                }
            }

    fun create(baseUrl: String, authToken: String? = null, userId: String? = null): RocketChatApi {
        val client = if (authToken != null && userId != null) {
            baseClientBuilder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("X-Auth-Token", authToken)
                        .addHeader("X-User-Id", userId)
                        .build()
                    chain.proceed(request)
                }
                .build()
        } else {
            baseClientBuilder().build()
        }

        val url = baseUrl.ensureEndsWithSlash()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RocketChatApi::class.java)
    }
}

private fun String.ensureEndsWithSlash() = if (endsWith("/")) this else "$this/"
