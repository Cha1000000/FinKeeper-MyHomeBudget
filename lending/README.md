# FinKeeper Landing Page

Стильный, современный и технологичный лендинг для мультиплатформенного приложения ведения домашних финансов **FinKeeper**. 

Создан с акцентом на "Glassmorphism" и ощущении финансовой премиальности. Лендинг использует производительные анимации, эффекты свечения и типографику, оптимизированную для кириллицы.

## Стек технологий

* **Фреймворк:** React 19 + TypeScript
* **Сборщик:** Vite 7
* **Стилизация:** Tailwind CSS v4 (включая использование `@theme` API)
* **Анимации:** Framer Motion (Scroll animations, Hover effects, Staggering)
* **Иконки:** Lucide React
* **Шрифты:** Unbounded (Заголовки), Inter (UI текст), JetBrains Mono (Суммы)

## Разработка и запуск

Проект находится в директории `lending` внутри основного репозитория `FinKeeper-MyHomeBudget`.

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
├── src/
│   ├── App.tsx          # Основной компонент лендинга
│   ├── index.css        # Точка входа стилей и Tailwind v4 Theme Variables
│   ├── main.tsx         # Точка входа React (импорты шрифтов)
│   └── vite-env.d.ts    # Типы Vite среды
├── index.html           # HTML-документ, мета-теги и описание
├── package.json         # Зависимости и скрипты
├── tailwind.config.ts   # (не требуется, конфигурация внутри index.css для Tailwind v4)
└── vite.config.ts       # Настройка Vite, плагинов React и TailwindCSS
```

## Дополнительно

Лендинг ссылается:
* На Web-версию: http://217.114.8.82:3002/
* Разделы для мобильного приложения (RuStore) и десктопных версий (GitHub) пока ссылаются на `#`.
