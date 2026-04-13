package com.rocketlauncher.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.rocketlauncher.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Сохраняет лог ошибок и необработанных исключений в файл внутри app-private директории.
 *
 * Файл: `<filesDir>/crash_log.txt` (максимум ~256 КБ, ротация при превышении).
 *
 * Использование:
 * 1. Вызвать [CrashLogger.init] в `Application.onCreate()`.
 * 2. Использовать [CrashLogger.appendError] из [AppLog.e].
 * 3. Читать через [CrashLogger.readLog] или [CrashLogger.getLogFile].
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_FILE_NAME = "crash_log.txt"
    private const val MAX_FILE_SIZE_BYTES = 256 * 1024L // 256 KB

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var initialized = false

    /** Вызвать один раз в [Application.onCreate]. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val f = File(context.filesDir, LOG_FILE_NAME)
        logFile = f

        // Ротация: если файл слишком большой — обрезаем до половины (убираем старые записи)
        executor.execute {
            try {
                if (f.exists() && f.length() > MAX_FILE_SIZE_BYTES) {
                    val content = f.readText()
                    val half = content.drop(content.length / 2)
                    f.writeText("=== Log rotated at ${timestamp()} ===\n$half")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rotation failed: ${e.message}")
            }
        }

        // Перехват необработанных исключений
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val msg = buildString {
                    appendLine("=== UNCAUGHT EXCEPTION ===")
                    appendLine("thread: ${thread.name}")
                    appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                    appendLine("app_version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine(throwable.stackTraceToString())
                    appendLine("==========================")
                }
                writeSync(f, msg)
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, throwable)
        }

        appendRaw("=== App started: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
            "Android ${Build.VERSION.RELEASE}, device: ${Build.MANUFACTURER} ${Build.MODEL} ===")
    }

    /** Запись ошибки (non-blocking). */
    fun appendError(tag: String, message: String) {
        val f = logFile ?: return
        val entry = "[${timestamp()}] E/$tag: $message"
        executor.execute {
            try {
                appendSync(f, entry)
            } catch (_: Exception) {}
        }
    }

    /** Читает содержимое лог-файла (для экрана диагностики). */
    fun readLog(): String {
        return try {
            logFile?.takeIf { it.exists() }?.readText() ?: "(лог пуст)"
        } catch (e: Exception) {
            "Ошибка чтения лога: ${e.message}"
        }
    }

    /** Возвращает файл лога для шаринга через FileProvider. */
    fun getLogFile(): File? = logFile?.takeIf { it.exists() }

    /** Очищает лог-файл. */
    fun clearLog() {
        executor.execute {
            try {
                logFile?.writeText("=== Log cleared at ${timestamp()} ===\n")
            } catch (_: Exception) {}
        }
    }

    private fun appendRaw(message: String) {
        val f = logFile ?: return
        executor.execute {
            try { appendSync(f, "[${timestamp()}] $message") } catch (_: Exception) {}
        }
    }

    private fun appendSync(f: File, entry: String) {
        f.appendText(entry + "\n")
        // Микро-ротация если файл вырос
        if (f.length() > MAX_FILE_SIZE_BYTES) {
            val content = f.readText()
            f.writeText(content.drop(content.length / 2))
        }
    }

    private fun writeSync(f: File, text: String) {
        f.appendText(text)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
}
