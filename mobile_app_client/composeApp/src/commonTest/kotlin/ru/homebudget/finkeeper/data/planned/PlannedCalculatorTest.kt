package ru.homebudget.finkeeper.data.planned

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты план-слоя — зеркало серверных сценариев (server/test/planned.test.js).
 * «Сегодня» во всех тестах — 11 июня 2026.
 */
class PlannedCalculatorTest {

    private val today = LocalDate(2026, 6, 11)

    private fun mortgage(autoDay: Int = 5, requireConfirm: Boolean = false) = PlannedTemplate(
        templateType = TEMPLATE_TYPE_CATEGORY,
        templateId = 1L,
        name = "Ипотека",
        fixedAmountCents = 45000_00L,
        autoDay = autoDay,
        requireConfirm = requireConfirm,
    )

    private fun salary(autoDay: Int = 25) = PlannedTemplate(
        templateType = TEMPLATE_TYPE_INCOME_SOURCE,
        templateId = 1L,
        name = "Зарплата",
        fixedAmountCents = 150000_00L,
        autoDay = autoDay,
        requireConfirm = false,
    )

    private fun calc(
        templates: List<PlannedTemplate>,
        materialized: Set<String> = emptySet(),
        overrides: Map<String, PlannedOverrideData> = emptyMap(),
        year: Int = 2026,
        month: Int = 6,
    ) = PlannedCalculator.calculate(templates, materialized, overrides, year, month, today)

    @Test
    fun `ненаступивший платёж текущего месяца попадает в план`() {
        val result = calc(listOf(mortgage(autoDay = 20)))
        assertEquals(1, result.expenses.size)
        val item = result.expenses.first()
        assertEquals(45000_00L, item.amountCents)
        assertEquals(LocalDate(2026, 6, 20), item.dueDate)
        assertEquals(false, item.isOverdue)
    }

    @Test
    fun `наступивший обычный платёж текущего месяца в план не попадает`() {
        val result = calc(listOf(mortgage(autoDay = 5)))
        assertTrue(result.expenses.isEmpty())
    }

    @Test
    fun `материализованный шаблон исключается из плана`() {
        val result = calc(
            listOf(mortgage(autoDay = 20)),
            materialized = setOf(plannedKey(TEMPLATE_TYPE_CATEGORY, 1L)),
        )
        assertTrue(result.expenses.isEmpty())
    }

    @Test
    fun `skip - платёж виден с флагом, но не входит в сумму`() {
        val result = calc(
            listOf(mortgage(autoDay = 20)),
            overrides = mapOf(plannedKey(TEMPLATE_TYPE_CATEGORY, 1L) to PlannedOverrideData(isSkipped = true)),
        )
        assertEquals(1, result.expenses.size)
        assertTrue(result.expenses.first().isSkipped)
        assertEquals(0L, result.plannedExpensesCents)
    }

    @Test
    fun `скипнутый платёж виден даже после наступления дня`() {
        val result = calc(
            listOf(mortgage(autoDay = 5)),
            overrides = mapOf(plannedKey(TEMPLATE_TYPE_CATEGORY, 1L) to PlannedOverrideData(isSkipped = true)),
        )
        assertEquals(1, result.expenses.size)
    }

    @Test
    fun `override подменяет сумму и день, original сохраняется`() {
        val result = calc(
            listOf(mortgage(autoDay = 20)),
            overrides = mapOf(
                plannedKey(TEMPLATE_TYPE_CATEGORY, 1L) to PlannedOverrideData(overrideAmountCents = 50000_00L, overrideDay = 25),
            ),
        )
        val item = result.expenses.first()
        assertEquals(50000_00L, item.amountCents)
        assertEquals(45000_00L, item.originalAmountCents)
        assertEquals(25, item.dueDay)
        assertTrue(item.isOverridden)
    }

    @Test
    fun `require_confirm висит в плане после наступления дня как просроченный`() {
        val result = calc(listOf(mortgage(autoDay = 5, requireConfirm = true)))
        assertEquals(1, result.expenses.size)
        assertTrue(result.expenses.first().isOverdue)
    }

    @Test
    fun `require_confirm в прошлом месяце остаётся просроченным`() {
        val result = calc(listOf(mortgage(autoDay = 5, requireConfirm = true)), month = 5)
        assertEquals(1, result.expenses.size)
        assertTrue(result.expenses.first().isOverdue)
    }

    @Test
    fun `прошлый месяц - обычные платежи в план не попадают`() {
        val result = calc(listOf(mortgage(autoDay = 20)), month = 5)
        assertTrue(result.expenses.isEmpty())
    }

