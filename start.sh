#!/bin/bash

# Home Budget - Startup Script
# Автоматически освобождает порты и запускает серверы

# Resolve absolute path to the script's directory
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

echo "🧹 Очистка портов..."

# Kill any existing processes on common ports and all node/vite instances for this app
lsof -ti:3001,5173,5174,5175,5176,5177,5178,5179 | xargs kill -9 2>/dev/null
pkill -f "node index.js" 2>/dev/null
pkill -f "vite" 2>/dev/null

sleep 1

echo "🚀 Запуск Backend (порт 3001)..."
cd "$PROJECT_ROOT/server"
node index.js &
BACKEND_PID=$!

sleep 1

echo "🌐 Запуск Frontend (порт 5173)..."
cd "$PROJECT_ROOT/client"
npm run dev &
FRONTEND_PID=$!

echo ""
echo "✅ Приложение запущено!"
echo "   Frontend: http://localhost:5173"
echo "   Backend:  http://localhost:3001"
echo ""
echo "Для остановки нажмите Ctrl+C"

# Trap Ctrl+C to cleanup
cleanup() {
    echo ""
    echo "🛑 Останавливаем серверы..."
    kill $BACKEND_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    # Secondary cleanup to be sure
    lsof -ti:3001,5173 | xargs kill -9 2>/dev/null
    echo "👋 До свидания!"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Wait for processes
wait
