package com.rocketlauncher.util

import android.util.Log
import com.rocketlauncher.BuildConfig

/** Сообщения только в debug-сборке (в release не пишут в logcat). */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }
}
