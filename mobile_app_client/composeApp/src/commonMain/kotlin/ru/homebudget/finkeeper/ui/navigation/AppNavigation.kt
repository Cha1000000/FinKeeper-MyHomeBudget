package ru.homebudget.finkeeper.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ru.homebudget.finkeeper.ui.screens.*
import ru.homebudget.finkeeper.ui.viewmodel.*

enum class Screen(val title: String, val icon: String) {
    Dashboard("Обзор", "📊"),
    MonthView("Месяц", "📅"),
    Categories("Категории", "📁"),
    Savings("Копилки", "🏦"),
    Settings("Настройки", "⚙")
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    monthViewModel: MonthViewModel,
    categoriesViewModel: CategoriesViewModel,
    savingsViewModel: SavingsViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    val authState by authViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val monthState by monthViewModel.state.collectAsState()
    val categoriesState by categoriesViewModel.state.collectAsState()
    val savingsState by savingsViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Text(screen.icon) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    state = dashboardState,
                    onRefresh = { dashboardViewModel.loadData() }
                )

                Screen.MonthView -> MonthViewScreen(
                    state = monthState,
                    onPrevMonth = { monthViewModel.prevMonth() },
                    onNextMonth = { monthViewModel.nextMonth() },
                    onSetActiveTab = { monthViewModel.setActiveTab(it) },
                    onAddIncome = { source, amount -> monthViewModel.addIncome(source, amount) },
                    onAddExpense = { catId, amount, comment -> monthViewModel.addExpense(catId, amount, comment) },
                    onUpdateIncome = { id, amount -> monthViewModel.updateIncome(id, amount) },
                    onUpdateExpense = { id, amount -> monthViewModel.updateExpense(id, amount) },
                    onDeleteIncome = { monthViewModel.deleteIncome(it) },
                    onDeleteExpense = { monthViewModel.deleteExpense(it) },
                    onSetBudget = { catId, limit -> monthViewModel.setBudget(catId, limit) },
                    onAddIncomeSource = { monthViewModel.addIncomeSource(it) },
                    onRefresh = { monthViewModel.loadData() }
                )

                Screen.Categories -> CategoriesScreen(
                    state = categoriesState,
                    onSetActiveTab = { categoriesViewModel.setActiveTab(it) },
                    onAddCategory = { categoriesViewModel.addCategory(it) },
                    onUpdateCategory = { id, name -> categoriesViewModel.updateCategory(id, name) },
                    onDeactivateCategory = { categoriesViewModel.deactivateCategory(it) },
                    onAddIncomeSource = { categoriesViewModel.addIncomeSource(it) },
                    onUpdateIncomeSource = { id, name -> categoriesViewModel.updateIncomeSource(id, name) },
                    onDeactivateIncomeSource = { categoriesViewModel.deactivateIncomeSource(it) },
                    onRefresh = { categoriesViewModel.loadData() }
                )

                Screen.Savings -> SavingsScreen(
                    state = savingsState,
                    onCreateGoal = { name, target -> savingsViewModel.createGoal(name, target) },
                    onUpdateGoal = { id, name, target, current -> savingsViewModel.updateGoal(id, name, target, current) },
                    onDeleteGoal = { savingsViewModel.deleteGoal(it) },
                    onAddTransaction = { goalId, amount -> savingsViewModel.addTransaction(goalId, amount) },
                    onRefresh = { savingsViewModel.loadData() }
                )

                Screen.Settings -> SettingsScreen(
                    state = settingsState,
                    username = authState.user?.username ?: "",
                    onUpdateUsername = { name, callback ->
                        settingsViewModel.updateUsername(name, callback)
                    },
                    onUpdatePassword = { settingsViewModel.updatePassword(it) },
                    onCreateBackup = { settingsViewModel.createBackup() },
                    onRestoreBackup = { callback -> settingsViewModel.restoreBackup(callback) },
                    onLogout = { authViewModel.logout() },
                    onClearStatus = { settingsViewModel.clearStatus() }
                )
            }
        }
    }
}
