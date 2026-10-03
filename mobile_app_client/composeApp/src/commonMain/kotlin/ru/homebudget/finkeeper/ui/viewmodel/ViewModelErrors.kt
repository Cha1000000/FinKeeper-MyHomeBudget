package ru.homebudget.finkeeper.ui.viewmodel

import kotlinx.coroutines.CancellationException
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.data.repository.BlankNameException
import ru.homebudget.finkeeper.data.repository.DuplicateNameException
import ru.homebudget.finkeeper.data.repository.ReservedNameException
import ru.homebudget.finkeeper.data.repository.SavingsGoalNotEmptyException
import ru.homebudget.finkeeper.ui.Strings

/**
 * Текст ошибки действия пользователя для баннера экрана. Отмену корутины не глотает:
 * вызывать из `catch (e: Exception)` безопасно. Текст исключения пользователю не показываем
 * (английский, технический, с адресом сервера) — он уходит в лог, на экран — [fallback].
 */
internal fun Throwable.toActionError(fallback: String = Strings.OPERATION_FAILED): String {
    if (this is CancellationException) throw this
    println("[VM] action failed: ${this::class.simpleName}: $message")
    printStackTrace()
    return when {
        // Отказы проверок репозитория — понятная пользователю причина, а не сбой
        this is DuplicateNameException -> Strings.ERROR_NAME_EXISTS
        this is ReservedNameException -> Strings.ERROR_NAME_RESERVED
        this is BlankNameException -> Strings.ERROR_NAME_BLANK
        this is SavingsGoalNotEmptyException -> Strings.DELETE_PIGGY_BANK_NOT_EMPTY
        isConnectivityFailure() -> Strings.OFFLINE_RETRY_LATER
        else -> fallback
    }
}

/**
 * Ошибка загрузки после успешного чтения Room: «не удалось загрузить» снимается, а «сервер
 * ответил ошибкой» остаётся до следующей серверной фазы — локальное чтение её не опровергает.
 */
internal fun String?.withoutLocalLoadError(): String? = takeUnless { it == Strings.LOADING_ERROR }
