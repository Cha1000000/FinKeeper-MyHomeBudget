#!/bin/bash

# Home Budget - Production Startup Script (Fire and Forget Mode)
# Builds client and starts the server, then exits immediately

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

echo ""
echo "✅ Приложение запущено в PROD!"
echo "   http://217.114.8.82:3002"
echo ""

echo "Скрипт завершён. Сервер работает в фоне. Для остановки используйте: lsof -ti:3002 | xargs kill -9"
