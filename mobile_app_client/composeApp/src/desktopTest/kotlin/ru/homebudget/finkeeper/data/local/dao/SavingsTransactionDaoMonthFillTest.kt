package ru.homebudget.finkeeper.data.local.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class SavingsTransactionDaoMonthFillTest {

    private fun newDao(): SavingsTransactionDao {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinKeeperDatabase.Schema.create(driver)
        return SavingsTransactionDao(FinKeeperDatabase(driver))
    }

    private fun SavingsTransactionDao.insertDeposit(monthId: Long?): Long =
        insert(
            userId = 1,
            savingsGoalId = 1,
            monthId = monthId,
            amount = 1000,
            type = "deposit",
            date = "2026-10-01",
            createdAt = "2026-10-01 10:00:00",
            updatedAt = "2026-10-01 10:00:00",
            serverId = "5",
        )

    @Test
    fun fillsMissingMonthWithoutTouchingUpdatedAt() {
        val dao = newDao()
        val id = dao.insertDeposit(monthId = null)

        dao.fillMonthIdIfMissing(id, 7)

        val tx = dao.getById(id)!!
        assertEquals(7L, tx.monthId)
        assertEquals("2026-10-01 10:00:00", tx.updatedAt)
    }

    @Test
    fun keepsAlreadySetMonth() {
        val dao = newDao()
        val id = dao.insertDeposit(monthId = 3)

        dao.fillMonthIdIfMissing(id, 7)

        assertEquals(3L, dao.getById(id)!!.monthId)
    }
}
