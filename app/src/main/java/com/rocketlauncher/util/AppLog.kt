package com.rocketlauncher.util

import android.util.Log
import com.rocketlauncher.BuildConfig

/**
 * Централизованное логирование приложения.
 *
 * - `d` / `w` — только в debug-сборке (logcat)
 * - `e` — всегда пишет в logcat (важно для диагностики крашей в release!)
 *           и дополнительно — в [CrashLogger] если он инициализирован
 */
object AppLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, message, throwable)
            else Log.w(tag, message)
        }
    }

    /**
     * Ошибка — пишет всегда (в debug и release), чтобы ADB logcat показывал крашовые события.
     * Дополнительно сохраняет в файл через [CrashLogger].
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable)
        else Log.e(tag, message)
        // Записываем в постоянный лог-файл для диагностики
        val full = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        CrashLogger.appendError(tag, full)
    }
}
