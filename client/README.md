# FinKeeper Web Client

Веб-клиент FinKeeper для домашней бухгалтерии.

## Стек

- React 19
- TypeScript
- Vite 7
- Tailwind CSS 4
- React Router 7
- Recharts 3
- Axios
- `@dnd-kit` для drag-and-drop

## Что умеет клиент

- авторизация и хранение JWT
- просмотр dashboard и аналитики
- учёт доходов и расходов по месяцам
- управление категориями и источниками дохода
- лимиты бюджета по категориям
- копилки и транзакции накоплений
- настройки профиля и ручные backup/restore операции

## Основные команды

```bash
npm run dev
```

Запуск Vite dev server.

```bash
npm run build
```

Production build клиента.

```bash
npm run lint
```

Проверка ESLint.

```bash
npm run preview
```

Локальный preview production build.

## API

Клиент работает с backend из корня репозитория и использует Bearer token для всех защищённых `/api/*` запросов.

Основной API слой расположен в `src/api/`.

## Маршруты

- `/login`
- `/`
- `/month`
- `/categories`
- `/savings`
- `/settings`

## Особенности реализации

- protected routes через auth guard
- desktop layout с боковой навигацией
- mobile layout с bottom navigation
- периодическое обновление финансовой сводки
- работа с дополнительными server полями не ломает web client, так как API изменения в основном additive

## Связанные документы

- корневой обзор проекта: `../README.md`
- актуальная документация по sync: `../docs_and_instructions/current_sync_implementation.md`
