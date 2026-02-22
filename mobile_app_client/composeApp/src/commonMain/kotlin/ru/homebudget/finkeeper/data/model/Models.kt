package ru.homebudget.finkeeper.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthData(
    val token: String,
    val user: User
)

@Serializable
data class User(
    val id: Int,
    val username: String
)

@Serializable
data class Category(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Int = 1
)

@Serializable
data class IncomeSource(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("is_active") val isActive: Int = 1
)

@Serializable
data class Month(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val year: Int,
    val month: Int
)

@Serializable
data class Income(
    val id: Int,
    @SerialName("month_id") val monthId: Int,
    val source: String,
    val amount: Double,
    val date: String,
    val description: String? = null,
)

@Serializable
data class Expense(
    val id: Int,
    @SerialName("month_id") val monthId: Int,
    @SerialName("category_id") val categoryId: Int,
    val amount: Double,
    val date: String,
    val comment: String? = null,
    @SerialName("category_name") val categoryName: String? = null
)

@Serializable
data class Budget(
    val id: Int,
    @SerialName("month_id") val monthId: Int,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("limit_amount") val limitAmount: Double
)

@Serializable
data class SavingsGoal(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("current_amount") val currentAmount: Double = 0.0
)

@Serializable
data class SavingsTransaction(
    val id: Int,
    @SerialName("goal_id") val goalId: Int,
    val amount: Double,
    val date: String,
    @SerialName("month_id") val monthId: Int? = null
)

@Serializable
data class MonthSummary(
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val savings: Double = 0.0,
    val balance: Double = 0.0
)

@Serializable
data class CumulativeBalanceResponse(
    @SerialName("cumulativeBalance") val cumulativeBalance: Double = 0.0
)

@Serializable
data class TrendItem(
    val month: String,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val savings: Double = 0.0
)

// Request bodies
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AddIncomeRequest(
    @SerialName("month_id") val monthId: Int,
    val source: String,
    val amount: Double,
    val date: String
)

@Serializable
data class AddExpenseRequest(
    @SerialName("month_id") val monthId: Int,
    @SerialName("category_id") val categoryId: Int,
    val amount: Double,
    val date: String,
    val comment: String? = null
)

@Serializable
data class UpdateAmountRequest(
    val amount: Double
)

@Serializable
data class SetBudgetRequest(
    @SerialName("month_id") val monthId: Int,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("limit_amount") val limitAmount: Double
)

@Serializable
data class EnsureMonthRequest(
    val year: Int,
    val month: Int
)

@Serializable
data class CreateCategoryRequest(
    val name: String
)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    @SerialName("is_active") val isActive: Int? = null
)

@Serializable
data class CreateIncomeSourceRequest(
    val name: String
)

@Serializable
data class UpdateIncomeSourceRequest(
    val name: String? = null,
    @SerialName("is_active") val isActive: Int? = null
)

@Serializable
data class CreateSavingsGoalRequest(
    val name: String,
    @SerialName("target_amount") val targetAmount: Double = 0.0
)

@Serializable
data class UpdateSavingsGoalRequest(
    val name: String? = null,
    @SerialName("target_amount") val targetAmount: Double? = null,
    @SerialName("current_amount") val currentAmount: Double? = null
)

@Serializable
data class AddSavingsTransactionRequest(
    @SerialName("goal_id") val goalId: Int,
    val amount: Double,
    val date: String,
    @SerialName("month_id") val monthId: Int? = null
)

@Serializable
data class ReorderCategoriesRequest(
    val ids: List<Int>
)

@Serializable
data class ReorderIncomeSourcesRequest(
    val ids: List<Int>
)

@Serializable
data class UpdateUsernameRequest(
    @SerialName("newUsername") val newUsername: String
)

@Serializable
data class UpdatePasswordRequest(
    @SerialName("newPassword") val newPassword: String
)

@Serializable
data class ErrorResponse(
    val error: String? = null
)
