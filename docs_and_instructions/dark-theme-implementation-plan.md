# План реализации тёмной темы для FinKeeper Web

## Обзор задачи

Добавить возможность переключения между светлой и тёмной темами в web-клиенте FinKeeper. Тёмная тема должна быть стильной, с хорошей читаемостью и контрастом (референс: fintech-дизайн Austria Wealth с Behance).

## Анализ текущего состояния

### Текущая архитектура CSS

**Файл**: `client/src/index.css`

- Tailwind CSS 4 с `@theme` блоком
- CSS-переменные для цветовой палитры:
  - `--color-primary`: #1b905b (primary green)
  - `--color-brand`: #6b8e23 (olive brand)
  - `--color-success`: #558b2f (income green)
  - `--color-danger`: #c62828 (expense red)
  - `--color-warning`: #d4a017 (savings gold)
  - `--color-background`: #d5ddd0 (olive background)
  - `--color-surface`: #ffffff (card white)
  - `--color-text-main`: #2c352d (main text)
  - `--color-text-muted`: #6e7a70 (muted text)

### AuthContext

**Файл**: `client/src/context/AuthContext.tsx`

- UiSettings хранится в localStorage как 'uiSettings'
- Текущие настройки: `{ autoCollapseSidebar: boolean }`
- Нужно добавить: `{ theme: 'light' | 'dark' | 'system' }`

### Layout

**Файл**: `client/src/components/Layout.tsx`

- Sidebar: glassmorphism с emerald gradient (`from-emerald-900/85`)
- Mobile nav: bottom navigation с gradient
- Main content: gradient from-green-50/20 via-emerald-50 to-teal-100
- Hardcoded Tailwind классы с цветами

### Страницы для стилизации

- Dashboard
- MonthView
- Categories
- Savings
- Settings
- Login
- PasswordRecovery
- EmailVerification
- SocialAuthCallback

---

## Референс дизайна

