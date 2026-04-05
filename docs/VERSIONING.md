# Версии для Google Play

## Правило

- **`versionCode`** — целое число, **строго возрастающее** с каждой загрузкой AAB/APK в любой трек Play (внутреннее тестирование, закрытое, открытое, production). Повторно использовать или уменьшать нельзя.
- **`versionName`** — строка для пользователей (семантическое версионирование по желанию: `MAJOR.MINOR.PATCH`).

## Перед каждой загрузкой в консоль

1. Увеличить `versionCode` минимум на **1**.
2. При необходимости обновить `versionName` (например новая минорная версия с заметными изменениями).
3. Собрать `bundleRelease` с **release-подписью** (см. `keystore.properties.example` и README).
4. Загрузить AAB в нужный трек.

## Где в проекте

`app/build.gradle.kts` → блок `defaultConfig`:

```kotlin
versionCode = …
versionName = "…"
```

Актуальные числа — в `app/build.gradle.kts` (`defaultConfig`); при последнем обновлении документа: `versionCode = 5`, `versionName = "1.0.4"`.

История релизов ведётся в changelog команды или в заметках к тегам Git; в коде достаточно актуальных `versionCode` / `versionName`.
