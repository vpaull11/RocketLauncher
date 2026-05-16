package com.rocketlauncher.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rocketlauncher.BuildConfig
import com.rocketlauncher.R
import com.rocketlauncher.data.github.GitHubApi
import com.rocketlauncher.util.SemVer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateCheckDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "update_check_prefs")

sealed class AppUpdateCheckResult {
    data object UpToDate : AppUpdateCheckResult()
    data class UpdateAvailable(
        val remoteVersion: String,
        val releaseNotes: String?,
        val apkUrl: String
    ) : AppUpdateCheckResult()
    data class Error(val message: String) : AppUpdateCheckResult()
}

private val LAST_CHECK_KEY = longPreferencesKey("update_last_auto_check_ms")
private const val AUTO_CHECK_INTERVAL_MS = 3_600_000L // 1 час

@Singleton
class AppUpdateRepository @Inject constructor(
    private val gitHubApi: GitHubApi,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {

    private val _autoCheckTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Горячий поток-команда: эмитит когда пора делать авто-проверку обновлений. */
    val autoCheckTrigger: SharedFlow<Unit> = _autoCheckTrigger.asSharedFlow()

    /** Возвращает true, если с момента последней авто-проверки прошло не менее 1 часа. */
    suspend fun shouldAutoCheck(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.updateCheckDataStore.data.first()
        val lastCheck = prefs[LAST_CHECK_KEY] ?: 0L
        System.currentTimeMillis() - lastCheck >= AUTO_CHECK_INTERVAL_MS
    }

    /** Сохраняет текущее время как момент последней авто-проверки. */
    suspend fun markAutoChecked(): Unit = withContext(Dispatchers.IO) {
        context.updateCheckDataStore.edit { it[LAST_CHECK_KEY] = System.currentTimeMillis() }
    }

    /**
     * Вызывается из [MainActivity.onStart]. Если прошёл час с последней авто-проверки —
     * сохраняет timestamp и сигнализирует подписчикам через [autoCheckTrigger].
     */
    suspend fun emitAutoCheckIfNeeded() {
        if (shouldAutoCheck()) {
            markAutoChecked()
            _autoCheckTrigger.tryEmit(Unit)
        }
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val release = gitHubApi.getLatestRelease()
            val remoteVersion = SemVer.normalizeTag(release.tagName)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext AppUpdateCheckResult.Error(
                    context.getString(R.string.app_update_error_no_apk)
                )
            val localVersion = SemVer.normalizeTag(BuildConfig.VERSION_NAME)
            when {
                SemVer.compareNormalized(remoteVersion, localVersion) <= 0 -> AppUpdateCheckResult.UpToDate
                else -> AppUpdateCheckResult.UpdateAvailable(
                    remoteVersion = remoteVersion,
                    releaseNotes = release.body?.takeIf { it.isNotBlank() },
                    apkUrl = apk.browserDownloadUrl
                )
            }
        } catch (e: Exception) {
            AppUpdateCheckResult.Error(
                e.message ?: context.getString(R.string.app_update_error_network)
            )
        }
    }

    suspend fun downloadApk(
        url: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "update.apk")
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            context.getString(R.string.app_update_error_download_http, response.code)
                        )
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    Exception(context.getString(R.string.app_update_error_download_http, -1))
                )
                val total = body.contentLength()
                var read = 0L
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var n: Int
                        while (input.read(buffer).also { n = it } != -1) {
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            } else {
                                onProgress(-1f)
                            }
                        }
                    }
                }
                onProgress(1f)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApkIntent(file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun unknownSourcesSettingsIntent(): Intent =
        android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
