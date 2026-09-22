package ru.homebudget.finkeeper.data.planned

import ru.homebudget.finkeeper.data.model.Expense
import ru.homebudget.finkeeper.data.model.Income

// Тот же комментарий, что ставит сервер при создании регулярного расхода
// (server/db/helpers.js, materializePlannedRecord) — запись не должна меняться при синхронизации
private const val AUTO_EXPENSE_COMMENT = "Регулярный платёж"

// Отрицательный id: у виртуальной записи нет строки в БД, и id не пересечётся с настоящими
fun PlannedItem.toAutoExpense(monthLocalId: Long): Expense =
    Expense(
        id = -templateId.toInt(),
        monthId = monthLocalId.toInt(),
        categoryId = templateId.toInt(),
        amount = amountCents / 100.0,
        date = dueDate.toString(),
        comment = AUTO_EXPENSE_COMMENT,
    )

// Страховка от двойного счёта: основной признак созданной записи — materializedKeys
// (auto_created из planned-state), но если скачать planned-state не удалось, а сами записи
// уже скачаны — узнаём их по виду, с которым их создаёт сервер
fun PlannedResult.withoutDownloadedRecords(expenses: List<Expense>, incomes: List<Income>): PlannedResult =
    copy(
        autoAppliedExpenses = autoAppliedExpenses.filterNot { item ->
            expenses.any {
                it.id > 0 && it.categoryId.toLong() == item.templateId &&
                    it.comment == AUTO_EXPENSE_COMMENT && it.date.take(10) == item.dueDate.toString()
            }
        },
        autoAppliedIncomes = autoAppliedIncomes.filterNot { item ->
            incomes.any { it.id > 0 && it.source == item.name && it.date.take(10) == item.dueDate.toString() }
        },
    )

// Сервер создаёт доход с источником = имя шаблона
fun PlannedItem.toAutoIncome(monthLocalId: Long): Income =
    Income(
        id = -templateId.toInt(),
        monthId = monthLocalId.toInt(),
        source = name,
        amount = amountCents / 100.0,
        date = dueDate.toString(),
    )
