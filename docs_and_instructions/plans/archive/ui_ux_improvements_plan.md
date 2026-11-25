# План: комплексное улучшение UI/UX и юзабилити KMP-клиента FinKeeper24

## Цель и критерии готовности

Устранить визуальные дефекты мобильной вёрстки и контрастности, привести навигацию и иконографику к единому дизайн-стандарту, ускорить ежедневный ввод операций и повысить интерактивность аналитики на Дашборде.

**Критерии готовности (как Володя увидит результат):**
1. **Идеальная читаемость в любой из 5 тем:** системные часы и иконки статус-бара Android чётко видны в светлых и тёмных темах; плавающая кнопка «Добавить» не просвечивает текст списка под собой; заголовки карточек «Всего активов» и «Всего накоплено», баннер безопасности и все кнопки `GlassyButton` имеют высокий контраст (WCAG AA).
2. **Чистая мобильная навигация и вёрстка:** нижний таб-бар без горизонтального скролла и без лишней 6-й вкладки «Выход» (ровно 5 равномерных вкладок); баннер привязки email компактный, адаптирован под тёмную тему и показывается только на Дашборде; кнопка «Лимиты» аккуратно встроена в интерфейс без одиночной строки с захардкоженным синим цветом.
3. **Уникальная фирменная иконографика (без `compose.materialIconsExtended`):** в приложении не остаётся ни стандартных иконок Material, ни разнородных системных 3D-эмодзи (`💰`, `🕐`, `⏳`, `📅`, `✋`, `⚙`), ни текстовых спецсимволов (`✍︎`, `✕`, `◀`, `▶`, `▾`, `▸`, `Перетащить`). Все значки создаются вручную в едином стиле **Liquid Glass** (дополняя существующий набор [NavIcons.kt](file:///home/racer/projects/FinKeeper24-MyHomeBudget/mobile_app_client/composeApp/src/commonMain/kotlin/ru/homebudget/finkeeper/ui/components/NavIcons.kt)) — сначала согласуются на наглядном HTML-макете, затем переносятся в KMP.
4. **Быстрый ввод операций (1–2 тапа):** при открытии диалога добавления/редактирования фокус сразу стоит в поле суммы, выбор категории выполнен сеткой чипов (`FlowRow`) вместо прокрутки списка радиокнопок, а калькулятор выражений `AmountInputWithExpression` работает во всех денежных полях приложения (расходы, доходы, редактирование, плановые платежи, копилки, лимиты).
5. **Живой Дашборд и экран входа:** карточки Дашборда кликабельны (ведут в «Месяц» и «Накопления»), в центре бублика расходов отображается итоговая сумма (или выбранный сектор без грубой заливки градиента), столбчатый график динамики показывает точные цифры по тапу на месяц, а поля пароля имеют переключатель видимости («глазик») и пошаговое восстановление пароля.

---

## Выбранный подход и визуальные макеты изменений

### 1. Нижний бар и плавающая кнопка (FAB) на экранах со списками
- **Было:** 6 элементов в `horizontalScroll` (5 экранов + «Выйти» за краем экрана); полупрозрачная кнопка «Добавить» по центру внизу (`BottomCenter`), сквозь которую просвечивает текст карточек.
- **Станет:**
```text
┌──────────────────────────────────────────────┐
│  [Карточка категории / планового платежа]    │
│                                              │
│                          ┌─────────────────┐ │
│                          │  ＋  Добавить   │ │  <- Непрозрачный Solid FAB (BottomEnd) + тень
├──────────────────────────┴─────────────────┴─┤
│  📊       📅       🏷️       🏦       ⚙️     │  <- 5 вкладок с Modifier.weight(1f), без скролла
│ Сводка   Месяц  Категории Копилки Настройки  │
└──────────────────────────────────────────────┘
```

### 2. Карточка «Лимит трат» и переключатель на экране «Месяц»
- **Было:** отдельная строка с одинокой синей кнопкой `Лимиты` (`#0D47A1`) между карточками сводки и вкладками `Расходы | Доходы`.
- **Станет:** компактная кнопка-чип с иконкой настройки лимитов прямо в шапке карточки «Лимит трат» (рядом с `ⓘ`) в цветах активной темы — экономим ~48.dp вертикального пространства на мобильном экране.

### 3. Диалог добавления записи (`AddEntryDialog`)
- **Было:** поле суммы без автофокуса + вертикальный `LazyColumn` из `RadioButton` высотой `200.dp`.
- **Станет:**
```text
┌──────────────────────────────────────────────┐
│  Добавить расход                             │
│  [ ＋ ] [ − ] [ × ] [ ÷ ] [ ( ] [ ) ] [ ⌫ ]  │
│  ┌────────────────────────────────────────┐  │
│  │ Сумма: 450+320│                        │  │ <- Автофокус при открытии
│  └────────────────────────────────────────┘  │
│  = 770 ₽                                     │
│                                              │
│  Категория:                                  │
│  (● Продукты) ( ЖКХ ) ( Транспорт )          │ <- FlowRow чипов, 1-я категория выбрана
│  ( Кафе ) ( Здоровье ) ( Развлечения )       │
│                                              │
│  [Описание (необязательно)                ]  │
│  [Дата: 25.11.2026                        ]  │
│                       [ Отмена ] [ Добавить ]│
└──────────────────────────────────────────────┘
```

### 4. Графики на Дашборде
- **Бублик расходов (`ExpensePieChart`):** рисуется кольцевым `Stroke` (внутри бублика остаётся естественный стеклянный фон карточки без плоской круглой заплатки), в центре кольца — общая сумма расходов крупным шрифтом (а при тапе на сектор или строку легенды — название выбранной категории, её сумма и `%`).
- **Столбчатый график (`FinancialDynamicsChart`):** тап по группе столбцов любого месяца выделяет месяц и показывает над графиком компактную плашку: `Ноябрь 2026: Доходы 155 000 ₽ · Расходы 64 200 ₽ · Копилки 30 000 ₽`.

---

## Этапы реализации

### Этап 1 (P0): Системные бары, навигация, FAB, верхние баннеры и контраст карточек активов

1. **Системные статус-бар и нав-бар Android:**
   - Файлы: `commonMain/.../ui/theme/Theme.kt`, `commonMain/.../util/Platform.kt`, `androidMain/.../util/Platform.kt`, `desktopMain/.../util/Platform.kt`, `iosMain/.../util/Platform.kt`.
   - Добавить `@Composable expect fun ConfigureSystemBars(isDark: Boolean)` и вызывать её внутри `FinKeeperTheme`. На Android через `WindowCompat.getInsetsController(window, view)` выставлять `isAppearanceLightStatusBars = !isDark` и `isAppearanceLightNavigationBars = !isDark`.
2. **Нижнее меню (`GradientBottomBar`):**
   - Файл: `commonMain/.../ui/navigation/AppNavigation.kt`.
   - Убрать 6-й пункт «Выйти» из мобильного `GradientBottomBar` (оставив выход в боковом меню на Desktop и на экране «Настройки»).
   - Убрать `horizontalScroll(rememberScrollState())`, распределить 5 вкладок равномерно через `Modifier.weight(1f)`.
3. **Плавающая кнопка «Добавить» (`FloatingAddButton`):**
   - Файлы: `commonMain/.../ui/screens/MonthViewScreen.kt`, `commonMain/.../ui/screens/CategoriesScreen.kt`, `commonMain/.../ui/components/CommonComponents.kt`, `commonMain/.../ui/components/AppIcons.kt`.
   - Вынести `FloatingAddButton` в `CommonComponents.kt`: непрозрачный стиль `GlassyButtonStyle.Solid`, фирменная иконка `IconPlus`, `shadow(8.dp, RoundedCornerShape(26.dp))`, позиционирование `Alignment.BottomEnd` с отступом `padding(end = 16.dp, bottom = 16.dp)`.
   - Увеличить нижний `contentPadding` у `LazyColumn` на `MonthViewScreen` и `CategoriesScreen` до `88.dp`, чтобы кнопка не перекрывала последнюю строку списка в конце прокрутки.
4. **Баннеры `SecurityOnboardingPromptCard` и `AppUpdatePromptCard`:**
   - Файл: `commonMain/.../ui/navigation/AppNavigation.kt`.
   - Показывать `SecurityOnboardingPromptCard` только на экране `Screen.Dashboard` (чтобы не занимать место на рабочих экранах «Месяц» и «Категории»).
   - Сделать вёрстку компактной и адаптировать палитру карточки под `LocalIsDarkTheme.current` (в тёмной теме — тёмно-янтарный фон `Color(0xFF2D2112)` и светло-янтарный текст `Color(0xFFFDE68A)` вместо светло-бежевого `#FFF6E5` с коричневым текстом `#7C2D12`).
5. **Контраст карточек «Всего активов» и «Всего накоплено»:**
   - Файлы: `commonMain/.../ui/screens/DashboardScreen.kt`, `commonMain/.../ui/screens/SavingsScreen.kt`.
   - Использовать `MaterialTheme.colorScheme.onPrimary` как для заголовка, так и для суммы в обеих карточках во всех темах.

### Этап 2 (P1): Контраст кнопок `GlassyButton`, уникальная иконографика и кнопка «Лимиты»

1. **Контрастность `GlassyButton` в светлых темах:**
   - Файлы: `commonMain/.../ui/components/GlassyComponents.kt`, `commonMain/.../ui/screens/SavingsScreen.kt`, `commonMain/.../ui/screens/SettingsScreen.kt`.
   - В `GlassyComponents.kt` для `GlassyButtonStyle.Glassy` в светлой теме повысить контраст фона и бордера, а если передан кастомный акцентный `backgroundColor` (например, зелёный «Пополнить», красный «Снять» или «Выйти из аккаунта») — в светлой теме использовать хорошо читаемый насыщенный текст или более плотную заливку (`bgAlpha = 0.85f`), чтобы белый текст имел контраст не ниже `4.5:1`.
2. **Уникальный набор кастомных иконок FinKeeper24 (HTML-макет → KMP `AppIcons.kt`, без `materialIconsExtended`):**
   - **Шаг 2а — HTML-концепт и согласование:** собрать единый HTML-макет `docs_and_instructions/ui-concepts/custom-icons/index.html` с галереей всех авторских иконок в светлой и тёмной темах, линейкой размеров (`16 / 20 / 24 / 32 / 48dp`) и сравнением ключевых карточек «до / после», согласовать с Володей до переноса в Kotlin.
   - **Шаг 2б — Перенос в `commonMain/.../ui/components/AppIcons.kt` (кроссплатформенный `Canvas` с градиентами и бликами под стиль `Liquid Glass` и геометрию `NavIcons.kt`):**
     - **Объёмные стеклянные иконки с глубиной (18–32dp+):**
       - На `LoginScreen` заменить эмодзи `"💰"` на фирменную иконку `painterResource(Res.drawable.app_icon)`.
       - `IconScheduleGlass` (вместо `🕐` и `⏳`) — полупрозрачный стеклянный циферблат с бликом и золотисто-изумрудными стрелками (для плановых и авто-операций).
       - `IconFixedCalendarGlass` (вместо `📅`) — стеклянная карточка календаря с изумрудной шапкой и индикатором цикла (для заголовка «Фиксированные»).
       - `IconManualConfirmGlass` (вместо `✋`) — стеклянный значок подтверждения рукой/штампом для бейджа `вручную`.
       - `IconServerNodeGlass` (вместо `⚙`) — стеклянный модуль настроек сервера на экране входа.
       - `IconInfoGlass` (вместо `Icons.Outlined.Info`) — стеклянный значок пояснения расчёта лимита.
       - `IconLimitsGaugeGlass` — мини-регулятор/шкала для кнопки «Лимиты».
     - **Авторские компактные иконки действий в геометрии FinKeeper (14–22dp, округлые линии + стеклянный внутренний штрих):**
       - `IconEditPen` (вместо `✍︎`) — наклоненное перо со штрихом.
       - `IconTrash` (вместо `✕` на удалении записей/категорий) и `IconCloseCross` (вместо `✕` на отмене редактирования/закрытии поповера).
       - `IconChevronLeft` / `IconChevronRight` (вместо `◀` / `▶` в переключателе месяца).
       - `IconExpandChevron` (вместо `▾`, `▸`, `▼` и `Icons.Filled.ExpandMore` в `CollapsibleGlassyCard`, `PlannedGroupHeader`, `LimitInfoButton`).
       - `IconDragGrip` (вместо текстовой кнопки `"Перетащить"` `Strings.DRAG_HANDLE` в `CategoriesScreen`).
       - `IconPlus` (для FAB и кнопок добавления).
       - `IconEyeToggle(visible: Boolean)` (для переключения видимости пароля).
       - `IconCheckMark` (вместо `"✓"`).
   - **Шаг 2в — Полный отказ от `compose.materialIconsExtended`:**
     - Удалить импорты `androidx.compose.material.icons.*` из `CollapsibleGlassyCard.kt` и `MonthViewScreen.kt`.
     - Удалить `implementation compose.materialIconsExtended` из `mobile_app_client/composeApp/build.gradle` (меньше размер APK и гарантия 100% уникальных иконок в коде).
   - Файлы: `AppIcons.kt` (новый), `build.gradle`, `LoginScreen.kt`, `DashboardScreen.kt`, `MonthViewScreen.kt`, `CategoriesScreen.kt`, `SavingsScreen.kt`, `SettingsScreen.kt`, `CollapsibleGlassyCard.kt`, `PlannedComponents.kt`, `AppNavigation.kt`, `Strings.kt`.
3. **Кнопка «Лимиты» и `DesktopAddButton`:**
   - Файлы: `commonMain/.../ui/screens/MonthViewScreen.kt`, `commonMain/.../ui/components/CommonComponents.kt`.
   - Перенести кнопку «Лимиты» (с иконкой `IconLimitsGaugeGlass`) на мобилке внутрь карточки «Лимит трат» (в строку заголовка) и заменить захардкоженные цвета `#0D47A1` и `#1B5E20` на цвета текущей `MaterialTheme.colorScheme`.

### Этап 3 (P2): Единый ввод сумм с выражениями, автофокус и чипы категорий

1. **Вынос и расширение `AmountInputWithExpression`:**
   - Файлы: `commonMain/.../ui/components/CommonComponents.kt`, `MonthViewScreen.kt`, `PlannedComponents.kt`, `SavingsScreen.kt`.
   - Перенести `AmountInputWithExpression` в `CommonComponents.kt` с поддержкой опционального `autoFocus: Boolean = false` (через `FocusRequester` в `LaunchedEffect(Unit)`).
   - Подключить `AmountInputWithExpression` и `evalAmount` во все денежные диалоги:
     - `AddEntryDialog` (`autoFocus = true`)
     - `EditEntryDialog` (`autoFocus = true`)
     - `ConfirmPlannedDialog` и `OverridePlannedDialog` (`PlannedComponents.kt`, `autoFocus = true`)
     - `TransactionDialog` и `CreateGoalDialog` (`SavingsScreen.kt`, `autoFocus = true` для суммы пополнения/снятия)
     - `BudgetDialog` (`MonthViewScreen.kt` — поддержка выражений `evalAmount` в полях лимитов категорий).
2. **Сетка чипов категорий в `AddEntryDialog`:**
   - Файл: `commonMain/.../ui/screens/MonthViewScreen.kt`.
   - Заменить `LazyColumn` из `RadioButton` на `FlowRow` из компактных стеклянных чипов категорий/источников дохода внутри скроллируемого контейнера (`heightIn(max = 180.dp)`), с автовыбором первой активной категории при открытии диалога.

### Этап 4 (P2): Интерактивный Дашборд, графики и UX экрана входа

1. **Кликабельные карточки на Дашборде:**
   - Файлы: `commonMain/.../ui/screens/DashboardScreen.kt`, `commonMain/.../ui/navigation/AppNavigation.kt`, `commonMain/.../ui/viewmodel/MonthViewModel.kt`.
   - Передать в `DashboardScreen` колбэки `onOpenMonthExpenses`, `onOpenMonthIncomes`, `onOpenSavings`.
   - Тап по карточкам «Расходы» и «Прогноз на месяц» переключает на экран «Месяц» (вкладка Расходы); тап по «Доходы» — на экран «Месяц» (вкладка Доходы); тап по «Накопления» — на экран «Накопления».
2. **Улучшение графиков (`ChartComponents.kt`):**
   - `ExpensePieChart`: убрать плоскую заливку внутреннего круга (`drawCircle(color = surfaceColorVal)`), рисовать кольцевые сектора через `Stroke`, а в центре бублика отображать общую сумму расходов (или название, сумму и `%` выбранной по тапу категории).
   - `FinancialDynamicsChart`: добавить интерактивный выбор месяца по тапу на столбец/подпись месяца с отображением плашки точных сумм доходов, расходов и накоплений за выбранный месяц.
3. **Экран входа (`LoginScreen.kt` и `CommonComponents.kt`):**
   - В `AppTextField` при `isPassword = true` добавить `trailingIcon` с кнопкой переключения видимости пароля (`IconEyeToggle` из `AppIcons.kt`).
   - В `LoginScreen` сделать восстановление пароля пошаговым: сначала шаг 1 (ввод Email + кнопка «Получить код» + ссылка «У меня уже есть код»), а после запроса кода или клика по ссылке — шаг 2 (ввод токена + нового пароля + кнопка «Сменить пароль»).

### Этап 5: Проверка и тестирование

1. Запуск unit-тестов: `./gradlew :composeApp:testDebugUnitTest :composeApp:desktopTest`.
2. Проверка компиляции Desktop и Android (`./gradlew :composeApp:compileKotlinDesktop :composeApp:assembleDebug`).
3. Визуальная проверка всех экранов, диалогов и тем на эмуляторе `Pixel_10` с демо-данными (с последующим удалением временных скриншотов и БД).

---

## Что вне рамок задачи

- Изменения серверного API, схемы БД или логики синхронизации (все правки строго в UI-слое KMP-клиента).
- Изменение веб-клиента (`client/`).

## Ветка и Git

- Разработка ведётся в отдельной feature-ветке `feature/kmp-ui-ux-improvements`.
- Коммит, пуш и слияние в `main` — только после твоей проверки и явного одобрения.
