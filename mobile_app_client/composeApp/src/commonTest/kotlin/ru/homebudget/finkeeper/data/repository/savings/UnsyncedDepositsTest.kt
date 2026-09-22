package ru.homebudget.finkeeper.data.repository.savings

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.homebudget.finkeeper.data.local.dao.SavingsTransaction
import ru.homebudget.finkeeper.data.local.model.SyncStatus

class UnsyncedDepositsTest {

    private fun tx(
        monthId: Long?,
        amount: Long,
        syncStatus: SyncStatus,
    ) = SavingsTransaction(
        id = 0,
        userId = 1,
        savingsGoalId = 1,
        monthId = monthId,
        amount = amount,
        type = if (amount >= 0) "deposit" else "withdrawal",
        description = null,
        date = "2026-09-22",
        createdAt = "2026-09-22T00:00:00Z",
        updatedAt = "2026-09-22T00:00:00Z",
        serverId = null,
        syncStatus = syncStatus.value,
    )

    @Test
    fun countsOnlyNotSyncedDepositsOfThisMonth() {
        val transactions = listOf(
            tx(monthId = 5, amount = 1000, syncStatus = SyncStatus.PENDING),
            tx(monthId = 5, amount = 300, syncStatus = SyncStatus.FAILED),
            // уже на сервере — его скрытый расход придёт синхронизацией расходов
            tx(monthId = 5, amount = 700, syncStatus = SyncStatus.SYNCED),
            // снятие — сервер для него скрытый расход не создаёт
            tx(monthId = 5, amount = -200, syncStatus = SyncStatus.PENDING),
            // другой месяц и пополнение без месяца
            tx(monthId = 6, amount = 900, syncStatus = SyncStatus.PENDING),
            tx(monthId = null, amount = 400, syncStatus = SyncStatus.PENDING),
        )

        assertEquals(1300.0, unsyncedDepositsTotal(transactions, monthLocalId = 5))
    }

    @Test
    fun zeroWhenNothingPending() {
        assertEquals(0.0, unsyncedDepositsTotal(emptyList(), monthLocalId = 5))
        assertEquals(
            0.0,
            unsyncedDepositsTotal(listOf(tx(monthId = 5, amount = 500, syncStatus = SyncStatus.SYNCED)), monthLocalId = 5),
        )
    }
}
