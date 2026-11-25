package ru.homebudget.finkeeper.data.analytics

/**
 * Словарь имён событий и ключей параметров аналитики FinKeeper24.
 *
 * Правило приватности: суммы, комментарии, названия категорий/копилок, email и пароли
 * в аналитику не передаются — только факты действий и безопасные метаданные.
 */
object AnalyticsEvents {
    // Навигация
    const val SCREEN_VIEW = "screen_view"

    // Авторизация
    const val AUTH_LOGIN_SUCCESS = "auth_login_success"
    const val AUTH_LOGIN_FAILED = "auth_login_failed"
    const val AUTH_REGISTER_SUCCESS = "auth_register_success"
    const val AUTH_REGISTER_FAILED = "auth_register_failed"
    const val AUTH_SOCIAL_START = "auth_social_start"
    const val AUTH_SOCIAL_SUCCESS = "auth_social_success"
    const val AUTH_SOCIAL_FAILED = "auth_social_failed"
    const val AUTH_LOGOUT = "auth_logout"
    const val AUTH_PASSWORD_RECOVERY_REQUESTED = "auth_password_recovery_requested"
    const val AUTH_PASSWORD_RECOVERY_CONFIRMED = "auth_password_recovery_confirmed"

    // Сеть и синхронизация
    const val NETWORK_OFFLINE = "network_offline"
    const val NETWORK_ONLINE = "network_online"
    const val SERVER_UNREACHABLE = "server_unreachable"
    const val SERVER_REACHABLE = "server_reachable"
    const val SYNC_FAILED = "sync_failed"
    const val SYNC_MANUAL_RETRY = "sync_manual_retry"

    // Операции за месяц
    const val EXPENSE_ADDED = "expense_added"
    const val EXPENSE_UPDATED = "expense_updated"
    const val EXPENSE_DELETED = "expense_deleted"
    const val INCOME_ADDED = "income_added"
    const val INCOME_UPDATED = "income_updated"
    const val INCOME_DELETED = "income_deleted"
    const val BUDGET_SET = "budget_set"
    const val PLANNED_CONFIRMED = "planned_confirmed"
    const val PLANNED_SKIPPED = "planned_skipped"
    const val PLANNED_OVERRIDDEN = "planned_overridden"
    const val PLANNED_RESET = "planned_reset"

    // Категории и источники дохода
    const val CATEGORY_ADDED = "category_added"
    const val CATEGORY_DELETED = "category_deleted"
    const val INCOME_SOURCE_ADDED = "income_source_added"
    const val INCOME_SOURCE_DELETED = "income_source_deleted"

    // Копилки
    const val SAVINGS_GOAL_CREATED = "savings_goal_created"
    const val SAVINGS_GOAL_DELETED = "savings_goal_deleted"
    const val SAVINGS_TX_ADDED = "savings_tx_added"

    // Настройки
    const val THEME_CHANGED = "theme_changed"
    const val BACKUP_CREATED = "backup_created"
    const val BACKUP_RESTORED = "backup_restored"
    const val EMAIL_VERIFICATION_REQUESTED = "email_verification_requested"

    // Ошибки
    const val ERROR_OCCURRED = "error_occurred"

    object Params {
        const val SCREEN = "screen"
        const val PROVIDER = "provider"
        const val IS_FIXED = "is_fixed"
        const val TYPE = "type"
        const val THEME = "theme"
        const val SKIPPED = "skipped"
        const val REASON = "reason"
        const val CONTEXT = "context"
        const val ERROR_TYPE = "error_type"
        const val ERROR_MESSAGE = "error_message"
    }

    object Screens {
        const val LOGIN = "login"
        const val DASHBOARD = "dashboard"
        const val MONTH_VIEW = "month_view"
        const val CATEGORIES = "categories"
        const val SAVINGS = "savings"
        const val SETTINGS = "settings"
    }
}
