package com.rocketlauncher.data.emoji

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentEmojiDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_emojis")

private const val MAX_RECENT = 5

/**
 * Последние выбранные в пикере смайлики (shortcode `:name:`), переживают перезапуск приложения.
 */
@Singleton
class RecentEmojiPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("recent_shortcodes")

    val recentFlow: Flow<List<String>> = context.recentEmojiDataStore.data.map { prefs ->
        val raw = prefs[key] ?: ""
        if (raw.isEmpty()) emptyList()
        else raw.split('\n').filter { it.isNotEmpty() }.take(MAX_RECENT)
    }

    suspend fun record(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        context.recentEmojiDataStore.edit { prefs ->
            val current = (prefs[key] ?: "").split('\n').filter { it.isNotEmpty() }.toMutableList()
            current.removeAll { it == trimmed }
            current.add(0, trimmed)
            while (current.size > MAX_RECENT) current.removeAt(current.lastIndex)
            prefs[key] = current.joinToString("\n")
        }
    }
}
