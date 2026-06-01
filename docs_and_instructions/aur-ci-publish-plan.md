# План: автопубликация AUR-пакета `finkeeper24-bin` через GitHub Actions

## Цель

Чтобы при выходе новой версии AUR-пакет `finkeeper24-bin` обновлялся автоматически
(новый `pkgver`, пересчёт `sha256`, регенерация `.SRCINFO`, `git push` в AUR), а не
правился руками.

## Контекст и ключевое ограничение

- AUR **не хранит бинарники** — там только `PKGBUILD` (рецепт). Сборка идёт на машине
  пользователя: PKGBUILD скачивает `.deb` с `finkeeper24.ru` и проверяет его по `sha256`.
- `.deb` на сайт `finkeeper24.ru/downloads/linux/` **заливается вручную** (CI этого не делает;
  текущий `build-linux.yml` лишь кладёт `.deb`/`.rpm` в артефакты GitHub на 3 дня).
- Compose/jpackage `.deb` **не воспроизводим** побайтово → `sha256` CI-сборки может не совпасть
  с тем файлом, что в итоге окажется на сайте.

**Вывод:** публикацию в AUR нельзя привязывать к моменту сборки `.deb`. Её нужно делать
**после** заливки `.deb` на сайт и брать `sha256` **с живого файла на сайте**. Иначе у
пользователей `paru -S finkeeper24-bin` упадёт на несовпадении контрольной суммы.

## Решение: отдельный workflow `publish-aur.yml` (ручной запуск)

Не трогаем `build-linux.yml`. Добавляем независимый workflow, который Володя запускает
вручную **после** того, как залил новый `.deb` на сайт.

### Что появится в репозитории

1. **`packaging/aur/PKGBUILD`** — канонический PKGBUILD (переносим текущий из `~/aur/finkeeper24-bin`,
   он уже выверен). Репозиторий становится источником правды для рецепта.
2. **`.github/workflows/publish-aur.yml`** — новый workflow.
   (`.SRCINFO` в репозитории не держим — его генерит сам шаг публикации.)

### Логика `publish-aur.yml`

- **Триггер:** `workflow_dispatch` со входными параметрами:
  - `version` — необязательный; если пусто, берётся из `mobile_app_client/composeApp/build.gradle`
    (строка `app_version = '...'`).
  - `pkgrel` — по умолчанию `1` (на случай переиздания той же версии).
- **Шаги:**
  1. `checkout`.
  2. **Определить версию** (вход или парсинг `app_version` из `build.gradle`).
  3. **Guard:** `HEAD`-запрос на `https://finkeeper24.ru/downloads/linux/finkeeper24_<ver>_amd64.deb`.
     Если не `200` — упасть с понятной ошибкой («сначала залей .deb на сайт»). Защита от
     публикации раньше времени.
  4. **Скачать `.deb` с сайта и посчитать `sha256`** (именно с сайта — гарантия совпадения у людей).
  5. **Обновить `packaging/aur/PKGBUILD`:** проставить `pkgver=<ver>`, `pkgrel=<pkgrel>` и первую
     запись `sha256sums` (контрольная сумма лицензии Apache не меняется).
  6. **Опубликовать в AUR** через готовый action `KSXGitHub/github-actions-deploy-aur@v2`
     (работает в Arch-контейнере): копирует PKGBUILD, генерит `.SRCINFO` (`makepkg --printsrcinfo`),
     валидирует, коммитит и пушит в `ssh://aur@aur.archlinux.org/finkeeper24-bin.git`.
     Включаем встроенную тест-сборку (`makepkg`) — заодно проверка, что рецепт собирается.
  7. **(опционально) Закоммитить обновлённый `PKGBUILD` обратно в `main`** (через `GITHUB_TOKEN`),
     чтобы репозиторий отражал опубликованное состояние. — *обсуждаемо, см. вопросы ниже.*

### Секреты GitHub

- **`AUR_SSH_PRIVATE_KEY`** — приватный SSH-ключ, привязанный к AUR-аккаунту. Уже есть готовый:
  `~/.ssh/aur` (тот, которым делали первый push). Добавляется командой:
  ```fish
  gh secret set AUR_SSH_PRIVATE_KEY < ~/.ssh/aur
  ```
  (или через веб: Settings → Secrets and variables → Actions → New secret).
- Имя/почта коммиттера AUR (`Cha1000000` / `racerkafa@yandex.ru`) — зашьём прямо в workflow,
  это не секрет.

## Что НЕ входит в этот план

- Автозаливка `.deb` на сайт остаётся ручной (как сейчас). Если позже захочешь — отдельная задача
  (нужны доступы к хостингу downloads-страницы).
- `build-linux.yml`, `build-desktop.yml`, mac/windows — не трогаем.

## Порядок релиза после внедрения

1. Поднять `app_version` в `build.gradle`, собрать `.deb` (через `build-linux.yml`).
2. Залить новый `.deb` на `finkeeper24.ru/downloads/linux/`.
3. Запустить `publish-aur.yml` (Actions → Run workflow). Готово — AUR обновлён.

## Принятые решения

1. **Коммитить обновлённый `PKGBUILD` обратно в `main`** — **ДА** (шаг 7 включён, через `GITHUB_TOKEN`).
2. **Тест-сборка `makepkg` в CI** — **НЕТ** (ради скорости; рецепт уже выверен ручной сборкой).
3. **Источник версии** — **авто из `build.gradle`** (`app_version`), с возможностью переопределить
   вручную через вход `version`.
4. **Механизм публикации** — **готовый action `KSXGitHub/github-actions-deploy-aur@v2`**.
```
```
```text
Дерево добавляемого:
.github/workflows/publish-aur.yml   (новый)
packaging/aur/PKGBUILD              (перенос из ~/aur/finkeeper24-bin)
```
