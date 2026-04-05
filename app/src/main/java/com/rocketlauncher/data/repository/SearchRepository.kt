package com.rocketlauncher.data.repository

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.UsersInfoUser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SearchRepo"

data class GlobalMessageHit(
    val messageId: String,
    val roomId: String,
    val roomDisplayName: String,
    val roomType: String,
    val text: String,
    val ts: String,
    val username: String?
)

@Singleton
class SearchRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val roomDao: RoomDao
) {

    suspend fun searchMessagesInRoom(roomId: String, searchText: String): Result<List<MessageDto>> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val resp = api.chatSearch(
                roomId = roomId,
                searchText = searchText,
                count = 50,
                offset = 0
            )
            if (resp.success) Result.success(resp.messages)
            else Result.failure(IllegalStateException("chat.search failed"))
        } catch (e: Exception) {
            Log.e(TAG, "searchMessagesInRoom: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Глобальный поиск: [chat.search] по всем локальным комнатам (параллельно с ограничением).
     */
    suspend fun searchMessagesGlobally(searchText: String, maxRooms: Int = 40): List<GlobalMessageHit> {
        val api = apiProvider.getApi() ?: return emptyList()
        val q = searchText.trim()
        if (q.length < 2) return emptyList()
        val rooms = roomDao.getAllRooms().take(maxRooms)
        if (rooms.isEmpty()) return emptyList()
        val roomById = rooms.associateBy { it.id }
        val semaphore = Semaphore(6)
        return coroutineScope {
            rooms.map { room ->
                async {
                    semaphore.withPermit {
                        try {
                            val resp = api.chatSearch(
                                roomId = room.id,
                                searchText = q,
                                count = 15,
                                offset = 0
                            )
                            if (!resp.success) return@withPermit emptyList()
                            resp.messages.map { m ->
                                val r = roomById[m.rid]
                                GlobalMessageHit(
                                    messageId = m._id,
                                    roomId = m.rid,
                                    roomDisplayName = r?.displayName ?: r?.name ?: m.rid,
                                    roomType = r?.type ?: "c",
                                    text = m.msg,
                                    ts = m.ts,
                                    username = m.u.username
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "global search room ${room.id}: ${e.message}")
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten().sortedByDescending { it.ts }
        }
    }

    suspend fun spotlight(query: String) = runCatching {
        val api = apiProvider.getApi() ?: return@runCatching null
        api.spotlight(query)
    }.getOrElse {
        Log.e(TAG, "spotlight: ${it.message}", it)
        null
    }

    suspend fun autocompleteRooms(query: String) = runCatching {
        val api = apiProvider.getApi() ?: return@runCatching null
        val selector = buildJsonObject { put("name", query) }.toString()
        api.roomsAutocompleteChannelAndPrivate(selector)
    }.getOrElse {
        Log.e(TAG, "autocompleteRooms: ${it.message}", it)
        null
    }

    suspend fun getUserById(userId: String): Result<UsersInfoUser> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        if (userId.isBlank()) return Result.failure(IllegalStateException("Пустой userId"))
        return try {
            val resp = api.getUsersInfo(userId)
            if (resp.success && resp.user != null) Result.success(resp.user)
            else Result.failure(IllegalStateException("users.info: нет данных"))
        } catch (e: Exception) {
            Log.e(TAG, "getUserById: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserByUsername(username: String): Result<UsersInfoUser> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val u = username.trim().removePrefix("@").trim()
        if (u.isBlank()) return Result.failure(IllegalStateException("Пустой username"))
        return try {
            val resp = api.getUsersInfoByUsername(u)
            if (resp.success && resp.user != null) Result.success(resp.user)
            else Result.failure(IllegalStateException("users.info: нет данных"))
        } catch (e: Exception) {
            Log.e(TAG, "getUserByUsername: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createDirectMessage(username: String): Result<String> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val resp = api.imCreate(com.rocketlauncher.data.dto.ImCreateRequest(username = username))
            if (resp.success && resp.room != null) {
                Result.success(resp.room._id)
            } else {
                Result.failure(IllegalStateException(resp.error ?: "im.create failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
