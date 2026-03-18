package ru.homebudget.finkeeper.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // ── AuthData ──

    @Test
    fun authData_deserialize() {
        val raw = """{"token":"abc123","accessToken":"abc123","refreshToken":"refresh456","user":{"id":1,"username":"test"}}"""
        val result = json.decodeFromString<AuthData>(raw)
        assertEquals("abc123", result.token)
        assertEquals("abc123", result.accessToken)
        assertEquals("refresh456", result.refreshToken)
        assertEquals(1, result.user.id)
        assertEquals("test", result.user.username)
    }

    @Test
    fun authData_serialize() {
        val data = AuthData(token = "tok", refreshToken = "ref", user = User(id = 5, username = "admin"))
        val raw = json.encodeToString(AuthData.serializer(), data)
        assert(raw.contains("\"token\":\"tok\""))
        assert(raw.contains("\"refreshToken\":\"ref\""))
        assert(raw.contains("\"username\":\"admin\""))
    }

    // ── User ──

    @Test
    fun user_deserialize() {
        val raw = """{"id":42,"username":"alice"}"""
        val result = json.decodeFromString<User>(raw)
        assertEquals(42, result.id)
        assertEquals("alice", result.username)
    }

    @Test
    fun user_deserialize_withRecoveryFields() {
        val raw =
            """{"id":7,"username":"alice","email":"alice@example.com","emailConfirmed":true,"recoverabilityStatus":"protected","canSelfRecover":true}"""
        val result = json.decodeFromString<User>(raw)
        assertEquals(7, result.id)
        assertEquals("alice@example.com", result.email)
        assertEquals(true, result.emailConfirmed)
        assertEquals("protected", result.recoverabilityStatus)
        assertEquals(true, result.canSelfRecover)
    }

    @Test
    fun passwordRecoveryRequestResponse_deserialize_withDebugToken() {
        val raw =
            """{"success":true,"message":"ok","debug":{"passwordReset":{"token":"reset-123","expiresAt":"2026-03-13T10:00:00.000Z"}}}"""
        val result = json.decodeFromString<PasswordRecoveryRequestResponse>(raw)
        assertEquals(true, result.success)
        assertEquals("ok", result.message)
        assertEquals("reset-123", result.debug?.passwordReset?.token)
        assertEquals("2026-03-13T10:00:00.000Z", result.debug?.passwordReset?.expiresAt)
    }

    @Test
    fun userEmailUpdateResponse_deserialize_withVerificationDebug() {
        val raw =
            """{"success":true,"verificationRequired":true,"user":{"id":7,"username":"alice","email":"alice@example.com","emailConfirmed":false,"recoverabilityStatus":"pending_email_verification","canSelfRecover":false},"debug":{"emailVerification":{"token":"verify-123","expiresAt":"2026-03-13T11:00:00.000Z"}}}"""
        val result = json.decodeFromString<UserEmailUpdateResponse>(raw)
        assertEquals(true, result.success)
        assertEquals(true, result.verificationRequired)
        assertEquals("alice@example.com", result.user.email)
        assertEquals(false, result.user.emailConfirmed)
        assertEquals("pending_email_verification", result.user.recoverabilityStatus)
        assertEquals("verify-123", result.debug?.emailVerification?.token)
    }

    @Test
    fun userEmailVerificationRequestResponse_deserialize_withoutDebug() {
        val raw =
            """{"success":true,"verificationRequired":true,"user":{"id":7,"username":"alice","email":"alice@example.com","emailConfirmed":false,"recoverabilityStatus":"pending_email_verification","canSelfRecover":false}}"""
        val result = json.decodeFromString<UserEmailVerificationRequestResponse>(raw)
        assertEquals(true, result.success)
        assertEquals(true, result.verificationRequired)
        assertEquals("alice@example.com", result.user.email)
        assertNull(result.debug)
    }

    // ── Category ──

    @Test
    fun category_deserialize_full() {
        val raw = """{"id":1,"user_id":10,"name":"Еда","sort_order":3,"is_active":1}"""
        val result = json.decodeFromString<Category>(raw)
        assertEquals(1, result.id)
        assertEquals(10, result.userId)
        assertEquals("Еда", result.name)
        assertEquals(3, result.sortOrder)
        assertEquals(1, result.isActive)
    }

    @Test
    fun category_deserialize_defaults() {
        val raw = """{"id":2,"name":"Транспорт"}"""
        val result = json.decodeFromString<Category>(raw)
        assertEquals(2, result.id)
        assertNull(result.userId)
        assertEquals("Транспорт", result.name)
        assertEquals(0, result.sortOrder)
        assertEquals(1, result.isActive)
    }

    @Test
    fun category_serialName_mapping() {
        val cat = Category(id = 1, userId = 5, name = "Test", sortOrder = 2, isActive = 0)
        val raw = json.encodeToString(Category.serializer(), cat)
        assert(raw.contains("\"user_id\":5"))
        assert(raw.contains("\"sort_order\":2"))
        assert(raw.contains("\"is_active\":0"))
    }

    // ── IncomeSource ──

    @Test
    fun incomeSource_deserialize() {
        val raw = """{"id":1,"name":"Зарплата","is_active":1}"""
        val result = json.decodeFromString<IncomeSource>(raw)
        assertEquals(1, result.id)
        assertEquals("Зарплата", result.name)
        assertEquals(1, result.isActive)
    }

    @Test
    fun incomeSource_inactive() {
        val raw = """{"id":2,"name":"Фриланс","is_active":0}"""
        val result = json.decodeFromString<IncomeSource>(raw)
        assertEquals(0, result.isActive)
    }

    // ── Month ──

    @Test
    fun month_deserialize() {
        val raw = """{"id":10,"user_id":1,"year":2025,"month":2}"""
        val result = json.decodeFromString<Month>(raw)
        assertEquals(10, result.id)
        assertEquals(1, result.userId)
        assertEquals(2025, result.year)
        assertEquals(2, result.month)
    }

    // ── Income ──

    @Test
    fun income_deserialize() {
        val raw = """{"id":1,"month_id":10,"source":"Зарплата","amount":100000.0,"date":"2025-02-01"}"""
        val result = json.decodeFromString<Income>(raw)
        assertEquals(1, result.id)
        assertEquals(10, result.monthId)
        assertEquals("Зарплата", result.source)
        assertEquals(100000.0, result.amount)
        assertEquals("2025-02-01", result.date)
    }

    // ── Expense ──

    @Test
    fun expense_deserialize_full() {
        val raw = """{"id":5,"month_id":10,"category_id":3,"amount":500.0,"date":"2025-02-05","comment":"Обед","category_name":"Еда"}"""
        val result = json.decodeFromString<Expense>(raw)
        assertEquals(5, result.id)
        assertEquals(10, result.monthId)
        assertEquals(3, result.categoryId)
        assertEquals(500.0, result.amount)
        assertEquals("Обед", result.comment)
        assertEquals("Еда", result.categoryName)
    }

    @Test
    fun expense_deserialize_nullOptionals() {
        val raw = """{"id":1,"month_id":1,"category_id":1,"amount":100.0,"date":"2025-01-01"}"""
        val result = json.decodeFromString<Expense>(raw)
        assertNull(result.comment)
        assertNull(result.categoryName)
    }

    // ── Budget ──

    @Test
    fun budget_deserialize() {
        val raw = """{"id":1,"month_id":10,"category_id":3,"limit_amount":15000.0}"""
        val result = json.decodeFromString<Budget>(raw)
        assertEquals(1, result.id)
        assertEquals(10, result.monthId)
        assertEquals(3, result.categoryId)
        assertEquals(15000.0, result.limitAmount)
    }

    // ── SavingsGoal ──

    @Test
    fun savingsGoal_deserialize() {
        val raw = """{"id":1,"user_id":1,"name":"Отпуск","target_amount":200000.0,"current_amount":50000.0}"""
        val result = json.decodeFromString<SavingsGoal>(raw)
        assertEquals(1, result.id)
        assertEquals("Отпуск", result.name)
        assertEquals(200000.0, result.targetAmount)
        assertEquals(50000.0, result.currentAmount)
    }

    @Test
    fun savingsGoal_defaults() {
        val raw = """{"id":2,"name":"Машина"}"""
        val result = json.decodeFromString<SavingsGoal>(raw)
        assertEquals(0.0, result.targetAmount)
        assertEquals(0.0, result.currentAmount)
        assertNull(result.userId)
    }

    // ── SavingsTransaction ──

    @Test
    fun savingsTransaction_deserialize() {
        val raw = """{"id":1,"goal_id":5,"amount":10000.0,"date":"2025-02-01","month_id":10}"""
        val result = json.decodeFromString<SavingsTransaction>(raw)
        assertEquals(1, result.id)
        assertEquals(5, result.goalId)
        assertEquals(10000.0, result.amount)
        assertEquals(10, result.monthId)
    }

    // ── MonthSummary ──

    @Test
    fun monthSummary_deserialize() {
        val raw = """{"income":100000.0,"expenses":60000.0,"savings":5000.0,"balance":35000.0}"""
        val result = json.decodeFromString<MonthSummary>(raw)
        assertEquals(100000.0, result.income)
        assertEquals(60000.0, result.expenses)
        assertEquals(5000.0, result.savings)
        assertEquals(35000.0, result.balance)
    }

    @Test
    fun monthSummary_defaults() {
        val raw = """{}"""
        val result = json.decodeFromString<MonthSummary>(raw)
        assertEquals(0.0, result.income)
        assertEquals(0.0, result.expenses)
        assertEquals(0.0, result.savings)
        assertEquals(0.0, result.balance)
    }

    // ── TrendItem ──

    @Test
    fun trendItem_deserialize() {
        val raw = """{"month":"1/2025","income":100000.0,"expense":50000.0,"savings":10000.0}"""
        val result = json.decodeFromString<TrendItem>(raw)
        assertEquals("1/2025", result.month)
        assertEquals(100000.0, result.income)
        assertEquals(50000.0, result.expense)
        assertEquals(10000.0, result.savings)
    }

    @Test
    fun trendItem_defaults() {
        val raw = """{"month":"6/2025"}"""
        val result = json.decodeFromString<TrendItem>(raw)
        assertEquals(0.0, result.income)
        assertEquals(0.0, result.expense)
        assertEquals(0.0, result.savings)
    }

    // ── Request bodies ──

    @Test
    fun loginRequest_serialize() {
        val req = LoginRequest("user", "pass")
        val raw = json.encodeToString(LoginRequest.serializer(), req)
        assert(raw.contains("\"username\":\"user\""))
        assert(raw.contains("\"password\":\"pass\""))
    }

    @Test
    fun addIncomeRequest_serialize() {
        val req = AddIncomeRequest(monthId = 10, source = "Зарплата", amount = 50000.0, date = "2025-02-01")
        val raw = json.encodeToString(AddIncomeRequest.serializer(), req)
        assert(raw.contains("\"month_id\":10"))
        assert(raw.contains("\"source\":\"Зарплата\""))
    }

    @Test
    fun addExpenseRequest_serialize() {
        val req = AddExpenseRequest(monthId = 10, categoryId = 3, amount = 1500.0, date = "2025-02-05", comment = "Обед")
        val raw = json.encodeToString(AddExpenseRequest.serializer(), req)
        assert(raw.contains("\"month_id\":10"))
        assert(raw.contains("\"category_id\":3"))
        assert(raw.contains("\"comment\":\"Обед\""))
    }

    @Test
    fun addExpenseRequest_nullComment() {
        val req = AddExpenseRequest(monthId = 1, categoryId = 1, amount = 100.0, date = "2025-01-01")
        val raw = json.encodeToString(AddExpenseRequest.serializer(), req)
        assert(raw.contains("\"comment\":null"))
    }

    @Test
    fun passwordRecoveryConfirmRequest_serialize() {
        val req = PasswordRecoveryConfirmRequest(token = "token-123", newPassword = "secret123")
        val raw = json.encodeToString(PasswordRecoveryConfirmRequest.serializer(), req)
        assert(raw.contains("\"token\":\"token-123\""))
        assert(raw.contains("\"newPassword\":\"secret123\""))
    }

    @Test
    fun emailVerificationConfirmRequest_serialize() {
        val req = EmailVerificationConfirmRequest(token = "verify-123")
        val raw = json.encodeToString(EmailVerificationConfirmRequest.serializer(), req)
        assert(raw.contains("\"token\":\"verify-123\""))
    }

    @Test
    fun updateUserEmailRequest_serialize() {
        val req = UpdateUserEmailRequest(email = "alice@example.com")
        val raw = json.encodeToString(UpdateUserEmailRequest.serializer(), req)
        assert(raw.contains("\"email\":\"alice@example.com\""))
    }

    @Test
    fun setBudgetRequest_serialize() {
        val req = SetBudgetRequest(monthId = 10, categoryId = 3, limitAmount = 20000.0)
        val raw = json.encodeToString(SetBudgetRequest.serializer(), req)
        assert(raw.contains("\"month_id\":10"))
        assert(raw.contains("\"limit_amount\":20000.0"))
    }

    @Test
    fun ensureMonthRequest_serialize() {
        val req = EnsureMonthRequest(year = 2025, month = 2)
        val raw = json.encodeToString(EnsureMonthRequest.serializer(), req)
        assert(raw.contains("\"year\":2025"))
        assert(raw.contains("\"month\":2"))
    }

    @Test
    fun createSavingsGoalRequest_serialize() {
        val req = CreateSavingsGoalRequest(name = "Отпуск", targetAmount = 200000.0)
        val raw = json.encodeToString(CreateSavingsGoalRequest.serializer(), req)
        assert(raw.contains("\"name\":\"Отпуск\""))
        assert(raw.contains("\"target_amount\":200000.0"))
    }

    @Test
    fun updateSavingsGoalRequest_nullFields() {
        val req = UpdateSavingsGoalRequest(name = "Новое имя")
        val raw = json.encodeToString(UpdateSavingsGoalRequest.serializer(), req)
        assert(raw.contains("\"name\":\"Новое имя\""))
    }

    @Test
    fun addSavingsTransactionRequest_serialize() {
        val req = AddSavingsTransactionRequest(goalId = 1, amount = 5000.0, date = "2025-02-01", monthId = 10)
        val raw = json.encodeToString(AddSavingsTransactionRequest.serializer(), req)
        assert(raw.contains("\"goal_id\":1"))
        assert(raw.contains("\"month_id\":10"))
    }

    @Test
    fun reorderCategoriesRequest_serialize() {
        val req = ReorderCategoriesRequest(ids = listOf(3, 1, 2))
        val raw = json.encodeToString(ReorderCategoriesRequest.serializer(), req)
        assert(raw.contains("\"ids\":[3,1,2]"))
    }

    @Test
    fun updateCategoryRequest_serialize() {
        val req = UpdateCategoryRequest(name = "Новая", isActive = 0)
        val raw = json.encodeToString(UpdateCategoryRequest.serializer(), req)
        assert(raw.contains("\"name\":\"Новая\""))
        assert(raw.contains("\"is_active\":0"))
    }

    @Test
    fun updateIncomeSourceRequest_serialize() {
        val req = UpdateIncomeSourceRequest(name = "Фриланс", isActive = 1)
        val raw = json.encodeToString(UpdateIncomeSourceRequest.serializer(), req)
        assert(raw.contains("\"name\":\"Фриланс\""))
        assert(raw.contains("\"is_active\":1"))
    }

    @Test
    fun errorResponse_deserialize() {
        val raw = """{"error":"Not found"}"""
        val result = json.decodeFromString<ErrorResponse>(raw)
        assertEquals("Not found", result.error)
    }

    @Test
    fun errorResponse_nullError() {
        val raw = """{}"""
        val result = json.decodeFromString<ErrorResponse>(raw)
        assertNull(result.error)
    }

    // ── Тест на неизвестные поля (ignoreUnknownKeys) ──

    @Test
    fun user_ignoresUnknownFields() {
        val raw = """{"id":1,"username":"test","extra_field":"ignored"}"""
        val result = json.decodeFromString<User>(raw)
        assertEquals(1, result.id)
        assertEquals("test", result.username)
    }
}
