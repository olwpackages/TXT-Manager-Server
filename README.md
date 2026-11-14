# Текст

Небольшой TXT-редактор для Android. Заметки можно хранить через системный выбор файлов или отправлять на компьютер по HTTPS в локальной Wi-Fi сети. Тот же Node.js-сервер отдаёт APK и описание доступной версии.

## Возможности

<div align="center">
<table>
<tr>
<td width="50%" valign="top">

### Редактор

Открывает и сохраняет TXT, считает слова и символы. Есть отмена и повтор действий.

</td>
<td width="50%" valign="top">

### Файлы

Переименование заметок и создание папок через системный выбор хранилища Android.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Оформление

Настройка акцентного цвета и полупрозрачного меню.

</td>
<td width="50%" valign="top">

### Сервер

Принимает заметки и отдаёт обновления по HTTPS на порту `8765`.

</td>
</tr>
</table>
</div>

## Передача заметки и обновление

```text
Телефон ── HTTPS /upload ──▶ Компьютер
       ◀── /update.json и /Tekst.apk ──
```

Сервер доступен в локальной сети; внешний хостинг не нужен. Полученные TXT сохраняются в `outputs/wifi-server/received`.

## Node.js-сервер

Нужны Node.js 18+ и OpenSSL. Откройте PowerShell в корне проекта, укажите LAN IPv4 компьютера и создайте отдельный токен:

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

Разрешите Node.js входящие подключения в брандмауэре Windows для частной сети. Оставьте окно сервера открытым и подключите телефон к той же Wi-Fi сети.

## Сборка приложения

Для отправки заметок добавьте в локальный корневой `gradle.properties` IP компьютера, тот же токен и отпечаток сертификата:

```properties
wifiServerIp=<IP компьютера>
wifiUploadToken=<токен сервера>
wifiCertPin=<SHA-256 отпечаток сертификата, DER, hex>
```

Получить отпечаток:

```powershell
openssl x509 -in .\local-tls\server-cert.pem -outform DER | openssl dgst -sha256
```

Вставьте hex после `=` в `wifiCertPin`. После смены сертификата pin тоже нужно обновить.

Нужны Android SDK 35 и JDK 17+:

```powershell
gradle assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`. Для отправки заметки в приложении выберите **Действия → Отправить заметку**.

## Обновления приложения

Положите APK в `outputs/update/Tekst.apk`, а описание версии — в `outputs/update/latest.json`:

```json
{
  "versionCode": 44,
  "versionName": "1.43",
  "notes": "Краткое описание изменений"
}
```

`versionCode` должен быть больше установленного. На телефоне откройте **Действия → Проверить / скачать APK** и введите `https://<IP компьютера>:8765`. Приложение получит описание по `/update.json`, скачает APK по `/Tekst.apk` и передаст его системному установщику. Для установки поверх текущей версии нужна совместимая подпись.

## Локальные секреты

IP и токен задаются владельцем и не хранятся в исходниках. Не публикуйте `server-key.pem`, токен или локальный `gradle.properties`. Значения, включённые в APK, можно извлечь; используйте отдельный токен для личного сервера.

## Support & development

[Telegram](https://t.me/s1lentpacket)