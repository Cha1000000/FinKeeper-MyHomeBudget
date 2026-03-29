#!/bin/bash

# Home Budget - Startup Script (Fire and Forget Mode)
# Automatically frees ports and starts servers, then exits immediately

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
load_env_file "$PROJECT_ROOT/.env.development"
load_env_file "$PROJECT_ROOT/.env.remote"
load_env_file "$PROJECT_ROOT/server/.env"
load_env_file "$PROJECT_ROOT/server/.env.development"
load_env_file "$PROJECT_ROOT/server/.env.remote"
load_env_file "$PROJECT_ROOT/client/.env"
load_env_file "$PROJECT_ROOT/client/.env.development"
load_env_file "$PROJECT_ROOT/client/.env.remote"

echo "🧹 Очистка портов..."

# Kill any existing processes on common ports and all node/vite instances for this app
lsof -ti:3002,5174,5175,5176,5177,5178,5179 | xargs kill -9 2>/dev/null
pkill -f "node index.js" 2>/dev/null
pkill -f "vite" 2>/dev/null

sleep 1

echo "🚀 Запуск Backend (порт 3002)..."
cd "$PROJECT_ROOT/server"
nohup env JWT_SECRET="${JWT_SECRET:-local-dev-jwt-secret}" node index.js > backend.log 2>&1 &

sleep 1

echo "🌐 Запуск Frontend (порт 5174)..."
cd "$PROJECT_ROOT/client"
nohup npm run dev -- --host 0.0.0.0 --port 5174 > frontend.log 2>&1 &

echo ""
echo "✅ Приложение запущено!"
echo "   Frontend: http://157.22.172.217:5174"
echo "   Backend:  http://157.22.172.217:3002"
echo ""
echo "⚠️ ВНИМАНИЕ: Это режим для разработки. Для реального использования соберите проект командой 'npm run start:prod'."
echo ""

echo "Скрипт завершён. Серверы работают в фоне. Для остановки используйте: lsof -ti:3002,5174 | xargs kill -9"
