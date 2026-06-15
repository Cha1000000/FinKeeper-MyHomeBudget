# Домашняя бухгалтерия — Инструкция по развёртыванию

## Деплой обновлений (TL;DR)

После того как обновлённые файлы залиты на сервер — **одна команда**:

```bash
cd /var/www/app.finkeeper24.ru
sudo bash deploy.sh
```

Скрипт `deploy.sh` автоматически:

1. Обновляет зависимости (root, client, server)
2. Пересобирает frontend (`npm run build`)
3. Перезапускает systemd-сервис `finkeeper`
4. Проверяет локальный и публичный healthcheck

Ничего больше запускать не нужно — ни `npm run build`, ни `systemctl restart`.

---

## Требования

- Node.js версии 18 или выше
- npm
- для production: `nginx` и SSL-сертификат (например, Let's Encrypt)

## Переменные окружения

### Обязательные

```bash
export JWT_SECRET="replace-with-long-random-secret"
export ALLOWED_ORIGINS="http://localhost:5174"
export APP_BASE_URL="http://localhost:5174"
export PUBLIC_API_BASE_URL="http://localhost:3002"
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
- `APP_BASE_URL` используется для внешнего URL приложения в письмах и redirect-сценариях
- `PUBLIC_API_BASE_URL` используется сервером для построения публичных callback/API URL
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

1. Установите зависимости сервера:

```bash
cd server && npm install && cd ..
```

## Запуск в режиме разработки

1. Задайте переменные окружения для локальной разработки:

```bash
export JWT_SECRET="replace-with-long-random-secret"
export ALLOWED_ORIGINS="http://localhost:5174"
export APP_BASE_URL="http://localhost:5174"
export PUBLIC_API_BASE_URL="http://localhost:3002"
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

## Первоначальная настройка production-сервера

1. Создайте файл `.env.production` в корне проекта:

```bash
NODE_ENV=production
JWT_SECRET=replace-with-long-random-secret
ALLOWED_ORIGINS=https://app.finkeeper24.ru
APP_BASE_URL=https://app.finkeeper24.ru
PUBLIC_API_BASE_URL=https://app.finkeeper24.ru
PORT=3002
JSON_BODY_LIMIT=1mb
RATE_LIMIT_WINDOW_MS=900000
AUTH_RATE_LIMIT_MAX=5
PASSWORD_RATE_LIMIT_MAX=5
RESTORE_RATE_LIMIT_MAX=3
```

1. Настройте `nginx` так, чтобы он:

- принимал HTTPS-запросы на `app.finkeeper24.ru`
- раздавал frontend из `client/dist`
- проксировал `/api/*` на `http://127.0.0.1:3002`

1. Первый запуск:

```bash
sudo bash deploy.sh
```

После запуска приложение должно быть доступно по адресу `https://app.finkeeper24.ru`.

## Структура портов

| Порт | Назначение |
| ---- | ---------- |
| 80 | HTTP → HTTPS redirect через nginx |
| 443 | Основной публичный HTTPS-вход через nginx |
| 3002 | Внутренний порт backend Node.js на сервере |
| 5174 | Порт frontend для локальной разработки |

## Управление сервисом

Backend управляется через `systemd`-сервис `finkeeper`.

## Установка и запуск на Ubuntu

1. Подключитесь к серверу по SSH:

```bash
ssh root@157.22.172.217
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
cd server && npm install && cd ..
```

1. Создайте `.env.production`:

```bash
nano .env.production
```

1. Заполните файл значениями production-конфига (см. раздел выше).

1. Установите и настройте `nginx`, если он ещё не установлен.

1. Разрешите публичные порты в firewall:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw reload
```

1. Настройте `nginx` для `app.finkeeper24.ru`, чтобы:

- frontend раздавался из `/var/www/app.finkeeper24.ru/client/dist`
- `/api/*` проксировался на `http://127.0.0.1:3002`
- HTTP перенаправлялся на HTTPS

1. Выпустите и подключите SSL-сертификат для `app.finkeeper24.ru`.

1. Запустите приложение:

```bash
sudo bash deploy.sh
```

1. Проверьте доступность:

```bash
curl -I https://app.finkeeper24.ru
curl -I https://app.finkeeper24.ru/api/health
```

## База данных

Данные приложения хранятся в файле `server/database.sqlite`. При первом запуске автоматически создаются необходимые таблицы и добавляются начальные данные.

## Резервное копирование и восстановление БД

> Бэкапы лежат **вне** папки деплоя (`/var/backups/finkeeper/`) — намеренно, чтобы их
> нельзя было случайно затереть перезаливкой кода/папки `server/` через FileZilla.

### Авто-бэкап (ежедневно)

- **Скрипт:** `/usr/local/bin/finkeeper-db-backup.sh`
- **Расписание:** `/etc/cron.d/finkeeper-db-backup` — каждый день в **23:55**
- **Куда:** `/var/backups/finkeeper/database_ГГГГММДД_ЧЧММСС.sqlite.gz`
- **Что делает:** консистентный снимок (`sqlite3 .backup`) → проверка `integrity_check`
  → gzip → хранит **последние 7** копий (старые удаляет сам)
- **Лог:** `/var/log/finkeeper-db-backup.log`

Запустить вручную (например, перед рискованной операцией):

```bash
sudo /usr/local/bin/finkeeper-db-backup.sh
```

### Ручное восстановление из бэкапа

- **Скрипт:** `/var/backups/finkeeper/finkeeper-db-restore.sh` (запускать от root)

Что делает: показывает список бэкапов по датам → даёт выбрать → проверяет целостность и
показывает сводку (юзеры, число расходов, max дата) → **перед заменой сохраняет текущую БД**
в `pre_restore_*.sqlite.gz` → останавливает сервис, подменяет файл, стартует, проверяет.
Путь к БД определяет сам (из `DB_PATH`, иначе `server/database.sqlite`).

```bash
# интерактивно — список и выбор номера:
sudo /var/backups/finkeeper/finkeeper-db-restore.sh

# сразу выбрать бэкап по дате (подстрока имени) или по имени файла:
sudo /var/backups/finkeeper/finkeeper-db-restore.sh 20260608
sudo /var/backups/finkeeper/finkeeper-db-restore.sh database_20260608_180738.sqlite.gz
```

Если восстановление оказалось неудачным — запусти скрипт снова и выбери созданный им
`pre_restore_*` (откат к состоянию до восстановления).

### Дополнительные ручные копии

Перед особо рискованными операциями копии кладутся также в `/root/finkeeper_db_backups/`
(точки отката, вне ротации). Исходники скриптов продублированы локально в `~/finkeeper_recovery/`.

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

### Проблемы после смены домена или SSL

- если `curl` по HTTPS отвечает корректно, а браузер всё ещё показывает старый сертификат или пометку «Небезопасно», сначала попробуйте:
  - открыть сайт в режиме инкогнито
  - выполнить hard reload
  - полностью перезапустить браузер
- если сайт открывается через `nginx`, в production используйте домен вида `https://app.finkeeper24.ru`, а не прямой IP с портом `3002`
- если настроены Google/Yandex OAuth, не забудьте обновить их redirect URI на новый production-домен

## Команды управления сервисом

- **Запуск**

```bash
systemctl start finkeeper
```

- **Остановка**

```bash
systemctl stop finkeeper
```

- **Перезапуск**

```bash
systemctl restart finkeeper
```

- **Статус**

```bash
systemctl status finkeeper
```

- **Автозапуск включить**

```bash
systemctl enable finkeeper
```

- **Автозапуск выключить**

```bash
systemctl disable finkeeper
```
