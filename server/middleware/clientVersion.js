// Версия клиента из заголовков запроса. KMP-приложение шлёт X-App-Version и X-App-Platform
// начиная с 2.2.4, веб — только X-App-Platform: web (он деплоится вместе с сервером и всегда
// актуален). Запрос без заголовков — от старого приложения: ему сохраняется прежнее поведение.

const APP_VERSION_HEADER = 'x-app-version';
const APP_PLATFORM_HEADER = 'x-app-platform';

// Первая версия приложения, которая знает про запрет удаления непустой копилки
const MIN_VERSION_SAVINGS_GOAL_NOT_EMPTY = '2.2.4';

// «2.2.4», «v2.2.4», «2.2.4-debug» → [2, 2, 4]; нераспознанная версия → null
function parseVersion(value) {
    const match = /^v?(\d+)\.(\d+)(?:\.(\d+))?/.exec(String(value ?? '').trim());
    if (!match) return null;
    return [Number(match[1]), Number(match[2]), Number(match[3] ?? 0)];
}

function compareVersions(a, b) {
    for (let i = 0; i < 3; i += 1) {
        if (a[i] !== b[i]) return a[i] - b[i];
    }
    return 0;
}

// Клиент поддерживает поведение, появившееся в версии minVersion
function clientSupports(req, minVersion) {
    if (String(req.get(APP_PLATFORM_HEADER) ?? '').trim().toLowerCase() === 'web') return true;
    const version = parseVersion(req.get(APP_VERSION_HEADER));
    return version !== null && compareVersions(version, parseVersion(minVersion)) >= 0;
}

module.exports = {
    MIN_VERSION_SAVINGS_GOAL_NOT_EMPTY,
    clientSupports,
    parseVersion,
};
