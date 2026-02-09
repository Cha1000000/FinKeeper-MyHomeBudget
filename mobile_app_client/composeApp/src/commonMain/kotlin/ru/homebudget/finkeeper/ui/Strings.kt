package ru.homebudget.finkeeper.ui

/**
 * Строковые ресурсы для KMP приложения.
 * Используется для локализации и упрощения поддержки текстовых констант.
 */
object Strings {
    // === Экран входа ===
    const val APP_TITLE = "FinKeeper"
    const val APP_SUBTITLE = "Домашняя бухгалтерия"
    const val LOGIN_TITLE = "Вход"
    const val REGISTER_TITLE = "Регистрация"
    const val USERNAME_LABEL = "Имя пользователя"
    const val PASSWORD_LABEL = "Пароль"
    const val LOGIN_BUTTON = "Войти"
    const val REGISTER_BUTTON = "Зарегистрироваться"
    const val ALREADY_HAVE_ACCOUNT = "Уже есть аккаунт? Войти"
    const val NO_ACCOUNT = "Нет аккаунта? Зарегистрироваться"
    const val SERVER_SETTINGS = "⚙ Настройки сервера"
    const val SERVER_URL_LABEL = "URL сервера"
    const val SERVER_URL_PLACEHOLDER = "http://10.0.2.2:3002"
    const val SAVE = "Сохранить"

    // === Экран обзора (Dashboard) ===
    const val DASHBOARD_TITLE = "Обзор"
    const val INCOMES = "Доходы"
    const val EXPENSES = "Расходы"
    const val SAVINGS = "Накопления"
    const val SAVINGS_PERCENT = "% в копилку"
    const val AVAILABLE = "Доступно"
    const val TOTAL_ASSETS = "Всего активов"
    const val FINANCIAL_DYNAMICS = "Динамика финансов"
    const val EXPENSE_STRUCTURE = "Структура расходов"

    // === Экран месяца (MonthView) ===
    const val EXPENSES_TAB = "Расходы"
    const val INCOMES_TAB = "Доходы"
    const val SPENDING_LIMIT = "Лимит трат"
    const val REMAINDER = "Остаток:"
    const val EXPENSES_BY_CATEGORIES = "Расходы по категориям"
    const val BUDGET = "Бюджет"
    const val ADD = "+ Добавить"
    const val NO_EXPENSES_THIS_MONTH = "Нет расходов за этот месяц"
    const val NO_INCOMES_THIS_MONTH = "Нет доходов за этот месяц"
    const val DELETE_INCOME = "Удалить доход"
    const val DELETE_EXPENSE = "Удалить расход"
    const val CONFIRM_DELETE = "Вы уверены, что хотите удалить этот доход?"
    const val CONFIRM_DELETE_EXPENSE = "Вы уверены, что хотите удалить этот расход?"
    const val CONFIRMATION = "Подтверждение"
    const val SOURCE_NOT_FOUND = "Источник дохода \"%1\$s\" не найден. Создать его и добавить доход?"
    const val YES = "Да"
    const val CANCEL = "Отмена"
    const val ADD_EXPENSE_IN_CATEGORY = "+ Добавить расход в \"%1\$s\""
    const val ADD_EXPENSE = "Добавить расход"
    const val ADD_INCOME = "Добавить доход"
    const val CATEGORY = "Категория"
    const val SOURCE = "Источник"
    const val OTHER = "Другой:"
    const val NEW_SOURCE = "Новый источник"
    const val AMOUNT = "Сумма"
    const val COMMENT_OPTIONAL = "Комментарий (необязательно)"
    const val BUDGET_SETTINGS = "Настройка бюджета"
    const val ADD_EXPENSE_FOR_CATEGORY = "Добавить расход в \"%1\$s\""
    const val EDIT_EXPENSE = "Редактировать расход"

    // === Экран справочников (Categories) ===
    const val REFERENCE_BOOKS = "Справочники"
    const val CATEGORIES_TAB = "Категории"
    const val INCOME_SOURCES_TAB = "Источники дохода"
    const val ADD_NEW = "+ Добавить"
    const val NO_CATEGORIES = "Нет категорий"
    const val NO_INCOME_SOURCES = "Нет источников дохода"
    const val NEW_CATEGORY = "Новая категория"
    const val NEW_INCOME_SOURCE = "Новый источник дохода"
    const val DELETE_TITLE = "Удалить"
    const val DELETE_CONFIRMATION = "Вы уверены? Элемент будет деактивирован."
    const val NAME = "Название"
    const val NEW_NAME = "Новая копилка"

