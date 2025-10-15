# Безопасность вебхуков WAHook

## Обзор
Модуль теперь использует HMAC-SHA256 для подписи всех исходящих вебхуков и включает Device-ID для идентификации устройства.

## Настройка

### 1. Получение секрета после первого запуска
После установки и запуска модуля на устройстве выполните:

```bash
adb shell "run-as com.whatsapp cat /data/data/com.whatsapp/shared_prefs/wahook_prefs.xml | grep hmac_secret"
```

Вы получите строку вида:
```xml
<string name="hmac_secret">a1b2c3d4e5f6...64_hex_символа</string>
```

Скопируйте это значение (64 hex символа).

### 2. Получение Device ID
Выполните на устройстве:

```bash
adb shell settings get secure android_id
```

Или посмотрите в логах LSPosed при загрузке модуля:
```bash
adb logcat | grep "WAHook: deviceId="
```

### 3. Настройка n8n

1. Создайте Webhook Trigger node в n8n.
2. Добавьте сразу после него Code Node.
3. Вставьте код из файла `n8n-webhook-validation.js`.
4. Замените `HMAC_SECRET_HEX` на секрет из п.1.
5. Замените `allowedDevices` на ваш Device ID из п.2.

## Формат запроса

### HTTP заголовки
```
POST /webhook/your-id HTTP/1.1
Content-Type: application/json; charset=utf-8
X-Signature: sha256=<64_hex_символа>
X-Device-Id: <android_id>
```

### JSON body
```json
{
  "type": "text",
  "docid": 105,
  "text": "Hello",
  "chat_jid": "79936005920@s.whatsapp.net",
  "chat_name": "Contact Name",
  "timestamp": 1759484121000,
  "device_id": "<android_id>",
  "source": "wahook"
}
```

## Проверка подписи (алгоритм)

1. Получить тело запроса как строку JSON (точно как было отправлено).
2. Вычислить HMAC-SHA256(body_bytes, secret_from_hex).
3. Сравнить с подписью из заголовка `X-Signature` (без префикса `sha256=`).
4. Если не совпадают — отклонить запрос.

## Ротация секрета

Чтобы сменить секрет:
1. Удалите файл настроек на устройстве:
   ```bash
   adb shell "run-as com.whatsapp rm /data/data/com.whatsapp/shared_prefs/wahook_prefs.xml"
   ```
2. Перезапустите WhatsApp — модуль сгенерирует новый секрет.
3. Получите новый секрет (см. п.1 Настройки) и обновите n8n.

## Логи безопасности

При загрузке модуля в `adb logcat | grep WAHook` вы увидите:
```
WAHook: generated new HMAC secret  (при первом запуске)
WAHook: loaded existing HMAC secret  (при последующих)
WAHook: deviceId=<android_id>
```

## Что защищено

- ✅ Подпись каждого вебхука (невозможно подделать без секрета).
- ✅ Идентификация устройства (можно whitelist только свои девайсы).
- ✅ Защита от replay-атак частично (n8n может добавить проверку timestamp).

## Дополнительные улучшения (опционально)

- Добавить nonce/request_id в payload для защиты от replay.
- Использовать timestamp в формуле HMAC.
- TLS client certificate для HTTPS (требует сертификат на устройстве).

