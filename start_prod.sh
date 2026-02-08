#!/bin/bash

# Home Budget - Production Startup Script
# Builds client and starts the server

# Ensure bash (in case started with sh)
if [ -z "$BASH_VERSION" ]; then
    exec /bin/bash "$0" "$@"
fi

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

echo "🧹 Очистка порта 3002..."
lsof -ti:3002 | xargs kill -9 2>/dev/null
pkill -f "node index.js" 2>/dev/null

echo "🛠️  Сборка Frontend (prod)..."
cd "$PROJECT_ROOT/client"
npm run build
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
    echo "❌ Сборка не удалась. Остановка."
    exit 1
fi

echo "🚀 Запуск Backend (prod, порт 3002)..."
cd "$PROJECT_ROOT/server"
nohup node index.js > backend.log 2>&1 &
BACKEND_PID=$!

echo ""
echo "✅ Приложение запущено в PROD!"
echo "   http://217.114.8.82:3002"
echo ""
echo "Для остановки нажмите Ctrl+C"

cleanup() {
    echo ""
    echo "🛑 Останавливаем сервер..."
    kill $BACKEND_PID 2>/dev/null
    lsof -ti:3002 | xargs kill -9 2>/dev/null
    echo "👋 До свидания!"
    exit 0
}

trap cleanup SIGINT SIGTERM

wait
