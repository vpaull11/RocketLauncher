package com.rocketlauncher

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.rocketlauncher.data.notifications.AppForegroundState
import com.rocketlauncher.data.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class RocketLauncherApp : Application(), ImageLoaderFactory {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var appForegroundState: AppForegroundState

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appForegroundState.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                appForegroundState.setForeground(false)
            }
        })
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val state = authRepository.authState.value
                val builder = chain.request().newBuilder()
                state.authToken?.let { builder.addHeader("X-Auth-Token", it) }
                state.userId?.let { builder.addHeader("X-User-Id", it) }
                val token = state.authToken
                val uid = state.userId
                if (token != null && uid != null) {
                    builder.addHeader("Cookie", "rc_token=$token; rc_uid=$uid")
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
