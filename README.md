# RocketLauncher

Нативный Android-клиент для Rocket.Chat на Kotlin + Jetpack Compose.

## Стек

- **Kotlin** + **Jetpack Compose** — UI
- **MVVM** — архитектура
- **Hilt** — DI
- **Retrofit** + **OkHttp** + **Kotlinx Serialization** — REST API
- **Room** — локальная БД
- **WorkManager** — фоновая синхронизация (заготовка)
- **WebSocket** (OkHttp) — real-time (заготовка)
- **Coil** — изображения/аватары
- **DataStore** — сессия, недавние смайлики в пикере
- **Jitsi Meet Android SDK** — встроенный видеозвонок по ссылке с сервера (`video-conference.start` / `join`)

## Архитектура

```
app/
├── data/           # API, DB, DTO, репозитории
├── domain/         # Use cases, модели
├── presentation/   # Compose экраны, ViewModels
└── di/             # Hilt модули
```

## Возможности (релевантные доработкам)

### Закреплённые сообщения

- В обычном чате (не в треде) под шапкой показывается **превью последнего закрепления** (по `pinnedAt` / `ts`).
- **Тап по баннеру** — прокрутка к этому сообщению в ленте и переключение превью на **следующее более старое** закрепление (по кругу).
- Данные: `GET /api/v1/chat.getPinnedMessages` (`roomId`, сортировка по `pinnedAt`).

### Текст сообщений и ссылки

- Поддержка формата Rocket.Chat / Slack: **`<https://…|подпись>`** — в чате отображается подпись, тап открывает URL.
- Разбор **HTML-сущностей** (`&lt;` / `&gt;`) для таких ссылок в теле сообщения.
- Текст под **картинкой/файлом** (подпись вложения) проходит через тот же форматтер, что и основное тело, чтобы ссылки не «ломались» при дублировании `msg` и описания.

### Инвайт-ссылки

- Ссылки приглашения (`https://go.rocket.chat/invite?…`, `https://сервер/invite/…`) по тапу в чате обрабатываются **внутри приложения** (вступление в комнату и переход в чат), а не через внешний браузер. Логика совпадает с обработкой deep link при открытии приложения по ссылке.

### Пикер эмодзи

- Блок **«Недавние»**: до **5** последних выбранных смайликов (стандартных и кастомных shortcode).
- Список хранится в **DataStore** и сохраняется после перезапуска приложения.

### Сборка release и установка

- **Целевой API приложения:** `compileSdk` **35**, `targetSdk` **35** (Android 15) — см. `app/build.gradle.kts` и [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md) (требования Play к `targetSdk`).
- **Release** подписывается: если в корне проекта есть **`keystore.properties`**, используется конфиг **`release`** (см. **`keystore.properties.example`**). Если файла нет — для `release` используется **debug-keystore**, чтобы `assembleRelease` давал APK, который можно ставить на эмулятор/устройство без отдельного ключа.
- **Google Play**: загружайте только AAB, собранный с **настоящим** release-keystore. Принудительная проверка: `gradlew bundleRelease -PrequireReleaseKeystore=true` (без `keystore.properties` сборка упадёт). Политика версий и чеклист консоли: **[docs/VERSIONING.md](docs/VERSIONING.md)**, **[docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md)**; черновик политики конфиденциальности: **[docs/PRIVACY_POLICY_TEMPLATE.md](docs/PRIVACY_POLICY_TEMPLATE.md)**.
- **`google-services.json`** (Firebase): не коммитьте в публичный репозиторий; в репозитории есть **`app/google-services.json.example`**, рабочий файл — локально (см. `.gitignore`).
- **R8**: по умолчанию выключен (`rocketLauncher.enableR8=false` в `gradle.properties`). Включение — после регрессии release на устройстве.
- Скрипт **`run-emulator-release.bat`**: сборка release APK, установка на эмулятор, запуск приложения (пути к JDK/SDK в скрипте при необходимости поправьте под свою машину).

## API Rocket.Chat

Используемые эндпоинты (неполный список):

- `POST /api/v1/login` — вход
- `GET /api/v1/rooms.get` — список комнат (каналы + DM)
- `GET /api/v1/channels.messages` / `groups.messages` / `im.messages` — сообщения
- `GET /api/v1/chat.getPinnedMessages` — закреплённые сообщения комнаты
- `POST /api/v1/chat.postMessage` — отправка сообщения
- `POST /api/v1/chat.pinMessage` / `chat.unPinMessage` — закрепление

Документация: https://developer.rocket.chat/reference/api

## Настройка

1. Установите [Android Studio](https://developer.android.com/studio) или Android SDK
2. Создайте `local.properties` в корне проекта:
   ```
   sdk.dir=G\:\\Android\\android-sdk
   ```
3. **Java 17** — для сборки нужна Java 17 (kapt не совместим с Java 21+):
   ```bash
   set JAVA_HOME=G:\Android\openjdk\jdk-17.0.12
   ```

## Сборка

Debug:

```bash
gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Release (подпись см. выше):

```bash
gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Для **Google Play** используйте App Bundle:

```bash
gradlew bundleRelease
```

AAB: `app/build/outputs/bundle/release/app-release.aab`

## Запуск

1. Установите APK на устройство/эмулятор
2. Введите URL сервера (например `https://open.rocket.chat`)
3. Введите логин и пароль
4. После входа отобразится список чатов

## Keycloak / OAuth

Вход через Keycloak (и другие OAuth-провайдеры) настраивается **только на сервере Rocket.Chat**.

1. **Rocket.Chat**: Manage → Settings → OAuth → Add Custom OAuth (Keycloak) по [инструкции](https://docs.rocket.chat/docs/openid-connect-keycloak). Укажите URL Keycloak, realm, Client ID, Client Secret.

2. **Keycloak**: Valid Redirect URIs — `https://ваш-rocket-chat/_oauth/keycloak` (и с `?close` при необходимости).

3. **Приложение**: Введите URL сервера Rocket.Chat и нажмите «Войти через Keycloak». Откроется WebView с формой Keycloak (логин, пароль, OTP). Пользователь вводит учётные данные на странице Keycloak.

