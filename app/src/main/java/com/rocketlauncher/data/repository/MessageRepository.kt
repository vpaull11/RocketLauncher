package com.rocketlauncher.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.rocketlauncher.data.api.ApiProvider
import com.rocketlauncher.data.api.RocketChatApi
import com.rocketlauncher.data.db.MessageDao
import com.rocketlauncher.data.db.MessageEntity
import com.rocketlauncher.data.db.RoomDao
import com.rocketlauncher.data.db.RoomEntity
import com.rocketlauncher.data.dto.MessageDto
import com.rocketlauncher.data.dto.ReadReceiptItemDto
import com.rocketlauncher.data.dto.ReadReceiptReaderRow
import com.rocketlauncher.data.mentions.mentionsFromDtoList
import com.rocketlauncher.data.dto.AttachmentDto
import com.rocketlauncher.data.dto.PostMessageAttachment
import com.rocketlauncher.data.dto.PostMessageNestedFileAttachment
import com.rocketlauncher.data.dto.PinnedBannerItem
import com.rocketlauncher.data.dto.PostMessageRequest
import com.rocketlauncher.data.dto.RoomsMediaConfirmRequest
import com.rocketlauncher.data.dto.RoomsMediaUploadResponse
import com.rocketlauncher.data.dto.MarkReadRequest
import com.rocketlauncher.data.dto.ReactRequest
import com.rocketlauncher.data.message.collectQuoteSegmentsFromDto
import com.rocketlauncher.data.message.findFirstQuoteAttachmentDto
import com.rocketlauncher.data.message.quoteSegmentsToJson
import com.rocketlauncher.data.message.stripRocketQuotePermalinkMarkdown
import com.rocketlauncher.data.mentions.RocketChatMessageIds
import com.rocketlauncher.data.notifications.MessageNotifier
import com.rocketlauncher.data.notifications.ThreadParticipationPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MessageRepo"

