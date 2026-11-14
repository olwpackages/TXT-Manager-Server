# Текст

<p>
  <img alt="Android 6+" src="https://img.shields.io/badge/Android-6%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Локальная передача" src="https://img.shields.io/badge/передача-HTTPS%20в%20LAN-356859">
</p>

Тёмный редактор TXT-файлов для Android с простым экраном и основными действиями под рукой.

## Возможности

- Открытие и сохранение `.txt` через системный выбор файлов Android.
- Последовательные имена заметок: `Заметка.txt`, `Заметка 1.txt`, `Заметка 2.txt` и далее.
- Вырезание, копирование, вставка, отмена и повтор.
- Отправка заметок на личный HTTPS-приёмник Node.js в локальной сети.

## Сборка

Нужны Android Studio, JDK 17+ и Android SDK 35.

```powershell
gradle assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## Локальный HTTPS-приёмник

Нужен Node.js 18+. Создай TLS-сертификат с IP компьютера в SAN, случайный токен и задай пути к PEM-файлам в PowerShell:

```powershell
$env:WIFI_SERVER_CERT = (Resolve-Path .\server-cert.pem).Path
$env:WIFI_SERVER_KEY = (Resolve-Path .\server-key.pem).Path
$env:WIFI_UPLOAD_TOKEN = '<свой случайный токен>'
node .\outputs\wifi-server\server.js
```

Разреши Node.js входящие соединения в частной сети Windows. Телефон и компьютер должны быть в одной сети. Приёмник сохраняет файлы в `outputs/wifi-server/received`.

Для локальной сборки приложения укажи параметры в локальном `gradle.properties` и не публикуй этот файл:

```properties
wifiServerIp=<IP компьютера>
wifiUploadToken=<тот же случайный токен>
wifiCertPin=<SHA-256 DER-сертификата в hex без разделителей>
```

Без этих значений сетевая отправка отключена. Не публикуй токен или приватный ключ сервера.

## Структура

- `app/` — Android-приложение на Kotlin и Android Views.
- `outputs/wifi-server/server.js` — HTTPS-приёмник без внешних npm-пакетов.
- `outputs/Tekst-debug.apk` — готовая debug-сборка, если добавлена в репозиторий.
