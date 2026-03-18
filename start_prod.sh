#!/bin/bash

# Home Budget - Production Startup Script (Fire and Forget Mode)
# Builds client and starts the server, then exits immediately

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

load_env_file() {
    local env_file="$1"
    if [ -f "$env_file" ]; then
        echo "🔐 Загрузка env: ${env_file#$PROJECT_ROOT/}"
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
HEALTHCHECK_URL="http://127.0.0.1:${PORT}/api/health"
PUBLIC_URL="${APP_BASE_URL:-http://127.0.0.1:${PORT}}"

echo "🧹 Очистка порта ${PORT}..."
lsof -ti:"$PORT" | xargs kill -9 2>/dev/null
pkill -f "node index.js" 2>/dev/null

echo "🛠️  Сборка Frontend (prod)..."
cd "$PROJECT_ROOT/client"
npm run build
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
    echo "❌ Сборка не удалась. Остановка."
    exit 1
fi

if [ -z "${JWT_SECRET:-}" ]; then
    echo "❌ Для production запуска требуется JWT_SECRET."
    echo "   Пример:"
    echo "   JWT_SECRET='your-strong-secret' ALLOWED_ORIGINS='https://your-domain.com' bash start_prod.sh"
    exit 1
fi

if [ -z "${ALLOWED_ORIGINS:-}" ]; then
    echo "❌ Для production запуска требуется ALLOWED_ORIGINS."
    echo "   Пример:"
    echo "   JWT_SECRET='your-strong-secret' ALLOWED_ORIGINS='https://your-domain.com' bash start_prod.sh"
    exit 1
fi

echo "🚀 Запуск Backend (prod, порт ${PORT})..."
cd "$PROJECT_ROOT/server"
nohup env NODE_ENV=production PORT="$PORT" JWT_SECRET="$JWT_SECRET" ALLOWED_ORIGINS="$ALLOWED_ORIGINS" node index.js > backend.log 2>&1 &
BACKEND_PID=$!

sleep 2

if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    echo "❌ Backend завершился сразу после запуска."
    echo "   Последние строки server/backend.log:"
    tail -n 50 backend.log 2>/dev/null || true
    exit 1
fi

if command -v curl >/dev/null 2>&1; then
    if ! curl -fsS "$HEALTHCHECK_URL" >/dev/null 2>&1; then
        echo ""
        echo "⚠️  Backend процесс запущен, но healthcheck не прошёл: $HEALTHCHECK_URL"
        echo "   Последние строки server/backend.log:"
        tail -n 50 backend.log 2>/dev/null || true
        exit 1
    fi
fi

echo ""
echo "✅ Приложение запущено в PROD!"
echo "   ${PUBLIC_URL}"
echo ""

echo "Скрипт завершён. Сервер работает в фоне. Для остановки используйте: lsof -ti:${PORT} | xargs kill -9"
