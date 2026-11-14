# Текст

Тёмный редактор TXT-файлов для Android: открытие и сохранение заметок, базовое редактирование, настройка оформления, передача текста на компьютер и получение обновлений через личный HTTPS-сервер.

## Возможности

| Раздел | Описание |
| --- | --- |
| Файлы | Открытие и сохранение `.txt` через системный выбор файлов Android |
| Редактирование | Вырезать, копировать, вставить, отменить и повторить действие |
| Заметки | Счётчик слов и символов, переименование, создание папок |
| Оформление | Акцентный цвет и полупрозрачное меню |
| Локальная сеть | Отправка заметок и раздача обновлений по HTTPS |

## Запуск Node.js-сервера

Нужен Node.js 18+ и OpenSSL. Телефон и компьютер должны быть в одной Wi-Fi сети. Откройте PowerShell в корне проекта и укажите **свой** IP компьютера в локальной сети и новый случайный токен. Используйте IPv4-адрес Wi-Fi адаптера компьютера:

```powershell
$ip = "<IP компьютера в локальной сети>"
$token = "<длинный случайный токен>"
$tlsDir = Join-Path $PWD "local-tls"
New-Item -ItemType Directory -Force $tlsDir | Out-Null
```

Создайте локальный TLS-сертификат, в котором IP указан как SAN:

```powershell
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 365 `
  -keyout (Join-Path $tlsDir "server-key.pem") `
  -out (Join-Path $tlsDir "server-cert.pem") `
  -subj "/CN=$ip" `
  -addext "subjectAltName=IP:$ip" `
  -addext "extendedKeyUsage=serverAuth"
```

Запустите сервер:

```powershell
$env:WIFI_SERVER_CERT = (Resolve-Path (Join-Path $tlsDir "server-cert.pem")).Path
$env:WIFI_SERVER_KEY = (Resolve-Path (Join-Path $tlsDir "server-key.pem")).Path
$env:WIFI_UPLOAD_TOKEN = $token
node .\outputs\wifi-server\server.js
```

Разрешите Node.js входящие подключения для частной сети Windows. Сервер слушает порт `8765`, принимает заметки в `outputs/wifi-server/received` и раздаёт обновления из `outputs/update`. Он не использует сторонние npm-пакеты.

## Сборка приложения

В корневом `gradle.properties` укажите собственный IP, тот же токен и SHA-256 отпечаток сертификата. При ручной проверке обновлений в приложении введите адрес `https://<ваш-IP>:8765`.

```properties
wifiServerIp=<IP компьютера в локальной сети>
wifiUploadToken=<тот же случайный токен>
wifiCertPin=<SHA-256 отпечаток DER-сертификата, hex без разделителей>
```

Получить отпечаток:

```powershell
openssl x509 -in .\local-tls\server-cert.pem -outform DER | openssl dgst -sha256
```

Скопируйте hex после `=` в `wifiCertPin`. При пересоздании сертификата pin меняется, поэтому приложение нужно пересобрать. Сборка требует Android SDK 35 и JDK 17+:

```powershell
gradle assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`. Для установки поверх уже установленной версии нужна совместимая подпись.

## Публикация обновления

Положите подписанный APK в `outputs/update/Tekst.apk`. В `outputs/update/latest.json` укажите версию с `versionCode` больше установленной:

```json
{
  "versionCode": 43,
  "versionName": "1.42",
  "notes": "Краткое описание изменений"
}
```

Сервер отдаёт эти данные по `/update.json`, APK — по `/Tekst.apk`. `work/build_update.ps1` увеличивает версию, собирает debug APK и подготавливает файлы обновления.

## Хранение локальных данных

Токен, TLS-приватный ключ и IP не хранятся в исходниках. Не публикуйте токен, `server-key.pem` или локальный `gradle.properties` с секретами. Токен, встроенный в APK, можно извлечь, поэтому используйте его только для личного сервера в доверенной сети.