# Автодеплой лендинга + прежние имена Linux-пакетов

Дата: 2026-09-22 · Одобрено Володей заранее (шаг 1 из предложения)

## A. Имена Linux-пакетов без `-1`

**Причина.** CMP 1.12.1 передаёт в `jpackage` `--linux-app-release` только если задан
`linux { appRelease }`; иначе `jpackage` берёт релиз по умолчанию `1` и вшивает его в имя:
`finkeeper24_2.2.3-1_amd64.deb`, `finkeeper24-2.2.3-1.x86_64.rpm`. Убрать релиз из имени
опциями `jpackage` нельзя.

**Решение.** В `composeApp/build.gradle` — `doLast` у задач упаковки Linux (`packageDeb`,
`packageRpm`, `packageReleaseDeb`, `packageReleaseRpm`): переименовать результат в прежний формат
`finkeeper24_<ver>_amd64.deb` / `finkeeper24-<ver>.x86_64.rpm`. Совместимо с configuration cache
(захватывается `DirectoryProperty`, не задача). Ссылки лендинга, `publish-aur.yml`, `PKGBUILD` и
шаги CI (glob `*.deb`/`*.rpm`) не меняются.

**Проверка.** Локально, если есть `dpkg-deb`/`rpmbuild`; обязательно — прогон `build-linux.yml`
в CI и имена файлов в артефактах.

## B. `lending/deploy.sh` — выкладка лендинга одной командой

Nginx: сайт = `/var/www/finkeeper24.ru/html/dist`, загрузки = `.../html/downloads` (alias).

Команды:
- `./deploy.sh` — проверки → сборка у себя → выкладка `dist` → проверка живого сайта;
- `./deploy.sh --check` — только проверки (ничего не выкладывает);
- `./deploy.sh --rollback` — вернуть предыдущую версию (`dist.prev`);
- `./deploy.sh --bump 2.2.4` — проставить версию в `downloads.html`, `latest.json`, бейдж `App.tsx`.

Проверки перед выкладкой (скрипт останавливается при ошибке):
1. версия одинакова в `downloads.html` (ссылки и подпись), `latest.json`, бейдже `App.tsx` и
   `app_version` в `mobile_app_client/composeApp/build.gradle`;
2. все ссылки со страницы загрузок отвечают 200 (`curl -I`);
3. нет незакоммиченных изменений в `lending/` (обход — `--allow-dirty`).

Выкладка: `npm ci && npm run build` локально → `rsync -a --delete dist/ → html/dist.new/` →
на сервере `dist → dist.prev`, `dist.new → dist` (подмена двумя `mv`, простоя практически нет).
`downloads/` скрипт не трогает (rsync только в `dist.new`, пути заданы жёстко).
После: на живом сайте `latest.json` = версии, бейдж в JS = версии, ссылки 200.

Параметры (env): `DEPLOY_HOST` (по умолчанию `root@157.22.172.217`), `DEPLOY_ROOT`
(`/var/www/finkeeper24.ru/html`), `SITE_URL` (`https://finkeeper24.ru`).

Исходники и `node_modules` на сервере скрипт не трогает (не нужны, но удалять — решение Володи).

**Проверка.** `--check` на текущем состоянии; негативные проверки (рассинхрон версии в копии
файлов, битая ссылка) — ошибка; реальная выкладка текущей 2.2.3 (сайт не меняется по содержанию)
→ живые проверки; `--rollback` и обратная выкладка.

## Документация

Раздел про лендинг в `lending/README.md` + `AGENTS.md` (Common Commands).

## Итог (2026-09-22)

- A: `cfca5de`. Локально и в CI (`build-linux`, run 35737765852) артефакты —
  `finkeeper24_2.2.3_amd64.deb`, `finkeeper24-2.2.3.x86_64.rpm`; патч `StartupWMClass` применяется.
- B: `fe14d1a` + документация `dcb85c6`. Проверено: `--check`, отказ при грязном git, `--bump`
  (те же 13 строк, что при ручной правке), отказ при расхождении с `app_version`, реальная выкладка
  2.2.3 с живыми проверками, двойной `--rollback`; `downloads/` не затронута.
- На сервере остались исходники лендинга и `node_modules` — для `deploy.sh` не нужны; удалять ли — решать Володе.
