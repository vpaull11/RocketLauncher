package com.rocketlauncher.data.api

import com.rocketlauncher.data.dto.ChannelsListResponse
import com.rocketlauncher.data.dto.ImListResponse
import com.rocketlauncher.data.dto.LoginRequest
import com.rocketlauncher.data.dto.LoginResponse
import com.rocketlauncher.data.dto.MethodCallAnonRequest
import com.rocketlauncher.data.dto.MethodCallAnonResponse
import com.rocketlauncher.data.dto.CustomEmojiResponse
import com.rocketlauncher.data.dto.DiscussionsResponse
import com.rocketlauncher.data.dto.MessagesResponse
import com.rocketlauncher.data.dto.ThreadsListResponse
import com.rocketlauncher.data.dto.OAuthSettingsResponse
import com.rocketlauncher.data.dto.PostMessageRequest
import com.rocketlauncher.data.dto.PostMessageResponse
import com.rocketlauncher.data.dto.ReactRequest
import com.rocketlauncher.data.dto.ReactResponse
import com.rocketlauncher.data.dto.GenericResponse
import com.rocketlauncher.data.dto.MarkReadRequest
import com.rocketlauncher.data.dto.SaveRoomNotificationRequest
import com.rocketlauncher.data.dto.RoomsResponse
import com.rocketlauncher.data.dto.SubscriptionsResponse
import com.rocketlauncher.data.dto.UpdateOwnBasicInfoRequest
import com.rocketlauncher.data.dto.UpdateOwnBasicInfoResponse
import com.rocketlauncher.data.dto.UsersInfoResponse
import com.rocketlauncher.data.dto.ChatSearchResponse
import com.rocketlauncher.data.dto.SpotlightResponse
import com.rocketlauncher.data.dto.RoomAutocompleteResponse
import com.rocketlauncher.data.dto.ImCreateRequest
import com.rocketlauncher.data.dto.ImCreateResponse
import com.rocketlauncher.data.dto.FindOrCreateInviteRequest
import com.rocketlauncher.data.dto.FindOrCreateInviteResponse
import com.rocketlauncher.data.dto.UseInviteTokenRequest
import com.rocketlauncher.data.dto.UseInviteTokenResponse
import com.rocketlauncher.data.dto.PushTokenRegisterRequest
import com.rocketlauncher.data.dto.VideoConferenceJoinRequest
import com.rocketlauncher.data.dto.VideoConferenceStartRequest
import com.rocketlauncher.data.dto.VideoConferenceStartResponse
import com.rocketlauncher.data.dto.VideoConferenceJoinResponse
import com.rocketlauncher.data.dto.ReadReceiptsResponse
import com.rocketlauncher.data.dto.RoomsMediaConfirmRequest
import com.rocketlauncher.data.dto.RoomsMediaUploadResponse
import com.rocketlauncher.data.dto.RoomFavoriteRequest
import com.rocketlauncher.data.dto.RoomIdBody
import com.rocketlauncher.data.dto.RoomIdUserIdBody
import com.rocketlauncher.data.dto.ChannelCreateBody
import com.rocketlauncher.data.dto.GroupCreateBody
import com.rocketlauncher.data.dto.TeamCreateBody
import com.rocketlauncher.data.dto.DiscussionCreateApiBody
import com.rocketlauncher.data.dto.ChannelCreateResponse
import com.rocketlauncher.data.dto.GroupCreateResponse
import com.rocketlauncher.data.dto.TeamCreateResponse
import com.rocketlauncher.data.dto.DiscussionCreateResponse
import com.rocketlauncher.data.dto.RoomMembersResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rocket.Chat REST API
 * @see https://developer.rocket.chat/reference/api
 */
interface RocketChatApi {

