package ru.homebudget.finkeeper.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.homebudget.finkeeper.ui.components.*
import ru.homebudget.finkeeper.ui.screens.*
import ru.homebudget.finkeeper.ui.theme.*
import ru.homebudget.finkeeper.ui.viewmodel.*
import kotlin.collections.listOf

enum class Screen(val title: String) {
    Dashboard("Обзор"),
    MonthView("Месяц"),
    Categories("Категории"),
    Savings("Копилки"),
    Settings("Настр."),
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel,
    monthViewModel: MonthViewModel,
    categoriesViewModel: CategoriesViewModel,
    savingsViewModel: SavingsViewModel,
    settingsViewModel: SettingsViewModel,
    currentThemeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val authState by authViewModel.state.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val monthState by monthViewModel.state.collectAsState()
    val categoriesState by categoriesViewModel.state.collectAsState()
    val savingsState by savingsViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    val semantic = AppTheme.semanticColors
    val isDark = MaterialTheme.colorScheme.background == BackgroundDark

    Scaffold(
        bottomBar = {
            GradientBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it },
                onLogout = { showLogoutConfirm = true },
                isDark = isDark,
                semantic = semantic
            )
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
                    onClearStatus = { settingsViewModel.clearStatus() },
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Выход",
            message = "Вы уверены, что хотите выйти?",
            onConfirm = { authViewModel.logout(); showLogoutConfirm = false },
            onDismiss = { showLogoutConfirm = false },
            isDestructive = true
        )
    }
}

@Composable
private fun GradientBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean,
    semantic: AppSemanticColors
) {
    val gradientBrush = if (isDark) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF0D1520),
                Color(0xFF111D2B),
                Color(0xFF0D1520)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF064E3B), // emerald-900
                Color(0xFF047857), // emerald-700
                Color(0xFF134E4A)  // teal-900
            )
        )
    }

    val activeColor = if (isDark) Color(0xFF00E676) else Color.White
    val inactiveColor = if (isDark) Color(0xFF4A6070) else Color(0xBBD1FAE5)

    val navigationBarInsets = WindowInsets.navigationBars
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradientBrush)
            .windowInsetsPadding(navigationBarInsets)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Screen.entries.forEach { screen ->
                val isSelected = currentScreen == screen
                val color = if (isSelected) activeColor else inactiveColor
                NavBarItem(
                    icon = {
                        when (screen) {
                            Screen.Dashboard -> IconDashboard(color)
                            Screen.MonthView -> IconCalendar(color)
                            Screen.Categories -> IconReceipt(color)
                            Screen.Savings -> IconPiggyBank(color)
                            Screen.Settings -> IconSettings(color)
                        }
                    },
                    label = screen.title,
                    isSelected = isSelected,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    onClick = { onScreenSelected(screen) }
                )
            }
            // Logout button
            NavBarItem(
                icon = { IconLogOut(inactiveColor) },
                label = "Выход",
                isSelected = false,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun NavBarItem(
    icon: @Composable () -> Unit,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.background(activeColor.copy(alpha = 0.15f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .widthIn(min = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
