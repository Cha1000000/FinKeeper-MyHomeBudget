#!/usr/bin/env bash
# Заливка десктопных дистрибутивов из локального хранилища на сервер (папка downloads сайта).
#
#   ./upload-downloads.sh              залить самые свежие версии пакетов
#   ./upload-downloads.sh --dry-run    показать, что будет залито, ничего не копируя
#   ./upload-downloads.sh --prune      после заливки удалить на сервере старые версии залитых пакетов
#
# Локально:  <SRC>/Linux, <SRC>/macOS, <SRC>/Windows  (Android не заливается: APK идёт в RuStore,
#            рядом лежит keystore — он не должен попасть на сервер).
# На сервере: $DEPLOY_ROOT/downloads/linux, mac, windows.
# «Свежая» версия — наибольшая среди файлов одного типа (deb, rpm, dmg, pkg, msi) в папке ОС.
# Файлы льются через временное имя и переименовываются по завершении, поэтому сайт
# не отдаёт недокачанный пакет. Прерванную заливку можно просто запустить заново.
# Скрипт можно запускать из любой копии: из lending/ и из папки Release builds.
set -euo pipefail

DEPLOY_HOST="${DEPLOY_HOST:-root@157.22.172.217}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/var/www/finkeeper24.ru/html}"
DEFAULT_SRC="/home/racer/YandexDisk/My Projects/Release builds/FinKeeper24"
VERSION_RE='[0-9]+\.[0-9]+\.[0-9]+'
# локальная папка -> папка на сервере
TARGETS=("Linux:linux" "macOS:mac" "Windows:windows")

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
die()  { printf '\n\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

DRY=0; PRUNE=0
for arg in "$@"; do
	case "$arg" in
		--dry-run) DRY=1 ;;
		--prune) PRUNE=1 ;;
		-h|--help) sed -n '2,15p' "$0"; exit 0 ;;
		*) die "Неизвестный параметр: $arg (см. --help)" ;;
	esac
done