**Источник**: [Austria Wealth - Behance](https://www.behance.net/gallery/244845973)

### Характеристики тёмной темы референса

- **Фон**: глубокий тёмно-синий/угольный (#0a0f1a, #0d1117)
- **Surface**: тёмно-серый с subtle границами (#161b22, #1c2128)
- **Акценты**: яркий emerald/teal (#10b981, #14b8a6)
- **Текст**: высокий контраст (#f0fdf4 для основного, #9ca3af для muted)
- **Границы**: subtle (@/10, white/10)
- **Контраст**: минимум 4.5:1 для обычного текста, 3:1 для large text

---

## Предлагаемая архитектура

### 1. Структура CSS-переменных

```css
/* ===== LIGHT THEME (current) ===== */
:root {
  --bg-primary: #d5ddd0;
  --bg-surface: #ffffff;
  --bg-surface-hover: #f8faf8;
  
  --text-primary: #2c352d;
  --text-secondary: #6e7a70;
  --text-inverted: #ffffff;
  
  --border-default: #e2e8e0;
  --border-strong: #c8d4c8;
  
  --accent-primary: #1b905b;
  --accent-brand: #6b8e23;
  --accent-success: #558b2f;
  --accent-danger: #c62828;
  --accent-warning: #d4a017;
  
  /* Sidebar */
  --sidebar-bg: linear-gradient(to bottom, rgba(16, 64, 40, 0.85), rgba(16, 64, 40, 0.85), rgba(13, 51, 38, 0.85));
  --sidebar-text: #ffffff;
  --sidebar-text-muted: rgba(255, 255, 255, 0.7);
  
  /* Shadows */
  --shadow-sm: 0 1px 2px rgba(47, 62, 48, 0.05);
  --shadow-md: 0 4px 6px rgba(47, 62, 48, 0.07);
  --shadow-lg: 0 10px 15px rgba(47, 62, 48, 0.1);
}

/* ===== DARK THEME ===== */
[data-theme="dark"] {
  --bg-primary: #0a0f14;
  --bg-surface: #111820;
  --bg-surface-hover: #1a222d;
  
  --text-primary: #e8f5ec;
  --text-secondary: #8b9a8e;
  --text-inverted: #0a0f14;
  
  --border-default: #2a3441;
  --border-strong: #3d4a5c;
  
  --accent-primary: #10b981;
  --accent-brand: #22c55e;
  --accent-success: #10b981;
  --accent-danger: #ef4444;
  --accent-warning: #fbbf24;
  
  /* Sidebar - deeper emerald */
  --sidebar-bg: linear-gradient(to bottom, rgba(6, 78, 59, 0.95), rgba(6, 78, 59, 0.95), rgba(3, 47, 38, 0.95));
  --sidebar-text: #ecfdf5;
  --sidebar-text-muted: rgba(236, 253, 245, 0.6);
  
  /* Shadows - colored for dark mode */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.3);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.4);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.5);
}
```

### 2. Цветовые палитры

#### Светлая тема (существующая)

| Элемент | Цвет | HEX |
|---------|------|-----|
| Фон | Olive | #d5ddd0 |
| Surface | White | #ffffff |
| Текст | Dark olive | #2c352d |
| Muted | Gray olive | #6e7a70 |
| Primary | Emerald | #1b905b |
| Success | Green | #558b2f |
| Danger | Red | #c62828 |
| Warning | Gold | #d4a017 |

#### Тёмная тема (новая)

| Элемент | Цвет | HEX | Примечание |
|---------|------|-----|----------|
| Фон | Deep navy | #0a0f14 | Почти чёрный с синим оттенком |
| Surface | Dark slate | #111820 | Карточки |
| Surface hover | Lighter slate | #1a222d | Hover states |
| Текст | Soft white | #e8f5ec | Высокий контраст |
| Muted | Gray green | #8b9a8e | Вторичный текст |
| Primary | Bright emerald | #10b981 | Яркий акцент |
| Success | Emerald | #10b981 | |
| Danger | Red | #ef4444 | |
| Warning | Amber | #fbbf24 | |
| Border | Slate | #2a3441 | Subtle |
| Sidebar bg | Deep emerald | #064e3b | С сохранением brand |

### 3. Применение темы

#### Подход: data-theme атрибут

```html
<!-- На html или body -->
<html data-theme="light">
<html data-theme="dark">
```

Преимущества:
- Простота реализации
- CSS-переменные каскадируются
- Easy toggle через JavaScript
- Поддержка system preference через media query

---

## План реализации по шагам

### Шаг 1: Расширить CSS-переменные

**Файл**: `client/src/index.css`

1. Рефакторить существующие переменные в формат `--bg-*`, `--text-*`, `--border-*`
2. Добавить dark theme variables
3. Добавить `[data-theme="dark"]` селектор
4. Обновить body стили

### Шаг 2: Добавить ThemeProvider

**Новый файл**: `client/src/context/ThemeContext.tsx`

```typescript
interface ThemeContextType {
  theme: 'light' | 'dark' | 'system';
  resolvedTheme: 'light' | 'dark';
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
}
```

Функционал:
- Чтение сохранённой темы из localStorage
- Подписка на system preference
- Применение темы через data-theme атрибут
- Persist выбора пользователя

### Шаг 3: Обновить AuthContext

**Файл**: `client/src/context/AuthContext.tsx`

1. Добавить `theme: 'light' | 'dark' | 'system'` в UiSettings
2. Интегрировать ThemeContext или использовать отдельный storage key

### Шаг 4: Добавить переключатель в Settings

**Файл**: `client/src/pages/Settings.tsx`

Добавить в секцию "Интерфейс":

```tsx
<div className="flex items-center justify-between">
  <div>
    <h3 className="font-semibold">Тема оформления</h3>
    <p className="text-sm text-muted">Выберите светлую или тёмную тему</p>
  </div>
  <select 
    value={uiSettings.theme}
    onChange={(e) => updateUiSettings({ theme: e.target.value })}
    className="..."
  >
    <option value="light">Светлая</option>
    <option value="dark">Тёмная</option>
    <option value="system">Системная</option>
  </select>
</div>
```

### Шаг 5: Обновить Layout

**Файл**: `client/src/components/Layout.tsx`

1. Использовать CSS-переменные вместо hardcoded Tailwind классов
2. Заменить `from-emerald-900/85` на `var(--sidebar-bg)`
3. Обновить gradient фоны main content

### Шаг 6: Обновить компоненты

Проверить и обновить:

- `Modal.tsx` - backdrop, поверхности
- `StatusBanner.tsx` - фоны и текст
- `PageState.tsx` - пустые состояния
- `Login.tsx` - формы и кнопки
- `Dashboard.tsx` - карточки, графики
- `MonthView.tsx` - списки, категории
- `Categories.tsx` - таблицы, кнопки
- `Savings.tsx` - прогресс-бары

### Шаг 7: Обновить стили страниц

Заменить hardcoded Tailwind классы на CSS-переменные:

```tsx
// Вместо:
className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100"

// Использовать:
className="bg-[var(--bg-surface)] backdrop-blur-md rounded-3xl shadow-[var(--shadow-lg)] border border-[var(--border-default)]"
```

### Шаг 8: Обновить Login страницу

**Файл**: `client/src/pages/Login.tsx`

Требует особого внимания:
- Фон страницы логина
- Форма входа
- Кнопки действий

### Шаг 9: Тестирование

1. Проверить светлую тему - всё работает как раньше
2. Проверить тёмную тему - все элементы читаемы
3. Проверить переключение тем
4. Проверить system preference
5. Проверить mobile layout в обеих темах
6. Проверить accessibility (контраст)

---

## Mermaid диаграмма: Архитектура темы

```mermaid
graph TD
    A[User Settings] --> B[AuthContext UiSettings]
    B --> C{theme value}
    C -->|light| D[Light Theme CSS]
    C -->|dark| E[Dark Theme CSS]
    C -->|system| F[System Preference]
    F --> G{Media Query}
    G -->|prefers-dark| E
    G -->|prefers-light| D
    
    D --> H[data-theme='light']
    E --> I[data-theme='dark']
    
    H --> J[CSS Variables]
    I --> J
    
    J --> K[All Components]
    K --> L[Layout]
    K --> M[Pages]
    K --> N[Modals]
    K --> O[Forms]
    
    style D fill=#d5ddd0,color:#2c352d
    style E fill:#0a0f14,color:#e8f5ec
    style J fill:#10b981,color:#fff
```

---

## Приоритеты реализации

### Фаза 1: Основа (критично)
1. CSS-переменные для обеих тем
2. ThemeProvider с переключением
3. Применение к Layout и Sidebar
4. Переключатель в Settings

### Фаза 2: Компоненты (важно)
5. Modal, StatusBanner, PageState
6. Login страница
7. Dashboard карточки

### Фаза 3: Детали (улучшение)
8. MonthView, Categories, Savings
9. Графики (Recharts)
10. Mobile bottom navigation

---

## Файлы для изменения

| Файл | Действие |
|------|----------|
| `client/src/index.css` | Расширить CSS-переменные |
| `client/src/context/ThemeContext.tsx` | Создать (новый) |
| `client/src/context/AuthContext.tsx` | Добавить theme в UiSettings |
| `client/src/components/Layout.tsx` | Использовать CSS-переменные |
| `client/src/pages/Settings.tsx` | Добавить переключатель |
| `client/src/components/Modal.tsx` | Обновить стили |
| `client/src/components/StatusBanner.tsx` | Обновить стили |
| `client/src/components/PageState.tsx` | Обновить стили |
| `client/src/pages/Login.tsx` | Обновить стили |
| `client/src/pages/Dashboard.tsx` | Обновить стили |
| `client/src/pages/MonthView.tsx` | Обновить стили |
| `client/src/pages/Categories.tsx` | Обновить стили |
| `client/src/pages/Savings.tsx` | Обновить стили |

---

## Acceptance Criteria

1. **Переключение тем работает**: пользователь может выбрать light/dark/system
2. **Контраст соответствует WCAG**: минимум 4.5:1 для текста
3. **Все элементы читаемы**: кнопки, карточки, формы, графики
4. **Сохраняется выбор**: тема сохраняется между сессиями
5. **Mobile ready**: тёмная тема работает на мобильных устройствах
6. **Performance**: нет FOUC (flash of unstyled content)
7. **Совместимость**: sidebar glassmorphism сохранён в обеих темах