    @POST("api/v1/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    /**
     * DDP login через REST (method.callAnon).
     * OAuth: params=[{oauth:{credentialToken,credentialSecret}}]
     * С 2FA: params=[{oauth:{...},totp:{code:"..."}}]
     */
    @POST("api/v1/method.callAnon/login")
    suspend fun loginMethodCallAnon(
        @Body request: MethodCallAnonRequest,
        @Header("Cookie") cookie: String? = null
    ): MethodCallAnonResponse

    /** OAuth services (public, no auth). URL = {server}/_oauth/{service.name} */
    @GET("api/v1/settings.oauth")
    suspend fun getOAuthSettings(): OAuthSettingsResponse

    /** Get user info (requires X-Auth-Token, X-User-Id) */
    @GET("api/v1/users.info")
    suspend fun getUsersInfo(@Query("userId") userId: String): UsersInfoResponse

    @GET("api/v1/users.info")
    suspend fun getUsersInfoByUsername(@Query("username") username: String): UsersInfoResponse

    /** Имя, ник, био, статус (statusType / statusText). Без email/username/password в этом клиенте. */
    @POST("api/v1/users.updateOwnBasicInfo")
    suspend fun updateOwnBasicInfo(@Body body: UpdateOwnBasicInfoRequest): UpdateOwnBasicInfoResponse

    /** Get all rooms (channels + DMs) for the user. Auth via interceptor. */
    @GET("api/v1/rooms.get")
    suspend fun getRooms(@Query("updatedSince") updatedSince: String? = null): RoomsResponse

    @GET("api/v1/channels.listJoined")
    suspend fun getChannelsJoined(
        @Query("offset") offset: Int = 0,
        @Query("count") count: Int = 50
    ): ChannelsListResponse

    @GET("api/v1/im.list")
    suspend fun getDirectMessages(
        @Query("offset") offset: Int = 0,
        @Query("count") count: Int = 50
    ): ImListResponse

    @GET("api/v1/channels.messages")
    suspend fun getChannelMessages(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = "{\"ts\": -1}"
    ): MessagesResponse

    @GET("api/v1/groups.messages")
    suspend fun getGroupMessages(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = "{\"ts\": -1}"
    ): MessagesResponse

    @GET("api/v1/im.messages")
    suspend fun getDmMessages(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = "{\"ts\": -1}"
    ): MessagesResponse

    @GET("api/v1/chat.getThreadsList")
    suspend fun getThreadsList(
        @Query("rid") roomId: String,
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0
    ): ThreadsListResponse

    @GET("api/v1/chat.getThreadMessages")
    suspend fun getThreadMessages(
        @Query("tmid") tmid: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = "{\"ts\": -1}"
    ): MessagesResponse

    @GET("api/v1/channels.getDiscussions")
    suspend fun getChannelDiscussions(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0
    ): DiscussionsResponse

    @GET("api/v1/groups.getDiscussions")
    suspend fun getGroupDiscussions(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0
    ): DiscussionsResponse

    /**
     * DDP `subscriptions/get` через REST (как в веб-клиенте; тело — JSON-строка [MethodCallAnonRequest.message]).
     * Путь с `:` как у сервера: `subscriptions%3Aget`.
     */
    @POST("api/v1/method.call/subscriptions%3Aget")
    suspend fun methodCallSubscriptionsGet(
        @Body body: MethodCallAnonRequest
    ): MethodCallAnonResponse

    /**
     * Подписки пользователя (REST). Запасной вариант, если [methodCallSubscriptionsGet] недоступен.
     * [updatedSince] — опционально: дельта после даты (ISO 8601); null = полный список.
     * Без count/offset: в актуальных Rocket.Chat query для этого маршрута валидируется строго;
     * лишние параметры дают HTTP 400.
     */
    @GET("api/v1/subscriptions.get")
    suspend fun getSubscriptions(
        @Query("updatedSince") updatedSince: String? = null
    ): SubscriptionsResponse

    @GET("api/v1/emoji-custom.list")
    suspend fun getCustomEmojis(
        @Query("count") count: Int = 500
    ): CustomEmojiResponse

    @POST("api/v1/chat.postMessage")
    suspend fun postMessage(@Body request: PostMessageRequest): PostMessageResponse

    /**
     * Классическая загрузка: путь **`rooms.upload`** (с точкой), не `rooms/upload`.
     * @see https://developer.rocket.chat/reference/api/rest-api/endpoints/rooms/rooms-upload
     */
    @Multipart
    @POST("api/v1/rooms.upload/{rid}")
    suspend fun roomsUpload(
        @Path("rid") rid: String,
        @Part file: MultipartBody.Part,
        @Part("msg") msg: RequestBody?,
        @Part("description") description: RequestBody?,
        @Part("tmid") tmid: RequestBody?
    ): PostMessageResponse

    /** Новый API: сначала загрузить файл, затем [roomsMediaConfirm]. */
    @Multipart
    @POST("api/v1/rooms.media/{rid}")
    suspend fun roomsMediaUpload(
        @Path("rid") rid: String,
        @Part file: MultipartBody.Part
    ): RoomsMediaUploadResponse

    @POST("api/v1/rooms.mediaConfirm/{rid}/{fileId}")
    suspend fun roomsMediaConfirm(
        @Path("rid") rid: String,
        @Path("fileId") fileId: String,
        @Body body: RoomsMediaConfirmRequest
    ): PostMessageResponse

    @POST("api/v1/chat.react")
    suspend fun reactToMessage(@Body request: ReactRequest): ReactResponse

    /** API для запуска слэш-команд как это делает веб-клиент (костыль через метод DDP обернутый в REST) */
    @POST("api/v1/method.call/slashCommand")
    suspend fun callSlashCommandMethod(@Body request: kotlinx.serialization.json.JsonObject): okhttp3.ResponseBody

    /**
     * Apps Engine UI-взаимодействие: blockAction (голосование в опросе) и viewSubmit (создание опроса).
     * Правильный REST-метод, который использует официальный клиент Rocket.Chat.
     * appId — UUID приложения (из поля `appId` UIKit-блока).
     * Возвращает сырое тело ответа — сервер может вернуть разные структуры.
     */
    @POST("api/apps/ui.interaction/{appId}")
    suspend fun sendUiInteraction(
        @Path("appId") appId: String,
        @Body request: kotlinx.serialization.json.JsonObject
    ): okhttp3.ResponseBody


    /** Удаление сообщения: `roomId`, `msgId` (Rocket.Chat REST). */
    @POST("api/v1/chat.delete")
    suspend fun deleteMessage(@Body body: Map<String, String>): GenericResponse

    /** Редактирование: `roomId`, `msgId`, `text`. */
    @POST("api/v1/chat.update")
    suspend fun updateMessage(@Body body: Map<String, String>): GenericResponse

    /** Подписка на уведомления по треду: тело `{"mid":"&lt;id корневого сообщения&gt;"}` */
    @POST("api/v1/chat.followMessage")
    suspend fun followMessage(@Body body: Map<String, String>): GenericResponse

    /** Отписка от уведомлений по треду: тело `{"mid":"..."}` */
    @POST("api/v1/chat.unfollowMessage")
    suspend fun unfollowMessage(@Body body: Map<String, String>): GenericResponse

    /**
     * Квитанции о прочтении сообщения (на серверах с включённой функцией; на CE может быть недоступно).
     */
    @GET("api/v1/chat.getMessageReadReceipts")
    suspend fun getMessageReadReceipts(
        @Query("messageId") messageId: String
    ): ReadReceiptsResponse

    /**
     * Тело: `{"rid":"&lt;room_id&gt;"}`.
     * Ответ: [ResponseBody], чтобы не падать на пустом/нестандартном JSON при десериализации.
     */
    @POST("api/v1/subscriptions.read")
    suspend fun markRoomAsRead(@Body request: MarkReadRequest): Response<ResponseBody>

    /** Настройки уведомлений по комнате (подписка пользователя). */
    @POST("api/v1/rooms.saveNotification")
    suspend fun saveRoomNotification(@Body body: SaveRoomNotificationRequest): GenericResponse

    /** Пометить комнату как избранную (звезда) на сервере Rocket.Chat. */
    @POST("api/v1/rooms.favorite")
    suspend fun roomsFavorite(@Body body: RoomFavoriteRequest): GenericResponse

    @POST("api/v1/chat.pinMessage")
    suspend fun pinMessage(@Body body: Map<String, String>): GenericResponse

    @POST("api/v1/chat.unPinMessage")
    suspend fun unpinMessage(@Body body: Map<String, String>): GenericResponse

    /** Закреплённые сообщения комнаты. */
    @GET("api/v1/chat.getPinnedMessages")
    suspend fun getPinnedMessages(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = """{"pinnedAt":-1}"""
    ): MessagesResponse

    @POST("api/v1/discussion.create")
    suspend fun createDiscussion(@Body body: DiscussionCreateApiBody): DiscussionCreateResponse

    @POST("api/v1/channels.leave")
    suspend fun channelsLeave(@Body body: RoomIdBody): GenericResponse

    @POST("api/v1/groups.leave")
    suspend fun groupsLeave(@Body body: RoomIdBody): GenericResponse

    @POST("api/v1/channels.create")
    suspend fun channelsCreate(@Body body: ChannelCreateBody): ChannelCreateResponse

    @POST("api/v1/groups.create")
    suspend fun groupsCreate(@Body body: GroupCreateBody): GroupCreateResponse

    @POST("api/v1/teams.create")
    suspend fun teamsCreate(@Body body: TeamCreateBody): TeamCreateResponse

    @GET("api/v1/channels.members")
    suspend fun channelsMembers(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0
    ): RoomMembersResponse

    @GET("api/v1/groups.members")
    suspend fun groupsMembers(
        @Query("roomId") roomId: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0
    ): RoomMembersResponse

    @POST("api/v1/channels.invite")
    suspend fun channelsInvite(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/groups.invite")
    suspend fun groupsInvite(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/channels.addModerator")
    suspend fun channelsAddModerator(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/channels.removeModerator")
    suspend fun channelsRemoveModerator(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/channels.addOwner")
    suspend fun channelsAddOwner(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/channels.removeOwner")
    suspend fun channelsRemoveOwner(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/groups.addModerator")
    suspend fun groupsAddModerator(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/groups.removeModerator")
    suspend fun groupsRemoveModerator(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/groups.addOwner")
    suspend fun groupsAddOwner(@Body body: RoomIdUserIdBody): GenericResponse

    @POST("api/v1/groups.removeOwner")
    suspend fun groupsRemoveOwner(@Body body: RoomIdUserIdBody): GenericResponse

    /** Регистрация push-токена (FCM) для серверных уведомлений. */
    @POST("api/v1/push.token")
    suspend fun registerPushToken(@Body body: PushTokenRegisterRequest): GenericResponse

    /** Поиск сообщений в комнате */
    @GET("api/v1/chat.search")
    suspend fun chatSearch(
        @Query("roomId") roomId: String,
        @Query("searchText") searchText: String,
        @Query("count") count: Int = 50,
        @Query("offset") offset: Int = 0
    ): ChatSearchResponse

    /** Поиск пользователей и комнат (ещё не вступивших и т.д.) */
    @GET("api/v1/spotlight")
    suspend fun spotlight(@Query("query") query: String): SpotlightResponse

    /**
     * Автодополнение каналов/приватных комнат по имени (среди доступных пользователю).
     * [selector] — JSON, например {"name":"общий"}
     */
    @GET("api/v1/rooms.autocomplete.channelAndPrivate")
    suspend fun roomsAutocompleteChannelAndPrivate(
        @Query("selector") selector: String
    ): RoomAutocompleteResponse

    /**
     * Получить или создать приглашение в комнату (нужны права `create-invite-links` и т.п.).
     * Ответ: [FindOrCreateInviteResponse.url] или [FindOrCreateInviteResponse.inviteId] для ссылки `/invite/{token}`.
     */
    @POST("api/v1/findOrCreateInvite")
    suspend fun findOrCreateInvite(@Body body: FindOrCreateInviteRequest): FindOrCreateInviteResponse

    /** Вступить в комнату по токену из ссылки-приглашения ([InviteLinkParser]). */
    @POST("api/v1/useInviteToken")
    suspend fun useInviteToken(@Body body: UseInviteTokenRequest): UseInviteTokenResponse

    /** Создать или открыть DM */
    @POST("api/v1/im.create")
    suspend fun imCreate(@Body body: ImCreateRequest): ImCreateResponse

    /** Старт видеозвонка (Jitsi и др. через VideoConf на сервере) */
    @POST("api/v1/video-conference.start")
    suspend fun videoConferenceStart(
        @Body body: VideoConferenceStartRequest
    ): VideoConferenceStartResponse

    /** Получить URL для входа в конференцию */
    @POST("api/v1/video-conference.join")
    suspend fun videoConferenceJoin(
        @Body body: VideoConferenceJoinRequest
    ): VideoConferenceJoinResponse
}
