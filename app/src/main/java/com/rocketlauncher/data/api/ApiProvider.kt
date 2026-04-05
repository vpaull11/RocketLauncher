package com.rocketlauncher.data.api

import com.rocketlauncher.data.repository.SessionPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiProvider @Inject constructor(
    private val prefs: SessionPrefs
) {
    private val mutex = Mutex()
    @Volatile private var cachedApi: RocketChatApi? = null
    @Volatile private var cachedServerUrl: String? = null
    @Volatile private var cachedAuthToken: String? = null
    @Volatile private var cachedUserId: String? = null

    suspend fun getApi(): RocketChatApi? {
        mutex.withLock {
            val snap = prefs.getAll()
            if (snap.serverUrl == null || snap.authToken == null || snap.userId == null) {
                clearCache()
                return null
            }
            if (cachedApi != null && cachedServerUrl == snap.serverUrl && cachedAuthToken == snap.authToken) {
                return cachedApi
            }
            cachedApi = ApiFactory.create(snap.serverUrl, snap.authToken, snap.userId)
            cachedServerUrl = snap.serverUrl
            cachedAuthToken = snap.authToken
            cachedUserId = snap.userId
            return cachedApi!!
        }
    }

    fun invalidate() {
        clearCache()
    }

    private fun clearCache() {
        cachedApi = null
        cachedServerUrl = null
        cachedAuthToken = null
        cachedUserId = null
    }
}
