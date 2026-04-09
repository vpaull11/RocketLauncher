package com.rocketlauncher.data.realtime

import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.util.AppLog
import com.rocketlauncher.data.db.MessageDao
import com.rocketlauncher.data.db.MessageEntity
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.data.message.collectQuoteSegmentsFromJsonArray
import com.rocketlauncher.data.message.findFirstQuoteAttachmentJson
import com.rocketlauncher.data.message.quoteSegmentsToJson
import com.rocketlauncher.data.message.stripRocketQuotePermalinkMarkdown
import com.rocketlauncher.data.mentions.RocketChatMessageIds
import com.rocketlauncher.data.mentions.mentionsArrayContainsCurrentUser
import com.rocketlauncher.data.mentions.mentionsArrayToJsonString
import com.rocketlauncher.data.notifications.IncomingJitsiCallNotifier
import com.rocketlauncher.data.notifications.MessageNotifier
import com.rocketlauncher.data.notifications.RoomNotificationPolicy
import com.rocketlauncher.data.notifications.ThreadParticipationPrefs
import com.rocketlauncher.data.RocketChatMessageKinds
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.SubscriptionDto
import com.rocketlauncher.data.repository.MessageRepository
import com.rocketlauncher.data.repository.RoomRepository
import com.rocketlauncher.data.subscriptions.SubscriptionUnreadPolicy
import com.rocketlauncher.data.repository.SessionPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RealtimeWS"
private const val RECONNECT_MIN_MS = 1_000L
private const val RECONNECT_MAX_MS = 30_000L

/**
 * [JSONObject.has] возвращает true и для `"field": null`, а [JSONObject.optInt] в этом случае даёт 0,
 * из‑за чего при частичных DDP-событиях затирались реальные непрочитанные без открытия чата.
 */
private fun JSONObject.hasNonNull(name: String): Boolean =
    has(name) && !isNull(name)
private const val PING_INTERVAL_MS = 25_000L

