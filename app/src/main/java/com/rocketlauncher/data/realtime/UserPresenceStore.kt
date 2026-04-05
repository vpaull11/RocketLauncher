package com.rocketlauncher.data.realtime

import com.rocketlauncher.data.dto.UsersInfoUser
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserPresenceSnapshot(
    val byUserId: Map<String, UserPresenceStatus> = emptyMap(),
    val byUsernameLower: Map<String, UserPresenceStatus> = emptyMap(),
) {
    fun resolve(userId: String?, username: String?): UserPresenceStatus {
        if (!userId.isNullOrBlank()) byUserId[userId]?.let { return it }
        val u = username?.trim()?.lowercase(Locale.ROOT)
        if (!u.isNullOrEmpty()) byUsernameLower[u]?.let { return it }
        return UserPresenceStatus.UNKNOWN
    }
}

@Singleton
class UserPresenceStore @Inject constructor() {
    private val byUserId = ConcurrentHashMap<String, UserPresenceStatus>()
    private val byUsernameLower = ConcurrentHashMap<String, UserPresenceStatus>()
    private val _snapshot = MutableStateFlow(UserPresenceSnapshot())
    val snapshot: StateFlow<UserPresenceSnapshot> = _snapshot.asStateFlow()

    private fun emit() {
        _snapshot.value = UserPresenceSnapshot(
            byUserId = HashMap(byUserId),
            byUsernameLower = HashMap(byUsernameLower)
        )
    }

    fun update(userId: String, username: String?, status: UserPresenceStatus) {
        if (userId.isBlank() || status == UserPresenceStatus.UNKNOWN) return
        byUserId[userId] = status
        if (!username.isNullOrBlank()) {
            byUsernameLower[username.lowercase(Locale.ROOT)] = status
        }
        emit()
    }

    fun applyFromUserInfo(user: UsersInfoUser) {
        val st = UserPresenceStatus.fromRestApi(user.status)
        if (st == UserPresenceStatus.UNKNOWN) return
        update(user._id, user.username, st)
    }

    fun clear() {
        byUserId.clear()
        byUsernameLower.clear()
        emit()
    }
}
