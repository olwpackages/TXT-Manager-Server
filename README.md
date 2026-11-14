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

Нужны Node.js 18+ и OpenSSL. Телефон и компьютер должны быть в одной Wi-Fi сети.

### 1. Задайте адрес и токен

Откройте PowerShell в корне проекта. Подставьте IPv4 компьютера из настроек Wi-Fi и придумайте отдельный длинный токен:

```powershell
$ip = "<IP компьютера в локальной сети>"
$token = "<длинный случайный токен>"
$tlsDir = Join-Path $PWD "local-tls"
New-Item -ItemType Directory -Force $tlsDir | Out-Null
```

### 2. Создайте сертификат

```powershell
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 365 `
  -keyout (Join-Path $tlsDir "server-key.pem") `
  -out (Join-Path $tlsDir "server-cert.pem") `
  -subj "/CN=$ip" `
  -addext "subjectAltName=IP:$ip" `
  -addext "extendedKeyUsage=serverAuth"
```

### 3. Запустите сервер

```powershell
$env:WIFI_SERVER_CERT = (Resolve-Path (Join-Path $tlsDir "server-cert.pem")).Path
$env:WIFI_SERVER_KEY = (Resolve-Path (Join-Path $tlsDir "server-key.pem")).Path
$env:WIFI_UPLOAD_TOKEN = $token
node .\outputs\wifi-server\server.js
```

Разрешите Node.js входящие подключения для частной сети Windows и оставьте окно PowerShell открытым. Сервер принимает заметки в `outputs/wifi-server/received`.

## Сборка приложения

Укажите те же сетевые параметры в локальном корневом `gradle.properties`:

```properties
wifiServerIp=<IP компьютера>
wifiUploadToken=<токен сервера>
wifiCertPin=<SHA-256 отпечаток сертификата, DER, hex>
```

Отпечаток сертификата:

```powershell
openssl x509 -in .\local-tls\server-cert.pem -outform DER | openssl dgst -sha256
```

Скопируйте hex после `=` в `wifiCertPin`. После смены сертификата обновите pin и пересоберите приложение. Для сборки нужны Android SDK 35 и JDK 17+:

```powershell
gradle assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`. Для отправки заметки выберите в приложении **Действия → Отправить заметку**.

## Обновления приложения

Положите подписанный APK в `outputs/update/Tekst.apk`, затем заполните `outputs/update/latest.json`:

```json
{
  "versionCode": 44,
  "versionName": "1.43",
  "notes": "Краткое описание изменений"
}
```

`versionCode` должен быть выше версии на телефоне. Откройте **Действия → Проверить / скачать APK** и введите адрес сервера:

```text
https://<IP компьютера>:8765
```

Приложение проверит `/update.json`, скачает `/Tekst.apk` и откроет системный установщик. Для обновления поверх установленной версии подпись APK должна совпадать.
## Локальные секреты

IP и токен задаются владельцем и не хранятся в исходниках. Не публикуйте `server-key.pem`, токен или локальный `gradle.properties`. Значения, включённые в APK, можно извлечь; используйте отдельный токен для личного сервера.

## Support & development

[Telegram](https://t.me/s1lentpacket)