@Singleton
class MessageRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val messageDao: MessageDao,
    private val roomDao: RoomDao,
    private val messageNotifier: MessageNotifier,
    private val threadParticipationPrefs: ThreadParticipationPrefs,
    @ApplicationContext private val context: Context
) {

    fun observeMessages(roomId: String): Flow<List<MessageEntity>> =
        messageDao.observeMessages(roomId)

    fun observeThreadMessages(tmid: String): Flow<List<MessageEntity>> =
        messageDao.observeThreadMessages(tmid)

    /** Первая страница истории; @return число сообщений (для пагинации «старые»). */
    suspend fun loadMessages(roomId: String, roomType: String): Int {
        val api = apiProvider.getApi() ?: return 0
        return try {
            val response = when (roomType) {
                "d" -> api.getDmMessages(roomId = roomId)
                "p" -> api.getGroupMessages(roomId = roomId)
                else -> api.getChannelMessages(roomId = roomId)
            }
            if (response.success) {
                val entities = response.messages.map { dto ->
                    mergeReadReceiptsFromDb(dto.toEntity(roomId))
                }
                messageDao.insertAll(entities)
                entities.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMessages failed for $roomId: ${e.message}")
            throw e
        }
    }

    /**
     * Догружает историю комнаты с API, пока [messageId] не появится в БД или выборка не исчерпается.
     * Для перехода к сообщению из поиска, когда его ещё нет локально.
     */
    suspend fun ensureMessageInRoomLoaded(
        roomId: String,
        roomType: String,
        messageId: String
    ): Boolean {
        if (messageId.isBlank()) return false
        var offset = 0
        repeat(500) {
            val existing = messageDao.getById(messageId)
            if (existing != null && existing.roomId == roomId) return true
            val n = loadMessagesPage(roomId, roomType, offset = offset, count = 50)
            if (n == 0) return false
            offset += n
            delay(50)
        }
        return messageDao.getById(messageId)?.roomId == roomId
    }

    suspend fun getMessageById(id: String): MessageEntity? = messageDao.getById(id)

    /**
     * Закреплённые сообщения комнаты (новее закрепления — выше в списке для баннера).
     */
    suspend fun loadPinnedBannerItems(roomId: String): List<PinnedBannerItem> {
        val api = apiProvider.getApi() ?: return emptyList()
        return try {
            val resp = try {
                api.getPinnedMessages(roomId = roomId, count = 50, offset = 0, sort = """{"pinnedAt":-1}""")
            } catch (_: Exception) {
                api.getPinnedMessages(roomId = roomId, count = 50, offset = 0, sort = """{"ts":-1}""")
            }
            if (!resp.success) return emptyList()
            val sorted = resp.messages.sortedWith(
                compareByDescending<MessageDto> { dto ->
                    val p = dto.pinnedAt?.trim()?.takeIf { it.isNotEmpty() }
                    if (p != null) parseTimestamp(p) else parseTimestamp(dto.ts)
                }
            )
            sorted.map { dto ->
                PinnedBannerItem(
                    messageId = dto._id,
                    previewText = previewTextForPinnedBanner(dto.msg)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPinnedBannerItems: ${e.message}")
            emptyList()
        }
    }

    private fun previewTextForPinnedBanner(msg: String): String {
        val t = stripRocketQuotePermalinkMarkdown(msg).trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return "Закреплённое сообщение"
        return if (t.length > 140) t.take(140) + "…" else t
    }

    suspend fun loadMessagesPage(
        roomId: String,
        roomType: String,
        offset: Int,
        count: Int = 50
    ): Int {
        val api = apiProvider.getApi() ?: return 0
        return try {
            val response = when (roomType) {
                "d" -> api.getDmMessages(roomId = roomId, count = count, offset = offset)
                "p" -> api.getGroupMessages(roomId = roomId, count = count, offset = offset)
                else -> api.getChannelMessages(roomId = roomId, count = count, offset = offset)
            }
            if (response.success) {
                val entities = response.messages.map { dto ->
                    mergeReadReceiptsFromDb(dto.toEntity(roomId))
                }
                messageDao.insertAll(entities)
                entities.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMessagesPage offset=$offset: ${e.message}")
            0
        }
    }

    /** Самое раннее сообщение за календарный день [startInclusive, endExclusive) (порядок в БД — по возрастанию времени). */
    suspend fun findEarliestMessageIdOnCalendarDay(
        roomId: String,
        startInclusive: Long,
        endExclusive: Long
    ): String? {
        val msgs = messageDao.getMessages(roomId)
        return msgs.firstOrNull { it.timestamp >= startInclusive && it.timestamp < endExclusive }?.id
    }

    /** Минимальный timestamp среди сохранённых сообщений комнаты (или null). */
    suspend fun getOldestStoredMessageTimestamp(roomId: String): Long? =
        messageDao.getMessages(roomId).firstOrNull()?.timestamp

    /**
     * Вставка сообщений из REST (в т.ч. [loadMissedMessages]): сохраняет tmid/drid и т.д.
     */
    suspend fun insertMessagesFromDtos(dtos: List<MessageDto>) {
        if (dtos.isEmpty()) return
        val entities = dtos.map { dto ->
            mergeReadReceiptsFromDb(dto.toEntity(dto.rid))
        }
        messageDao.insertAll(entities)
    }

    /** Первая страница треда; @return число сообщений для пагинации. */
    suspend fun loadThreadMessages(tmid: String, roomId: String): Int {
        val api = apiProvider.getApi() ?: return 0
        return try {
            val response = api.getThreadMessages(tmid = tmid)
            if (response.success) {
                val entities = response.messages.map { dto ->
                    mergeReadReceiptsFromDb(dto.toEntity(roomId))
                }
                messageDao.insertAll(entities)
                entities.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadThreadMessages failed for tmid=$tmid: ${e.message}")
            throw e
        }
    }

    /** Постраничная догрузка ответов треда ([chat.getThreadMessages]). */
    suspend fun loadThreadMessagesPage(
        tmid: String,
        roomId: String,
        offset: Int,
        count: Int = 50
    ): Int {
        val api = apiProvider.getApi() ?: return 0
        return try {
            val response = api.getThreadMessages(tmid = tmid, count = count, offset = offset)
            if (response.success) {
                val entities = response.messages.map { dto ->
                    mergeReadReceiptsFromDb(dto.toEntity(roomId))
                }
                messageDao.insertAll(entities)
                entities.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadThreadMessagesPage tmid=$tmid offset=$offset: ${e.message}")
            0
        }
    }

    suspend fun ensureMessageInThreadLoaded(tmid: String, roomId: String, messageId: String): Boolean {
        if (messageId.isBlank()) return false
        var offset = 0
        repeat(300) {
            if (messageDao.getById(messageId) != null) return true
            val n = loadThreadMessagesPage(tmid, roomId, offset)
            if (n == 0) return messageDao.getById(messageId) != null
            offset += n
            delay(50)
        }
        return messageDao.getById(messageId) != null
    }

    private suspend fun mergeReadReceiptsFromDb(entity: MessageEntity): MessageEntity {
        val old = messageDao.getById(entity.id) ?: return entity
        return if (old.readByOthers != null) entity.copy(readByOthers = old.readByOthers) else entity
    }

    suspend fun sendMessage(roomId: String, text: String, tmid: String? = null): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val response = api.postMessage(
                PostMessageRequest(roomId = roomId, text = text, tmid = tmid)
            )
            if (response.success && response.message != null) {
                messageDao.insert(response.message!!.toEntity(roomId))
                val tm = tmid?.trim()?.takeIf { it.isNotEmpty() }
                if (tm != null) {
                    threadParticipationPrefs.markParticipatedInThread(tm)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Failed to send"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Пересылка через [chat.postMessage] в форме **официального клиента** Rocket.Chat:
     * `msg` = `"[ ](permalink)"`, `parseUrls: true`, внешнее вложение с `message_link`/`ts`/автором,
     * при медиа — `text: ""` и **вложенный** `attachments[]` с `type: file`, `description` (подпись);
     * `image_url` / `title_link` / `author_icon` — **абсолютные** https URL (REST иначе «Invalid href value provided»).
     */
    suspend fun forwardMessage(
        targetRoomId: String,
        sourceMessage: MessageEntity,
        messagePermalink: String,
        serverBaseUrl: String?
    ): Result<Unit> {
        val api = apiProvider.getApi()
            ?: return Result.failure(
                ForwardMessageDebugException(
                    message = "Не авторизован",
                    debugReport = "apiProvider.getApi() вернул null (нет сессии)."
                )
            )
        var lastDebugCtx: String? = null
        return try {
            val author = sourceMessage.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: sourceMessage.username?.trim()?.takeIf { it.isNotEmpty() }
            val authorIcon = sourceMessage.username?.trim()?.takeIf { it.isNotEmpty() }?.let { u ->
                // REST chat.postMessage часто отклоняет относительные href → «Invalid href value provided»
                absoluteUrl(serverBaseUrl, "/avatar/$u")
            }
            val msgBody = "[ ]($messagePermalink)"
            val hasImage = !sourceMessage.imageUrl.isNullOrBlank() ||
                !sourceMessage.fullImageUrl.isNullOrBlank()
            val hasFileOnly = !hasImage && !sourceMessage.fileUrl.isNullOrBlank()
            val caption = forwardNestedDescription(sourceMessage, hasMedia = hasImage || hasFileOnly)
                ?.trim()
                .orEmpty()

            val attachment = when {
                hasImage -> {
                    val imgRel = sourceMessage.imageUrl ?: sourceMessage.fullImageUrl
                    val linkRel = sourceMessage.fullImageUrl ?: sourceMessage.imageUrl
                    val nested = PostMessageNestedFileAttachment(
                        title = sourceMessage.fileName?.takeIf { it.isNotBlank() } ?: "image",
                        title_link = absoluteUrl(serverBaseUrl, linkRel)
                            ?: toRocketAttachmentPath(serverBaseUrl, linkRel),
                        title_link_download = true,
                        image_url = absoluteUrl(serverBaseUrl, imgRel)
                            ?: toRocketAttachmentPath(serverBaseUrl, imgRel),
                        image_type = sourceMessage.fileType,
                        image_size = sourceMessage.fileSizeBytes,
                        type = "file",
                        description = caption.takeIf { it.isNotEmpty() }
                    )
                    PostMessageAttachment(
                        message_link = messagePermalink,
                        ts = millisToRocketAttachmentTs(sourceMessage.timestamp),
                        text = "",
                        author_name = author,
                        author_icon = authorIcon,
                        attachments = listOf(nested)
                    )
                }
                hasFileOnly -> {
                    val nested = PostMessageNestedFileAttachment(
                        title = sourceMessage.fileName,
                        title_link = absoluteUrl(serverBaseUrl, sourceMessage.fileUrl)
                            ?: toRocketAttachmentPath(serverBaseUrl, sourceMessage.fileUrl),
                        title_link_download = true,
                        image_size = sourceMessage.fileSizeBytes,
                        type = "file",
                        description = caption.takeIf { it.isNotEmpty() }
                    )
                    PostMessageAttachment(
                        message_link = messagePermalink,
                        ts = millisToRocketAttachmentTs(sourceMessage.timestamp),
                        text = "",
                        author_name = author,
                        author_icon = authorIcon,
                        attachments = listOf(nested)
                    )
                }
                else -> {
                    PostMessageAttachment(
                        message_link = messagePermalink,
                        ts = millisToRocketAttachmentTs(sourceMessage.timestamp),
                        text = sourceMessage.text.trim().takeIf { it.isNotEmpty() } ?: "\u00A0",
                        author_name = author,
                        author_icon = authorIcon,
                        attachments = emptyList()
                    )
                }
            }

            lastDebugCtx = forwardDebugContext(
                targetRoomId = targetRoomId,
                source = sourceMessage,
                permalink = messagePermalink,
                msgBody = msgBody,
                attachment = attachment,
                serverBaseUrl = serverBaseUrl
            )

            val response = api.postMessage(
                PostMessageRequest(
                    roomId = targetRoomId,
                    text = msgBody,
                    parseUrls = true,
                    attachments = listOf(attachment)
                )
            )
            if (response.success) {
                response.message?.let { messageDao.insert(it.toEntity(targetRoomId)) }
                Result.success(Unit)
            } else {
                val report = buildString {
                    appendLine(lastDebugCtx)
                    appendLine("--- ответ API (тело десериализовано) ---")
                    appendLine("success=false")
                    appendLine("error=${response.error}")
                    appendLine("errorType=${response.errorType}")
                }
                Log.e(TAG, report)
                Result.failure(
                    ForwardMessageDebugException(
                        message = response.error ?: "Пересылка отклонена сервером",
                        debugReport = report
                    )
                )
            }
        } catch (e: HttpException) {
            val head = lastDebugCtx ?: forwardDebugContextForError(
                targetRoomId = targetRoomId,
                source = sourceMessage,
                permalink = messagePermalink,
                serverBaseUrl = serverBaseUrl
            )
            val report = buildString {
                appendLine(head)
                appendLine("--- HTTP ---")
                appendLine("code=${e.code()}")
                appendLine("url=${e.response()?.raw()?.request?.url}")
                appendLine(
                    "body=${
                        try {
                            e.response()?.errorBody()?.use { it.string() } ?: "(пусто)"
                        } catch (read: Exception) {
                            "(не удалось прочитать: ${read.message})"
                        }
                    }"
                )
            }
            Log.e(TAG, report, e)
            Result.failure(
                ForwardMessageDebugException(
                    message = "HTTP ${e.code()} при пересылке",
                    debugReport = report
                )
            )
        } catch (e: Exception) {
            val head = lastDebugCtx ?: forwardDebugContextForError(
                targetRoomId = targetRoomId,
                source = sourceMessage,
                permalink = messagePermalink,
                serverBaseUrl = serverBaseUrl
            )
            val report = buildString {
                appendLine(head)
                appendLine("--- исключение ---")
                appendLine("exception=${e.javaClass.name}")
                appendLine("message=${e.message}")
                appendLine("--- stack (до 24 строк) ---")
                appendLine(e.stackTraceToString().lineSequence().take(24).joinToString("\n"))
            }
            Log.e(TAG, report, e)
            Result.failure(
                ForwardMessageDebugException(
                    message = e.message ?: "Ошибка пересылки",
                    debugReport = report
                )
            )
        }
    }

    /**
     * Загрузка файла в комнату ([rooms.upload]).
     * @param threadMessageId если не null — сообщение попадёт в тред ([tmid]).
     */
    suspend fun uploadFile(
        roomId: String,
        uri: Uri,
        fileNameOverride: String?,
        message: String?,
        description: String?,
        threadMessageId: String?
    ): Result<Unit> {
        val api = apiProvider.getApi()
            ?: return Result.failure(
                FileUploadDebugException("Не авторизован", "apiProvider.getApi() == null")
            )
        if (roomId.isBlank()) {
            return Result.failure(
                FileUploadDebugException("Пустой roomId", "roomId пустой — загрузка невозможна.")
            )
        }
        val fileName = fileNameOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: queryUriDisplayName(uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val mediaType = mime.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val safeName = fileName
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .take(220)
            .ifBlank { "upload" }
        val debugBase = uploadDebugHeader(roomId, fileName, mime)
        val plain = "text/plain; charset=utf-8".toMediaTypeOrNull()
        val msgPart = message?.trim()?.takeIf { it.isNotEmpty() }?.toRequestBody(plain)
        val descPart = description?.trim()?.takeIf { it.isNotEmpty() }?.toRequestBody(plain)
        val tmidPart = threadMessageId?.trim()?.takeIf { it.isNotEmpty() }?.toRequestBody(plain)

        return try {
            val part = buildUploadMultipartPart(uri, safeName, mediaType)
            try {
                val response = api.roomsUpload(roomId, part, msgPart, descPart, tmidPart)
                if (response.success && response.message != null) {
                    messageDao.insert(response.message!!.toEntity(roomId))
                    threadMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        threadParticipationPrefs.markParticipatedInThread(it)
                    }
                    Result.success(Unit)
                } else {
                    val report = buildString {
                        appendLine(debugBase)
                        appendLine("--- ответ rooms.upload ---")
                        appendLine("success=false")
                        appendLine("error=${response.error}")
                        appendLine("errorType=${response.errorType}")
                    }
                    Log.e(TAG, report)
                    Result.failure(
                        FileUploadDebugException(
                            response.error ?: "Сервер отклонил загрузку (rooms.upload)",
                            report
                        )
                    )
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    val note = debugBase + "\n---\nrooms.upload → HTTP 404 (часто неверный путь API или старая/новая версия сервера). Пробуем rooms.media + rooms.mediaConfirm.\n"
                    uploadViaMediaPipeline(
                        api = api,
                        roomId = roomId,
                        uri = uri,
                        safeName = safeName,
                        mediaType = mediaType,
                        message = message,
                        description = description,
                        threadMessageId = threadMessageId,
                        debugBase = note
                    )
                } else {
                    Result.failure(httpToFileUploadException(e, debugBase, "rooms.upload"))
                }
            }
        } catch (e: FileUploadDebugException) {
            Result.failure(e)
        } catch (e: Exception) {
            val report = buildString {
                appendLine(debugBase)
                appendLine("--- исключение ---")
                appendLine("${e.javaClass.name}: ${e.message}")
                appendLine(e.stackTraceToString().lineSequence().take(20).joinToString("\n"))
            }
            Log.e(TAG, report, e)
            Result.failure(
                FileUploadDebugException(e.message ?: "Ошибка загрузки файла", report)
            )
        }
    }

    private fun uploadDebugHeader(roomId: String, fileName: String, mime: String): String = buildString {
        appendLine("=== RocketLauncher — отладка загрузки файла ===")
        appendLine("client=com.rocketlauncher (Android)")
        appendLine("roomId=$roomId")
        appendLine("fileName=$fileName")
        appendLine("mime=$mime")
    }

    private fun httpToFileUploadException(
        e: HttpException,
        debugBase: String,
        endpoint: String
    ): FileUploadDebugException {
        val errBody = try {
            e.response()?.errorBody()?.use { it.string() } ?: "(пусто)"
        } catch (_: Exception) {
            "(не удалось прочитать тело ответа)"
        }
        val report = buildString {
            appendLine(debugBase)
            appendLine("--- HTTP ($endpoint) ---")
            appendLine("code=${e.code()}")
            appendLine("url=${e.response()?.raw()?.request?.url}")
            appendLine("body=$errBody")
        }
        Log.e(TAG, report, e)
        return FileUploadDebugException("HTTP ${e.code()} при загрузке ($endpoint)", report)
    }

    private fun buildUploadMultipartPart(uri: Uri, safeName: String, mediaType: MediaType): MultipartBody.Part {
        val body = object : RequestBody() {
            override fun contentType() = mediaType
            override fun writeTo(sink: okio.BufferedSink) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть файл")
                input.source().use { src -> sink.writeAll(src) }
            }
        }
        return MultipartBody.Part.createFormData("file", safeName, body)
    }

    private suspend fun uploadViaMediaPipeline(
        api: RocketChatApi,
        roomId: String,
        uri: Uri,
        safeName: String,
        mediaType: MediaType,
        message: String?,
        description: String?,
        threadMessageId: String?,
        debugBase: String
    ): Result<Unit> {
        val part = buildUploadMultipartPart(uri, safeName, mediaType)
        val mediaResp: RoomsMediaUploadResponse = try {
            api.roomsMediaUpload(roomId, part)
        } catch (e: HttpException) {
            return Result.failure(httpToFileUploadException(e, debugBase, "rooms.media"))
        }
        if (!mediaResp.success || mediaResp.file == null) {
            val report = buildString {
                appendLine(debugBase)
                appendLine("--- ответ rooms.media ---")
                appendLine("success=${mediaResp.success}")
                appendLine("error=${mediaResp.error}")
            }
            Log.e(TAG, report)
            return Result.failure(
                FileUploadDebugException(
                    mediaResp.error ?: "rooms.media: файл не принят",
                    report
                )
            )
        }
        val confirmBody = RoomsMediaConfirmRequest(
            msg = message?.trim()?.takeIf { it.isNotEmpty() },
            tmid = threadMessageId?.trim()?.takeIf { it.isNotEmpty() },
            description = description?.trim()?.takeIf { it.isNotEmpty() }
        )
        return try {
            val response = api.roomsMediaConfirm(roomId, mediaResp.file.id, confirmBody)
            if (response.success && response.message != null) {
                messageDao.insert(response.message!!.toEntity(roomId))
                threadMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    threadParticipationPrefs.markParticipatedInThread(it)
                }
                Result.success(Unit)
            } else {
                val report = buildString {
                    appendLine(debugBase)
                    appendLine("--- ответ rooms.mediaConfirm ---")
                    appendLine("success=false")
                    appendLine("error=${response.error}")
                    appendLine("errorType=${response.errorType}")
                }
                Log.e(TAG, report)
                Result.failure(
                    FileUploadDebugException(
                        response.error ?: "rooms.mediaConfirm отклонён",
                        report
                    )
                )
            }
        } catch (e: HttpException) {
            Result.failure(httpToFileUploadException(e, debugBase, "rooms.mediaConfirm"))
        }
    }

    private fun queryUriDisplayName(uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val n = c.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }
                    if (n != null) return n
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: "file"
    }

    fun observeAllRooms(): Flow<List<RoomEntity>> = roomDao.observeAllRooms()

    /**
     * Пометка прочитанным только после успешного ответа сервера [subscriptions.read],
     * чтобы локальное состояние не расходилось с сервером и не «обнулялись» другие чаты при ошибке.
     */
    suspend fun markAsRead(roomId: String, readThreads: Boolean = false) {
        if (roomId.isBlank()) {
            Log.w(TAG, "markAsRead: empty roomId, skip")
            return
        }
        val api = apiProvider.getApi() ?: return
        try {
            val response = api.markRoomAsRead(
                MarkReadRequest(rid = roomId, readThreads = readThreads.takeIf { it })
            )
            response.body()?.close()
            if (response.isSuccessful) {
                roomDao.markAsRead(roomId)
                messageNotifier.cancelNotificationsForRoom(roomId)
            } else {
                val err = response.errorBody()?.use { it.string() }
                Log.w(TAG, "markAsRead: HTTP ${response.code()} for $roomId: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "markAsRead failed for $roomId: ${e.message}", e)
        }
    }

    /**
     * Обновляет [MessageEntity.readByOthers] для последних своих сообщений в комнате
     * (если сервер отдаёт [chat.getMessageReadReceipts]).
     */
    suspend fun refreshReadReceiptsForOwnMessages(roomId: String, myUserId: String, messages: List<MessageEntity>) {
        val api = apiProvider.getApi() ?: return
        val own = messages
            .asSequence()
            .filter { it.roomId == roomId && it.userId == myUserId && it.syncStatus == MessageEntity.SYNC_SYNCED }
            .sortedByDescending { it.timestamp }
            .take(16)
            .toList()
        if (own.isEmpty()) return
        for (msg in own) {
            try {
                val resp = api.getMessageReadReceipts(msg.id)
                if (!resp.success) continue
                val others = resp.receipts.count { receipt ->
                    val uid = receipt.userIdResolved()
                    uid != null && uid != myUserId
                }
                messageDao.updateReadByOthers(msg.id, others > 0)
            } catch (e: HttpException) {
                if (e.code() != 404 && e.code() != 403) {
                    Log.w(TAG, "readReceipts HTTP ${e.code()} for ${msg.id}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "readReceipts ${msg.id}: ${e.message}")
            }
        }
    }

    private fun ReadReceiptItemDto.userIdResolved(): String? =
        userId ?: user?._id

    /**
     * Пользователи, прочитавшие сообщение ([RocketChatApi.getMessageReadReceipts]).
     * На серверах без read receipts — 403/404.
     */
    suspend fun getReadReceiptReaders(messageId: String): Result<List<ReadReceiptReaderRow>> {
        val api = apiProvider.getApi() ?: return Result.failure(IllegalStateException("Не авторизован"))
        return try {
            val resp = api.getMessageReadReceipts(messageId)
            if (!resp.success) {
                return Result.failure(Exception(resp.error ?: "Сервер отклонил запрос"))
            }
            val rows = resp.receipts.mapNotNull { r ->
                val uid = r.userIdResolved() ?: return@mapNotNull null
                val u = r.user
                val label = listOfNotNull(
                    u?.name?.trim()?.takeIf { it.isNotEmpty() },
                    u?.username?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }
                ).firstOrNull() ?: uid
                ReadReceiptReaderRow(userId = uid, displayName = label)
            }
            Result.success(rows)
        } catch (e: HttpException) {
            when (e.code()) {
                403, 404 -> Result.failure(
                    Exception("Квитанции отключены на сервере или недоступны")
                )
                else -> Result.failure(Exception("HTTP ${e.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val response = api.deleteMessage(
                mapOf(
                    "roomId" to roomId,
                    "msgId" to messageId
                )
            )
            if (response.success) {
                messageDao.deleteById(messageId)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Delete failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteMessage: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateMessage(roomId: String, messageId: String, newText: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val response = api.updateMessage(
                mapOf(
                    "roomId" to roomId,
                    "msgId" to messageId,
                    "text" to newText
                )
            )
            if (response.success) {
                messageDao.updateMessageText(messageId, newText, edited = true)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Update failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateMessage: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun isThreadFollowedLocally(threadRootMessageId: String): Boolean =
        threadParticipationPrefs.isParticipatingInThread(threadRootMessageId)

    /** Сервер: [RocketChatApi.followMessage]; локально — в [ThreadParticipationPrefs]. */
    suspend fun followThread(threadRootMessageId: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(Exception("Not logged in"))
        val mid = threadRootMessageId.trim()
        if (mid.isEmpty()) return Result.failure(Exception("Пустой тред"))
        return try {
            val response = api.followMessage(mapOf("mid" to mid))
            if (response.success) {
                threadParticipationPrefs.markParticipatedInThread(mid)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Не удалось подписаться"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "followThread: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun unfollowThread(threadRootMessageId: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(Exception("Not logged in"))
        val mid = threadRootMessageId.trim()
        if (mid.isEmpty()) return Result.failure(Exception("Пустой тред"))
        return try {
            val response = api.unfollowMessage(mapOf("mid" to mid))
            if (response.success) {
                threadParticipationPrefs.removeParticipation(mid)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Не удалось отписаться"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "unfollowThread: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun reactToMessage(messageId: String, emoji: String): Result<Unit> {
        val api = apiProvider.getApi() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val response = api.reactToMessage(ReactRequest(emoji = emoji, messageId = messageId))
            if (response.success) Result.success(Unit)
            else Result.failure(Exception(response.error ?: "React failed"))
        } catch (e: Exception) {
            Log.e(TAG, "reactToMessage failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun MessageDto.toEntity(roomId: String): MessageEntity {
        val flat = flattenAttachments(attachments)
        val quoteSegments = collectQuoteSegmentsFromDto(attachments)
        val quoteChainJson = quoteSegments.takeIf { it.isNotEmpty() }?.let { quoteSegmentsToJson(it) }
        val firstQuoteAtt = findFirstQuoteAttachmentDto(attachments)
        val imageAttachment = flat.firstOrNull { it.image_url != null }
        val fullUrl = imageAttachment?.title_link ?: imageAttachment?.image_url
        val fileAttachment = flat.firstOrNull { it.title_link != null && it.image_url == null }
        val cleanedMsg = stripRocketQuotePermalinkMarkdown(msg)
        val quotedId = RocketChatMessageIds.resolveQuotedId(firstQuoteAtt?.message_link, msg)
        val reactionsJson = reactions?.takeIf { it.isNotEmpty() }?.let { map ->
            org.json.JSONObject(map.mapValues { (_, v) -> v.usernames }).toString()
        }
        val fileDesc = imageAttachment?.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: imageAttachment?.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileAttachment?.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileAttachment?.text?.trim()?.takeIf { it.isNotEmpty() }
        val sizeBytes = imageAttachment?.image_size ?: fileAttachment?.image_size
        return MessageEntity(
            id = _id,
            roomId = rid,
            text = cleanedMsg,
            timestamp = parseTimestamp(ts),
            userId = u._id,
            username = u.username,
            displayName = u.name,
            isEdited = editedAt != null,
            parentId = parent,
            tmid = tmid,
            drid = drid,
            threadReplyCount = tcount?.takeIf { it > 0 },
            quoteText = quoteSegments.firstOrNull()?.text?.takeIf { it.isNotBlank() },
            quoteAuthor = quoteSegments.firstOrNull()?.author,
            quoteChainJson = quoteChainJson,
            quotedMessageId = quotedId,
            imageUrl = imageAttachment?.image_url,
            fullImageUrl = fullUrl,
            fileName = imageAttachment?.title ?: fileAttachment?.title,
            fileUrl = fileAttachment?.title_link,
            fileType = fileAttachment?.type ?: fileAttachment?.image_type
                ?: imageAttachment?.image_type,
            fileDescription = fileDesc,
            fileSizeBytes = sizeBytes,
            reactions = reactionsJson,
            mentionsJson = mentionsFromDtoList(mentions),
            syncStatus = MessageEntity.SYNC_SYNCED,
            msgType = t?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    companion object {
        private fun flattenAttachments(list: List<AttachmentDto?>?): List<AttachmentDto> {
            if (list.isNullOrEmpty()) return emptyList()
            val out = ArrayList<AttachmentDto>()
            for (a in list) {
                val a = a ?: continue
                out.add(a)
                out.addAll(flattenAttachments(a.attachments))
            }
            return out
        }

        /** ISO 8601 для поля `attachments[].ts` (как в примерах API Rocket.Chat). */
        private fun millisToRocketAttachmentTs(millis: Long): String {
            return try {
                Instant.ofEpochMilli(millis).toString()
            } catch (_: Exception) {
                Instant.now().toString()
            }
        }

        private fun forwardDebugContext(
            targetRoomId: String,
            source: MessageEntity,
            permalink: String,
            msgBody: String,
            attachment: PostMessageAttachment,
            serverBaseUrl: String?,
        ): String = buildString {
            appendLine("=== RocketLauncher — отладка пересылки ===")
            appendLine("client=com.rocketlauncher (Android)")
            appendLine("serverBaseUrl=${serverBaseUrl ?: "null"}")
            appendLine("targetRoomId=$targetRoomId")
            appendLine("sourceMessageId=${source.id}")
            appendLine("sourceRoomId=${source.roomId}")
            appendLine("permalink=$permalink")
            appendLine("msgBody=$msgBody")
            appendLine("attachment=$attachment")
        }

        /** Если полный контекст с вложением ещё не собран (ошибка до postMessage). */
        private fun forwardDebugContextForError(
            targetRoomId: String,
            source: MessageEntity,
            permalink: String,
            serverBaseUrl: String?,
        ): String = buildString {
            appendLine("=== RocketLauncher — отладка пересылки ===")
            appendLine("client=com.rocketlauncher (Android)")
            appendLine("serverBaseUrl=${serverBaseUrl ?: "null"}")
            appendLine("targetRoomId=$targetRoomId")
            appendLine("sourceMessageId=${source.id}")
            appendLine("sourceRoomId=${source.roomId}")
            appendLine("permalink=$permalink")
            appendLine("msgBody=[ ]($permalink)")
            appendLine("(объект вложения в этом отчёте не собран — сбой до отправки запроса)")
        }

        /** Абсолютный URL для полей, которые API валидирует как href (иначе 400). */
        private fun absoluteUrl(serverBaseUrl: String?, path: String?): String? {
            val p = path?.trim() ?: return null
            if (p.isEmpty()) return null
            if (p.startsWith("http://", ignoreCase = true) || p.startsWith("https://", ignoreCase = true)) return p
            val b = serverBaseUrl?.trim()?.trimEnd('/') ?: return null
            return if (p.startsWith("/")) b + p else "$b/$p"
        }

        /**
         * Относительный путь `/file-upload/...` — fallback, если [absoluteUrl] недоступен (нет base).
         */
        private fun toRocketAttachmentPath(serverBaseUrl: String?, pathOrUrl: String?): String? {
            val raw = pathOrUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                return if (raw.startsWith("/")) raw else "/$raw"
            }
            val base = serverBaseUrl?.trim()?.trimEnd('/') ?: return try {
                java.net.URI(raw).path?.takeIf { it.isNotEmpty() } ?: raw
            } catch (_: Exception) {
                raw
            }
            if (raw.startsWith(base, ignoreCase = true)) {
                val rest = raw.substring(base.length)
                return if (rest.startsWith("/")) rest else "/$rest"
            }
            return try {
                java.net.URI(raw).path?.takeIf { it.isNotEmpty() } ?: raw
            } catch (_: Exception) {
                raw
            }
        }

        /** Подпись к медиа: сначала [MessageEntity.fileDescription], иначе текст сообщения. */
        private fun forwardNestedDescription(msg: MessageEntity, hasMedia: Boolean): String? {
            if (!hasMedia) return null
            val d = msg.fileDescription?.trim()?.takeIf { it.isNotEmpty() }
            val t = msg.text.trim().takeIf { it.isNotEmpty() }
            return d ?: t
        }
    }

    private fun parseTimestamp(ts: String): Long {
        return try {
            java.time.Instant.parse(ts).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
