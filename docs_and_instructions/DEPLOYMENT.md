# Домашняя бухгалтерия — Инструкция по развёртыванию

## Требования

- Node.js версии 18 или выше
- npm

## Переменные окружения

### Обязательные

```bash
export JWT_SECRET="replace-with-long-random-secret"
export ALLOWED_ORIGINS="http://localhost:5174"
```

### Дополнительные

```bash
export PORT=3002
export JSON_BODY_LIMIT=1mb
export NODE_ENV=development
export RATE_LIMIT_WINDOW_MS=900000
export AUTH_RATE_LIMIT_MAX=5
export PASSWORD_RATE_LIMIT_MAX=5
export RESTORE_RATE_LIMIT_MAX=3
```

### Пояснения

- `JWT_SECRET` обязателен во всех режимах запуска
- `ALLOWED_ORIGINS` задаётся списком origin через запятую
- в production для `ALLOWED_ORIGINS` нужно указывать только доверенные домены
- `PORT` по умолчанию равен `3002`
- при превышении лимитов сервер отвечает `429` и выставляет `Retry-After`

## Установка

1. Клонируйте репозиторий или скопируйте файлы проекта на сервер.

1. Установите зависимости проекта:

```bash
npm install
```

1. Установите зависимости клиента:

```bash
cd client && npm install && cd ..
```

## Запуск в режиме разработки

1. Задайте переменные окружения для локальной разработки:

```bash
export JWT_SECRET="replace-with-long-random-secret"
export ALLOWED_ORIGINS="http://localhost:5174"
export PORT=3002
export JSON_BODY_LIMIT=1mb
export NODE_ENV=development
export RATE_LIMIT_WINDOW_MS=900000
export AUTH_RATE_LIMIT_MAX=5
export PASSWORD_RATE_LIMIT_MAX=5
export RESTORE_RATE_LIMIT_MAX=3
```

1. Запустите приложение:

```bash
npm start
```

После запуска:

- Frontend доступен по адресу `http://localhost:5174`
- Backend API доступен по адресу `http://localhost:3002`
- если нужен другой frontend origin, добавьте его в `ALLOWED_ORIGINS`

## Запуск на production-сервере

1. Задайте production-переменные окружения:

```bash
export NODE_ENV=production
export JWT_SECRET="replace-with-long-random-secret"
export ALLOWED_ORIGINS="https://your-domain.example"
export PORT=3002
export JSON_BODY_LIMIT=1mb
export RATE_LIMIT_WINDOW_MS=900000
export AUTH_RATE_LIMIT_MAX=5
export PASSWORD_RATE_LIMIT_MAX=5
export RESTORE_RATE_LIMIT_MAX=3
```

1. Запустите production-сборку и сервер:

```bash
npm run start:prod
```

Команда:

- собирает клиентскую часть через `client`
- запускает Node.js сервер на порту из `PORT` или `3002` по умолчанию

После запуска приложение будет доступно по адресу `http://<IP-адрес-вашего-сервера>:3002`.

## Структура портов

| Порт | Назначение |
| ---- | ---------- |
| 3002 | Основной порт приложения по умолчанию, API и статика |
| 5174 | Порт frontend для локальной разработки |

## Управление сервером

Для остановки процесса нажмите `Ctrl+C` в терминале, где запущена команда.

Для постоянной работы сервера рекомендуется использовать:

- `pm2`
- `systemd`

Пример для `pm2`:

```bash
pm2 start npm --name "home-budget" -- run start:prod
```

## Установка и запуск на Ubuntu

1. Подключитесь к серверу по SSH:

```bash
ssh username@your-server-ip
```

1. Установите Node.js, если он ещё не установлен:

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
```

1. Клонируйте проект:

```bash
git clone <ваш-репозиторий> /home/username/home-budget
cd /home/username/home-budget
```

1. Установите зависимости:

```bash
npm install
cd client && npm install && cd ..
```

1. Задайте production-переменные окружения:

```bash
export NODE_ENV=production
export JWT_SECRET="replace-with-long-random-secret"
export ALLOWED_ORIGINS="https://your-domain.example"
export PORT=3002
export JSON_BODY_LIMIT=1mb
export RATE_LIMIT_WINDOW_MS=900000
export AUTH_RATE_LIMIT_MAX=5
export PASSWORD_RATE_LIMIT_MAX=5
export RESTORE_RATE_LIMIT_MAX=3
```

1. Разрешите порт в firewall:

```bash
sudo ufw allow 3002/tcp
sudo ufw reload
```

1. Установите и настройте `pm2`:

```bash
sudo npm install -g pm2
pm2 start npm --name "home-budget" -- run start:prod
pm2 startup
pm2 save
```

1. Проверьте, что приложение доступно по адресу `http://<IP-сервера>:3002`.

## База данных

Данные приложения хранятся в файле `server/database.sqlite`. При первом запуске автоматически создаются необходимые таблицы и добавляются начальные данные.

## Решение проблем

### Ошибка `EADDRINUSE: Port in use`

- убедитесь, что порты `3002` и `5174` свободны
- в Linux и macOS можно освободить порты так:

```bash
lsof -ti:3002 | xargs kill -9
lsof -ti:5174 | xargs kill -9
```

### Изменение порта

- для изменения порта сервера используйте переменную окружения `PORT`
- для изменения порта разработки используйте `npm run dev -- --port <номер_порта>`
