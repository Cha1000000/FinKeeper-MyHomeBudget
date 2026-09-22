package ru.homebudget.finkeeper.data.local.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SavingsTransactionsMigrationTest {

    @BeforeTest
    fun isolateLog() {
        // DesktopSyncLog пишет в user.home/.finkeeper — не трогаем реальный лог пользователя
        System.setProperty("user.home", Files.createTempDirectory("finkeeper-test").toString())
    }

    private val baseColumns = """
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        savings_goal_id INTEGER NOT NULL
    """

    private val tailColumns = """
        amount INTEGER NOT NULL,
        type TEXT NOT NULL,
        description TEXT,
        date TEXT NOT NULL,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL,
        server_id TEXT,
        sync_status TEXT NOT NULL
    """

    private fun SqlDriver.exec(sql: String) = execute(null, sql, 0, null)

    private fun SqlDriver.queryLongs(sql: String): List<Long?> =
        executeQuery(null, sql, { cursor ->
            val values = mutableListOf<Long?>()
            while (cursor.next().value) values += cursor.getLong(0)
            QueryResult.Value(values)
        }, 0).value

    private fun SqlDriver.columns(): List<String> =
        executeQuery(null, "PRAGMA table_info(savings_transactions)", { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) cursor.getString(1)?.let(names::add)
            QueryResult.Value(names)
        }, 0).value

    private fun SqlDriver.insertRow(withMonth: Boolean) {
        val (cols, vals) = if (withMonth) "month_id, " to "7, " else "" to ""
        exec(
            "INSERT INTO savings_transactions (user_id, savings_goal_id, ${cols}amount, type, date, created_at, updated_at, sync_status) " +
                "VALUES (1, 1, ${vals}1000, 'deposit', '2026-09-22', 'x', 'x', 'pending')",
        )
    }

    @Test
    fun oldLayoutWithMonthIdAtEnd_isRebuiltAndKeepsMonthId() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.exec("CREATE TABLE savings_transactions ($baseColumns, $tailColumns)")
        driver.exec("ALTER TABLE savings_transactions ADD COLUMN month_id INTEGER")
        driver.insertRow(withMonth = true)

        migrateSavingsTransactionsMonthColumn(driver)

        assertEquals("month_id", driver.columns()[3])
        assertEquals(listOf<Long?>(7), driver.queryLongs("SELECT month_id FROM savings_transactions"))
    }

    // Как в продакшене: файловая БД. Драйвер берёт соединение на каждый execute, поэтому
    // ручной BEGIN/COMMIT транзакцию не открывает — нужна транзакция самого SQLDelight
    @Test
    fun oldLayout_onFileDatabase_isMigratedInOneTransaction() {
        val file = Files.createTempFile("finkeeper-mig", ".db").toFile().apply { deleteOnExit() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        driver.exec("CREATE TABLE savings_transactions ($baseColumns, $tailColumns)")
        driver.exec("ALTER TABLE savings_transactions ADD COLUMN month_id INTEGER")
        driver.insertRow(withMonth = true)

        val committed = migrateSavingsTransactionsMonthColumn(driver)

        assertEquals(true, committed)
        assertEquals("month_id", driver.columns()[3])
        assertEquals(listOf<Long?>(7), driver.queryLongs("SELECT month_id FROM savings_transactions"))
    }

    @Test
    fun correctLayout_isNotRebuiltAndKeepsMonthIdAcrossLaunches() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.exec("CREATE TABLE savings_transactions ($baseColumns, month_id INTEGER, $tailColumns)")
        driver.insertRow(withMonth = true)

        repeat(3) { migrateSavingsTransactionsMonthColumn(driver) }

        assertEquals(listOf<Long?>(7), driver.queryLongs("SELECT month_id FROM savings_transactions"))
    }

    @Test
    fun failedMigration_isRolledBackAndKeepsData() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.exec("CREATE TABLE savings_transactions ($baseColumns, $tailColumns)")
        driver.exec("ALTER TABLE savings_transactions ADD COLUMN month_id INTEGER")
        driver.insertRow(withMonth = true)
        // DROP TABLE не удаляет представление — шаг миграции упадёт
        driver.exec("CREATE VIEW savings_transactions_new AS SELECT 1")
        val columnsBefore = driver.columns()

        migrateSavingsTransactionsMonthColumn(driver)

        assertEquals(columnsBefore, driver.columns())
        assertEquals(listOf<Long?>(7), driver.queryLongs("SELECT month_id FROM savings_transactions"))
    }

    @Test
    fun layoutWithoutMonthId_getsColumnInRightPlace() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.exec("CREATE TABLE savings_transactions ($baseColumns, $tailColumns)")
        driver.insertRow(withMonth = false)

        migrateSavingsTransactionsMonthColumn(driver)

        assertEquals("month_id", driver.columns()[3])
        assertEquals(listOf<Long?>(1000), driver.queryLongs("SELECT amount FROM savings_transactions"))
    }
}