@Singleton
class RealtimeMessageService @Inject constructor(
    private val sessionPrefs: SessionPrefs,
    private val messageDao: MessageDao,
    private val roomDao: RoomDao,
    private val apiProvider: ApiProvider,
    private val networkMonitor: NetworkMonitor,
    private val messageNotifier: MessageNotifier,
    private val incomingJitsiCallNotifier: IncomingJitsiCallNotifier,
    private val messageRepository: MessageRepository,
    private val roomRepository: RoomRepository,
    private val userPresenceStore: UserPresenceStore,
    private val threadParticipationPrefs: ThreadParticipationPrefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val subscriptions = ConcurrentHashMap<String, String>()
    private var userNotifySubId: String? = null
    private var userPresenceLoggedSubId: String? = null
    @Volatile private var connected = false
    @Volatile private var loginDone = false
    private val shouldBeConnected = AtomicBoolean(false)
    private var reconnectDelay = RECONNECT_MIN_MS
    private var reconnectJob: Job? = null
    private var networkJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState { CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED }

    /** Событие от Apps Engine: открытие модального окна (modal.open / modal.update). */
    data class UiInteractionEvent(val triggerId: String, val viewId: String, val viewJson: String)

    private val _uiInteractionEvents = MutableSharedFlow<UiInteractionEvent>(extraBufferCapacity = 4)
    val uiInteractionEvents: SharedFlow<UiInteractionEvent> = _uiInteractionEvents.asSharedFlow()

    private val _diagLog = MutableStateFlow<List<String>>(emptyList())
    val diagLog: StateFlow<List<String>> = _diagLog.asStateFlow()
    @Volatile var receivedMsgCount = 0; private set
    @Volatile var lastError: String? = null; private set

    private fun diag(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "$ts $msg"
        AppLog.d(TAG, entry)
        _diagLog.value = (_diagLog.value + entry).takeLast(50)
    }

    fun connect() {
        diag("connect() called")
        shouldBeConnected.set(true)
        startNetworkObserver()
        doConnect()
    }

    fun disconnect() {
        shouldBeConnected.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        networkJob?.cancel()
        networkJob = null
        networkMonitor.stop()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        connected = false
        loginDone = false
        subscriptions.clear()
        userNotifySubId = null
        userPresenceLoggedSubId = null
        userPresenceStore.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun subscribe(roomId: String) {
        if (roomId.isBlank()) return
        val subId = "stream-${roomId}-${System.currentTimeMillis()}"
        subscriptions[roomId] = subId
        if (loginDone) sendSubscribe(roomId, subId)
        if (webSocket == null && shouldBeConnected.get()) doConnect()
    }

    fun subscribeAll(roomIds: List<String>) {
        var newCount = 0
        for (roomId in roomIds) {
            if (roomId.isBlank() || subscriptions.containsKey(roomId)) continue
            val subId = "stream-${roomId}-${System.currentTimeMillis()}"
            subscriptions[roomId] = subId
            if (loginDone) sendSubscribe(roomId, subId)
            newCount++
        }
        diag("subscribeAll: +$newCount new, total=${subscriptions.size}, loginDone=$loginDone, ws=${webSocket != null}")
        if (webSocket == null && shouldBeConnected.get()) doConnect()
    }

    fun unsubscribe(roomId: String) {
        val subId = subscriptions.remove(roomId) ?: return
        webSocket?.send("""{"msg":"unsub","id":"$subId"}""")
    }

    private fun startNetworkObserver() {
        networkMonitor.start()
        if (networkJob?.isActive == true) return
        networkJob = scope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online && shouldBeConnected.get() && webSocket == null) {
                    AppLog.d(TAG, "Network restored, reconnecting")
                    // Не сбрасываем reconnectDelay: пусть backoff накапливается (до 30 сек).
                    // Иначе при нестабильной сети каждый переход isOnline=true
                    // сбрасывает задержку до 1 сек → бесконечный быстрый реконнект → аккумулятор.
                    doConnect()
                }
            }
        }
    }

    private fun doConnect() {
        if (webSocket != null) {
            diag("doConnect: already has webSocket, skip")
            return
        }
        scope.launch {
            val serverUrl = sessionPrefs.getServerUrl()
            val authToken = sessionPrefs.getAuthToken()
            if (serverUrl == null || authToken == null) {
                diag("doConnect: no serverUrl/authToken, abort")
                return@launch
            }
            if (webSocket != null) return@launch

            _connectionState.value = if (reconnectDelay > RECONNECT_MIN_MS)
                ConnectionState.RECONNECTING else ConnectionState.CONNECTING

            val wsUrl = serverUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .trimEnd('/') + "/websocket"

            diag("doConnect: opening $wsUrl")
            val request = Request.Builder().url(wsUrl).build()
            webSocket = client.newWebSocket(request, createListener(authToken))
        }
    }

    private fun scheduleReconnect() {
        if (!shouldBeConnected.get()) return
        if (!networkMonitor.isOnline.value) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(RECONNECT_MAX_MS)
            doConnect()
        }
    }

    private fun onConnectionEstablished() {
        connected = true
        loginDone = false
        reconnectDelay = RECONNECT_MIN_MS
        diag("WS connected (DDP handshake pending)")
    }

    private fun onLoginSuccess() {
        loginDone = true
        _connectionState.value = ConnectionState.CONNECTED
        diag("DDP login OK, subs=${subscriptions.size}")
        scope.launch {
            try {
                val userId = sessionPrefs.getUserId()
                if (userId != null) {
                    subscribeToUserNotifications(userId)
                }
                subscribeToLoggedUserPresence()
                /**
                 * Сначала только БД — без ожидания REST. Иначе при ошибке/долгом [subscriptions.get]
                 * [subscribeAll] не вызывается и сообщения по WebSocket не приходят вообще.
                 */
                val dbIds = roomDao.getAllRoomIds()
                diag("Auto-subscribe phase1: ${dbIds.size} rooms from DB")
                subscribeAll(dbIds)

                runCatching {
                    val subKeys = roomRepository.getAllSubscriptionsMap().keys
                        .filter { it.isNotBlank() && !subscriptions.containsKey(it) }
                    if (subKeys.isNotEmpty()) {
                        diag("Auto-subscribe phase2: +${subKeys.size} from subscriptions API")
                        subscribeAll(subKeys)
                    }
                }.onFailure { e ->
                    diag("Auto-subscribe phase2 (subscriptions API): ${e.message}")
                }
            } catch (e: Exception) {
                diag("Auto-subscribe error: ${e.message}")
                lastError = "auto-sub: ${e.message}"
                runCatching {
                    subscribeAll(roomDao.getAllRoomIds())
                    diag("Auto-subscribe fallback: ${subscriptions.size} after error")
                }
            }
        }
        resubscribeAll()
        scope.launch { syncMessagesAfterReconnect() }
    }

    private fun onConnectionLost() {
        connected = false
        loginDone = false
        webSocket = null
        diag("Connection lost")
        scheduleReconnect()
    }

    fun getDiagnosticInfo(): String {
        val sb = StringBuilder()
        sb.appendLine("--- WebSocket ---")
        sb.appendLine("state: ${_connectionState.value}")
        sb.appendLine("ws: ${if (webSocket != null) "exists" else "null"}")
        sb.appendLine("connected: $connected")
        sb.appendLine("loginDone: $loginDone")
        sb.appendLine("shouldBeConnected: ${shouldBeConnected.get()}")
        sb.appendLine("subscriptions: ${subscriptions.size}")
        sb.appendLine("receivedMsgCount: $receivedMsgCount")
        sb.appendLine("lastError: ${lastError ?: "none"}")
        sb.appendLine("network online: ${networkMonitor.isOnline.value}")
        sb.appendLine()
        sb.appendLine("--- Event Log (last 50) ---")
        _diagLog.value.forEach { sb.appendLine(it) }
        return sb.toString()
    }

    private fun resubscribeAll() {
        subscriptions.forEach { (roomId, _) ->
            val newSubId = "stream-${roomId}-${System.currentTimeMillis()}"
            subscriptions[roomId] = newSubId
            sendSubscribe(roomId, newSubId)
        }
    }

    /**
     * После успешного DDP-login: дельта подписок с [RoomDao.maxRoomsUpdatedAt], догрузка сообщений
     * по изменившимся комнатам; при непрочитанных на сервере — локальное уведомление о новом сообщении.
     */
    private suspend fun syncMessagesAfterReconnect() {
        val api = apiProvider.getApi() ?: return
        val myId = sessionPrefs.getUserId()

        val maxMs = try {
            roomDao.maxRoomsUpdatedAt()
        } catch (e: Exception) {
            Log.e(TAG, "maxRoomsUpdatedAt: ${e.message}")
            null
        }

        val changedSubs: List<SubscriptionDto> = try {
            if (maxMs != null && maxMs > 0L) {
                val sinceIso = Instant.ofEpochMilli(maxMs).toString()
                diag("subscriptions.get updatedSince=$sinceIso")
                roomRepository.fetchSubscriptionsUpdatedSince(sinceIso)
            } else {
                diag("subscriptions.get: skip (no max updatedAt)")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSubscriptionsUpdatedSince: ${e.message}")
            emptyList()
        }

        if (changedSubs.isNotEmpty()) {
            roomRepository.mergeSubscriptionsIntoRooms(changedSubs)
            diag("reconnect sync: ${changedSubs.size} subs changed, merge unread done")
        }

        val incremental = changedSubs.isNotEmpty()
        val targetRoomIds: List<String> = if (incremental) {
            changedSubs.map { it.rid }.filter { it.isNotBlank() }.distinct()
        } else {
            subscriptions.keys.toList()
        }
        val subByRid = changedSubs.associateBy { it.rid }

        for (roomId in targetRoomIds) {
            try {
                if (!incremental && messageDao.getLatestTimestamp(roomId) == null) continue
                val sub = subByRid[roomId]
                val room = roomDao.getRoom(roomId)
                val roomType = room?.type ?: sub?.t ?: "c"
                val response = when (roomType) {
                    "d" -> api.getDmMessages(roomId = roomId, count = 50)
                    "p" -> api.getGroupMessages(roomId = roomId, count = 50)
                    else -> api.getChannelMessages(roomId = roomId, count = 50)
                }
                if (!response.success || response.messages.isEmpty()) continue

                val unreadPositive = sub?.let {
                    SubscriptionUnreadPolicy.effectiveUnread(it.unread, it.alert, 0, null) > 0
                } == true
                val notifyDto = if (incremental && unreadPositive && myId != null) {
                    pickNewestNewIncomingMessage(response.messages, myId)
                } else {
                    null
                }

                messageRepository.insertMessagesFromDtos(response.messages)

                if (notifyDto != null) {
                    val r = room ?: roomDao.getRoom(roomId)
                    if (RoomNotificationPolicy.shouldNotifyForRoom(roomDao, roomId)) {
                        val roomTitle = r?.displayName ?: r?.name ?: notifyDto.u.username ?: "Чат"
                        val sender = notifyDto.u.name?.takeIf { it.isNotBlank() }
                            ?: notifyDto.u.username ?: "Кто-то"
                        val preview = notifyDto.msg.trim().ifBlank { "Вложение" }
                        messageNotifier.notifyNewChatMessage(
                            roomId = roomId,
                            roomDisplayName = roomTitle,
                            roomType = r?.type ?: roomType,
                            avatarPath = r?.avatarPath ?: "",
                            senderLabel = sender,
                            messagePreview = preview,
                            messageId = notifyDto._id
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncMessagesAfterReconnect room=$roomId: ${e.message}")
            }
        }
    }

    /** Самое новое сообщение из пачки, которого ещё не было в БД, не от меня, не ответ в треде. */
    private suspend fun pickNewestNewIncomingMessage(
        messages: List<MessageDto>,
        myUserId: String
    ): MessageDto? {
        val sorted = messages.sortedByDescending { dto ->
            try {
                Instant.parse(dto.ts).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
        for (dto in sorted) {
            if (!dto.tmid.isNullOrBlank()) continue
            if (!dto.t.isNullOrBlank() && RocketChatMessageKinds.isRoomEventOrService(dto.t)) continue
            if (dto.u._id == myUserId) continue
            if (messageDao.getById(dto._id) != null) continue
            return dto
        }
        return null
    }

    private fun sendSubscribe(roomId: String, subId: String) {
        webSocket?.send("""{"msg":"sub","id":"$subId","name":"stream-room-messages","params":["$roomId",false]}""")
    }

    private fun subscribeToUserNotifications(userId: String) {
        val subId = "notify-user-$userId-${System.currentTimeMillis()}"
        userNotifySubId = subId
        webSocket?.send("""{"msg":"sub","id":"$subId","name":"stream-notify-user","params":["$userId/subscriptions-changed",false]}""")
        // Подписка на UI-взаимодействия от Apps Engine (открытие модальных окон Poll App и т.п.)
        val uiSubId = "notify-user-ui-$userId-${System.currentTimeMillis()}"
        webSocket?.send("""{"msg":"sub","id":"$uiSubId","name":"stream-notify-user","params":["$userId/uiInteraction",false]}""")
        diag("Subscribed to user notifications + uiInteraction: $userId")
    }

    /** Глобальные обновления статусов пользователей (online / away / busy / offline). */
    private fun subscribeToLoggedUserPresence() {
        val subId = "notify-logged-presence-${System.currentTimeMillis()}"
        userPresenceLoggedSubId = subId
        webSocket?.send("""{"msg":"sub","id":"$subId","name":"stream-notify-logged","params":["user-status",false]}""")
        diag("Subscribed stream-notify-logged user-status")
    }

    private fun createListener(authToken: String): WebSocketListener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                diag("WS onOpen, code=${response.code}")
                onConnectionEstablished()
                webSocket.send("""{"msg":"connect","version":"1","support":["1"]}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val msgType = json.optString("msg")
                    when (msgType) {
                        "connected" -> {
                            diag("DDP connected, sending login")
                            val loginId = "login-${System.currentTimeMillis()}"
                            webSocket.send("""{"msg":"method","method":"login","id":"$loginId","params":[{"resume":"$authToken"}]}""")
                        }
                        "result" -> {
                            val err = json.optJSONObject("error")
                            if (err == null && json.optString("id").startsWith("login-")) {
                                onLoginSuccess()
                            } else if (err != null) {
                                val errMsg = err.optString("message", "unknown")
                                diag("DDP login error: $errMsg")
                                lastError = "login: $errMsg"
                            }
                        }
                        "ping" -> webSocket.send("""{"msg":"pong"}""")
                        "ready" -> { /* sub confirmed */ }
                        "nosub" -> {
                            val errObj = json.optJSONObject("error")
                            diag("nosub id=${json.optString("id")}: ${errObj?.optString("message") ?: "no error"}")
                        }
                        "removed" -> {
                            val collection = json.optString("collection")
                            if (collection == "stream-room-messages") {
                                val removedId = json.optString("id")
                                if (removedId.isNotBlank()) {
                                    scope.launch {
                                        try {
                                            messageDao.deleteById(removedId)
                                            diag("streamMsg removed id=${removedId.take(8)}..")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "delete removed msg: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        "added", "changed" -> {
                            val collection = json.optString("collection")
                            if (collection == "stream-room-messages") {
                                handleStreamMessage(json)
                            } else if (collection == "stream-notify-user") {
                                handleUserNotification(json)
                            } else if (collection == "stream-notify-logged") {
                                handleStreamNotifyLogged(json)
                            }
                        }
                    }
                } catch (e: Exception) {
                    diag("parse error: ${e.message}")
                    lastError = "parse: ${e.message}"
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                diag("WS failure: ${t.message}, resp=${response?.code}")
                lastError = "ws: ${t.message}"
                onConnectionLost()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                diag("WS closing: code=$code reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                diag("WS closed: code=$code reason=$reason")
                onConnectionLost()
            }
        }

    private fun handleStreamMessage(json: JSONObject) {
        val fields = json.optJSONObject("fields")
        if (fields == null) { diag("streamMsg: no fields"); return }
        val args = fields.optJSONArray("args")
        if (args == null || args.length() == 0) { diag("streamMsg: no args"); return }
        val msgObj = args.optJSONObject(0)
        if (msgObj == null) { diag("streamMsg: args[0] not object"); return }
        val entity = parseMessage(msgObj)
        if (entity == null) { diag("streamMsg: parseMessage returned null, raw_id=${msgObj.optString("_id")}"); return }
        val ddpEvent = json.optString("msg", "")
        receivedMsgCount++
        diag("MSG #$receivedMsgCount: room=${entity.roomId.take(8)}.. from=${entity.username} text=${entity.text.take(30)} ddp=$ddpEvent")
        scope.launch {
            try {
                val existing = messageDao.getById(entity.id)
                val merged = entity.copy(
                    readByOthers = entity.readByOthers ?: existing?.readByOthers,
                    threadReplyCount = entity.threadReplyCount ?: existing?.threadReplyCount
                )
                messageDao.insert(merged)
                val myId = sessionPrefs.getUserId()
                val fromSelf = myId != null && entity.userId == myId
                /**
                 * Реакции/редактирование: DDP `changed` по уже существующему id — без превью, unread и уведомления.
                 * Нельзя отсекать просто по [existing] != null: сообщение могло уже попасть в БД из синка,
                 * тогда приход только `added` — иначе непрочитанные считает сервер (subscriptions-changed),
                 * а локальное уведомление не покажется.
                 * Для ответов в треде тоже отсекаем дубликаты до отдельной ветки уведомления.
                 */
                if (ddpEvent == "changed" && existing != null) {
                    // Всегда обновляем запись в БД — Poll App и другие Apps Engine присылают
                    // «changed» с добавленными blocks/text уже после появления сообщения.
                    // Мы должны обновить blocksJson/text, но НЕ трогать счётчики unread/notify.
                    if (merged.blocksJson != existing.blocksJson || merged.text != existing.text) {
                        messageDao.insert(merged)
                        diag("streamMsg: changed id=${entity.id.take(8)}.. (blocks/text updated)")
                    } else {
                        diag("streamMsg: skip (DDP changed, no content diff, id=${entity.id.take(8)}..)")
                    }
                    return@launch
                }
                /** Повторный `added` по тому же id — не дублируем превью/unread/уведомление. */
                if (ddpEvent == "added" && existing != null) {
                    diag("streamMsg: skip duplicate added id=${entity.id.take(8)}..")
                    return@launch
                }

                /** Ответы треда (tmid) не должны попадать в превью/счётчик основной комнаты — только уведомление. */
                val isThreadReply = !entity.tmid.isNullOrBlank()
                if (isThreadReply) {
                    when (entity.msgType) {
                        "jitsi_call_started" -> {
                            if (!fromSelf) {
                                val room = roomDao.getRoom(entity.roomId)
                                incomingJitsiCallNotifier.showIncomingCall(
                                    roomId = entity.roomId,
                                    roomDisplayName = room?.displayName ?: room?.name ?: entity.username ?: "Чат",
                                    roomType = room?.type ?: "c",
                                    avatarPath = room?.avatarPath ?: "",
                                    callerName = entity.displayName ?: entity.username ?: "Участник"
                                )
                            }
                            return@launch
                        }
                        "jitsi_call_ended" -> {
                            incomingJitsiCallNotifier.cancelForRoom(entity.roomId)
                            return@launch
                        }
                    }
                    if (RocketChatMessageKinds.isRoomEventOrService(entity.msgType)) {
                        diag("streamMsg: thread reply, room event type=${entity.msgType}, skip notify")
                        return@launch
                    }
                    val mentionsArr = msgObj.optJSONArray("mentions")
                    val mentionNotify = mentionsArrayContainsCurrentUser(mentionsArr, myId)
                    val threadRoot = entity.tmid?.trim().orEmpty()
                    val participated = threadRoot.isNotEmpty() &&
                        threadParticipationPrefs.isParticipatingInThread(threadRoot)
                    if (!fromSelf &&
                        RoomNotificationPolicy.shouldNotifyForThreadReply(roomDao, entity.roomId) &&
                        (mentionNotify || participated)
                    ) {
                        val room = roomDao.getRoom(entity.roomId)
                        val roomTitle = room?.displayName ?: room?.name ?: entity.username ?: "Чат"
                        val sender = entity.displayName ?: entity.username ?: "Кто-то"
                        val preview = entity.text.ifBlank { "Вложение" }
                        messageNotifier.notifyNewChatMessage(
                            roomId = entity.roomId,
                            roomDisplayName = roomTitle,
                            roomType = room?.type ?: "c",
                            avatarPath = room?.avatarPath ?: "",
                            senderLabel = sender,
                            messagePreview = "В треде · $preview",
                            messageId = entity.id
                        )
                    }
                    return@launch
                }

                when (entity.msgType) {
                    "jitsi_call_started" -> {
                        if (!fromSelf) {
                            val room = roomDao.getRoom(entity.roomId)
                            incomingJitsiCallNotifier.showIncomingCall(
                                roomId = entity.roomId,
                                roomDisplayName = room?.displayName ?: room?.name ?: entity.username ?: "Чат",
                                roomType = room?.type ?: "c",
                                avatarPath = room?.avatarPath ?: "",
                                callerName = entity.displayName ?: entity.username ?: "Участник"
                            )
                        }
                        return@launch
                    }
                    "jitsi_call_ended" -> {
                        incomingJitsiCallNotifier.cancelForRoom(entity.roomId)
                        return@launch
                    }
                }

                if (RocketChatMessageKinds.isRoomEventOrService(entity.msgType)) {
                    diag("streamMsg: room event type=${entity.msgType}, skip preview/unread/notify")
                    return@launch
                }
                val isoTime = msgObj.opt("ts")?.let { formatIsoTime(it) }
                    ?: Instant.ofEpochMilli(entity.timestamp).toString()
                val unreadDelta = if (fromSelf) 0 else 1
                val mentionsArr = msgObj.optJSONArray("mentions")
                val mentionsDelta =
                    if (fromSelf) 0
                    else if (mentionsArrayContainsCurrentUser(mentionsArr, myId)) 1
                    else 0
                roomDao.updateLastMessage(
                    roomId = entity.roomId,
                    text = entity.text,
                    time = isoTime,
                    userId = entity.userId,
                    username = entity.username,
                    unreadDelta = unreadDelta,
                    mentionsDelta = mentionsDelta
                )
                if (!fromSelf) {
                    val room = roomDao.getRoom(entity.roomId)
                    if (RoomNotificationPolicy.shouldNotifyForRoom(roomDao, entity.roomId)) {
                        val roomTitle = room?.displayName ?: room?.name ?: entity.username ?: "Чат"
                        val sender = entity.displayName ?: entity.username ?: "Кто-то"
                        val preview = entity.text.ifBlank { "Вложение" }
                        messageNotifier.notifyNewChatMessage(
                            roomId = entity.roomId,
                            roomDisplayName = roomTitle,
                            roomType = room?.type ?: "c",
                            avatarPath = room?.avatarPath ?: "",
                            senderLabel = sender,
                            messagePreview = preview,
                            messageId = entity.id
                        )
                    }
                }
            } catch (e: Exception) {
                diag("streamMsg save ERROR: ${e.message}")
                lastError = "save: ${e.message}"
            }
        }
    }

    private fun handleStreamNotifyLogged(json: JSONObject) {
        val fields = json.optJSONObject("fields") ?: return
        val eventName = fields.optString("eventName")
        if (eventName != "user-status") return
        val args = fields.optJSONArray("args") ?: return
        if (args.length() == 0) return
        val first = args.opt(0)
        val userId: String
        val username: String?
        val rawStatus: Any?
        when (first) {
            is JSONArray -> {
                if (first.length() < 2) return
                userId = first.optString(0)
                username = first.optString(1).takeIf { it.isNotBlank() }
                rawStatus = if (first.length() > 2) first.get(2) else null
            }
            else -> {
                if (args.length() < 3) return
                userId = args.optString(0)
                username = args.optString(1).takeIf { it.isNotBlank() }
                rawStatus = args.opt(2)
            }
        }
        if (userId.isBlank()) return
        val status = UserPresenceStatus.fromRocketRealtime(rawStatus)
        if (status == UserPresenceStatus.UNKNOWN) return
        userPresenceStore.update(userId, username, status)
    }

    private fun handleUserNotification(json: JSONObject) {
        val fields = json.optJSONObject("fields")
        if (fields == null) { diag("userNotify: no fields"); return }
        val eventName = fields.optString("eventName")
        val args = fields.optJSONArray("args")
        if (args == null || args.length() == 0) { diag("userNotify: no args"); return }

        when {
            eventName.endsWith("/uiInteraction") -> {
                // Apps Engine прислал UI-событие (например, модальный диалог от Poll App)
                val arg = args.optJSONObject(0) ?: return
                val type = arg.optString("type")  // "modal.open", "modal.update", etc.
                if (type == "modal.open" || type == "modal.update") {
                    val triggerId = arg.optString("triggerId").takeIf { it.isNotBlank() } ?: return
                    val view = arg.optJSONObject("view") ?: return
                    val viewId = view.optString("id").takeIf { it.isNotBlank() } ?: return
                    diag("uiInteraction: type=$type triggerId=${triggerId.take(8)}.. viewId=${viewId.take(8)}..")
                    scope.launch {
                        _uiInteractionEvents.emit(UiInteractionEvent(triggerId, viewId, view.toString()))
                    }
                }
                return
            }
            eventName.endsWith("/subscriptions-changed") -> {
                val action = args.optString(0)
                val subscriptionData = args.optJSONObject(1)
                if (subscriptionData == null) { diag("userNotify: no subscription data"); return }

                val roomId = subscriptionData.optString("rid").ifBlank {
                    subscriptionData.optString("roomId")
                }
                /** Новая подписка (сабчат/обсуждение) — сразу подписываем stream-room-messages. */
                if (roomId.isNotBlank() && !subscriptions.containsKey(roomId)) {
                    subscribe(roomId)
                }
                // Частичный объект: unread / mentions / tunread* / настройки уведомлений.
                val hasUnread = subscriptionData.hasNonNull("unread")
                val hasMentions = subscriptionData.hasNonNull("userMentions")
                val hasTunreadFields =
                    subscriptionData.hasNonNull("tunread") ||
                        subscriptionData.hasNonNull("tunreadUser") ||
                        subscriptionData.hasNonNull("tunreadGroup")
                /** Избранное (звезда) в подписке Rocket.Chat — поле `f`. */
                val hasFavorite = subscriptionData.hasNonNull("f")
                val hasAlert = subscriptionData.hasNonNull("alert")
                val hasNotifFields =
                    subscriptionData.hasNonNull("disableNotifications") ||
                        subscriptionData.hasNonNull("mobilePushNotifications")
                if (!hasUnread && !hasMentions && !hasNotifFields && !hasTunreadFields && !hasFavorite && !hasAlert) {
                    diag("subscriptions-changed: action=$action room=${roomId.take(8)}.. (no relevant fields, skip DB)")
                    return
                }

                if (roomId.isNotBlank()) {
                    scope.launch {
                        try {
                            val existing = roomDao.getRoom(roomId)
                            if (hasUnread || hasMentions || hasAlert) {
                                val alertFlag = hasAlert && subscriptionData.optBoolean("alert")
                                val rawUnread = if (hasUnread) subscriptionData.optInt("unread") else null
                                val unreadCount = SubscriptionUnreadPolicy.effectiveUnread(
                                    rawUnread,
                                    alertFlag,
                                    existing?.unreadCount ?: 0,
                                    null
                                )
                                val userMentions = if (hasMentions) {
                                    subscriptionData.optInt("userMentions")
                                } else {
                                    existing?.userMentions ?: 0
                                }
                                diag("subscriptions-changed: action=$action room=${roomId.take(8)}.. unread=$unreadCount mentions=$userMentions")
                                roomDao.updateUnreadCounts(roomId, unreadCount, userMentions)
                                if (unreadCount == 0 && userMentions == 0) {
                                    messageNotifier.cancelNotificationsForRoom(roomId)
                                }
                            }
                            if (hasTunreadFields) {
                                val tCount = when {
                                    subscriptionData.hasNonNull("tunread") ->
                                        subscriptionData.optJSONArray("tunread")?.length() ?: 0
                                    subscriptionData.hasNonNull("tunreadUser") ->
                                        subscriptionData.optJSONArray("tunreadUser")?.length() ?: 0
                                    subscriptionData.hasNonNull("tunreadGroup") ->
                                        subscriptionData.optJSONArray("tunreadGroup")?.length() ?: 0
                                    else -> -1
                                }
                                if (tCount >= 0) {
                                    roomDao.updateThreadUnreadCount(roomId, tCount)
                                    diag("subscriptions-changed: threadUnreadCount=$tCount room=${roomId.take(8)}..")
                                }
                            }
                            if (hasNotifFields) {
                                val muted = parseMutedFromSubscriptionJson(subscriptionData)
                                roomDao.updateNotificationsMuted(roomId, muted)
                                diag("subscriptions-changed: notificationsMuted=$muted room=${roomId.take(8)}..")
                            }
                            if (hasFavorite && existing != null) {
                                val fav = subscriptionData.optBoolean("f")
                                roomDao.updateFavorite(roomId, fav)
                                diag("subscriptions-changed: favorite=$fav room=${roomId.take(8)}..")
                            }
                        } catch (e: Exception) {
                            diag("subscriptions-changed ERROR: ${e.message}")
                        }
                    }
                }
            }
            else -> diag("userNotify: unknown event $eventName")
        }
    }

    /** Согласовано с логикой merge уведомлений в RoomRepository. */
    private fun parseMutedFromSubscriptionJson(o: JSONObject): Boolean {
        if (o.has("disableNotifications") && !o.isNull("disableNotifications")) {
            when (val v = o.get("disableNotifications")) {
                is Boolean -> if (v) return true
                is String -> if (v == "true" || v == "1") return true
                is Number -> if (v.toInt() != 0) return true
            }
        }
        if (o.optString("mobilePushNotifications", "") == "nothing") return true
        return false
    }

    private fun formatIsoTime(ts: Any?): String? {
        return when (ts) {
            is JSONObject -> {
                val millis = ts.optLong("\$date", 0L)
                if (millis > 0) Instant.ofEpochMilli(millis).toString() else null
            }
            is String -> ts
            is Number -> Instant.ofEpochMilli(ts.toLong()).toString()
            else -> null
        }
    }

    /** Плоский список вложений, включая `attachments` внутри вложений (пересылка с файлом). */
    private fun collectNestedAttachments(arr: JSONArray?): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val att = arr.optJSONObject(i) ?: continue
            out.add(att)
            out.addAll(collectNestedAttachments(att.optJSONArray("attachments")))
        }
        return out
    }

    private fun parseMessage(msgObj: JSONObject): MessageEntity? {
        val id = msgObj.optString("_id").takeIf { it.isNotBlank() } ?: return null
        val rid = msgObj.optString("rid").takeIf { it.isNotBlank() } ?: return null
        val msgTypeRaw = msgObj.optString("t").takeIf { it.isNotBlank() }
        if (msgObj.optBoolean("_hidden", false) && msgTypeRaw == null) return null
        val rawMsg = msgObj.optString("msg", "")
        val u = msgObj.optJSONObject("u")
        val userId = u?.optString("_id")?.takeIf { it.isNotBlank() } ?: ""

        val ts = parseDdpTimestamp(msgObj.opt("ts"))
        val username = u?.optString("username", "")?.takeIf { it.isNotBlank() }
        val displayName = u?.optString("name", "")?.takeIf { it.isNotBlank() }
        val editedAt = msgObj.opt("editedAt")
        val parent = msgObj.optString("parent", "").takeIf { it.isNotBlank() }
        val tmidStr = msgObj.optString("tmid", "").takeIf { it.isNotBlank() }
        val dridStr = msgObj.optString("drid", "").takeIf { it.isNotBlank() }
        val tcountVal = msgObj.optInt("tcount", 0).takeIf { it > 0 }

        var quoteText: String? = null
        var quoteAuthor: String? = null
        var quoteChainJson: String? = null
        var quotedMessageId: String? = null
        var imageUrl: String? = null
        var fullImageUrl: String? = null
        var fileName: String? = null
        var fileUrl: String? = null
        var fileType: String? = null
        var fileDescription: String? = null
        var fileSizeBytes: Long? = null
        val topAttachments = msgObj.optJSONArray("attachments")
        val quoteSegments = collectQuoteSegmentsFromJsonArray(topAttachments)
        if (quoteSegments.isNotEmpty()) {
            quoteChainJson = quoteSegmentsToJson(quoteSegments)
            quoteText = quoteSegments.first().text.takeIf { it.isNotBlank() }
            quoteAuthor = quoteSegments.first().author
            val firstQuote = findFirstQuoteAttachmentJson(topAttachments)
            quotedMessageId = RocketChatMessageIds.resolveQuotedId(
                firstQuote?.optString("message_link", "")?.takeIf { it.isNotBlank() },
                rawMsg
            )
        }
        val flatAttachments = collectNestedAttachments(topAttachments)
        for (att in flatAttachments) {
            if (imageUrl == null && att.has("image_url")) {
                imageUrl = att.optString("image_url", "").takeIf { it.isNotBlank() }
                fullImageUrl = att.optString("title_link", "").takeIf { it.isNotBlank() } ?: imageUrl
                fileName = att.optString("title", "").takeIf { it.isNotBlank() } ?: fileName
                fileDescription = att.optString("description", "").takeIf { it.isNotBlank() }
                    ?: att.optString("text", "").takeIf { it.isNotBlank() }
                    ?: fileDescription
                if (att.has("image_size")) {
                    fileSizeBytes = att.optLong("image_size", 0L).takeIf { it > 0L }
                }
                fileType = att.optString("image_type", "").takeIf { it.isNotBlank() }
                    ?: att.optString("type", "").takeIf { it.isNotBlank() } ?: fileType
            }
            if (fileName == null && att.has("title_link") && !att.has("image_url")) {
                fileName = att.optString("title", "").takeIf { it.isNotBlank() }
                fileUrl = att.optString("title_link", "").takeIf { it.isNotBlank() }
                fileType = att.optString("type", "").takeIf { it.isNotBlank() }
                fileDescription = att.optString("description", "").takeIf { it.isNotBlank() } ?: fileDescription
                if (att.has("image_size")) {
                    fileSizeBytes = att.optLong("image_size", 0L).takeIf { it > 0L }
                }
            }
        }

        val cleanedMsg = stripRocketQuotePermalinkMarkdown(rawMsg)

        // UIKit-блоки: опросы и интерактивные сообщения (Poll App, приходят в blocks или attachments[].blocks)
        var blocksArray = msgObj.optJSONArray("blocks")
        if (blocksArray == null) {
            val atts = msgObj.optJSONArray("attachments")
            if (atts != null && atts.length() > 0) {
                for (i in 0 until atts.length()) {
                    val att = atts.optJSONObject(i)
                    if (att != null && att.has("blocks")) {
                        blocksArray = att.optJSONArray("blocks")
                        break
                    }
                }
            }
        }
        val blocksJson: String? = if (blocksArray != null && blocksArray.length() > 0)
            blocksArray.toString() else null

        var reactionsJson: String? = null
        val reactionsObj = msgObj.optJSONObject("reactions")
        if (reactionsObj != null && reactionsObj.length() > 0) {
            val result = JSONObject()
            val keys = reactionsObj.keys()
            while (keys.hasNext()) {
                val emoji = keys.next()
                val info = reactionsObj.optJSONObject(emoji)
                val usernames = info?.optJSONArray("usernames")
                if (usernames != null) {
                    val arr = org.json.JSONArray()
                    for (j in 0 until usernames.length()) arr.put(usernames.optString(j))
                    result.put(emoji, arr)
                }
            }
            if (result.length() > 0) reactionsJson = result.toString()
        }

        val mentionsJson = mentionsArrayToJsonString(msgObj.optJSONArray("mentions"))

        return MessageEntity(
            id = id, roomId = rid, text = cleanedMsg, timestamp = ts,
            userId = userId, username = username, displayName = displayName,
            isEdited = editedAt != null, parentId = parent,
            tmid = tmidStr,
            drid = dridStr,
            threadReplyCount = tcountVal,
            quoteText = quoteText, quoteAuthor = quoteAuthor,
            quoteChainJson = quoteChainJson,
            quotedMessageId = quotedMessageId,
            imageUrl = imageUrl, fullImageUrl = fullImageUrl,
            fileName = fileName, fileUrl = fileUrl, fileType = fileType,
            fileDescription = fileDescription,
            fileSizeBytes = fileSizeBytes,
            reactions = reactionsJson,
            mentionsJson = mentionsJson,
            syncStatus = MessageEntity.SYNC_SYNCED,
            msgType = msgTypeRaw,
            blocksJson = blocksJson
        )
    }

    /**
     * Отправляет голос за вариант опроса через DDP-метод `ui.blockAction`.
     * Вызывается из [com.rocketlauncher.presentation.chat.ChatViewModel.votePoll].
     *
     * @param msgId    ID сообщения с опросом
     * @param roomId   ID комнаты
     * @param appId    appId из блока (обычно "poll" или "poll-plus")
     * @param blockId  blockId кнопки-варианта
     * @param actionId actionId кнопки (обычно "vote")
     * @param value    значение варианта (индекс строкой: "0", "1", …)
     */
    fun sendBlockAction(
        msgId: String,
        roomId: String,
        appId: String,
        blockId: String,
        actionId: String,
        value: String
    ) {
        val id = "ba-${System.currentTimeMillis()}"
        // triggerId обязателен для Apps Engine, генерируем уникальный
        val triggerId = "$id-trigger"
        val payload = buildString {
            append("{\"msg\":\"method\",\"method\":\"ui.blockAction\",\"id\":\"$id\",")
            append("\"params\":[{")
            append("\"actionId\":\"$actionId\",")
            append("\"appId\":\"$appId\",")
            append("\"value\":\"$value\",")
            append("\"blockId\":\"$blockId\",")
            append("\"mid\":\"$msgId\",")
            append("\"rid\":\"$roomId\",")
            append("\"triggerId\":\"$triggerId\",")
            append("\"container\":{\"type\":\"message\",\"id\":\"$msgId\"}")
            append("}]}")
        }
        val ws = webSocket
        if (ws != null) {
            ws.send(payload)
            diag("sendBlockAction: appId=$appId actionId=$actionId blockId=${blockId.take(8)}.. value=$value msg=${msgId.take(8)}..")
        } else {
            diag("sendBlockAction: no active webSocket")
        }
    }


    private fun parseDdpTimestamp(ts: Any?): Long {
        if (ts == null) return System.currentTimeMillis()
        val raw = when (ts) {
            is JSONObject -> ts.optLong("\$date", System.currentTimeMillis())
            is Number -> ts.toLong()
            is String -> try {
                return Instant.parse(ts).toEpochMilli()
            } catch (_: Exception) { return System.currentTimeMillis() }
            else -> return System.currentTimeMillis()
        }
        return if (raw < 1_000_000_000_000L) raw * 1000 else raw
    }
}
