# План: вынести боевую БД из деплой-директории (защита от случайной перезаписи)

> Статус: **отложено** (зафиксировано на будущее). Поводом стал инцидент 08.06.2026 —
> случайная заливка через FileZilla старого `server/database.sqlite` поверх боевого.

## Проблема
Боевая БД лежит в `server/database.sqlite` — внутри той же папки, что и код, который
заливается на сервер (FileZilla/деплой). Любая невнимательная перезаливка папки `server/`
может затереть боевую БД. Дисциплина помогает, но не убирает саму возможность катастрофы.

## Ключевой факт
Код **уже** читает путь к БД из переменной окружения `DB_PATH` и только при её отсутствии
берёт `server/database.sqlite`:
```js
// server/index.js, server/db_setup.js, server/db/connection.js
const dbPath = process.env.DB_PATH?.trim()
    ? process.env.DB_PATH
    : path.resolve(__dirname, 'database.sqlite');
```
Значит правка кода НЕ нужна — достаточно задать `DB_PATH` и перенести файл.

## Решение
1. Перенести боевую БД в **`/var/lib/finkeeper/database.sqlite`** (вне деплой-дерева).
2. Задать `DB_PATH=/var/lib/finkeeper/database.sqlite` **на уровне systemd** (drop-in),
   а НЕ в `.env` внутри `server/` — иначе заливка папки снова затрёт настройку.

После этого случайная заливка `server/database.sqlite` через FileZilla **безвредна**:
приложение этот файл не читает, оно работает с `/var/lib/finkeeper/`.

## Шаги (выполнять с остановленным сервисом, простой ~секунды)
```bash
systemctl stop finkeeper.service

mkdir -p /var/lib/finkeeper
mv /var/www/app.finkeeper24.ru/server/database.sqlite /var/lib/finkeeper/database.sqlite
# при наличии — перенести и WAL/SHM:
mv /var/www/app.finkeeper24.ru/server/database.sqlite-wal /var/lib/finkeeper/ 2>/dev/null || true
mv /var/www/app.finkeeper24.ru/server/database.sqlite-shm /var/lib/finkeeper/ 2>/dev/null || true
chown root:root /var/lib/finkeeper/database.sqlite && chmod 644 /var/lib/finkeeper/database.sqlite

# DB_PATH через systemd drop-in (переживёт перезаливку кода):
mkdir -p /etc/systemd/system/finkeeper.service.d
cat > /etc/systemd/system/finkeeper.service.d/override.conf <<'EOF'
[Service]
Environment=DB_PATH=/var/lib/finkeeper/database.sqlite
EOF
systemctl daemon-reload
systemctl start finkeeper.service
```

## После переноса — поправить вспомогательные скрипты
- `/usr/local/bin/finkeeper-db-backup.sh` — переменную `DB` на новый путь.
- `/var/backups/finkeeper/finkeeper-db-restore.sh` — он уже умеет авто-определять `DB_PATH`
  из systemd; проверить, что подхватывает новый путь.

## Проверка
- В логах при старте `dbPath=/var/lib/finkeeper/database.sqlite`.
- Данные на месте, сервис `active`, `integrity_check = ok`.
- Тест: заново залить старый `server/database.sqlite` — на боевые данные это НЕ влияет.

## Локальная разработка (рекомендация)
Держать дев-БД вне синхронизируемой папки проекта (или хотя бы `.gitignore` + не таскать
её в FileZilla). Главная защита — серверная (выше), но и локально лучше убрать файл с глаз.

## Откат
Снять drop-in (`rm .../override.conf` + `daemon-reload`) и вернуть файл в `server/` —
приложение снова возьмёт `server/database.sqlite`.
```
