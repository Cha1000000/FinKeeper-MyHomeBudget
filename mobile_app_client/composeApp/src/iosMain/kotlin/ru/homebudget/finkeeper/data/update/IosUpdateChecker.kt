package ru.homebudget.finkeeper.data.update

/**
 * iOS вне scope фичи (приложение не распространяется в App Store) — заглушка.
 */
class IosUpdateChecker : AppUpdateChecker {
    override suspend fun check(): AppUpdateStatus = AppUpdateStatus.UpToDate
    override fun startUpdate() = Unit
}
