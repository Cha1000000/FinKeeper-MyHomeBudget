package ru.homebudget.finkeeper.data.planned

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Виртуальный план-слой регулярных платежей.
 *
 * Зеркало серверной логики `getPlannedRecords()` (server/db/helpers.js): плановый платёж =
 * активный фиксированный шаблон, ещё не материализованный в реальную запись за месяц,
 * с учётом исключений (пропуск / override суммы или дня). Ничего не пишет в БД —
 * чистое вычисление, работает оффлайн. Суммы — в копейках (как в локальной БД).
 */

const val TEMPLATE_TYPE_CATEGORY = "category"
const val TEMPLATE_TYPE_INCOME_SOURCE = "income_source"

fun plannedKey(templateType: String, templateId: Long): String = "$templateType:$templateId"

/** Фиксированный шаблон (категория расхода или источник дохода) */
data class PlannedTemplate(
    val templateType: String,
    val templateId: Long,
    val name: String,
    val fixedAmountCents: Long,
    val autoDay: Int,
    val requireConfirm: Boolean,
)

/** Исключение на месяц (skip / override) */
data class PlannedOverrideData(
    val overrideAmountCents: Long? = null,
    val overrideDay: Int? = null,
    val isSkipped: Boolean = false,
)

/** Плановый (ещё не материализованный) платёж месяца */
data class PlannedItem(
    val templateType: String,
    val templateId: Long,
    val name: String,
    val amountCents: Long,
    val originalAmountCents: Long,
    val dueDay: Int,
    val dueDate: LocalDate,
    val requireConfirm: Boolean,
    val isSkipped: Boolean,
    val isOverridden: Boolean,
    val isOverdue: Boolean,
) {
    val key: String get() = plannedKey(templateType, templateId)
}

data class PlannedResult(
    val expenses: List<PlannedItem>,
    val incomes: List<PlannedItem>,
    // Наступившие регулярные записи, которые сервер ещё не создал (или они не пришли синхронизацией):
    // учитываются как факт, пока настоящая запись не появится — иначе офлайн они пропадали бы из сумм
    val autoAppliedExpenses: List<PlannedItem> = emptyList(),
    val autoAppliedIncomes: List<PlannedItem> = emptyList(),
) {
    /** Суммы для прогнозных агрегатов: скипнутые не считаются */
    val plannedExpensesCents: Long get() = expenses.filterNot { it.isSkipped }.sumOf { it.amountCents }
    val plannedIncomesCents: Long get() = incomes.filterNot { it.isSkipped }.sumOf { it.amountCents }

    companion object {
        val EMPTY = PlannedResult(emptyList(), emptyList())
    }
}

object PlannedCalculator {

    fun daysInMonth(year: Int, month: Int): Int =
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

    /**
     * @param templates активные фиксированные шаблоны (is_fixed=1, is_active=1, сумма и день заданы)
     * @param materializedKeys ключи [plannedKey] шаблонов, уже материализованных за месяц
     *        (зеркало auto_created_records; включает и удалённые пользователем платежи — «удаление = пропуск»)
     * @param overrides исключения месяца по ключу [plannedKey]
     * @param year/month месяц-бакет
     * @param today текущая дата (инъекция для тестов)
     */
    fun calculate(
        templates: List<PlannedTemplate>,
        materializedKeys: Set<String>,
        overrides: Map<String, PlannedOverrideData>,
        year: Int,
        month: Int,
        today: LocalDate,
    ): PlannedResult {
        val isPastMonth = year < today.year || (year == today.year && month < today.monthNumber)
        val isCurrentMonth = year == today.year && month == today.monthNumber
        val maxDay = daysInMonth(year, month)

        val expenses = mutableListOf<PlannedItem>()
        val incomes = mutableListOf<PlannedItem>()
        val autoAppliedExpenses = mutableListOf<PlannedItem>()
        val autoAppliedIncomes = mutableListOf<PlannedItem>()

        for (template in templates) {
            val key = plannedKey(template.templateType, template.templateId)
            if (key in materializedKeys) continue // уже факт (или удалён пользователем = пропуск месяца)

            val override = overrides[key]
            val requireConfirm = template.requireConfirm
            val isSkipped = override?.isSkipped == true
            val dueDay = minOf(override?.overrideDay ?: template.autoDay, maxDay)

            // Попадание в план: будущий месяц — все шаблоны; текущий — require_confirm
            // и скипнутые всегда (для подтверждения/unskip), обычные — пока день не наступил;
            // прошлый — только неподтверждённые require_confirm (висят как просроченные).
            // Наступившие обычные создаёт сервер при ensure; пока их нет — autoApplied.
            val isDueAuto = !requireConfirm && !isSkipped &&
                (isPastMonth || (isCurrentMonth && dueDay <= today.dayOfMonth))
            // Прошлый месяц, как на сервере (`isPastMonth && (!requireConfirm || isSkipped)` → не план):
            // скипнутые отбрасываем здесь, обычные уходят в autoApplied через isDueAuto,
            // в плане остаются только неподтверждённые require_confirm
            if (isPastMonth && isSkipped) continue

            val isOverdue = requireConfirm && !isSkipped &&
                (isPastMonth || (isCurrentMonth && dueDay < today.dayOfMonth))

            val item = PlannedItem(
                templateType = template.templateType,
                templateId = template.templateId,
                name = template.name,
                amountCents = override?.overrideAmountCents ?: template.fixedAmountCents,
                originalAmountCents = template.fixedAmountCents,
                dueDay = dueDay,
                dueDate = LocalDate(year, month, dueDay),
                requireConfirm = requireConfirm,
                isSkipped = isSkipped,
                isOverridden = override != null && (override.overrideAmountCents != null || override.overrideDay != null),
                isOverdue = isOverdue,
            )

            val isExpense = template.templateType == TEMPLATE_TYPE_CATEGORY
            when {
                isDueAuto && isExpense -> autoAppliedExpenses.add(item)
                isDueAuto -> autoAppliedIncomes.add(item)
                isExpense -> expenses.add(item)
                else -> incomes.add(item)
            }
        }

        expenses.sortBy { it.dueDay }
        incomes.sortBy { it.dueDay }
        autoAppliedExpenses.sortBy { it.dueDay }
        autoAppliedIncomes.sortBy { it.dueDay }
        return PlannedResult(expenses, incomes, autoAppliedExpenses, autoAppliedIncomes)
    }
}
