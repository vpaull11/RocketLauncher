package com.rocketlauncher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rocketlauncher.domain.model.FavoriteDisplayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.roomListDataStore: DataStore<Preferences> by preferencesDataStore(name = "room_list_prefs")

@Singleton
class RoomListPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyFavoriteDisplay = stringPreferencesKey("favorite_display_mode")

    val favoriteDisplayMode: Flow<FavoriteDisplayMode> = context.roomListDataStore.data.map { prefs ->
        prefs[keyFavoriteDisplay]?.let { raw ->
            runCatching { FavoriteDisplayMode.valueOf(raw) }.getOrNull()
        } ?: FavoriteDisplayMode.INLINE_IN_GROUPS
    }

    suspend fun setFavoriteDisplayMode(mode: FavoriteDisplayMode) {
        context.roomListDataStore.edit { prefs ->
            prefs[keyFavoriteDisplay] = mode.name
        }
    }
}
