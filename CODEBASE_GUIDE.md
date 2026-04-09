# RocketLauncher — Руководство по кодовой базе для AI

> Этот файл создан для помощи AI-ассистентам в быстрой ориентации по проекту.
> Обновляй его при добавлении новых модулей, экранов или паттернов.

---

## О проекте

**RocketLauncher** — нативный Android-клиент для [Rocket.Chat](https://rocket.chat),
написанный на Kotlin с использованием Jetpack Compose.

| Параметр | Значение |
|---|---|
| Package | `com.rocketlauncher` |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |
| versionName | 1.0.6 (versionCode = 6) |
| Язык | Kotlin + 1 Java-файл (`UrlFormEncode.java`) |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Сеть | Retrofit 2 + OkHttp 4 + kotlinx.serialization |
| БД | Room (файл `rocket.db`, версия 20) |
| Real-time | WebSocket (DDP / Meteor-протокол Rocket.Chat) |
| Push | Firebase Cloud Messaging (FCM) |
| Звонки | Jitsi Meet SDK 12.0 |
| Изображения | Coil 2 + SVG |
| Настройки | DataStore Preferences |

---

## Архитектура

Проект следует **Clean Architecture** с тремя слоями:

```
presentation  ←  domain  ←  data
```

- **presentation** — Compose-экраны + ViewModel
- **domain** — модели (`Room`, `Message`) + use-cases
- **data** — API, WebSocket, Room DB, репозитории, push, уведомления

DI обеспечивается **Hilt** (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Singleton`).

---

## Структура директорий

```
RocketLauncher/
├── app/
│   ├── build.gradle.kts          # зависимости, подпись, версия приложения
│   ├── google-services.json      # Firebase конфиг (не в git; есть .example)
│   ├── proguard-rules.pro        # ProGuard/R8 правила
│   └── src/main/
│       ├── AndroidManifest.xml   # активности, сервисы, ресивер, разрешения
│       ├── java/com/rocketlauncher/
│       │   ├── RocketLauncherApp.kt      # Application-класс, Coil ImageLoader
│       │   ├── data/                     # слой данных
│       │   ├── domain/                   # бизнес-логика
│       │   ├── presentation/             # UI
│       │   ├── di/                       # Hilt-модули
│       │   ├── receiver/                 # BroadcastReceiver
│       │   └── util/                     # утилиты
│       └── res/
│           ├── values/                   # строки, цвета, стили
│           ├── values-ru/                # русские переводы
│           ├── drawable*/                # иконки, векторы
│           └── xml/                      # network_security_config, file_paths
├── docs/                                 # документация
│   ├── GOOGLE_PLAY.md
│   ├── PUSH_AND_BACKGROUND.md
│   ├── VERSIONING.md
│   └── PRIVACY_POLICY_TEMPLATE.md
├── keystore.properties           # подпись (не в git; есть .example)
├── build-aab.bat                 # скрипт сборки релиза
├── run-emulator.bat              # запуск debug-сборки на эмуляторе
└── run-emulator-release.bat      # запуск release-сборки на эмуляторе
```

---

## Слой `data/`

### `data/api/` — REST API
| Файл | Назначение |
|---|---|
| `RocketChatApi.kt` | Retrofit-интерфейс для всех REST-эндпоинтов Rocket.Chat (~17 КБ) |
| `ApiFactory.kt` | Создаёт Retrofit + OkHttp, добавляет auth-заголовки (`X-Auth-Token`, `X-User-Id`) |
| `ApiProvider.kt` | Hilt-провайдер для `RocketChatApi`, обновляет URL при смене сервера |

### `data/auth/` — Аутентификация
| Файл | Назначение |
|---|---|
| `OAuthWebSocketLogin.kt` | Вход через WebSocket (DDP-метод `login`); поддерживает OAuth-токены (~15 КБ) |
| `OAuthRestLogin.kt` | Вход через REST API (`/api/v1/login`); используется как fallback (~6.7 КБ) |

### `data/db/` — Room БД (`rocket.db`)
| Файл | Назначение |
|---|---|
| `RocketDatabase.kt` | `@Database` (версия 20, таблицы: `messages`, `rooms`, `room_pins`) |
| `MessageEntity.kt` | Сущность сообщения в БД |
| `RoomEntity.kt` | Сущность чат-комнаты в БД |
| `RoomPinEntity.kt` | Сущность закреплённой комнаты |
| `MessageDao.kt` | Запросы к таблице сообщений |
| `RoomDao.kt` | Запросы к таблице комнат |
| `RoomPinDao.kt` | Запросы к таблице закреплённых комнат |

> **Миграции БД** находятся в `di/AppModule.kt` (MIGRATION_1_2 … MIGRATION_19_20).
> При добавлении нового поля — добавляй миграцию туда же.

### `data/dto/` — Data Transfer Objects (26 файлов)
Все DTO для сериализации/десериализации JSON через `kotlinx.serialization`.
Ключевые файлы:
- `MessageDto.kt` — структура сообщения из API
- `SubscriptionDto.kt` / `RoomDto.kt` — подписки и комнаты
- `LoginRequest.kt` / `LoginResponse.kt` — аутентификация
- `PostMessageRequest.kt` — отправка сообщения
- `VideoConferenceDtos.kt` — Jitsi/видеозвонки

### `data/repository/` — Репозитории (основной слой данных)
| Файл | Назначение |
|---|---|
| `AuthRepository.kt` | Состояние аутентификации (`authState: StateFlow`), вход/выход (~10 КБ) |
| `MessageRepository.kt` | CRUD сообщений, загрузка из API + кэш в Room (~47 КБ — самый крупный файл) |
| `RoomRepository.kt` | Список комнат, синхронизация с сервером, Room DB (~29 КБ) |
| `ChatRoomActionsRepository.kt` | Действия с комнатой: invite, mute, kick и т.д. (~13 КБ) |
| `SearchRepository.kt` | Глобальный поиск по комнатам и пользователям |
| `AppUpdateRepository.kt` | Проверка обновлений через GitHub Releases API |
| `UserProfileRepository.kt` | Профиль текущего пользователя |
| `VideoConferenceRepository.kt` | Управление Jitsi-звонками |
| `SessionPrefs.kt` | DataStore: URL сервера, userId, authToken |
| `ThemePreferences.kt` | DataStore: выбранная тема (LIGHT/DARK/SYSTEM) |
| `RoomListPreferences.kt` | DataStore: настройки фильтрации списка комнат |

### `data/realtime/` — WebSocket / реал-тайм
| Файл | Назначение |
|---|---|
| `RealtimeMessageService.kt` | Foreground Service; поддерживает WebSocket-соединение, принимает новые сообщения (~47 КБ) |
| `MessageForegroundService.kt` | Вспомогательный Foreground Service для фоновой работы |
| `NetworkMonitor.kt` | Отслеживает состояние сети (`ConnectivityManager`) |
| `UserPresenceStore.kt` | Хранит в памяти статус присутствия пользователей |
| `UserPresenceStatus.kt` | Enum: Online / Away / Busy / Offline |

### `data/websocket/`
| Файл | Назначение |
|---|---|
| `RocketWebSocket.kt` | Обёртка над OkHttp WebSocket |

### `data/push/` — Firebase Push
| Файл | Назначение |
|---|---|
| `RocketFirebaseMessagingService.kt` | `FirebaseMessagingService`: обрабатывает входящие push, отображает уведомления |
| `PushTokenRegistrar.kt` | Регистрирует FCM-токен на сервере Rocket.Chat |

### `data/notifications/` — Локальные уведомления
| Файл | Назначение |
|---|---|
| `MessageNotifier.kt` | Создаёт/обновляет уведомления о новых сообщениях |
| `IncomingJitsiCallNotifier.kt` | Full-screen уведомление о входящем видеозвонке |
| `AppForegroundState.kt` | Флаг: приложение на переднем плане или нет |
| `OpenedChatTracker.kt` | Какой чат сейчас открыт (не нужно уведомлять) |
| `RoomNotificationPolicy.kt` | Политика уведомлений для каждой комнаты (mute и т.д.) |
| `NotificationConstants.kt` | Channel IDs, notification IDs |
| `ThreadParticipationPrefs.kt` | DataStore: в каких тредах участвует пользователь |

### `data/emoji/`
| Файл | Назначение |
|---|---|
| `EmojiStore.kt` | Встроенная база эмодзи + кастомные эмодзи Rocket.Chat; shortcode → Unicode (~17 КБ) |
| `RecentEmojiPrefs.kt` | DataStore: недавно использованные эмодзи |
| `TwemojiUrls.kt` | Генерирует URL для Twemoji-картинок |

### `data/message/` — Обработка сообщений
| Файл | Назначение |
|---|---|
| `QuoteAttachments.kt` | Парсинг и построение цепочек цитирования (~4.8 КБ) |
| `QuoteMarkdownUtils.kt` | Форматирование цитат в Markdown |

### `data/mentions/`
| Файл | Назначение |
|---|---|
| `StoredMentions.kt` | DataStore: непрочитанные упоминания (`@user`) |
| `RocketChatMessageIds.kt` | Генерация уникальных ID сообщений (клиентская сторона) |

### `data/invite/`
| Файл | Назначение |
|---|---|
| `InviteLinkParser.kt` | Парсит invite-ссылки (`/invite/...` и `go.rocket.chat/invite?...`) |

### `data/share/`
| Файл | Назначение |
|---|---|
| `ShareUploadQueue.kt` | Очередь файлов для шаринга (Intent.ACTION_SEND*) |

### `data/subscriptions/`
| Файл | Назначение |
|---|---|
| `SubscriptionUnreadPolicy.kt` | Логика подсчёта непрочитанных |

### `data/github/`
| Файл | Назначение |
|---|---|
| `GitHubApi.kt` | Retrofit-интерфейс для GitHub Releases API |
| `GitHubReleaseDto.kt` | DTO ответа GitHub |

### `data/RocketChatMessageKinds.kt`
Enum со всеми типами системных сообщений Rocket.Chat (`ul`, `uj`, `ru`, `rtc` и т.д.).

---

## Слой `domain/`

### `domain/model/` — Доменные модели
| Файл | Назначение |
|---|---|
| `Room.kt` | Модель комнаты (id, name, type, unread, ...) |
| `Message.kt` | Модель сообщения (id, text, author, ...) |
| `FavoriteDisplayMode.kt` | Enum: как отображать избранные комнаты |

### `domain/usecase/` — Use Cases
| Файл | Назначение |
|---|---|
| `LoginUseCase.kt` | Вход пользователя |
| `LoginWithKeycloakUseCase.kt` | Вход через Keycloak SSO |
| `GetOAuthUrlUseCase.kt` | Получение URL для OAuth-авторизации |
| `SendMessageUseCase.kt` | Отправка сообщения в комнату |
| `SyncChatsFromServerUseCase.kt` | Синхронизация списка чатов с сервером |

---

## Слой `presentation/`

### Экраны и ViewModels
| Пакет | Screen | ViewModel | Назначение |
|---|---|---|---|
| `login/` | `LoginScreen.kt` | `LoginViewModel.kt` | Ввод URL сервера + логин/пароль/OAuth |
| `rooms/` | `RoomListScreen.kt` | `RoomListViewModel.kt` | Список чатов, поиск, фильтры, создание комнат |
| `chat/` | `ChatScreen.kt` | `ChatViewModel.kt` | Экран чата (~111 КБ + ~72 КБ — самые крупные файлы) |
| `threads/` | `ThreadListScreen.kt` | `ThreadListViewModel.kt` | Список тредов комнаты |
| `search/` | `GlobalSearchScreen.kt` | `GlobalSearchViewModel.kt` | Глобальный поиск по чатам |
| `profile/` | `MyProfileScreen.kt` | `MyProfileViewModel.kt` | Профиль пользователя, смена аватара |
| `jitsi/` | `IncomingCallActivity.kt` | — | Экран входящего звонка (отдельная Activity) |

### Навигация (`presentation/navigation/`)
| Файл | Назначение |
|---|---|
| `NavRoutes.kt` | Константы маршрутов: `LOGIN`, `ROOMS`, `CHAT`, `GLOBAL_SEARCH`, `MY_PROFILE` |
| `RocketNavHost.kt` | `NavHost` с `composable()`-маршрутами |
| `NavViewModel.kt` | Обрабатывает входящие Intent'ы (push-нотификации, invite-ссылки, share) |
| `NavArgDecoding.kt` | URL-декодирование аргументов навигации |
| `UrlFormEncode.java` | URL-кодирование для API < 33 (единственный Java-файл) |

### Вспомогательные компоненты в `chat/`
| Файл | Назначение |
|---|---|
| `MessageFormatter.kt` | Рендер Markdown в Compose (жирный, курсив, код, ссылки) |
| `ComposerWithSelectionToolbar.kt` | Поле ввода с тулбаром (Bold/Italic/...) |
| `ComposerMarkdown.kt` | Markdown-тулбар над клавиатурой |
| `ChatRoomMembersSheet.kt` | Bottom sheet: участники комнаты |
| `UserProfileBottomSheet.kt` | Bottom sheet: профиль пользователя |
| `PresenceRingAvatar.kt` | Аватар с кольцом статуса присутствия |

### Темы (`presentation/theme/`)
| Файл | Назначение |
|---|---|
| `Theme.kt` | `RocketLauncherTheme` — Material 3 ColorScheme (light + dark) |
| `ThemeMode.kt` | Enum: `LIGHT`, `DARK`, `SYSTEM` |

### OAuth (`presentation/oauth/`)
`RocketChatOAuthActivity` — скрытая Activity для обработки OAuth redirect.

---

## DI (`di/`)

| Файл | Назначение |
|---|---|
| `AppModule.kt` | Room DB + DAO + OAuthWebSocketLogin + миграции (MIGRATION_1_2..19_20) |
| `GitHubModule.kt` | GitHub Retrofit API |

> Все репозитории (`AuthRepository`, `RoomRepository` и т.д.) используют
> **constructor injection** (`@Inject constructor(...)`) и помечены `@Singleton` через Hilt.

---

## Прочее

### `receiver/BootReceiver.kt`
`BroadcastReceiver` на `BOOT_COMPLETED` — перезапускает `RealtimeMessageService`
после перезагрузки устройства, если пользователь авторизован.

### `util/`
| Файл | Назначение |
|---|---|
| `AppLog.kt` | Обёртка над `Log` — включает логи только в debug-режиме |
| `SemVer.kt` | Парсинг и сравнение версий для проверки обновлений приложения |

---

## Ключевые потоки данных

### Поток авторизации
```
LoginScreen → LoginViewModel → LoginUseCase
→ OAuthWebSocketLogin (или OAuthRestLogin)
→ AuthRepository.authState (StateFlow<AuthState>)
→ NavViewModel → навигация на RoomListScreen
```

### Поток сообщений (реал-тайм)
```
RealtimeMessageService (WebSocket) → получает DDP-events
→ MessageRepository (сохраняет в Room DB)
→ ChatViewModel (Flow из Room) → ChatScreen
```

### Поток Push-уведомлений
```
RocketFirebaseMessagingService.onMessageReceived()
→ MessageNotifier (создаёт уведомление)
→ tap → MainActivity.onNewIntent()
→ NavViewModel.handleIntent() → навигация на ChatScreen
```

### Поток Jitsi-звонков
```
RocketFirebaseMessagingService (тип "videoconf")
→ IncomingJitsiCallNotifier → IncomingCallActivity (full-screen)
→ принять: JitsiMeetLauncher → JitsiMeet SDK
```

---

## Конфигурационные файлы (не в git)

| Файл | Описание | Шаблон |
|---|---|---|
| `keystore.properties` | Параметры подписи релиза | `keystore.properties.example` |
| `google-services.json` | Firebase конфигурация | `google-services.json.example` |
| `local.properties` | Путь к Android SDK | — (генерируется Android Studio) |

---

## Сборка и запуск

```bash
# Debug на подключённом устройстве/эмуляторе
./gradlew installDebug

# Release AAB для Google Play
build-aab.bat

# Запуск на эмуляторе (debug)
run-emulator.bat

# Запуск на эмуляторе (release)
run-emulator-release.bat
```

---

## Правила при изменении кода

1. **Новое поле в Room** → добавить миграцию `MIGRATION_N_N+1` в `di/AppModule.kt`
   и обновить соответствующий `*Entity.kt` + `*Dao.kt`.
2. **Новый DTO** → добавить `@Serializable` + поместить в `data/dto/`.
3. **Новый экран** → добавить маршрут в `NavRoutes.kt` и `composable()` в `RocketNavHost.kt`.
4. **Новая зависимость** → добавить в `app/build.gradle.kts`, обновить версию в `VERSIONING.md`.
5. **Новое разрешение** → добавить в `AndroidManifest.xml` и обработать runtime-запрос в `MainActivity`.
6. **Логирование** → использовать только `AppLog`, никогда прямой `android.util.Log`.