    @Test
    fun `будущий месяц - полный план, расходы и доходы раздельно`() {
        val result = calc(listOf(mortgage(autoDay = 5), salary(autoDay = 25)), month = 7)
        assertEquals(1, result.expenses.size)
        assertEquals(1, result.incomes.size)
        assertEquals(45000_00L, result.plannedExpensesCents)
        assertEquals(150000_00L, result.plannedIncomesCents)
    }

    @Test
    fun `кламп - auto_day 31 в сентябре даёт 30-е`() {
        val result = calc(listOf(mortgage(autoDay = 31)), month = 9)
        assertEquals(30, result.expenses.first().dueDay)
        assertEquals(LocalDate(2026, 9, 30), result.expenses.first().dueDate)
    }

    @Test
    fun `кламп - февраль 2026 даёт 28-е`() {
        assertEquals(28, PlannedCalculator.daysInMonth(2026, 2))
        val result = calc(listOf(mortgage(autoDay = 31)), month = 2)
        // февраль 2026 — прошлый месяц относительно today, обычный платёж не в плане
        assertTrue(result.expenses.isEmpty())
    }

    @Test
    fun `сортировка по дню списания`() {
        val t1 = mortgage(autoDay = 25).copy(templateId = 1L)
        val t2 = mortgage(autoDay = 15).copy(templateId = 2L, name = "Интернет", fixedAmountCents = 900_00L)
        val result = calc(listOf(t1, t2))
        assertEquals(listOf(15, 25), result.expenses.map { it.dueDay })
    }

    // === autoApplied: наступившие регулярные записи, ещё не пришедшие с сервера ===

    @Test
    fun `наступивший обычный платёж без материализации отмечается автоматически`() {
        val result = calc(listOf(mortgage(autoDay = 5)))
        assertTrue(result.expenses.isEmpty())
        val item = result.autoAppliedExpenses.single()
        assertEquals(45000_00L, item.amountCents)
        assertEquals(LocalDate(2026, 6, 5), item.dueDate)
    }

    @Test
    fun `платёж с днём сегодня тоже отмечается автоматически`() {
        val result = calc(listOf(mortgage(autoDay = 11)))
        assertEquals(1, result.autoAppliedExpenses.size)
    }

    @Test
    fun `материализованный платёж не отмечается автоматически`() {
        val result = calc(listOf(mortgage(autoDay = 5)), materialized = setOf(plannedKey(TEMPLATE_TYPE_CATEGORY, 1L)))
        assertTrue(result.autoAppliedExpenses.isEmpty())
        assertTrue(result.expenses.isEmpty())
    }

    @Test
    fun `ненаступивший платёж остаётся в плане, а не отмечается`() {
        val result = calc(listOf(mortgage(autoDay = 20)))
        assertTrue(result.autoAppliedExpenses.isEmpty())
        assertEquals(1, result.expenses.size)
    }

    @Test
    fun `требующий подтверждения и пропущенный не отмечаются автоматически`() {
        val confirm = calc(listOf(mortgage(autoDay = 5, requireConfirm = true)))
        assertTrue(confirm.autoAppliedExpenses.isEmpty())
        val skipped = calc(
            listOf(mortgage(autoDay = 5)),
            overrides = mapOf(plannedKey(TEMPLATE_TYPE_CATEGORY, 1L) to PlannedOverrideData(isSkipped = true)),
        )
        assertTrue(skipped.autoAppliedExpenses.isEmpty())
    }

    @Test
    fun `отмеченный автоматически учитывает override суммы и дня`() {
        val result = calc(
            listOf(mortgage(autoDay = 5)),
            overrides = mapOf(plannedKey(TEMPLATE_TYPE_CATEGORY, 1L) to PlannedOverrideData(overrideAmountCents = 40000_00L, overrideDay = 8)),
        )
        val item = result.autoAppliedExpenses.single()
        assertEquals(40000_00L, item.amountCents)
        assertEquals(8, item.dueDay)
    }

    @Test
    fun `прошлый месяц — все обычные шаблоны без материализации отмечены`() {
        val result = calc(listOf(mortgage(autoDay = 28), salary(autoDay = 25)), month = 5)
        assertEquals(1, result.autoAppliedExpenses.size)
        assertEquals(1, result.autoAppliedIncomes.size)
        assertTrue(result.expenses.isEmpty())
        assertTrue(result.incomes.isEmpty())
    }

    @Test
    fun `будущий месяц ничего не отмечает`() {
        val result = calc(listOf(mortgage(autoDay = 1), salary(autoDay = 1)), month = 7)
        assertTrue(result.autoAppliedExpenses.isEmpty())
        assertTrue(result.autoAppliedIncomes.isEmpty())
    }

    @Test
    fun `наступившее поступление отмечается как доход`() {
        val result = calc(listOf(salary(autoDay = 10)))
        assertEquals(150000_00L, result.autoAppliedIncomes.single().amountCents)
        assertTrue(result.autoAppliedExpenses.isEmpty())
    }
}
