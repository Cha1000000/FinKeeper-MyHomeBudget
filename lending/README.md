# FinKeeper24 Landing Page
Стильный, современный и технологичный лендинг для мультиплатформенного приложения ведения домашних финансов **FinKeeper24**. 
Создан с акцентом на "Glassmorphism" и ощущении финансовой премиальности. Лендинг использует производительные анимации, эффекты свечения и типографику, оптимизированную для кириллицы.

## Стек технологий

* **Фреймворк:** React 19 + TypeScript
* **Сборщик:** Vite 7
* **Стилизация:** Tailwind CSS v4 (включая использование `@theme` API)
* **Анимации:** Framer Motion (Scroll animations, Hover effects, Staggering)
* **Иконки:** Lucide React
* **Шрифты:** Unbounded (Заголовки), Inter (UI текст), JetBrains Mono (Суммы)

## Разработка и запуск

Проект находится в директории `lending` внутри основного репозитория `FinKeeper24-MyHomeBudget`.

### Требования
* Node.js (v18+)
* npm / yarn / pnpm

### Установка зависимостей

```bash
cd lending
npm install
```

### Запуск в режиме разработки (Dev Server)

```bash
npm run dev
```

После выполнения команды лендинг будет доступен по локальному адресу, обычно: `http://localhost:5173`. Горячая перезагрузка (HMR) работает "из коробки" благодаря Vite.

### Сборка для Production

```bash
npm run build
```

Команда скомпилирует TypeScript-код и соберет оптимальный бандл (minified HTML, CSS, JS и оптимизированные шрифты) внутри папки `dist/`.

### Предпросмотр Production сборки

```bash
npm run preview
```

## Структура проекта

```text
lending/
├── public/              # Статические файлы и дополнительные страницы
│   ├── downloads.html   # Страница загрузки приложений (macOS, Windows, Android)
│   ├── privacy.html     # Политика конфиденциальности
│   └── terms.html       # Пользовательское соглашение
├── src/
│   ├── App.tsx          # Основной компонент лендинга
│   ├── index.css        # Точка входа стилей и Tailwind v4 Theme Variables
│   ├── main.tsx         # Точка входа React (импорты шрифтов)
│   └── vite-env.d.ts    # Типы Vite среды
├── index.html           # HTML-документ, мета-теги и описание
├── package.json         # Зависимости и скрипты
└── vite.config.ts       # Настройка Vite, плагинов React и TailwindCSS
```

## Дополнительно

Лендинг ссылается на:
* **Web-версию:** https://app.finkeeper24.ru
* **Страницу загрузок:** `/downloads.html` (Desktop и Mobile версии)
* **Документы:** `/privacy.html` и `/terms.html`

## Выкладка на finkeeper24.ru

Одной командой из `lending/` (нужен SSH-доступ к серверу, `rsync`, Node):

```bash
./deploy.sh --bump 2.2.4   # новая версия в downloads.html, latest.json и бейдже App.tsx
git commit -am "chore(lending): версия 2.2.4"
./deploy.sh --check        # только проверки
./deploy.sh                # проверки → сборка → выкладка → проверка живого сайта
./deploy.sh --rollback     # вернуть предыдущую выложенную версию (повторный — обратно)
```

Перед выкладкой скрипт останавливается, если:
* версия расходится между `downloads.html`, `latest.json`, бейджем `App.tsx` и `app_version`
  в `mobile_app_client/composeApp/build.gradle` (`latest.json` — оповещение desktop о новой версии);
* хоть одна ссылка со страницы загрузок не отвечает 200 — **сначала залей пакеты** в
  `/var/www/finkeeper24.ru/html/downloads/`;
* в `lending/` есть незакоммиченные изменения (`--allow-dirty` — обойти).

Сборка идёт у тебя, на сервер уходит только `dist` (через `dist.new` с подменой; прежняя версия
остаётся как `dist.prev`). Папку `downloads` скрипт не трогает. Сервер/путь/адрес сайта можно
переопределить: `DEPLOY_HOST`, `DEPLOY_ROOT`, `SITE_URL`.

Порядок релиза desktop: пакеты из GitHub Actions → залить в `downloads/` → `./deploy.sh --bump X.Y.Z`
→ коммит → `./deploy.sh` → запустить `publish-aur.yml`.
