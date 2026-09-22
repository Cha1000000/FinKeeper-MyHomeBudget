package ru.homebudget.finkeeper.data.repository.savings

import ru.homebudget.finkeeper.data.local.dao.SavingsTransaction
import ru.homebudget.finkeeper.data.local.model.SyncStatus

// Скрытый расход «Пополнение копилки» создаёт только сервер. Пока пополнение не выгружено,
// его сумму нужно учитывать в расходах месяца самим, иначе офлайн остаток лимита не уменьшится.
// После выгрузки (synced) пополнение перестаёт учитываться — его место занимает скрытый расход
// с сервера. Снятия не считаем: для них сервер скрытый расход не создаёт.
fun unsyncedDepositsTotal(transactions: List<SavingsTransaction>, monthLocalId: Long): Double =
    transactions
        .filter {
            it.monthId == monthLocalId &&
                it.amount > 0 &&
                it.syncStatus != SyncStatus.SYNCED.value
        }
        .sumOf { it.amount.toDouble() }