# Защита от опечатки в переменной: пишем только внутри .../html/downloads
[[ "$DEPLOY_ROOT" == /var/www/*/html ]] || die "Подозрительный DEPLOY_ROOT: $DEPLOY_ROOT"
REMOTE_BASE="$DEPLOY_ROOT/downloads"

# Источник: сначала папка со скриптом (если это Release builds), иначе путь по умолчанию
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$HERE/Linux" ] || [ -d "$HERE/Windows" ]; then SRC="$HERE"; else SRC="${SRC:-$DEFAULT_SRC}"; fi
[ -d "$SRC" ] || die "Нет папки с дистрибутивами: $SRC"

command -v rsync >/dev/null || die "Нужен rsync"
ssh -o BatchMode=yes -o ConnectTimeout=10 "$DEPLOY_HOST" true || die "Нет SSH-доступа к $DEPLOY_HOST"

# Тип файла: имя с версией, заменённой на «#». Суффикс релиза «-1» (старые имена Linux-пакетов,
# jpackage) отбрасывается, чтобы finkeeper24_2.2.3-1_amd64.deb и finkeeper24_2.2.4_amd64.deb были одним типом
kind_of()    { sed -E "s/$VERSION_RE(-1)?/#/" <<<"$1"; }
version_of() { grep -oE "$VERSION_RE" <<<"$1" | head -n1; }

step "Источник: $SRC"
[ "$DRY" = 1 ] && echo "  (dry-run: ничего не копируется)"

declare -a UPLOADS=()   # локальный_путь|папка_на_сервере
declare -A KEEP=()      # "папка|тип" -> версия, которая остаётся на сервере
versions_seen=()

for pair in "${TARGETS[@]}"; do
	local_dir="${pair%%:*}"; remote_dir="${pair##*:}"
	dir="$SRC/$local_dir"
	[ -d "$dir" ] || die "Нет папки $dir"
	declare -A best=() best_file=()
	while IFS= read -r -d '' f; do
		name="${f##*/}"; v="$(version_of "$name")"
		[ -n "$v" ] || { echo "  · пропуск без версии в имени: $local_dir/$name"; continue; }
		k="$(kind_of "$name")"
		if [ -z "${best[$k]:-}" ]; then
			best[$k]="$v"; best_file[$k]="$f"
		elif [ "${best[$k]}" != "$v" ] && [ "$(printf '%s\n%s\n' "${best[$k]}" "$v" | sort -V | tail -n1)" = "$v" ]; then
			best[$k]="$v"; best_file[$k]="$f"
		fi
	done < <(find "$dir" -maxdepth 1 -type f \( -name '*.deb' -o -name '*.rpm' -o -name '*.dmg' -o -name '*.pkg' -o -name '*.msi' \) -print0)
	[ "${#best[@]}" -gt 0 ] || die "В $dir нет пакетов (deb/rpm/dmg/pkg/msi)"
	for k in "${!best[@]}"; do
		UPLOADS+=("${best_file[$k]}|$remote_dir")
		KEEP["$remote_dir|$k"]="${best[$k]}"
		versions_seen+=("${best[$k]}")
	done
	unset best best_file
done

if [ "$(printf '%s\n' "${versions_seen[@]}" | sort -u | wc -l)" -gt 1 ]; then
	echo
	echo "  ⚠ Версии пакетов разные: $(printf '%s\n' "${versions_seen[@]}" | sort -uV | paste -sd' ')"
	echo "    Возможно, не все пакеты этого релиза скачаны с GitHub."
	if [ "$DRY" = 0 ]; then
		read -r -p "  Продолжить? [y/N] " answer
		[[ "$answer" =~ ^[yY]$ ]] || die "Отменено"
	fi
fi

step "К заливке"
for item in "${UPLOADS[@]}"; do
	f="${item%%|*}"; rd="${item##*|}"
	printf '  %-8s %s (%s)\n' "$rd/" "${f##*/}" "$(du -h "$f" | cut -f1)"
done

if [ "$DRY" = 1 ]; then
	step "Dry-run: на сервере сейчас"
	ssh -o BatchMode=yes "$DEPLOY_HOST" "cd '$REMOTE_BASE' && ls -1 linux mac windows"
	[ "$PRUNE" = 1 ] && echo "  (--prune удалил бы старые версии тех же типов пакетов)"
	exit 0
fi

step "Заливка"
for item in "${UPLOADS[@]}"; do
	f="${item%%|*}"; rd="${item##*|}"; name="${f##*/}"
	ssh -o BatchMode=yes "$DEPLOY_HOST" "mkdir -p '$REMOTE_BASE/$rd'"
	# --partial: докачка после обрыва; временный файл переименовывается по завершении
	rsync -t --partial --progress --chmod=F644 -e "ssh -o BatchMode=yes" "$f" "$DEPLOY_HOST:$REMOTE_BASE/$rd/"
	local_size="$(stat -c %s "$f")"
	remote_size="$(ssh -o BatchMode=yes "$DEPLOY_HOST" "stat -c %s '$REMOTE_BASE/$rd/$name'")"
	[ "$local_size" = "$remote_size" ] || die "Размер не совпал: $name (локально $local_size, на сервере $remote_size)"
	ok "$rd/$name"
done

if [ "$PRUNE" = 1 ]; then
	step "Удаление старых версий на сервере"
	for key in "${!KEEP[@]}"; do
		rd="${key%%|*}"; k="${key#*|}"; keep_v="${KEEP[$key]}"
		while IFS= read -r remote_name; do
			[ -n "$remote_name" ] || continue
			[ "$(kind_of "$remote_name")" = "$k" ] || continue
			[ "$(version_of "$remote_name")" != "$keep_v" ] || continue
			ssh -o BatchMode=yes "$DEPLOY_HOST" "rm -f -- '$REMOTE_BASE/$rd/$remote_name'"
			ok "удалён $rd/$remote_name"
		done < <(ssh -o BatchMode=yes "$DEPLOY_HOST" "ls -1 '$REMOTE_BASE/$rd'")
	done
fi

step "Готово"
echo "  Дальше: ./deploy.sh --bump X.Y.Z && ./deploy.sh (в lending/) — выложить лендинг с новой версией."
