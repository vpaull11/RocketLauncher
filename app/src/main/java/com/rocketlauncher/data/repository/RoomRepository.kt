package com.rocketlauncher.data.repository

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.api.RocketChatApi
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.data.db.RoomEntity
import com.rocketlauncher.data.dto.InviteJoinResult
import com.rocketlauncher.data.dto.RoomFavoriteRequest
import com.rocketlauncher.data.dto.RoomDto
import com.rocketlauncher.data.dto.SaveRoomNotificationRequest
import com.rocketlauncher.data.dto.MethodCallAnonRequest
import com.rocketlauncher.data.dto.SubscriptionDto
import com.rocketlauncher.data.dto.SubscriptionsMethodCall
import com.rocketlauncher.data.dto.UseInviteTokenRequest
import com.rocketlauncher.BuildConfig
import com.rocketlauncher.data.notifications.MessageNotifier
import com.rocketlauncher.data.subscriptions.SubscriptionUnreadPolicy
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RoomRepo"

/** Logcat: сравнение `unread` с официальным клиентом — фильтр по этому тегу. */
private const val TAG_SUBS_SYNC = "RoomRepoSubsSync"

private const val SUBS_RAW_LOG_CHUNK = 3500

@Singleton
class RoomRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val roomDao: RoomDao,
    private val prefs: SessionPrefs,
    private val messageNotifier: MessageNotifier
) {

    fun observeRooms(): Flow<List<RoomEntity>> = roomDao.observeAllRooms()

    fun observeRoom(roomId: String): Flow<RoomEntity?> = roomDao.observeRoom(roomId)

    suspend fun getRoom(roomId: String): RoomEntity? = roomDao.getRoom(roomId)

    suspend fun getAllRoomIds(): List<String> = roomDao.getAllRoomIds()

    suspend fun syncRooms() {
        val api = apiProvider.getApi() ?: return
        try {
            val roomsResponse = api.getRooms()
            if (!roomsResponse.success) return

            val subsMap = loadAllSubscriptionsMap(api)

            val roomIdsFromGet = roomsResponse.update.map { it._id }
            val missingSubs = roomIdsFromGet.filter { it !in subsMap }
            if (missingSubs.isNotEmpty()) {
                Log.w(
                    TAG_SUBS_SYNC,
                    "syncRooms: rooms.get без строки в subscriptions: count=${missingSubs.size} " +
                        "sample=${missingSubs.take(8)}"
                )
            }

            val existingById = try {
                roomDao.getAllRooms().associateBy { it.id }
            } catch (e: Exception) {
                Log.e(TAG, "getAllRooms for merge: ${e.message}")
                emptyMap()
            }

            val currentUsername = prefs.getUsername()
            val entities = roomsResponse.update.map { dto ->
                val sub = subsMap[dto._id]
                val existing = existingById[dto._id]
                dto.toEntity(currentUsername, sub, existing)
            }
            logFavoriteStatsFromSubscriptions("syncRooms/before insert", subsMap, roomsResponse.update.size)
            val favFromEntities = entities.count { it.isFavorite }
            Log.i(TAG, "syncRooms: entities from rooms.get isFavorite=true count=$favFromEntities / ${entities.size}")
            roomDao.insertAll(entities)
            for (e in entities) {
                if (e.unreadCount == 0 && e.userMentions == 0) {
                    messageNotifier.cancelNotificationsForRoom(e.id)
                }
            }
            /** Подписки — источник истины для [SubscriptionDto.f] (избранное); догоняем после insert. */
            mergeSubscriptionsIntoRooms(subsMap.values.toList())
            try {
                val dbFav = roomDao.getAllRooms().count { it.isFavorite }
                Log.i(TAG, "syncRooms/after merge: DB rooms with isFavorite=true count=$dbFav")
            } catch (_: Exception) { }
            if (roomsResponse.remove.isNotEmpty()) {
                roomDao.deleteByIds(roomsResponse.remove.map { it._id })
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncRooms failed: ${e.message}")
        }
    }

    /**
     * Обновить только счётчики непрочитанных из подписок (источник истины — сервер).
     * Вызывать после старта / возврата в приложение, если полный syncRooms уже был недавно.
     */
    /**
     * Вступить в комнату по токену из ссылки `/invite/...`.
     * После успеха синхронизирует список комнат.
     */
    suspend fun joinRoomViaInviteToken(token: String): Result<InviteJoinResult> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val resp = api.useInviteToken(UseInviteTokenRequest(token.trim()))
            val room = resp.room
            if (resp.success && room != null) {
                syncRooms()
                val displayName = room.fname?.takeIf { it.isNotBlank() }
                    ?: room.name?.takeIf { it.isNotBlank() }
                    ?: room.rid
                val avatarPath = when (room.t) {
                    "d" -> room.name?.takeIf { it.isNotEmpty() } ?: ""
                    else -> "room/${room.rid}"
                }
                Result.success(
                    InviteJoinResult(
                        roomId = room.rid,
                        roomName = displayName,
                        roomType = room.t,
                        avatarPath = avatarPath
                    )
                )
            } else {
                Result.failure(IllegalStateException(resp.error ?: "useInviteToken failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "joinRoomViaInviteToken: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncUnreadFromSubscriptionsOnly() {
        val api = apiProvider.getApi() ?: return
        try {
            val subsMap = loadAllSubscriptionsMap(api)
            if (subsMap.isEmpty()) return
            for ((rid, sub) in subsMap) {
                val u = sub.unread
                val m = sub.userMentions
                if (u != null || m != null || sub.alert) {
                    val room = roomDao.getRoom(rid) ?: continue
                    val newUnread = SubscriptionUnreadPolicy.effectiveUnread(
                        u,
                        sub.alert,
                        room.unreadCount,
                        null
                    )
                    val newMentions = m ?: room.userMentions
                    roomDao.updateUnreadCounts(
                        roomId = rid,
                        unreadCount = newUnread,
                        userMentions = newMentions
                    )
                    if (newUnread == 0 && newMentions == 0) {
                        messageNotifier.cancelNotificationsForRoom(rid)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncUnreadFromSubscriptionsOnly: ${e.message}")
        }
    }

    /** Все подписки с сервера (с пагинацией). Для обновления счётчиков в UI без полного syncRooms. */
    suspend fun getAllSubscriptionsMap(): Map<String, SubscriptionDto> {
        val api = apiProvider.getApi() ?: return emptyMap()
        return loadAllSubscriptionsMap(api)
    }

    /**
     * Сводка для экрана «Диагностика»: сколько в [subscriptions.getAll] приходит [SubscriptionDto.f],
     * совпадает ли с [RoomEntity.isFavorite]. Ищет ORPHAN (избранное на сервере, но нет строки комнаты).
     */
    suspend fun getFavoriteDiagnosticsReport(): String {
        val sb = StringBuilder()
        sb.appendLine("--- Избранное: подписки vs БД ---")
        val api = apiProvider.getApi()
        if (api == null) {
            sb.appendLine("API: null (нет сессии).")
            return sb.toString()
        }
        val subsMap = try {
            loadAllSubscriptionsMap(api)
        } catch (e: Exception) {
            sb.appendLine("Ошибка subscriptions.get: ${e.message}")
            return sb.toString()
        }
        val subs = subsMap.values.toList()
        val nNull = subs.count { it.f == null }
        val nTrue = subs.count { it.f == true }
        val nFalse = subs.count { it.f == false }
        sb.appendLine("Подписок загружено: ${subs.size}")
        sb.appendLine("Поле f: true=$nTrue, false=$nFalse, null=$nNull")
        sb.appendLine("(null = ключ не попал в JSON после десериализации; смотрите Logcat RoomRepo + сырой ответ)")
        val rooms = try {
            roomDao.getAllRooms()
        } catch (e: Exception) {
            sb.appendLine("Ошибка чтения rooms: ${e.message}")
            return sb.toString()
        }
        sb.appendLine("В БД isFavorite=true: ${rooms.count { it.isFavorite }}")
        sb.appendLine()
        sb.appendLine("Расхождения sub.f=true → БД:")
        var bad = 0
        for (sub in subs) {
            if (sub.f != true) continue
            val r = roomDao.getRoom(sub.rid)
            when {
                r == null -> {
                    sb.appendLine("  ORPHAN rid=${sub.rid} (f=true, строки комнаты нет в БД)")
                    bad++
                }
                !r.isFavorite -> {
                    sb.appendLine("  MISMATCH rid=${sub.rid.take(8)}.. sub.f=true, DB isFavorite=false")
                    bad++
                }
            }
        }
        if (bad == 0) sb.appendLine("  (нет)")
        sb.appendLine()
        sb.appendLine("Первые подписки с f=true (rid + флаг в БД):")
        subs.filter { it.f == true }.take(15).forEach { s ->
            val r = roomDao.getRoom(s.rid)
            sb.appendLine("  ${s.rid.take(14)}.. DB isFavorite=${r?.isFavorite}")
        }
        if (nTrue == 0) {
            sb.appendLine()
            sb.appendLine("На сервере в JSON нет ни одной подписки с f=true.")
            sb.appendLine("Проверьте версию Rocket.Chat и поле избранного в ответе API.")
        }
        return sb.toString()
    }

    /**
     * ID комнат из локальной БД ∪ [subscriptions.get] (обсуждения/сабчаты часто есть в подписках,
     * но не попадают в [getRooms] сразу — без этого stream-room-messages на них не подписывается).
     */
    suspend fun getAllRoomIdsMergedWithSubscriptions(): List<String> {
        val ids = LinkedHashSet<String>()
        try {
            ids.addAll(roomDao.getAllRoomIds())
        } catch (e: Exception) {
            Log.e(TAG, "getAllRoomIdsMergedWithSubscriptions rooms: ${e.message}")
        }
        try {
            ids.addAll(getAllSubscriptionsMap().keys)
        } catch (e: Exception) {
            Log.e(TAG, "getAllRoomIdsMergedWithSubscriptions subs: ${e.message}")
        }
        return ids.toList()
    }

    /**
     * Подписки, обновлённые на сервере после [updatedSinceIso] (догрузка после обрыва связи).
     */
    suspend fun fetchSubscriptionsUpdatedSince(updatedSinceIso: String): List<SubscriptionDto> {
        val api = apiProvider.getApi() ?: return emptyList()
        if (updatedSinceIso.isBlank()) return emptyList()
        val sinceMs = parseUpdatedSinceToEpochMs(updatedSinceIso)
        if (sinceMs != null) {
            try {
                val msg = SubscriptionsMethodCall.buildMessage(lastUpdatedMs = sinceMs)
                val resp = api.methodCallSubscriptionsGet(MethodCallAnonRequest(message = msg))
                if (resp.success) {
                    val parsed = SubscriptionsMethodCall.parseResultMessage(resp.message)
                    if (parsed != null) {
                        Log.i(TAG, "fetchSubscriptionsUpdatedSince: method.call delta count=${parsed.update.size}")
                        return parsed.update
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchSubscriptionsUpdatedSince method.call: ${e.message}")
            }
        }
        val merged = ArrayList<SubscriptionDto>()
        try {
            val subsResponse = api.getSubscriptions(updatedSince = updatedSinceIso)
            if (subsResponse.success) merged.addAll(subsResponse.update)
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubscriptionsUpdatedSince REST: ${e.message}")
        }
        return merged
    }

    private fun parseUpdatedSinceToEpochMs(iso: String): Long? = try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    /** Применить счётчики/флаги уведомлений из дельты подписок (после [fetchSubscriptionsUpdatedSince]). */
    suspend fun mergeSubscriptionsIntoRooms(subs: List<SubscriptionDto>) {
        if (subs.isEmpty()) return
        for (sub in subs) {
            val rid = sub.rid
            if (rid.isBlank()) continue
            /** До [getRoom]: избранное приходит в подписке для всех rid; не терять из‑за отсутствия строки в БД на этом шаге. */
            if (sub.f != null) {
                roomDao.updateFavorite(rid, sub.f)
            }
            val room = roomDao.getRoom(rid) ?: continue
            val u = sub.unread
            val m = sub.userMentions
            if (u != null || m != null || sub.alert) {
                val newUnread = SubscriptionUnreadPolicy.effectiveUnread(
                    u,
                    sub.alert,
                    room.unreadCount,
                    null
                )
                val newMentions = m ?: room.userMentions
                roomDao.updateUnreadCounts(
                    roomId = rid,
                    unreadCount = newUnread,
                    userMentions = newMentions
                )
                if (newUnread == 0 && newMentions == 0) {
                    messageNotifier.cancelNotificationsForRoom(rid)
                }
            }
            val hasExplicit =
                sub.disableNotifications != null || sub.mobilePushNotifications != null
            if (hasExplicit) {
                val muted = subscriptionIndicatesMuted(sub)
                roomDao.updateNotificationsMuted(rid, muted)
            }
            if (sub.tunread != null) {
                roomDao.updateThreadUnreadCount(rid, sub.tunread.size)
                val mergedRoom = roomDao.getRoom(rid)
                if (mergedRoom != null &&
                    mergedRoom.unreadCount == 0 &&
                    mergedRoom.userMentions == 0 &&
                    mergedRoom.threadUnreadCount == 0
                ) {
                    messageNotifier.cancelNotificationsForRoom(rid)
                }
            }
        }
    }

    /**
     * Избранное (звезда) на сервере и в локальной БД.
     * Список избранного при старте приходит через [syncRooms] (поле [SubscriptionDto.f] в подписках).
     */
    suspend fun setRoomFavorite(roomId: String, favorite: Boolean): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val resp = api.roomsFavorite(RoomFavoriteRequest(roomId = roomId, favorite = favorite))
            if (resp.success) {
                roomDao.updateFavorite(roomId, favorite)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(resp.error ?: "rooms.favorite failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "setRoomFavorite: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun loadAllSubscriptionsMap(api: RocketChatApi): Map<String, SubscriptionDto> {
        try {
            val msg = SubscriptionsMethodCall.buildMessage(lastUpdatedMs = null)
            val resp = api.methodCallSubscriptionsGet(MethodCallAnonRequest(message = msg))
            if (resp.success) {
                val parsed = SubscriptionsMethodCall.parseResultMessage(resp.message)
                if (parsed != null) {
                    val merged = LinkedHashMap<String, SubscriptionDto>()
                    for (sub in parsed.update) {
                        if (sub.rid.isNotBlank()) merged[sub.rid] = sub
                    }
                    Log.i(
                        TAG,
                        "loadAllSubscriptionsMap: method.call subscriptions:get ok, count=${merged.size}"
                    )
                    logSubscriptionsSyncDiagnostics(
                        source = "method.call/subscriptions:get",
                        subsMap = merged,
                        rawMethodCallResultJson = if (BuildConfig.DEBUG) resp.message else null
                    )
                    return merged
                }
                val preview = resp.message.take(240).replace("\n", " ")
                Log.w(TAG, "loadAllSubscriptionsMap: method.call parse=null, preview=$preview")
            } else {
                Log.w(TAG, "loadAllSubscriptionsMap: method.call success=false, message.len=${resp.message.length}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadAllSubscriptionsMap: method.call failed, fallback REST: ${e.message}")
        }
        return loadAllSubscriptionsMapViaRest(api)
    }

    private suspend fun loadAllSubscriptionsMapViaRest(api: RocketChatApi): Map<String, SubscriptionDto> {
        val merged = LinkedHashMap<String, SubscriptionDto>()
        try {
            val subsResponse = api.getSubscriptions(updatedSince = null)
            if (!subsResponse.success) {
                Log.w(TAG, "getSubscriptions REST success=false")
                return merged
            }
            for (sub in subsResponse.update) {
                if (sub.rid.isNotBlank()) merged[sub.rid] = sub
            }
            Log.i(TAG, "loadAllSubscriptionsMapViaRest: count=${merged.size}")
            logSubscriptionsSyncDiagnostics(
                source = "REST GET subscriptions.list",
                subsMap = merged,
                rawMethodCallResultJson = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "getSubscriptions REST: ${e.message}")
        }
        return merged
    }

    /**
     * Сравнение с официальным клиентом: в logcat фильтр **RoomRepoSubsSync**.
     * — Сводка и таблица `rid` + `unread` + mentions + tunread (всегда).
     * — Сырой JSON `result` из `method.call/subscriptions:get` — только [BuildConfig.DEBUG].
     */
    private fun logSubscriptionsSyncDiagnostics(
        source: String,
        subsMap: Map<String, SubscriptionDto>,
        rawMethodCallResultJson: String?
    ) {
        if (!BuildConfig.DEBUG) return
        if (rawMethodCallResultJson != null) {
            Log.i(TAG_SUBS_SYNC, "---- raw method.call result (subscriptions:get) begin ----")
            logChunkedForLogcat(rawMethodCallResultJson)
            Log.i(
                TAG_SUBS_SYNC,
                "---- raw method.call result end len=${rawMethodCallResultJson.length} ----"
            )
        } else {
            val hint = if ("REST" in source) {
                "тело REST в лог не дублируется; ниже — распарсенные подписки (как в merge)."
            } else {
                "сырой JSON result — в debug-сборке (BuildConfig.DEBUG); ниже — распарсенные поля."
            }
            Log.i(TAG_SUBS_SYNC, "$source: $hint")
        }
        val sorted = subsMap.values.sortedBy { it.rid }
        val unreadPresent = sorted.count { it.unread != null }
        val unreadPositive = sorted.count { (it.unread ?: 0) > 0 }
        val sumUnread = sorted.sumOf { (it.unread ?: 0).toLong() }
        val effectivePositive = sorted.count {
            SubscriptionUnreadPolicy.effectiveUnread(it.unread, it.alert, 0, null) > 0
        }
        Log.i(
            TAG_SUBS_SYNC,
            "$source: rows=${sorted.size} unreadFieldPresent=$unreadPresent " +
                "rawUnreadPositive=$unreadPositive sumRawUnread=$sumUnread " +
                "effectiveUnreadPositive=$effectivePositive (unread+alert, см. SubscriptionUnreadPolicy)"
        )
        val table = StringBuilder()
        table.append("rid\trawUnread\tmentions\ttunreadSize\topen\talert\teffectiveUnread\n")
        for (sub in sorted) {
            val eff = SubscriptionUnreadPolicy.effectiveUnread(sub.unread, sub.alert, 0, null)
            table.append(
                "${sub.rid}\t${sub.unread}\t${sub.userMentions}\t${sub.tunread?.size}\t${sub.open}\t${sub.alert}\t$eff\n"
            )
        }
        logChunkedForLogcat(table.toString())
    }

    private fun logChunkedForLogcat(text: String) {
        if (text.isEmpty()) return
        var i = 0
        var part = 0
        while (i < text.length) {
            val end = minOf(i + SUBS_RAW_LOG_CHUNK, text.length)
            Log.i(TAG_SUBS_SYNC, "[chunk $part] ${text.substring(i, end)}")
            i = end
            part++
        }
    }

    private fun logFavoriteStatsFromSubscriptions(
        phase: String,
        subsMap: Map<String, SubscriptionDto>,
        roomsFromGetCount: Int
    ) {
        if (!BuildConfig.DEBUG) return
        val subs = subsMap.values.toList()
        val nNull = subs.count { it.f == null }
        val nTrue = subs.count { it.f == true }
        val nFalse = subs.count { it.f == false }
        Log.i(
            TAG,
            "$phase: rooms.get count=$roomsFromGetCount, subs total=${subs.size}, " +
                "sub.f: true=$nTrue false=$nFalse null=$nNull"
        )
        if (nTrue > 0) {
            val sample = subs.filter { it.f == true }.take(6).joinToString { it.rid.take(8) + ".." }
            Log.i(TAG, "$phase: sample f=true rids: $sample")
        }
    }

    /**
     * Вкл/выкл оповещения по чату на сервере ([rooms.saveNotification]) и в локальной БД.
     */
    suspend fun setRoomNotificationsMuted(roomId: String, muted: Boolean): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        val notifications = if (muted) {
            mapOf(
                "disableNotifications" to "1",
                "mobilePushNotifications" to "nothing"
            )
        } else {
            mapOf(
                "disableNotifications" to "0",
                "mobilePushNotifications" to "default"
            )
        }
        return try {
            val resp = api.saveRoomNotification(
                SaveRoomNotificationRequest(roomId = roomId, notifications = notifications)
            )
            if (resp.success) {
                roomDao.updateNotificationsMuted(roomId, muted)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(resp.error ?: "rooms.saveNotification failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "setRoomNotificationsMuted: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Цикл: все уведомления → только основной канал (без обсуждений-сабчатов) → выкл → снова все.
     * Режим «только основной» хранится локально ([RoomEntity.notifySubchats]); mute синхронизируется с сервером.
     */
    suspend fun cycleRoomNotificationPolicy(roomId: String) {
        val r = roomDao.getRoom(roomId) ?: return
        when {
            !r.notificationsMuted && r.notifySubchats -> {
                roomDao.updateNotifySubchats(roomId, false)
            }
            !r.notificationsMuted && !r.notifySubchats -> {
                setRoomNotificationsMuted(roomId, true)
            }
            else -> {
                setRoomNotificationsMuted(roomId, false).onSuccess {
                    roomDao.updateNotifySubchats(roomId, true)
                }
            }
        }
    }

    private fun RoomDto.toEntity(
        currentUsername: String?,
        sub: SubscriptionDto?,
        existing: RoomEntity?
    ): RoomEntity {
        val otherUsername = when (t) {
            "d" -> usernames?.firstOrNull { it != currentUsername }
                ?: name?.takeIf { it != currentUsername }
            else -> null
        }
        val otherDisplayName = lastMessage?.u?.let { msgUser ->
            if (msgUser.username != null && msgUser.username != currentUsername) {
                msgUser.name?.takeIf { it.isNotBlank() }
            } else null
        }
        val displayName = when (t) {
            "d" -> otherDisplayName ?: fname ?: otherUsername ?: name ?: _id
            else -> fname ?: name ?: _id
        }
        val avatarPath = when (t) {
            "d" -> otherUsername ?: name
            else -> "room/$_id"
        }

        val isFav = when {
            sub?.f != null -> sub.f
            f != null -> f
            else -> existing?.isFavorite ?: false
        }
        val unreadCount = SubscriptionUnreadPolicy.effectiveUnread(
            sub?.unread,
            sub?.alert ?: false,
            existing?.unreadCount ?: 0,
            unread
        )
        val mentions = sub?.userMentions ?: (existing?.userMentions ?: 0)
        val threadUnreadCount = when {
            sub?.tunread != null -> sub.tunread.size
            else -> existing?.threadUnreadCount ?: 0
        }

        val notificationsMuted = mergeNotificationMutedFromSubscription(sub, existing)
        val parentId = prid?.takeIf { it.isNotBlank() } ?: existing?.discussionParentId

        return RoomEntity(
            id = _id,
            name = name,
            displayName = displayName,
            type = t ?: "c",
            avatarPath = avatarPath,
            lastMessageText = lastMessage?.msg,
            lastMessageTime = lm ?: lastMessage?.ts,
            lastMessageUserId = lastMessage?.u?._id,
            lastMessageUsername = lastMessage?.u?.username,
            unreadCount = unreadCount,
            threadUnreadCount = threadUnreadCount,
            updatedAt = System.currentTimeMillis(),
            isFavorite = isFav,
            isDiscussion = !parentId.isNullOrBlank(),
            isTeam = teamMain == true,
            userMentions = mentions,
            notificationsMuted = notificationsMuted,
            notifySubchats = existing?.notifySubchats ?: true,
            discussionParentId = parentId
        )
    }

    /** Сервер — источник истины, но частичные ответы подписок не затирают локальное без полей уведомлений. */
    private fun mergeNotificationMutedFromSubscription(
        sub: SubscriptionDto?,
        existing: RoomEntity?
    ): Boolean {
        if (sub == null) return existing?.notificationsMuted ?: false
        val hasExplicit =
            sub.disableNotifications != null || sub.mobilePushNotifications != null
        if (!hasExplicit) return existing?.notificationsMuted ?: false
        return subscriptionIndicatesMuted(sub)
    }

    private fun subscriptionIndicatesMuted(sub: SubscriptionDto): Boolean {
        if (sub.disableNotifications == true) return true
        if (sub.mobilePushNotifications == "nothing") return true
        return false
    }
}
