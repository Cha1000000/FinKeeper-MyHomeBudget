package ru.homebudget.finkeeper.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {
    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    // ── AuthState ──

    @Test
    fun authState_defaultValues() {
        val state = AuthState()
        assertNull(state.user)
        assertTrue(state.isLoading)
        assertFalse(state.isAuthenticated)
        assertNull(state.error)
    }

    @Test
    fun authState_copyWithUser() {
        val state = AuthState()
        val updated = state.copy(
            user = ru.homebudget.finkeeper.data.model.User(id = 1, username = "test"),
            isLoading = false,
            isAuthenticated = true
        )
        assertEquals("test", updated.user?.username)
        assertFalse(updated.isLoading)
        assertTrue(updated.isAuthenticated)
    }

    @Test
    fun authState_copyWithError() {
        val state = AuthState(isLoading = false)
        val updated = state.copy(error = "Ошибка сети")
        assertEquals("Ошибка сети", updated.error)
        assertFalse(updated.isLoading)
    }

    @Test
    fun authState_copyWithRecoveryInfo() {
        val debugToken =
            ru.homebudget.finkeeper.data.model.DebugTokenPreview(
                token = "reset-123",
                expiresAt = "2026-03-13T10:00:00.000Z",
            )
        val updated =
            AuthState(isLoading = false).copy(
                infoMessage = "Письмо подготовлено",
                recoveryDebugToken = debugToken,
            )
        assertEquals("Письмо подготовлено", updated.infoMessage)
        assertEquals("reset-123", updated.recoveryDebugToken?.token)
    }

    // ── DashboardState ──

    @Test
    fun dashboardState_defaultValues() {
        val state = DashboardState()
        assertTrue(state.isLoading)
        assertEquals(0.0, state.totalIncome)
        assertEquals(0.0, state.totalExpense)
        assertEquals(0.0, state.totalSavings)
        assertEquals(0.0, state.savingsPercent)
        assertEquals(0.0, state.available)
        // Без ответа сервера «Всего активов» неизвестно, а не ноль
        assertNull(state.totalAssets)
        assertNull(state.availableWithoutSavings)
        assertTrue(state.trendData.isEmpty())
        assertTrue(state.expenseBreakdown.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun dashboardState_withData() {
        val breakdown = listOf(
            ExpenseCategoryBreakdown("Еда", 30000.0, 60.0),
            ExpenseCategoryBreakdown("Транспорт", 20000.0, 40.0)
        )
        val state = DashboardState(
            isLoading = false,
            totalIncome = 100000.0,
            totalExpense = 50000.0,
            totalSavings = 10000.0,
            savingsPercent = 10.0,
            available = 50000.0,
            totalAssets = 60000.0,
            expenseBreakdown = breakdown
        )
        assertEquals(100000.0, state.totalIncome)
        assertEquals(2, state.expenseBreakdown.size)
        assertEquals("Еда", state.expenseBreakdown[0].name)
        assertEquals(60.0, state.expenseBreakdown[0].percentage)
    }

    // ── ExpenseCategoryBreakdown ──

    @Test
    fun expenseCategoryBreakdown_creation() {
        val b = ExpenseCategoryBreakdown("Еда", 15000.0, 45.5)
        assertEquals("Еда", b.name)
        assertEquals(15000.0, b.amount)
        assertEquals(45.5, b.percentage)
    }

    // ── MonthViewState ──

    @Test
    fun monthViewState_defaultValues() {
        val state = MonthViewState()
        assertTrue(state.isLoading)
        assertEquals(0, state.year)
        assertEquals(0, state.month)
        assertNull(state.monthData)
        assertTrue(state.incomes.isEmpty())
        assertTrue(state.expenses.isEmpty())
        assertTrue(state.categories.isEmpty())
        assertTrue(state.incomeSources.isEmpty())
        assertTrue(state.budgets.isEmpty())
        assertTrue(state.groupedExpenses.isEmpty())
        assertEquals(0.0, state.totalIncome)
        assertEquals(0.0, state.totalExpense)
        assertEquals(0.0, state.totalLimit)
        assertEquals(0, state.activeTab)
        assertNull(state.error)
    }

    // ── GroupedExpense ──

    @Test
    fun groupedExpense_creation() {
        val items = listOf(
            ru.homebudget.finkeeper.data.model.Expense(
                id = 1, monthId = 10, categoryId = 3,
                amount = 500.0, date = "2025-02-01", comment = "Обед", categoryName = "Еда"
            ),
            ru.homebudget.finkeeper.data.model.Expense(
                id = 2, monthId = 10, categoryId = 3,
                amount = 300.0, date = "2025-02-02", comment = null, categoryName = "Еда"
            )
        )
        val group = GroupedExpense(
            categoryId = 3,
            categoryName = "Еда",
            items = items,
            total = 800.0,
            limit = 5000.0,
            isOverLimit = false
        )
        assertEquals(3, group.categoryId)
        assertEquals("Еда", group.categoryName)
        assertEquals(2, group.items.size)
        assertEquals(800.0, group.total)
        assertEquals(5000.0, group.limit)
        assertFalse(group.isOverLimit)
    }

    @Test
    fun groupedExpense_overLimit() {
        val group = GroupedExpense(
            categoryId = 1,
            categoryName = "Развлечения",
            items = emptyList(),
            total = 10000.0,
            limit = 5000.0,
            isOverLimit = true
        )
        assertTrue(group.isOverLimit)
    }

    // ── CategoriesState ──

    @Test
    fun categoriesState_defaultValues() {
        val state = CategoriesState()
        assertTrue(state.isLoading)
        assertTrue(state.categories.isEmpty())
        assertTrue(state.incomeSources.isEmpty())
        assertEquals(0, state.activeTab)
        assertNull(state.error)
    }

    @Test
    fun categoriesState_withData() {
        val state = CategoriesState(
            isLoading = false,
            categories = listOf(
                ru.homebudget.finkeeper.data.model.Category(id = 1, name = "Еда"),
                ru.homebudget.finkeeper.data.model.Category(id = 2, name = "Транспорт")
            ),
            incomeSources = listOf(
                ru.homebudget.finkeeper.data.model.IncomeSource(id = 1, name = "Зарплата")
            ),
            activeTab = 1
        )
        assertEquals(2, state.categories.size)
        assertEquals(1, state.incomeSources.size)
        assertEquals(1, state.activeTab)
    }

    // ── SavingsState ──

    @Test
    fun savingsState_defaultValues() {
        val state = SavingsState()
        assertTrue(state.isLoading)
        assertTrue(state.goals.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun savingsState_withGoals() {
        val goals = listOf(
            ru.homebudget.finkeeper.data.model.SavingsGoal(
                id = 1, name = "Отпуск", targetAmount = 200000.0, currentAmount = 50000.0
            ),
            ru.homebudget.finkeeper.data.model.SavingsGoal(
                id = 2, name = "Машина", targetAmount = 1000000.0, currentAmount = 0.0
            )
        )
        val state = SavingsState(isLoading = false, goals = goals)
        assertEquals(2, state.goals.size)
        assertEquals("Отпуск", state.goals[0].name)
        assertEquals(50000.0, state.goals[0].currentAmount)
    }

    // ── SettingsState ──

    @Test
    fun settingsState_defaultValues() {
        val state = SettingsState()
        assertFalse(state.isLoading)
        assertNull(state.statusMessage)
        assertFalse(state.statusIsError)
        assertNull(state.error)
        assertTrue(state.isOnline)
        assertFalse(state.isSyncing)
        assertEquals(0L, state.pendingSyncCount)
        assertNull(state.emailDebugToken)
    }

    @Test
    fun settingsState_successMessage() {
        val state = SettingsState(
            statusMessage = "Пароль изменён",
            statusIsError = false
        )
        assertEquals("Пароль изменён", state.statusMessage)
        assertFalse(state.statusIsError)
    }

    @Test
    fun settingsState_errorMessage() {
        val state = SettingsState(
            statusMessage = "Ошибка",
            statusIsError = true
        )
        assertTrue(state.statusIsError)
    }

    @Test
    fun settingsState_copyWithEmailDebugToken() {
        val debugToken =
            ru.homebudget.finkeeper.data.model.DebugTokenPreview(
                token = "verify-123",
                expiresAt = "2026-03-13T11:00:00.000Z",
            )
        val state =
            SettingsState().copy(
                statusMessage = "Email сохранён",
                emailDebugToken = debugToken,
            )
        assertEquals("Email сохранён", state.statusMessage)
        assertEquals("verify-123", state.emailDebugToken?.token)
    }

    // ── AuthViewModel Validation ──

    @Test
    fun authViewModel_login_emptyCredentials_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.login("", "")

        assertEquals("Введите имя пользователя и пароль", viewModel.state.value.error)
    }

    @Test
    fun authViewModel_register_emptyCredentials_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.register(" ", "")

        assertEquals("Введите имя пользователя и пароль", viewModel.state.value.error)
    }

    @Test
    fun authViewModel_login_shortPassword_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.login("user", "12345")

        assertEquals("Пароль должен быть не короче 6 символов", viewModel.state.value.error)
    }

    @Test
    fun authViewModel_register_shortPassword_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.register("user", "12345")

        assertEquals("Пароль должен быть не короче 6 символов", viewModel.state.value.error)
    }

    @Test
    fun authViewModel_requestPasswordRecovery_emptyEmail_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.requestPasswordRecovery("   ")

        assertEquals("Введите email для восстановления доступа", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.infoMessage)
        assertNull(viewModel.state.value.recoveryDebugToken)
    }

    @Test
    fun authViewModel_confirmPasswordRecovery_emptyToken_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.confirmPasswordRecovery("   ", "secret123")

        assertEquals("Введите токен восстановления", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.infoMessage)
    }

    @Test
    fun authViewModel_confirmPasswordRecovery_shortPassword_showsError() {
        val viewModel = createAuthViewModel()

        viewModel.confirmPasswordRecovery("token-123", "123")

        assertEquals("Пароль должен быть не короче 6 символов", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.infoMessage)
    }

    @Test
    fun authViewModel_login_validData_passesValidation() {
        val viewModel = createAuthViewModel()

        // Пустые данные не проходят валидацию
        viewModel.login("", "")
        assertEquals("Введите имя пользователя и пароль", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading) // Запрос не начался

        // Короткий пароль не проходит валидацию
        viewModel.login("user", "123")
        assertEquals("Пароль должен быть не короче 6 символов", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading) // Запрос не начался
    }

    @Test
    fun authViewModel_register_validData_passesValidation() {
        val viewModel = createAuthViewModel()

        // Пустые данные не проходят валидацию
        viewModel.register("", "")
        assertEquals("Введите имя пользователя и пароль", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading) // Запрос не начался

        // Короткий пароль не проходит валидацию
        viewModel.register("user", "123")
        assertEquals("Пароль должен быть не короче 6 символов", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading) // Запрос не начался
    }

    private fun createAuthViewModel(): AuthViewModel {
        val tokenStorage = ru.homebudget.finkeeper.data.remote.TokenStorage(
            com.russhwolf.settings.MapSettings(),
            object : ru.homebudget.finkeeper.data.remote.SecureTokenStorage {
                override var accessToken: String? = null
                override var refreshToken: String? = null

                override fun clear() {
                    accessToken = null
                    refreshToken = null
                }
            }
        ).apply {
            clear()
        }
        val apiClient = ru.homebudget.finkeeper.data.remote.ApiClient(tokenStorage)
        val socialAuthLauncher =
            object : ru.homebudget.finkeeper.data.remote.SocialAuthLauncher {
                override val clientType: String = "test"

                override fun open(url: String): Boolean = false
            }
        return AuthViewModel(apiClient, tokenStorage, socialAuthLauncher)
    }
}
