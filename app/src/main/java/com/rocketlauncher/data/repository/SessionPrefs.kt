package com.rocketlauncher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keys = object {
        val serverUrl = stringPreferencesKey("server_url")
        val authToken = stringPreferencesKey("auth_token")
        val userId = stringPreferencesKey("user_id")
        val username = stringPreferencesKey("username")
        val displayName = stringPreferencesKey("display_name")
        val avatarUrl = stringPreferencesKey("avatar_url")
    }

    private suspend fun prefs(): Preferences = context.dataStore.data.first()

    suspend fun getServerUrl(): String? = prefs()[keys.serverUrl]
    suspend fun getAuthToken(): String? = prefs()[keys.authToken]
    suspend fun getUserId(): String? = prefs()[keys.userId]
    suspend fun getUsername(): String? = prefs()[keys.username]
    suspend fun getDisplayName(): String? = prefs()[keys.displayName]
    suspend fun getAvatarUrl(): String? = prefs()[keys.avatarUrl]?.takeIf { it.isNotEmpty() }

    /** Single read for all session fields at once */
    suspend fun getAll(): SessionSnapshot {
        val p = prefs()
        return SessionSnapshot(
            serverUrl = p[keys.serverUrl],
            authToken = p[keys.authToken],
            userId = p[keys.userId],
            username = p[keys.username],
            displayName = p[keys.displayName],
            avatarUrl = p[keys.avatarUrl]?.takeIf { it.isNotEmpty() }
        )
    }

    data class SessionSnapshot(
        val serverUrl: String?,
        val authToken: String?,
        val userId: String?,
        val username: String?,
        val displayName: String?,
        val avatarUrl: String?
    )

    suspend fun saveSession(
        serverUrl: String,
        authToken: String,
        userId: String,
        username: String,
        displayName: String,
        avatarUrl: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[keys.serverUrl] = serverUrl
            prefs[keys.authToken] = authToken
            prefs[keys.userId] = userId
            prefs[keys.username] = username
            prefs[keys.displayName] = displayName
            prefs[keys.avatarUrl] = avatarUrl ?: ""
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
