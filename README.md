# Текст

![Android](https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Android%20Views-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Минималистичный тёмный TXT-редактор для Android. Поддерживает открытие и сохранение TXT через системный выбор файлов, буфер обмена, отмену/повтор и отправку текста на собственный HTTPS-приёмник в локальной сети.

## Сборка Android-приложения

Откройте проект в Android Studio или соберите из корня проекта:

```powershell
gradle assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`. Готовая debug-сборка находится в `outputs/Tekst-debug.apk`.

## Локальный HTTPS-приёмник

Требуется Node.js 18+ и сертификат TLS для локального IP компьютера. Сгенерируйте сертификат с IP в Subject Alternative Name, затем задайте переменные окружения в PowerShell:

```powershell
$env:WIFI_SERVER_CERT = (Resolve-Path .\server-cert.pem).Path
$env:WIFI_SERVER_KEY = (Resolve-Path .\server-key.pem).Path
$env:WIFI_UPLOAD_TOKEN = 'случайный-длинный-токен'
node .\outputs\wifi-server\server.js
```

Разрешите Node.js входящие подключения в частной сети Windows. Устройство и компьютер должны быть в одной Wi‑Fi сети. Сервер сохраняет полученные `.txt` в `outputs/wifi-server/received` и не перезаписывает существующие имена.

Для APK задайте те же параметры локально в корневом `gradle.properties` (этот файл не следует публиковать):

```properties
wifiServerIp=IP_КОМПЬЮТЕРА
wifiUploadToken=ТОТ_ЖЕ_ТОКЕН
wifiCertPin=SHA256_СЕРТИФИКАТА_БЕЗ_ДВОЕТОЧИЙ
```

Отпечаток сертификата вычисляется как SHA-256 от DER-сертификата в нижнем регистре. Без локальных значений сетевая отправка отключена; сетевой адрес и секреты не хранятся в репозитории.

## Структура

- `app/` — исходники Android-приложения.
- `outputs/wifi-server/server.js` — локальный HTTPS-приёмник на Node.js.
- `outputs/Tekst-debug.apk` — готовая отладочная APK.

