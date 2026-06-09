package ru.homebudget.finkeeper.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthData(
    val token: String,
    @SerialName("accessToken") private val rawAccessToken: String? = null,
    @SerialName("refreshToken") val refreshToken: String? = null,
    val user: User
) {
    val accessToken: String
        get() = rawAccessToken ?: token
}

@Serializable
data class User(
    val id: Int,
    val username: String,
    val email: String? = null,
    @SerialName("emailConfirmed") val emailConfirmed: Boolean = false,
    @SerialName("recoverabilityStatus") val recoverabilityStatus: String = "unprotected",
    @SerialName("canSelfRecover") val canSelfRecover: Boolean = false,
)

@Serializable
data class SocialProvider(
    val id: String,
    val enabled: Boolean = false,
    val displayName: String? = null,
)

@Serializable
data class SocialProvidersResponse(
    val providers: List<SocialProvider> = emptyList(),
)

@Serializable
data class NativeSocialAuthStartRequest(
    @SerialName("clientType") val clientType: String,
)

@Serializable
data class NativeSocialAuthStartResponse(
    val provider: String,
    @SerialName("clientType") val clientType: String,
    @SerialName("attemptToken") val attemptToken: String,
    @SerialName("authorizeUrl") val authorizeUrl: String,
    @SerialName("expiresAt") val expiresAt: String,
    @SerialName("pollIntervalMs") val pollIntervalMs: Int = 1500,
)

@Serializable
data class NativeSocialAuthStatusResponse(
    val provider: String,
    @SerialName("clientType") val clientType: String,
    val status: String,
    val code: String? = null,
    val error: String? = null,
    @SerialName("errorDescription") val errorDescription: String? = null,
    @SerialName("expiresAt") val expiresAt: String,
    @SerialName("completedAt") val completedAt: String? = null,
)

@Serializable
data class SocialAuthExchangeRequest(
    val code: String,
    @SerialName("rememberMe") val rememberMe: Boolean = true,
)

@Serializable
data class DebugTokenPreview(
    val token: String,
    @SerialName("expiresAt") val expiresAt: String,
)

@Serializable
data class PasswordRecoveryDebug(
    @SerialName("passwordReset") val passwordReset: DebugTokenPreview? = null,
)

@Serializable
data class EmailVerificationDebug(
    @SerialName("emailVerification") val emailVerification: DebugTokenPreview? = null,
)

@Serializable
data class PasswordRecoveryRequestResponse(
    val success: Boolean,
    val message: String,
    val debug: PasswordRecoveryDebug? = null,
)

@Serializable
data class PasswordRecoveryConfirmResponse(
    val success: Boolean,
)

@Serializable
data class EmailVerificationResponse(
    val success: Boolean,
    val user: User,
)

@Serializable
data class EmailDeliveryStatus(
    val delivered: Boolean,
    val reason: String? = null,
)

@Serializable
data class UserEmailUpdateResponse(
    val success: Boolean,
    val verificationRequired: Boolean,
    val user: User,
    val delivery: EmailDeliveryStatus? = null,
    val debug: EmailVerificationDebug? = null,
)

@Serializable
data class UserEmailVerificationRequestResponse(
    val success: Boolean,
    val verificationRequired: Boolean,
    val user: User,
    val delivery: EmailDeliveryStatus? = null,
    val debug: EmailVerificationDebug? = null,
)

@Serializable
data class BackupEntrySummary(
    val categories: Int = 0,
    val incomeSources: Int = 0,
    val savingsGoals: Int = 0,
    val months: Int = 0,
    val incomes: Int = 0,
    val expenses: Int = 0,
    val budgets: Int = 0,
    val savingsTransactions: Int = 0,
)

@Serializable
data class BackupEntry(
    val id: Int,
    val createdAt: String,
    val sizeBytes: Int = 0,
    val summary: BackupEntrySummary = BackupEntrySummary(),
)

@Serializable
data class BackupListResponse(
    val backups: List<BackupEntry> = emptyList(),
)

@Serializable
data class RestoreBackupResponse(
    val success: Boolean,
    val backup: BackupEntry,
)

@Serializable
data class Category(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Int = 1,
    @SerialName("is_fixed") val isFixed: Int = 0,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("auto_day") val autoDay: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class IncomeSource(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Int = 1,
    @SerialName("is_fixed") val isFixed: Int = 0,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("auto_day") val autoDay: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Month(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val year: Int,
    val month: Int,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Income(
    val id: Int,
    @SerialName("month_id") val monthId: Int,
    val source: String,
    val amount: Double,
    val date: String,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Expense(
    val id: Int,
    @SerialName("month_id") val monthId: Int,
    @SerialName("category_id") val categoryId: Int,
    val amount: Double,
    val date: String,
    val comment: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Budget(
    val id: Int,
    @SerialName("month_id") val monthId: Int,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("limit_amount") val limitAmount: Double,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SavingsGoal(
    val id: Int,
    @SerialName("user_id") val userId: Int? = null,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SavingsTransaction(
    val id: Int,
    @SerialName("goal_id") val goalId: Int,
    val amount: Double,
    val date: String,
    @SerialName("month_id") val monthId: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class DeletedRecord(
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: Int,
    @SerialName("deleted_at") val deletedAt: String,
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
    val password: String,
    val rememberMe: Boolean = true,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refreshToken") val refreshToken: String
)

@Serializable
data class PasswordRecoveryRequest(
    val email: String,
)

@Serializable
data class PasswordRecoveryConfirmRequest(
    val token: String,
    @SerialName("newPassword") val newPassword: String,
)

@Serializable
data class EmailVerificationConfirmRequest(
    val token: String,
)

@Serializable
data class UpdateUserEmailRequest(
    val email: String,
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
    val name: String,
    @SerialName("is_fixed") val isFixed: Int? = null,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("auto_day") val autoDay: Int? = null,
)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    @SerialName("is_active") val isActive: Int? = null,
    @SerialName("is_fixed") val isFixed: Int? = null,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("auto_day") val autoDay: Int? = null,
)

@Serializable
data class CreateIncomeSourceRequest(
    val name: String,
    @SerialName("is_fixed") val isFixed: Int? = null,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("auto_day") val autoDay: Int? = null,
)

@Serializable
data class UpdateIncomeSourceRequest(
    val name: String? = null,
    @SerialName("is_active") val isActive: Int? = null,
    @SerialName("is_fixed") val isFixed: Int? = null,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("auto_day") val autoDay: Int? = null,
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
    @SerialName("currentPassword") val currentPassword: String,
    @SerialName("newPassword") val newPassword: String
)

@Serializable
data class RestoreBackupRequest(
    @SerialName("backupId") val backupId: Int,
    @SerialName("confirmationText") val confirmationText: String,
)

@Serializable
data class ValidationErrorDetail(
    val field: String? = null,
    val message: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String? = null,
    val code: String? = null,
    val details: List<ValidationErrorDetail> = emptyList()
)