    // === Экран копилок (Savings) ===
    const val PIGGY_BANKS = "Копилки"
    const val CREATE = "Создать"
    const val NO_PIGGY_BANKS = "Нет копилок. Создайте первую!"
    const val FROM = "из"
    const val PERCENT = "%1\$d%%"
    const val DEPOSIT = "Пополнить"
    const val WITHDRAW = "Снять"
    const val NEW_PIGGY_BANK = "Новая копилка"
    const val TARGET_AMOUNT = "Целевая сумма"
    const val CURRENT_AMOUNT = "Текущая сумма"
    const val EDIT_PIGGY_BANK = "Редактировать копилку"
    const val DELETE_PIGGY_BANK = "Удалить копилка"
    const val DELETE_PIGGY_BANK_CONFIRM = "Вы уверены? Все данные копилки будут удалены."
    const val PIGGY_BANK_NAME = "Копилка: %1\$s"

    // === Экран настроек (Settings) ===
    const val SETTINGS = "Настройки"
    const val PROFILE = "Профиль"
    const val SAVE_NAME = "Сохранить имя"
    const val SECURITY = "Безопасность"
    const val NEW_PASSWORD = "Новый пароль"
    const val CONFIRM_PASSWORD = "Подтвердите пароль"
    const val CHANGE_PASSWORD = "Сменить пароль"
    const val DATA_MANAGEMENT = "Управление данными"
    const val CREATE_BACKUP = "Создать резервную копию"
    const val RESTORE_FROM_BACKUP = "Восстановить из копии"
    const val THEME = "Оформление"
    const val THEME_SYSTEM = "Как в системе"
    const val THEME_LIGHT = "Светлая"
    const val THEME_DARK = "Тёмная"
    const val LOGOUT = "Выйти из аккаунта"
    const val CHANGE_NAME = "Сменить имя"
    const val CHANGE_NAME_CONFIRM = "Изменить имя пользователя на \"%1\$s\"?"
    const val CHANGE_PASSWORD_CONFIRM = "Вы уверены, что хотите сменить пароль?"
    const val RESTORE_DATA = "Восстановить данные"
    const val RESTORE_DATA_CONFIRM = "Все текущие данные будут заменены данными из последней резервной копии. Продолжить?"
    const val BACKUP_RESTORE_CONFIRMATION_TITLE = "Восстановление из бэкапа"
    const val BACKUP_RESTORE_CONFIRMATION_MESSAGE = "Вы уверены? При восстановлении все текущие данные будут удалены и заменены данными резервной копии. Это действие нельзя отменить."
    const val BACKUP_RESTORE_BUTTON = "Восстановить"
    const val BACKUP_RESTORE_SUCCESS = "Бэкап успешно восстановлен"
    const val BACKUP_RESTORE_ERROR = "Ошибка восстановления бэкапа"
    const val LOGOUT_CONFIRM = "Вы уверены, что хотите выйти?"
    const val LOGOUT_TITLE = "Выход"

    // === Сообщения валидации и ошибок ===
    const val ENTER_USERNAME_AND_PASSWORD = "Введите имя пользователя и пароль"
    const val INVALID_USERNAME_OR_PASSWORD = "Неверное имя пользователя или пароль"
    const val CONNECTION_ERROR = "Ошибка подключения к серверу"
    const val LOADING_ERROR = "Ошибка загрузки данных"
    const val LOADING_ERROR_SHORT = "Ошибка загрузки"
    const val ERROR_ADDING_SOURCE = "Ошибка добавления источника"

    // === Сообщения об успехе ===
    const val USERNAME_UPDATED = "Имя пользователя успешно обновлено."
    const val PASSWORD_CHANGED = "Пароль успешно изменен."
    const val BACKUP_CREATED = "Резервная копия успешно создана."
    const val DATA_RESTORED = "Данные успешно восстановлены."

    // === Сообщения об ошибках ===
    const val ERROR_UPDATING_NAME = "Ошибка обновления имени."
    const val ERROR_CHANGING_PASSWORD = "Ошибка смены пароля."
    const val ERROR_CREATING_BACKUP = "Ошибка создания резервной копии."
    const val ERROR_RESTORING_DATA = "Ошибка восстановления данных."

    // === Навигация ===
    const val NAV_DASHBOARD = "Обзор"
    const val NAV_MONTH = "Месяц"
    const val NAV_CATEGORIES = "Категории"
    const val NAV_PIGGY_BANKS = "Копилки"
    const val NAV_SETTINGS = "Настр."
    const val NAV_EXIT = "Выход"

    // === Навигация месяца ===
    const val PREV_MONTH = "◀"
    const val NEXT_MONTH = "▶"
    const val COLLAPSE = "▲"
    const val EXPAND = "▼"

    // === Символы действий ===
    const val EDIT = "✍︎"
    const val DELETE = "✕"
    const val CHECK = "✓"

    // === API ошибки ===
    const val HTTP_ERROR = "HTTP %1\$d: %2\$s"

    // === Категории по умолчанию ===
    const val SAVINGS_CATEGORY = "Пополнение копилки"
    const val NO_CATEGORY = "Без категории"

    // === Диалоги ===
    const val NEW_INCOME_SOURCE_DIALOG = "Новый источник дохода"
    const val SOURCE_NOT_FOUND_DIALOG = "Источник \"%1\$s\" не найден. Создать его?"
}
