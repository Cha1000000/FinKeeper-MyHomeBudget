# План: добавить StartupWMClass в .deb и .rpm (фикс иконки/имени в Dock на Wayland)

## Проблема

Окно десктопного FinKeeper24 (Compose Desktop/JVM, через XWayland) репортит
**WM_CLASS / app_id = `ru-homebudget-finkeeper-MainKt`** (имя главного класса
`ru.homebudget.finkeeper.MainKt`). Чтобы Dock/таскбар (Niri/DMS, GNOME, KDE на Wayland)
сопоставил окно с ярлыком и показал правильную иконку и имя, в `.desktop` должна быть строка:

```
StartupWMClass=ru-homebudget-finkeeper-MainKt
```

jpackage (которым Compose пакует `.deb`/`.rpm`) это поле **не пишет вообще** — известная
проблема (JBR-9114, JOSM #18013). В Compose-DSL опции для этого нет.

**Что уже сделано:** AUR-пакет `finkeeper24-bin` чинит это сам (генерит свой `.desktop` со
`StartupWMClass`). Этот план — для тех, кто ставит **напрямую `.deb`/`.rpm`** с сайта.

## Подход (Вариант A): пост-обработка пакетов в CI

Не трогаем `build.gradle` и упаковку Compose. В `build-linux.yml` после `packageDeb`/`packageRpm`
дописываем `StartupWMClass` в `.desktop` **внутри собранного пакета** и пересобираем его.
`.desktop` лежит в обоих пакетах в `/opt/finkeeper24/lib/<...>.desktop` и регистрируется
скриптами установки (postinst / %post через `xdg-desktop-menu`).

### Изменения в `.github/workflows/build-linux.yml`

Добавить шаги **между** сборкой пакетов и их загрузкой как артефактов:

1. **Установить инструменты:** к текущему `apt-get install -y rpm fakeroot` добавить `rpmrebuild`
   (для пересборки `.rpm` с сохранением метаданных и скриптлетов).

2. **Патч `.deb`** (нативные `dpkg-deb`):
   ```bash
   deb=$(ls mobile_app_client/composeApp/build/compose/binaries/main/deb/*.deb)
   tmp=$(mktemp -d)
   dpkg-deb -R "$deb" "$tmp"
   desktop=$(find "$tmp/opt" -name '*.desktop' | head -n1)
   grep -q '^StartupWMClass=' "$desktop" || \
     echo 'StartupWMClass=ru-homebudget-finkeeper-MainKt' >> "$desktop"
   dpkg-deb -b "$tmp" "$deb"     # пересобрать на месте
   ```

3. **Патч `.rpm`** (через `rpmrebuild` со скриптом правки файлов):
   ```bash
   rpm=$(ls mobile_app_client/composeApp/build/compose/binaries/main/rpm/*.rpm)
   # --change-files выполняет скрипт в buildroot перед переупаковкой
   rpmrebuild --change-files='d=$(find ./opt -name "*.desktop" | head -n1); \
     grep -q "^StartupWMClass=" "$d" || echo "StartupWMClass=ru-homebudget-finkeeper-MainKt" >> "$d"' \
     -p -n "$rpm"   # -p: из готового пакета, -n: без запроса подтверждения
   # rpmrebuild кладёт результат в ~/rpmbuild/RPMS/... — скопировать поверх исходного
   ```
   > Точные флаги `rpmrebuild` уточним на первом прогоне CI (доступность пакета на
   > `ubuntu-latest` и поведение `--change-files`/вывод проверим логами; при проблемах —
   > запасной путь через `rpm2cpio` + `rpmbuild` со сгенерированным spec).

4. **Проверка (sanity):** перед загрузкой артефактов убедиться, что `StartupWMClass` реально
   присутствует в `.desktop` обоих пакетов (распаковать и `grep`), иначе — `exit 1`.

5. Шаги `Upload Linux DEB` / `Upload Linux RPM` остаются как есть — заливаются уже
   пропатченные пакеты.

### Значение StartupWMClass

`ru-homebudget-finkeeper-MainKt` — то же, что в рабочем локальном оверрайде и в AUR-пакете.
Захардкожено строкой (с комментарием), т.к. это стабильное имя главного класса.

## Что НЕ делаем

- `build.gradle` и код приложения не трогаем (app_id оставляем как есть; меняем только `.desktop`).
- Автозаливку `.deb`/`.rpm` на сайт не трогаем — остаётся ручной.
- AUR-пакет уже исправлен отдельно (см. `packaging/aur/PKGBUILD`).

## Порядок проверки после внедрения

1. Запустить workflow **Build Linux Desktop App**.
2. Скачать артефакты, проверить `.desktop` внутри (`StartupWMClass` на месте).
3. (Опц.) Поставить `.deb`/`.rpm` на Wayland-DE и убедиться, что иконка/имя в Dock корректны.

## Результат (2026-06-03)

- **`.deb` — сделано и проверено.** Шаг в `build-linux.yml` дописывает `StartupWMClass` в `.desktop`
  внутри `.deb` (`dpkg-deb -R/-b --root-owner-group`, владелец `root` сохраняется, с верификацией
  и безопасным откатом). Подтверждено локально на реальном пакете 2.0.3 и на CI (`✓ DEB: StartupWMClass добавлен`).
- **`.rpm` — НЕ делаем.** На `ubuntu-latest` `rpmrebuild` отсутствует в apt и не ставится, а
  пересборка через `rpm2cpio`+`rpmbuild`/spec убивает scriptlet'ы регистрации ярлыка (`%post`)
  → сломанный пакет, что хуже исходного. По решению владельца `.rpm` оставлен как есть
  (jpackage-дефолт, без `StartupWMClass`). Не-AUR пользователи `.rpm` на Wayland-DE не получат
  фикс иконки/имени в Dock — допустимый компромисс.
- Для не-AUR `.rpm` фикс возможен только на уровне самого jpackage (custom `.desktop`-шаблон через
  `--resource-dir`) — это Вариант B из обсуждения, отложен.
