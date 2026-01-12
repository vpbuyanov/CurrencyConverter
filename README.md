# currencyconverter — быстрый старт

Android-приложение (модуль `:app`).

## Требования

- macOS/Linux/Windows
- **JDK 17+** (для Android Gradle Plugin 8.x)
- Android SDK + Platform Tools (`adb`)
- Запущенный Android Emulator (AVD) или подключённое устройство

Проверка, что всё ок:

```sh
java -version
adb version
```

## Сборка

```sh
make build
```

Debug APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

В CI (GitHub Actions) APK доступен в Artifacts у последнего workflow run.

А также APK file есть в релизах

## Запуск на эмуляторе/устройстве

1. Запусти эмулятор в Android Studio (`Tools → Device Manager`) или подключи телефон с включённым USB debugging.
2. Выполни:

```sh
make run
```

## Команды

```sh
make help
```
