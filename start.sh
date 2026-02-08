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

echo "🧹 Очистка портов..."

# Kill any existing processes on common ports and all node/vite instances for this app
lsof -ti:3002,5174,5175,5176,5177,5178,5179 | xargs kill -9 2>/dev/null
pkill -f "node index.js" 2>/dev/null
pkill -f "vite" 2>/dev/null

sleep 1

echo "🚀 Запуск Backend (порт 3002)..."
cd "$PROJECT_ROOT/server"
nohup node index.js > backend.log 2>&1 &
BACKEND_PID=$!

sleep 1

echo "🌐 Запуск Frontend (порт 5174)..."
cd "$PROJECT_ROOT/client"
nohup npm run dev -- --host 0.0.0.0 --port 5174 > frontend.log 2>&1 &
FRONTEND_PID=$!

echo ""
echo "✅ Приложение запущено!"
echo "   Frontend: http://217.114.8.82:5174"
echo "   Backend:  http://217.114.8.82:3002"
echo ""
echo "⚠️ ВНИМАНИЕ: Это режим для разработки. Для реального использования соберите проект командой 'npm run start:prod'."
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
