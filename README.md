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

> Один сервер — два сценария: отправка TXT с телефона и раздача обновлений приложения. Телефон и компьютер должны быть в одной Wi-Fi сети.

<div align="center">
<table>
<tr>
<td align="center" width="33%"><strong>01</strong><br>укажите свой IP<br><sub>адрес компьютера в Wi-Fi</sub></td>
<td align="center" width="33%"><strong>02</strong><br>создайте токен<br><sub>длинная случайная строка</sub></td>
<td align="center" width="33%"><strong>03</strong><br>запустите сервер<br><sub>Node.js · HTTPS · порт 8765</sub></td>
</tr>
</table>
</div>

### Настройка

Нужны Node.js 18+ и OpenSSL. В PowerShell из корня проекта задайте **собственный** LAN IPv4 компьютера и токен:

```powershell
$ip = "<IP компьютера в локальной сети>"
$token = "<длинный случайный токен>"
$tlsDir = Join-Path $PWD "local-tls"
New-Item -ItemType Directory -Force $tlsDir | Out-Null
```

### Сертификат для HTTPS

Сертификат привязывается к IP компьютера. Не используйте чужой или опубликованный приватный ключ.

```powershell
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 365 `
  -keyout (Join-Path $tlsDir "server-key.pem") `
  -out (Join-Path $tlsDir "server-cert.pem") `
  -subj "/CN=$ip" `
  -addext "subjectAltName=IP:$ip" `
  -addext "extendedKeyUsage=serverAuth"
```

### Запуск

```powershell
$env:WIFI_SERVER_CERT = (Resolve-Path (Join-Path $tlsDir "server-cert.pem")).Path
$env:WIFI_SERVER_KEY = (Resolve-Path (Join-Path $tlsDir "server-key.pem")).Path
$env:WIFI_UPLOAD_TOKEN = $token
node .\outputs\wifi-server\server.js
```

Оставьте окно PowerShell открытым и разрешите Node.js входящие подключения для частной сети Windows. Принятые заметки появятся в `outputs/wifi-server/received`.

## Подключение приложения

Для отправки файлов приложение должно быть собрано с IP, токеном и pin сертификата сервера. Добавьте значения в локальный корневой `gradle.properties`:

```properties
wifiServerIp=<IP компьютера>
wifiUploadToken=<тот же токен>
wifiCertPin=<SHA-256 отпечаток DER-сертификата, hex>
```

Рассчитать pin:

```powershell
openssl x509 -in .\local-tls\server-cert.pem -outform DER | openssl dgst -sha256
```

Вставьте hex-строку после `=` в `wifiCertPin`. При создании нового сертификата pin меняется. Соберите приложение:

```powershell
gradle assembleDebug
```

Затем на телефоне выберите **Действия → Отправить заметку**. APK сборки находится в `app/build/outputs/apk/debug/app-debug.apk`.

## Публикация обновления

<div align="center">
<table>
<tr>
<td align="center" width="50%"><strong>1 · разместите APK</strong><br><code>outputs/update/Tekst.apk</code></td>
<td align="center" width="50%"><strong>2 · опишите версию</strong><br><code>outputs/update/latest.json</code></td>
</tr>
</table>
</div>

Пример `latest.json`:

```json
{
  "versionCode": 44,
  "versionName": "1.43",
  "notes": "Краткое описание изменений"
}
```

Укажите `versionCode` выше установленного на телефоне. В приложении откройте **Действия → Проверить / скачать APK** и введите адрес:

```text
https://<IP компьютера>:8765
```

Сервер отдаст описание версии через `/update.json`, затем APK через `/Tekst.apk`. Android откроет системный установщик; для обновления поверх существующей версии подпись должна совпадать.
## Локальные секреты

IP и токен задаются владельцем и не хранятся в исходниках. Не публикуйте `server-key.pem`, токен или локальный `gradle.properties`. Значения, включённые в APK, можно извлечь; используйте отдельный токен для личного сервера.

## Support & development

[Telegram](https://t.me/s1lentpacket)