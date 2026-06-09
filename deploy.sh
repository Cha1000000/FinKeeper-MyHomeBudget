#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

load_env_file() {
    local env_file="$1"
    if [ -f "$env_file" ]; then
        set -a
        . "$env_file"
        set +a
    fi
}

# Проверка доступности URL с ретраями: сервису нужно ~секунду на старт после рестарта,
# поэтому не падаем на первом отказе, а ждём. Кол-во попыток/паузу можно переопределить
# переменными окружения HEALTHCHECK_RETRIES / HEALTHCHECK_DELAY.
healthcheck() {
    local url="$1"
    local retries="${HEALTHCHECK_RETRIES:-15}"
    local delay="${HEALTHCHECK_DELAY:-1}"
    local i=1
    while [ "$i" -le "$retries" ]; do
        if curl -fsS "$url" >/dev/null 2>&1; then
            echo "    OK (попытка ${i}/${retries})"
            return 0
        fi
        sleep "$delay"
        i=$((i + 1))
    done
    echo "    НЕ ответил за $((retries * delay)) сек: $url"
    return 1
}

# Авто-коммит состояния в локальный git-репозиторий проекта (если он есть).
# Никогда не прерывает деплой: при отсутствии git/.git, отсутствии изменений или
# ошибке git просто пропускает шаг.
git_snapshot() {
    [ -d "$PROJECT_ROOT/.git" ] || return 0
    command -v git >/dev/null 2>&1 || return 0
    git -C "$PROJECT_ROOT" add -A 2>/dev/null || { echo "    git: не удалось добавить файлы, пропускаю"; return 0; }
    if git -C "$PROJECT_ROOT" diff --cached --quiet 2>/dev/null; then
        return 0  # изменений нет — коммит не нужен
    fi
    if git -C "$PROJECT_ROOT" \
            -c user.email="${GIT_AUTHOR_EMAIL:-admin@finkeeper24.ru}" \
            -c user.name="${GIT_AUTHOR_NAME:-FinKeeper Deploy}" \
            commit -qm "$1" 2>/dev/null; then
        echo "    git: коммит — $1"
    else
        echo "    git: коммит не удался, пропускаю"
    fi
    return 0
}

load_env_file "$PROJECT_ROOT/.env"
load_env_file "$PROJECT_ROOT/.env.production"
load_env_file "$PROJECT_ROOT/server/.env"
load_env_file "$PROJECT_ROOT/server/.env.production"
load_env_file "$PROJECT_ROOT/client/.env"
load_env_file "$PROJECT_ROOT/client/.env.production"

PORT="${PORT:-3002}"
SERVICE_NAME="${SERVICE_NAME:-finkeeper}"
PUBLIC_URL="${APP_BASE_URL:-https://app.finkeeper24.ru}"
LOCAL_HEALTHCHECK_URL="http://127.0.0.1:${PORT}/api/health"
PUBLIC_HEALTHCHECK_URL="${PUBLIC_URL%/}/api/health"

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
    echo "Этот скрипт нужно запускать от root или через sudo."
    exit 1
fi

echo "==> Git: снимок загруженных файлов (до сборки)"
git_snapshot "Перед деплоем — загруженные файлы ($(date '+%Y-%m-%d %H:%M:%S'))"

echo "==> Обновление зависимостей root"
npm install

echo "==> Обновление зависимостей client"
npm install --include=dev --prefix "$PROJECT_ROOT/client"

echo "==> Обновление зависимостей server"
npm install --prefix "$PROJECT_ROOT/server"

echo "==> Сборка frontend"
npm run build --prefix "$PROJECT_ROOT/client"

echo "==> Перезапуск сервиса ${SERVICE_NAME}"
systemctl restart "$SERVICE_NAME"

if ! systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "Сервис ${SERVICE_NAME} не запустился."
    systemctl status "$SERVICE_NAME" --no-pager || true
    exit 1
fi

if command -v curl >/dev/null 2>&1; then
    echo "==> Проверка локального backend healthcheck"
    if ! healthcheck "$LOCAL_HEALTHCHECK_URL"; then
        echo "Локальный healthcheck не прошёл."
        systemctl status "$SERVICE_NAME" --no-pager || true
        exit 1
    fi

    echo "==> Проверка публичного healthcheck"
    if ! healthcheck "$PUBLIC_HEALTHCHECK_URL"; then
        echo "Публичный healthcheck не прошёл (проверь nginx/SSL)."
        exit 1
    fi
fi

echo "==> Git: фиксация результата деплоя"
git_snapshot "Деплой OK ($(date '+%Y-%m-%d %H:%M:%S'))"

echo
echo "Деплой завершён успешно."
echo "Frontend: ${PUBLIC_URL}"
echo "Backend health: ${PUBLIC_HEALTHCHECK_URL}"
