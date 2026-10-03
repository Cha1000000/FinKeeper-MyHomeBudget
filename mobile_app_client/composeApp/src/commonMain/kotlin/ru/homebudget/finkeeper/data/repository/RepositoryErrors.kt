package ru.homebudget.finkeeper.data.repository

/**
 * Служебная скрытая категория расходов, в которую сервер записывает пополнения копилок.
 * Имя совпадает с серверным `SAVINGS_EXPENSE_CATEGORY` и занято для пользовательских категорий.
 */
const val SAVINGS_EXPENSE_CATEGORY_NAME = "Пополнение копилки"

/** Имя уже занято другой записью (в том числе удалённой: сервер хранит имена уникальными). */
class DuplicateNameException(val name: String) : Exception("Name already exists: $name")

/** Имя зарезервировано под служебную категорию [SAVINGS_EXPENSE_CATEGORY_NAME]. */
class ReservedNameException(val name: String) : Exception("Reserved name: $name")

/** Пустое имя. */
class BlankNameException : Exception("Name is blank")

/**
 * Копилку с ненулевым балансом удалить нельзя: её пополнения уже вычтены из «Свободно» своих
 * месяцев, и сумма пропала бы. Сначала средства выводят или переводят.
 */
class SavingsGoalNotEmptyException : Exception("Savings goal is not empty")
