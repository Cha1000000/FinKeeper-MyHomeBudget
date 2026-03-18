#!/bin/bash

# If started with sh, re-run with bash for proper trap handling
if [ -z "$BASH_VERSION" ]; then
    exec /bin/bash "$0" "$@"
fi

# Home Budget - Startup Script
# Автоматически освобождает порты и запускает серверы

# Resolve absolute path to the script's directory
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
load_env_file "$PROJECT_ROOT/.env.local"
load_env_file "$PROJECT_ROOT/server/.env"
load_env_file "$PROJECT_ROOT/server/.env.local"
load_env_file "$PROJECT_ROOT/client/.env"
load_env_file "$PROJECT_ROOT/client/.env.local"

echo "🧹 Очистка портов..."

# Kill any existing processes on common ports and all node/vite instances for this app
lsof -ti:3002,5174,5175,5176,5177,5178,5179 | xargs kill -9 2>/dev/null
pkill -f "node index.js" 2>/dev/null
pkill -f "vite" 2>/dev/null

sleep 1

echo "🚀 Запуск Backend (порт 3002)..."
cd "$PROJECT_ROOT/server"
env JWT_SECRET="${JWT_SECRET:-local-dev-jwt-secret}" node index.js &
BACKEND_PID=$!

sleep 1

echo "🌐 Запуск Frontend (порт 5174)..."
cd "$PROJECT_ROOT/client"
npm run dev -- --host 0.0.0.0 --port 5174 &
FRONTEND_PID=$!

echo ""
echo "✅ Приложение запущено!"
echo "   Frontend: http://localhost:5174"
echo "   Backend:  http://localhost:3002"
echo ""
echo "Для остановки нажмите Ctrl+C"

# Trap Ctrl+C to cleanup
cleanup() {
    echo ""
    echo "🛑 Останавливаем серверы..."
    kill $BACKEND_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    # Secondary cleanup to be sure
    lsof -ti:3002,5174 | xargs kill -9 2>/dev/null
    echo "👋 До свидания!"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Wait for processes
wait
