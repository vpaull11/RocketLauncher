package com.rocketlauncher.data.repository

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.db.MessageDao
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.data.dto.ChannelCreateBody
import com.rocketlauncher.data.dto.FindOrCreateInviteRequest
import com.rocketlauncher.data.dto.DiscussionCreateApiBody
import com.rocketlauncher.data.dto.DiscussionCreateResponse
import com.rocketlauncher.data.dto.GroupCreateBody
import com.rocketlauncher.data.dto.RoomDto
import com.rocketlauncher.data.dto.RoomIdBody
import com.rocketlauncher.data.dto.RoomIdUserIdBody
import com.rocketlauncher.data.dto.RoomMemberDto
import com.rocketlauncher.data.dto.RoomMembersResponse
import com.rocketlauncher.data.dto.TeamCreateBody
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatRoomActionsRepo"

@Singleton
class ChatRoomActionsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val roomDao: RoomDao,
    private val messageDao: MessageDao,
    private val sessionPrefs: SessionPrefs
) {

    suspend fun leaveRoom(roomId: String, roomType: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        if (roomId.isBlank()) return Result.failure(IllegalStateException("Пустой roomId"))
        return try {
            val resp = when (roomType) {
                "c" -> api.channelsLeave(RoomIdBody(roomId))
                "p" -> api.groupsLeave(RoomIdBody(roomId))
                else -> return Result.failure(IllegalStateException("Выход доступен только для канала или группы"))
            }
            if (resp.success) {
                try {
                    messageDao.deleteByRoom(roomId)
                } catch (e: Exception) {
                    Log.w(TAG, "delete messages after leave: ${e.message}")
                }
                try {
                    roomDao.deleteRoom(roomId)
                } catch (e: Exception) {
                    Log.w(TAG, "delete room after leave: ${e.message}")
                }
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(resp.error ?: "Не удалось выйти из чата"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "leaveRoom: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun loadAllMembers(roomId: String, roomType: String): Result<List<RoomMemberDto>> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        if (roomId.isBlank()) return Result.failure(IllegalStateException("Пустой roomId"))
        return try {
            val all = ArrayList<RoomMemberDto>()
            var offset = 0
            val page = 50
            while (true) {
                val resp: RoomMembersResponse = when (roomType) {
                    "c" -> api.channelsMembers(roomId, count = page, offset = offset)
                    "p" -> api.groupsMembers(roomId, count = page, offset = offset)
                    else -> return Result.failure(IllegalStateException("Список участников только для канала или группы"))
                }
                if (!resp.success) {
                    return Result.failure(IllegalStateException(resp.error ?: "members: ошибка сервера"))
                }
                if (resp.members.isEmpty()) break
                all.addAll(resp.members)
                if (resp.members.size < page) break
                if (resp.total > 0 && all.size >= resp.total) break
                offset += page
                if (offset > 5000) break
            }
            Result.success(all)
        } catch (e: Exception) {
            Log.e(TAG, "loadAllMembers: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Ссылка для вступления по инвайту (в буфер обмена). Только канал / приватная группа.
     */
    suspend fun getOrCreateRoomInviteLink(roomId: String, roomType: String): Result<String> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        if (roomId.isBlank()) return Result.failure(IllegalStateException("Пустой roomId"))
        if (roomType != "c" && roomType != "p") {
            return Result.failure(IllegalStateException("Инвайт только для канала или группы"))
        }
        return try {
            val resp = api.findOrCreateInvite(
                FindOrCreateInviteRequest(rid = roomId, days = 0, maxUses = 0)
            )
            if (!resp.success) {
                val msg = resp.error?.takeIf { it.isNotBlank() }
                    ?: resp.message?.takeIf { it.isNotBlank() }
                    ?: "Не удалось получить ссылку-приглашение"
                return Result.failure(IllegalStateException(msg))
            }
            val fromServer = resp.url?.trim()?.takeIf { it.isNotEmpty() }
            if (fromServer != null) return Result.success(fromServer)
            val token = resp.inviteId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return Result.failure(IllegalStateException("Пустой токен приглашения"))
            val base = sessionPrefs.getServerUrl()?.trim()?.trimEnd('/')
                ?: return Result.failure(IllegalStateException("Нет адреса сервера"))
            Result.success("$base/invite/$token")
        } catch (e: Exception) {
            Log.e(TAG, "getOrCreateRoomInviteLink: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun inviteUser(roomId: String, roomType: String, userId: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val body = RoomIdUserIdBody(roomId = roomId, userId = userId)
        return try {
            val resp = when (roomType) {
                "c" -> api.channelsInvite(body)
                "p" -> api.groupsInvite(body)
                else -> return Result.failure(IllegalStateException("Приглашение только в канал или группу"))
            }
            if (resp.success) Result.success(Unit)
            else Result.failure(IllegalStateException(resp.error ?: "invite failed"))
        } catch (e: Exception) {
            Log.e(TAG, "inviteUser: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setModerator(roomId: String, roomType: String, userId: String, add: Boolean): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val body = RoomIdUserIdBody(roomId, userId)
        return try {
            val resp = when (roomType) {
                "c" -> if (add) api.channelsAddModerator(body) else api.channelsRemoveModerator(body)
                "p" -> if (add) api.groupsAddModerator(body) else api.groupsRemoveModerator(body)
                else -> return Result.failure(IllegalStateException("Роли только в канале или группе"))
            }
            if (resp.success) Result.success(Unit)
            else Result.failure(IllegalStateException(resp.error ?: "moderator failed"))
        } catch (e: Exception) {
            Log.e(TAG, "setModerator: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setOwner(roomId: String, roomType: String, userId: String, add: Boolean): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val body = RoomIdUserIdBody(roomId, userId)
        return try {
            val resp = when (roomType) {
                "c" -> if (add) api.channelsAddOwner(body) else api.channelsRemoveOwner(body)
                "p" -> if (add) api.groupsAddOwner(body) else api.groupsRemoveOwner(body)
                else -> return Result.failure(IllegalStateException("Роли только в канале или группе"))
            }
            if (resp.success) Result.success(Unit)
            else Result.failure(IllegalStateException(resp.error ?: "owner failed"))
        } catch (e: Exception) {
            Log.e(TAG, "setOwner: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createChannel(name: String, readOnly: Boolean): Result<RoomDto> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val slug = name.trim().lowercase().replace("\\s+".toRegex(), "-")
        if (slug.isBlank()) return Result.failure(IllegalStateException("Введите имя канала"))
        return try {
            val resp = api.channelsCreate(ChannelCreateBody(name = slug, readOnly = readOnly))
            if (resp.success && resp.channel != null) Result.success(resp.channel)
            else Result.failure(IllegalStateException(resp.error ?: "channels.create failed"))
        } catch (e: Exception) {
            Log.e(TAG, "createChannel: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createGroup(name: String): Result<RoomDto> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val slug = name.trim().lowercase().replace("\\s+".toRegex(), "-")
        if (slug.isBlank()) return Result.failure(IllegalStateException("Введите имя чата"))
        return try {
            val resp = api.groupsCreate(GroupCreateBody(name = slug))
            if (resp.success && resp.group != null) Result.success(resp.group)
            else Result.failure(IllegalStateException(resp.error ?: "groups.create failed"))
        } catch (e: Exception) {
            Log.e(TAG, "createGroup: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createTeam(name: String, privateTeam: Boolean): Result<Pair<String, String?>> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val n = name.trim()
        if (n.isBlank()) return Result.failure(IllegalStateException("Введите имя команды"))
        return try {
            val resp = api.teamsCreate(TeamCreateBody(name = n, type = if (privateTeam) 1 else 0))
            if (resp.success && resp.team != null) {
                Result.success(resp.team._id to resp.team.roomId)
            } else {
                Result.failure(IllegalStateException(resp.error ?: "teams.create failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createTeam: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createDiscussion(parentRoomId: String, title: String): DiscussionCreateResponse {
        val api = apiProvider.getApi() ?: return DiscussionCreateResponse(success = false, error = "Не авторизован")
        val t = title.trim().ifBlank { "Обсуждение" }
        return try {
            api.createDiscussion(
                DiscussionCreateApiBody(
                    prid = parentRoomId,
                    tName = t,
                    pname = t
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "createDiscussion: ${e.message}", e)
            DiscussionCreateResponse(success = false, error = e.message)
        }
    }

    suspend fun createDiscussionFromMessage(parentRoomId: String, messageId: String, messageText: String): DiscussionCreateResponse {
        val api = apiProvider.getApi() ?: return DiscussionCreateResponse(success = false, error = "Не авторизован")
        val title = messageText.take(100).ifBlank { "Обсуждение" }
        return try {
            api.createDiscussion(
                DiscussionCreateApiBody(
                    prid = parentRoomId,
                    tName = title,
                    pmid = messageId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "createDiscussionFromMessage: ${e.message}", e)
            DiscussionCreateResponse(success = false, error = e.message)
        }
    }
}
