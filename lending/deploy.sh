#!/usr/bin/env bash
# Выкладка лендинга finkeeper24.ru одной командой.
#
#   ./deploy.sh              проверки → сборка у себя → выкладка dist → проверка живого сайта
#   ./deploy.sh --check      только проверки, ничего не выкладывает
#   ./deploy.sh --rollback   вернуть предыдущую выложенную версию (dist.prev)
#   ./deploy.sh --bump X.Y.Z проставить версию в downloads.html, latest.json и бейдж App.tsx
#   --allow-dirty            не требовать закоммиченного состояния lending/
#
# Nginx: сайт = $DEPLOY_ROOT/dist, загрузки = $DEPLOY_ROOT/downloads (отдельный alias).
# Скрипт пишет только в dist.new / dist / dist.prev — папку downloads не трогает никогда.
set -euo pipefail

DEPLOY_HOST="${DEPLOY_HOST:-root@157.22.172.217}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/var/www/finkeeper24.ru/html}"
SITE_URL="${SITE_URL:-https://finkeeper24.ru}"

cd "$(dirname "$0")"
REPO_ROOT="$(git rev-parse --show-toplevel)"
GRADLE_FILE="$REPO_ROOT/mobile_app_client/composeApp/build.gradle"
DOWNLOADS=public/downloads.html
LATEST=public/latest.json
APP=src/App.tsx
VERSION_RE='[0-9]+\.[0-9]+\.[0-9]+'

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
die()  { printf '\n\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# Защита от опечатки в переменной: rm/mv на сервере идут только внутри .../html
[[ "$DEPLOY_ROOT" == /var/www/*/html ]] || die "Подозрительный DEPLOY_ROOT: $DEPLOY_ROOT"

# --- чтение версий (только рядом с «якорями», чтобы не цеплять числа из SVG) ---
versions_in_downloads() {
	grep -oE "(FinKeeper24-|finkeeper24_|finkeeper24-|Версия )$VERSION_RE" "$DOWNLOADS" | grep -oE "$VERSION_RE" | sort -u
}
version_in_latest() { python3 -c "import json;print(json.load(open('$LATEST'))['version'])"; }
version_in_app()    { grep -oE "v$VERSION_RE • PRO" "$APP" | grep -oE "$VERSION_RE" | sort -u; }
version_in_gradle() { grep -oE "app_version = '$VERSION_RE'" "$GRADLE_FILE" | grep -oE "$VERSION_RE"; }
download_links()    { grep -oE "$SITE_URL/downloads/[^\"]+" "$DOWNLOADS" | sort -u; }

http_code() { # с повтором: сайт иногда отвечает не с первого раза
	local code
	for _ in 1 2 3; do
		code="$(curl -s -o /dev/null -I -w '%{http_code}' -m 30 "$1" || true)"
		[ "$code" = 200 ] && break
		sleep 2
	done
	echo "$code"
}

check_links() {
	local bad=0 url code
	while read -r url; do
		code="$(http_code "$url")"
		if [ "$code" = 200 ]; then ok "200 ${url##*/}"; else echo "  ✗ $code $url"; bad=1; fi
	done < <(download_links)
	[ "$bad" = 0 ] || die "Не все файлы доступны на сайте — сначала залей пакеты в $DEPLOY_ROOT/downloads"
}

# --- проверки перед выкладкой ---
preflight() {
	local allow_dirty="$1"

	step "1. Версии"
	local dl latest app gradle
	dl="$(versions_in_downloads)"; latest="$(version_in_latest)"; app="$(version_in_app)"; gradle="$(version_in_gradle)"
	echo "  downloads.html: $(echo "$dl" | tr '\n' ' ')"
	echo "  latest.json:    $latest"
	echo "  App.tsx:        $app"
	echo "  build.gradle:   $gradle (app_version)"
	[ "$(echo "$dl" | wc -l)" = 1 ] || die "В downloads.html несколько разных версий"
	[ "$dl" = "$latest" ] && [ "$dl" = "$app" ] || die "Версии лендинга расходятся (подсказка: ./deploy.sh --bump X.Y.Z)"
	[ "$dl" = "$gradle" ] || die "Версия лендинга ($dl) ≠ версии приложения ($gradle)"
	ok "везде $dl"
	VERSION="$dl"

	step "2. Файлы на сайте"
	check_links

	step "3. Git"
	if [ -n "$(git status --porcelain -- .)" ]; then
		[ "$allow_dirty" = 1 ] || die "Есть незакоммиченные изменения в lending/ (закоммить или --allow-dirty)"
		echo "  ! незакоммиченные изменения (--allow-dirty)"
	else
		ok "lending/ закоммичен"
	fi
}

build() {
	step "4. Сборка"
	# npm ci — только если зависимости не ставились или lock-файл новее установленных
	if [ ! -f node_modules/.package-lock.json ] || [ package-lock.json -nt node_modules/.package-lock.json ]; then
		npm ci
	fi
	npm run build
	for f in index.html downloads.html latest.json; do [ -f "dist/$f" ] || die "В dist нет $f"; done
	ok "dist собран"
}

upload_and_switch() {
	step "5. Выкладка"
	ssh -o BatchMode=yes "$DEPLOY_HOST" "rm -rf '$DEPLOY_ROOT/dist.new' && mkdir -p '$DEPLOY_ROOT/dist.new'"
	rsync -a --delete dist/ "$DEPLOY_HOST:$DEPLOY_ROOT/dist.new/"
	# Подмена двумя mv: окно, когда dist отсутствует, — доли миллисекунды
	ssh -o BatchMode=yes "$DEPLOY_HOST" "cd '$DEPLOY_ROOT' && rm -rf dist.prev && { [ ! -d dist ] || mv dist dist.prev; } && mv dist.new dist"
	ok "dist заменён (прежний сохранён как dist.prev)"
}

verify_live() {
	local expected="$1"
	step "6. Проверка живого сайта"
	local live js badge
	live="$(curl -s -m 30 "$SITE_URL/latest.json" | python3 -c 'import json,sys;print(json.load(sys.stdin)["version"])')"
	[ "$live" = "$expected" ] || die "latest.json на сайте: $live, ожидалось $expected"
	ok "latest.json = $live"
	js="$(curl -s -m 30 "$SITE_URL/" | grep -oE '/assets/index-[^"]+\.js' | head -1)"
	badge="$(curl -s -m 30 "$SITE_URL$js" | grep -oE "v$VERSION_RE • PRO" | grep -oE "$VERSION_RE" | head -1)"
	[ "$badge" = "$expected" ] || die "Бейдж на главной: $badge, ожидалось $expected"
	ok "бейдж на главной = v$badge"
	local page_versions
	page_versions="$(curl -s -m 30 "$SITE_URL/downloads.html" | grep -oE "(FinKeeper24-|finkeeper24_|finkeeper24-|Версия )$VERSION_RE" | grep -oE "$VERSION_RE" | sort -u)"
	[ "$page_versions" = "$expected" ] || die "downloads.html на сайте: $page_versions, ожидалось $expected"
	ok "downloads.html = $expected"
}

