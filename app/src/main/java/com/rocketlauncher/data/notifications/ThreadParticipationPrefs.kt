package com.rocketlauncher.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.threadParticipationStore: DataStore<Preferences> by preferencesDataStore(
    name = "thread_participation"
)

/**
 * Id корневых сообщений тредов ([tmid]), за которыми пользователь следует локально:
 * писал в треде, подписался кнопкой ([chat.followMessage]) или и то и другое.
 * Push по ответам в треде — при упоминании или наличии id в этом множестве.
 */
@Singleton
class ThreadParticipationPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyTmids = stringSetPreferencesKey("participated_thread_roots")

    suspend fun markParticipatedInThread(threadRootMessageId: String) {
        val id = threadRootMessageId.trim()
        if (id.isEmpty()) return
        context.threadParticipationStore.edit { prefs ->
            val cur = prefs[keyTmids].orEmpty()
            prefs[keyTmids] = cur + id
        }
    }

    suspend fun isParticipatingInThread(threadRootMessageId: String): Boolean {
        val id = threadRootMessageId.trim()
        if (id.isEmpty()) return false
        val set = context.threadParticipationStore.data.map { it[keyTmids].orEmpty() }.first()
        return id in set
    }

    suspend fun removeParticipation(threadRootMessageId: String) {
        val id = threadRootMessageId.trim()
        if (id.isEmpty()) return
        context.threadParticipationStore.edit { prefs ->
            val cur = prefs[keyTmids].orEmpty()
            if (cur.isEmpty()) return@edit
            prefs[keyTmids] = cur - id
        }
    }

    suspend fun clear() {
        context.threadParticipationStore.edit { it.remove(keyTmids) }
    }
}
