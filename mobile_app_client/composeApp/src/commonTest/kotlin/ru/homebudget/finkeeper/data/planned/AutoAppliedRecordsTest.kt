package ru.homebudget.finkeeper.data.planned

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.homebudget.finkeeper.data.model.Expense
import ru.homebudget.finkeeper.data.model.Income

class AutoAppliedRecordsTest {

    private fun item(templateType: String, templateId: Long, name: String, amountCents: Long, day: Int) = PlannedItem(
        templateType = templateType,
        templateId = templateId,
        name = name,
        amountCents = amountCents,
        originalAmountCents = amountCents,
        dueDay = day,
        dueDate = LocalDate(2026, 9, day),
        requireConfirm = false,
        isSkipped = false,
        isOverridden = false,
        isOverdue = false,
    )

    private val internet = item(TEMPLATE_TYPE_CATEGORY, 13, "Интернет", 2000_00, 22)
    private val salary = item(TEMPLATE_TYPE_INCOME_SOURCE, 5, "Зарплата", 50000_00, 22)

    @Test
    fun keepsAutoAppliedWhenRealRecordNotDownloaded() {
        val result = PlannedResult(emptyList(), emptyList(), listOf(internet), listOf(salary))
            .withoutDownloadedRecords(expenses = emptyList(), incomes = emptyList())
        assertEquals(listOf(internet), result.autoAppliedExpenses)
        assertEquals(listOf(salary), result.autoAppliedIncomes)
    }

    // Частичный сбой синхронизации: настоящая запись уже скачана, а отметка о её создании
    // (planned-state) — нет. Без страховки запись посчиталась бы дважды
    @Test
    fun dropsAutoAppliedWhenRealRecordAlreadyDownloaded() {
        val realExpense = Expense(id = 77, monthId = 1, categoryId = 13, amount = 2000.0, date = "2026-09-22", comment = "Регулярный платёж")
        val realIncome = Income(id = 88, monthId = 1, source = "Зарплата", amount = 50000.0, date = "2026-09-22")
        val result = PlannedResult(emptyList(), emptyList(), listOf(internet), listOf(salary))
            .withoutDownloadedRecords(expenses = listOf(realExpense), incomes = listOf(realIncome))
        assertTrue(result.autoAppliedExpenses.isEmpty())
        assertTrue(result.autoAppliedIncomes.isEmpty())
    }

    @Test
    fun ordinaryRecordInSameCategoryDoesNotHideAutoApplied() {
        val manual = Expense(id = 78, monthId = 1, categoryId = 13, amount = 500.0, date = "2026-09-22", comment = "роутер")
        val result = PlannedResult(emptyList(), emptyList(), listOf(internet), emptyList())
            .withoutDownloadedRecords(expenses = listOf(manual), incomes = emptyList())
        assertEquals(listOf(internet), result.autoAppliedExpenses)
    }
}
