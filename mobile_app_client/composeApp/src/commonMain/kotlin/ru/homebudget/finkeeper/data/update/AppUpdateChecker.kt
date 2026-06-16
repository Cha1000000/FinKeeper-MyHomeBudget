package ru.homebudget.finkeeper.data.update

/**
 * Результат проверки доступности обновления приложения.
 */
sealed interface AppUpdateStatus {
    /**
     * Доступна новая версия.
     *
     * @param versionLabel человекочитаемая версия для текста баннера (например "2.3.0").
     *                      Может быть пустой (Android/RuStore не всегда отдаёт имя версии).
     * @param versionToken стабильный идентификатор версии для логики «пропустить эту версию»
     *                     (desktop — semver, Android — versionCode).
     */
    data class Available(val versionLabel: String, val versionToken: String) : AppUpdateStatus

    /** Установлена актуальная версия — обновление не требуется. */
    data object UpToDate : AppUpdateStatus

    /** Проверить не удалось (нет сети, RuStore не установлен, сборка не из стора и т.п.). */
    data object Unknown : AppUpdateStatus
}

/**
 * Платформенная проверка обновлений.
 * - Android: RuStore In-app Update SDK.
 * - Desktop: JSON-манифест на сайте.
 * - iOS: заглушка.
 *
 * Реализации регистрируются в платформенных Koin-модулях (по образцу [SocialAuthLauncher]).
 */
interface AppUpdateChecker {
    /** Проверить наличие обновления. Не бросает исключений — при ошибке возвращает [AppUpdateStatus.Unknown]. */
    suspend fun check(): AppUpdateStatus

    /**
     * Запустить обновление.
     * - Android: FLEXIBLE-флоу RuStore (фоновая загрузка + установка); при сбое — открыть страницу в RuStore.
     * - Desktop: открыть страницу загрузок в браузере.
     * - iOS: no-op.
     */
    fun startUpdate()
}