rollback() {
	step "Откат"
	ssh -o BatchMode=yes "$DEPLOY_HOST" "cd '$DEPLOY_ROOT' && [ -d dist.prev ] || { echo 'Нет dist.prev — откатывать не на что' >&2; exit 1; }
		mv dist dist.tmp && mv dist.prev dist && mv dist.tmp dist.prev"
	ok "текущая ↔ предыдущая (повторный --rollback вернёт обратно)"
	local live
	live="$(curl -s -m 30 "$SITE_URL/latest.json" | python3 -c 'import json,sys;print(json.load(sys.stdin)["version"])')"
	ok "на сайте сейчас latest.json = $live"
}

bump() {
	local new="$1" old
	[[ "$new" =~ ^$VERSION_RE$ ]] || die "Версия должна быть вида X.Y.Z: $new"
	old="$(versions_in_downloads)"
	[ "$(echo "$old" | wc -l)" = 1 ] || die "В downloads.html несколько версий — поправь вручную"
	local o="${old//./\\.}"
	sed -i -E "s/(FinKeeper24-|finkeeper24_|finkeeper24-|Версия )$o/\1$new/g" "$DOWNLOADS"
	sed -i -E "s/v$o • PRO/v$new • PRO/" "$APP"
	python3 - "$LATEST" "$new" <<-'EOF'
		import json, sys
		path, new = sys.argv[1], sys.argv[2]
		data = json.load(open(path))
		data["version"] = new
		open(path, "w").write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
	EOF
	ok "лендинг: $old → $new (downloads.html, latest.json, App.tsx)"
	local gradle
	gradle="$(version_in_gradle)"
	[ "$gradle" = "$new" ] || echo "  ! app_version в build.gradle = $gradle — выкладка не пройдёт, пока версии не совпадут"
	git --no-pager diff --stat -- "$DOWNLOADS" "$LATEST" "$APP"
}

# --- разбор аргументов ---
MODE=deploy ALLOW_DIRTY=0 BUMP_TO=""
while [ $# -gt 0 ]; do
	case "$1" in
		--check) MODE=check ;;
		--rollback) MODE=rollback ;;
		--bump) MODE=bump; BUMP_TO="${2:-}"; shift ;;
		--allow-dirty) ALLOW_DIRTY=1 ;;
		-h|--help) sed -n '2,11p' "$0"; exit 0 ;;
		*) die "Неизвестный аргумент: $1 (см. --help)" ;;
	esac
	shift
done

case "$MODE" in
	check) preflight "$ALLOW_DIRTY"; step "Проверки пройдены — можно выкладывать ($VERSION)" ;;
	bump) bump "$BUMP_TO" ;;
	rollback) rollback ;;
	deploy)
		preflight "$ALLOW_DIRTY"
		build
		upload_and_switch
		verify_live "$VERSION"
		step "Готово: лендинг $VERSION на $SITE_URL"
		;;
esac
