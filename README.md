# Текст

Небольшой TXT-редактор для Android. Заметки можно хранить через системный выбор файлов или отправлять на компьютер по HTTPS в локальной Wi-Fi сети. Тот же Node.js-сервер умеет раздавать APK и описание доступной версии.

## Возможности

| Часть | Что делает |
| --- | --- |
| Редактор | Открывает и сохраняет TXT, считает слова и символы, поддерживает отмену и повтор |
| Файлы | Переименовывает заметки, создаёт папки через системный выбор хранилища |
| Оформление | Позволяет настроить акцентный цвет и полупрозрачное меню |
| Сервер | Принимает заметки и отдаёт APK обновлений через HTTPS на порту `8765` |

## Передача заметки и обновление

```text
Телефон ── HTTPS /upload ──▶ Компьютер
       ◀── /update.json и /Tekst.apk ──
```

Сервер доступен только в пределах сети, куда подключён компьютер. Внешний хостинг не требуется. Полученные TXT сохраняются в `outputs/wifi-server/received`.

## Node.js-сервер

Нужны Node.js 18+ и OpenSSL. В PowerShell из корня проекта укажите LAN IPv4 компьютера и создайте отдельный токен:

```powershell
$ip = "<IP компьютера в локальной сети>"
$token = "<длинный случайный токен>"
$tlsDir = Join-Path $PWD "local-tls"
New-Item -ItemType Directory -Force $tlsDir | Out-Null
```

Создайте самоподписанный сертификат с IP в SAN:

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

Разрешите Node.js входящие подключения в брандмауэре Windows для частной сети. Окно сервера должно оставаться открытым. Для отправки и телефона, и компьютера подключите к одной Wi-Fi сети.

## Сборка приложения

Для отправки заметок приложению нужны адрес компьютера, тот же токен и отпечаток серверного сертификата. Добавьте их в локальный корневой `gradle.properties`:

```properties
wifiServerIp=<IP компьютера>
wifiUploadToken=<токен сервера>
wifiCertPin=<SHA-256 отпечаток сертификата, DER, hex>
```

Получить отпечаток:

```powershell
openssl x509 -in .\local-tls\server-cert.pem -outform DER | openssl dgst -sha256
```

Вставьте hex-строку после `=` в `wifiCertPin`. При смене сертификата pin также изменится.

Нужны Android SDK 35 и JDK 17+:

```powershell
gradle assembleDebug
```

APK создаётся в `app/build/outputs/apk/debug/app-debug.apk`. В приложении отправка доступна через **Действия → Отправить заметку**.

## Сервер обновлений

В `outputs/update` разместите `Tekst.apk` и `latest.json`:

```json
{
  "versionCode": 44,
  "versionName": "1.43",
  "notes": "Краткое описание изменений"
}
```

`versionCode` должен быть выше установленного. Сервер возвращает JSON по `/update.json` и APK по `/Tekst.apk`. На телефоне откройте **Действия → Проверить / скачать APK** и введите `https://<IP компьютера>:8765`. Для обновления поверх установленной версии нужна совместимая подпись APK.

## Локальные секреты

IP и токен задаются владельцем проекта и не хранятся в исходниках. Не публикуйте `server-key.pem`, токен или локальный `gradle.properties`. Значения, включённые в APK, можно извлечь; токен подходит для ограничения доступа к личному серверу, но не является секретом от пользователя приложения.