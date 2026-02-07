package ru.homebudget.finkeeper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.homebudget.finkeeper.data.model.*

class ApiClient(private val tokenStorage: TokenStorage) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.NONE
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            val token = tokenStorage.token
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
        }
    }

    private val baseUrl: String get() = tokenStorage.serverUrl + "/api"

    // ── Auth ──

    suspend fun login(username: String, password: String): AuthData {
        val response = client.post("$baseUrl/auth/login") {
            setBody(LoginRequest(username, password))
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun register(username: String, password: String): AuthData {
        val response = client.post("$baseUrl/auth/register") {
            setBody(LoginRequest(username, password))
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun getMe(): User {
        val response = client.get("$baseUrl/auth/me")
        checkResponse(response)
        return response.body()
    }

    // ── User ──

    suspend fun updateUsername(newUsername: String) {
        val response = client.put("$baseUrl/user/rename") {
            setBody(UpdateUsernameRequest(newUsername))
        }
        checkResponse(response)
    }

    suspend fun updatePassword(newPassword: String) {
        val response = client.put("$baseUrl/user/password") {
            setBody(UpdatePasswordRequest(newPassword))
        }
        checkResponse(response)
    }

    suspend fun createManualBackup() {
        val response = client.post("$baseUrl/user/backup")
        checkResponse(response)
    }

    suspend fun restoreBackup() {
        val response = client.post("$baseUrl/user/restore")
        checkResponse(response)
    }

    // ── Categories ──

    suspend fun getCategories(): List<Category> {
        val response = client.get("$baseUrl/categories")
        checkResponse(response)
        return response.body()
    }

    suspend fun createCategory(name: String): Category {
        val response = client.post("$baseUrl/categories") {
            setBody(CreateCategoryRequest(name))
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateCategory(id: Int, request: UpdateCategoryRequest) {
        val response = client.put("$baseUrl/categories/$id") {
            setBody(request)
        }
        checkResponse(response)
    }

    suspend fun reorderCategories(ids: List<Int>) {
        val response = client.put("$baseUrl/categories/reorder") {
            setBody(ReorderCategoriesRequest(ids))
        }
        checkResponse(response)
    }

    // ── Income Sources ──

    suspend fun getIncomeSources(): List<IncomeSource> {
        val response = client.get("$baseUrl/income_sources")
        checkResponse(response)
        return response.body()
    }

    suspend fun createIncomeSource(name: String): IncomeSource {
        val response = client.post("$baseUrl/income_sources") {
            setBody(CreateIncomeSourceRequest(name))
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateIncomeSource(id: Int, request: UpdateIncomeSourceRequest) {
        val response = client.put("$baseUrl/income_sources/$id") {
            setBody(request)
        }
        checkResponse(response)
    }

    suspend fun deleteIncomeSource(id: Int) {
        val response = client.delete("$baseUrl/income_sources/$id")
        checkResponse(response)
    }

    // ── Months ──

    suspend fun ensureMonth(year: Int, month: Int): Month {
        val response = client.post("$baseUrl/months/ensure") {
            setBody(EnsureMonthRequest(year, month))
        }
        checkResponse(response)
        return response.body()
    }

    // ── Incomes ──

    suspend fun getIncomes(monthId: Int): List<Income> {
        val response = client.get("$baseUrl/months/$monthId/incomes")
        checkResponse(response)
        return response.body()
    }

    suspend fun addIncome(request: AddIncomeRequest): Income {
        val response = client.post("$baseUrl/incomes") {
            setBody(request)
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateIncome(id: Int, amount: Double) {
        val response = client.put("$baseUrl/incomes/$id") {
            setBody(UpdateAmountRequest(amount))
        }
        checkResponse(response)
    }

    suspend fun deleteIncome(id: Int) {
        val response = client.delete("$baseUrl/incomes/$id")
        checkResponse(response)
    }

    // ── Expenses ──

    suspend fun getExpenses(monthId: Int): List<Expense> {
        val response = client.get("$baseUrl/months/$monthId/expenses")
        checkResponse(response)
        return response.body()
    }

    suspend fun addExpense(request: AddExpenseRequest): Expense {
        val response = client.post("$baseUrl/expenses") {
            setBody(request)
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateExpense(id: Int, amount: Double) {
        val response = client.put("$baseUrl/expenses/$id") {
            setBody(UpdateAmountRequest(amount))
        }
        checkResponse(response)
    }

    suspend fun deleteExpense(id: Int) {
        val response = client.delete("$baseUrl/expenses/$id")
        checkResponse(response)
    }

    // ── Budgets ──

    suspend fun getBudgets(monthId: Int): List<Budget> {
        val response = client.get("$baseUrl/months/$monthId/budgets")
        checkResponse(response)
        return response.body()
    }

    suspend fun setBudget(request: SetBudgetRequest) {
        val response = client.post("$baseUrl/budgets") {
            setBody(request)
        }
        checkResponse(response)
    }

    // ── Savings ──

    suspend fun getSavingsGoals(): List<SavingsGoal> {
        val response = client.get("$baseUrl/savings_goals")
        checkResponse(response)
        return response.body()
    }

    suspend fun createSavingsGoal(request: CreateSavingsGoalRequest): SavingsGoal {
        val response = client.post("$baseUrl/savings_goals") {
            setBody(request)
        }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateSavingsGoal(id: Int, request: UpdateSavingsGoalRequest) {
        val response = client.put("$baseUrl/savings_goals/$id") {
            setBody(request)
        }
        checkResponse(response)
    }

    suspend fun deleteSavingsGoal(id: Int) {
        val response = client.delete("$baseUrl/savings_goals/$id")
        checkResponse(response)
    }

    suspend fun addSavingsTransaction(request: AddSavingsTransactionRequest) {
        val response = client.post("$baseUrl/savings_transactions") {
            setBody(request)
        }
        checkResponse(response)
    }

    suspend fun getSavingsTransactions(goalId: Int): List<SavingsTransaction> {
        val response = client.get("$baseUrl/savings_transactions/$goalId")
        checkResponse(response)
        return response.body()
    }

    // ── Analytics ──

    suspend fun getMonthSummary(monthId: Int): MonthSummary {
        val response = client.get("$baseUrl/months/$monthId/summary")
        checkResponse(response)
        return response.body()
    }

    suspend fun getTrend(): List<TrendItem> {
        val response = client.get("$baseUrl/analytics/trend")
        checkResponse(response)
        return response.body()
    }

    // ── Helpers ──

    private suspend fun checkResponse(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val errorBody = try {
                response.body<ErrorResponse>().error
            } catch (_: Exception) {
                null
            }
            throw ApiException(
                statusCode = response.status.value,
                message = errorBody ?: "HTTP ${response.status.value}: ${response.status.description}"
            )
        }
    }
}

class ApiException(val statusCode: Int, override val message: String) : Exception(message)
