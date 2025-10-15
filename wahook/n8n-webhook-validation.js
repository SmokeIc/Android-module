// n8n Code Node для проверки HMAC-подписи входящих вебхуков от wahook
// Вставьте этот код в Code Node сразу после Webhook Trigger

const crypto = require('crypto');

// ВАЖНО: замените на ваш секрет из SharedPreferences устройства
// После первого запуска модуля выполните на устройстве:
// adb shell "run-as com.whatsapp cat /data/data/com.whatsapp/shared_prefs/wahook_prefs.xml | grep hmac_secret"
// или посмотрите в логах: adb logcat | grep "WAHook: generated new HMAC secret"
const HMAC_SECRET_HEX = 'ВАШ_СЕКРЕТ_ИЗ_SHARED_PREFS'; // 64 hex символа (32 байта)

// Получить заголовки и тело запроса
const headers = $input.all()[0].json.headers;
const body = $input.all()[0].json.body;

// Проверка наличия подписи
const signatureHeader = headers['x-signature'];
const deviceId = headers['x-device-id'];

if (!signatureHeader) {
    throw new Error('Missing X-Signature header');
}

if (!deviceId) {
    throw new Error('Missing X-Device-Id header');
}

// Парсинг подписи (формат: sha256=<hex>)
const parts = signatureHeader.split('=');
if (parts.length !== 2 || parts[0] !== 'sha256') {
    throw new Error('Invalid X-Signature format');
}
const receivedSignature = parts[1];

// Вычисление ожидаемой подписи
const bodyString = JSON.stringify(body);
const secretBuffer = Buffer.from(HMAC_SECRET_HEX, 'hex');
const hmac = crypto.createHmac('sha256', secretBuffer);
hmac.update(bodyString, 'utf8');
const expectedSignature = hmac.digest('hex');

// Проверка подписи (timing-safe сравнение)
if (!crypto.timingSafeEqual(Buffer.from(receivedSignature, 'hex'), Buffer.from(expectedSignature, 'hex'))) {
    throw new Error('Invalid signature');
}

// Опционально: проверить device_id в whitelist
const allowedDevices = ['ВАШ_ANDROID_ID']; // замените на реальный ANDROID_ID устройства
if (!allowedDevices.includes(deviceId)) {
    throw new Error('Unknown device: ' + deviceId);
}

// Валидация пройдена, добавляем флаг
return {
    ...body,
    verified: true,
    device_id: deviceId
};

