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
    curl -fsS "$LOCAL_HEALTHCHECK_URL" >/dev/null

    echo "==> Проверка публичного healthcheck"
    curl -fsS "$PUBLIC_HEALTHCHECK_URL" >/dev/null
fi

echo
echo "Деплой завершён успешно."
echo "Frontend: ${PUBLIC_URL}"
echo "Backend health: ${PUBLIC_HEALTHCHECK_URL}"
