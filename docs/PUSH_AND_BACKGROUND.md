# Фон, автозапуск и push

## Что сделано в приложении

1. **Foreground Service** (`MessageForegroundService`) — как и раньше держит WebSocket для входящих сообщений. Показывает постоянное уведомление «Подключено / …».

2. **Локальные уведомления о новых сообщениях** — при приходе сообщения по WebSocket, если приложение **в фоне** (`ProcessLifecycleOwner`), показывается уведомление. Нажатие открывает нужный чат.

3. **Запуск после перезагрузки** — `BootReceiver` на `BOOT_COMPLETED`: при сохранённой сессии выполняется синхронизация комнат, запускается foreground service и `RealtimeMessageService.connect()`.

4. **FCM (Firebase Cloud Messaging)** — сервис `RocketFirebaseMessagingService`: регистрация токена на сервере Rocket.Chat (`POST /api/v1/push.token`) и показ уведомлений из data/payload, когда push приходит с сервера.

## Настройка Firebase (обязательно для настоящих push)

1. Создайте проект в [Firebase Console](https://console.firebase.google.com/), добавьте Android-приложение с package `com.rocketlauncher`.
2. Скачайте **настоящий** `google-services.json` из Firebase Console и положите как `app/google-services.json` (структура — как в **`app/google-services.json.example`**; файл с секретами не коммитьте в публичный репозиторий — см. `.gitignore`).
3. На сервере Rocket.Chat должны быть настроены **Google FCM credentials** (админка → Push → Firebase).

## Сервер Rocket.Chat

Без настроенного push на сервере FCM-токен будет зарегистрирован, но сервер не сможет слать уведомления при полностью остановленном приложении. Локальные уведомления по WebSocket работают, пока живёт процесс и foreground service (ограничения OEM/батареи могут убивать фон).

## Разрешения

- Android 13+: пользователю может понадобиться выдать **Уведомления** в настройках приложения.
- Некоторые прошивки отключают автозапуск после загрузки — проверьте настройки «Автозапуск» для приложения.
