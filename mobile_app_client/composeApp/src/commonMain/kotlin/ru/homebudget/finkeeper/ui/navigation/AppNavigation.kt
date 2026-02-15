package ru.homebudget.finkeeper.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.components.IconCalendar
import ru.homebudget.finkeeper.ui.components.IconDashboard
import ru.homebudget.finkeeper.ui.components.IconLogOut
import ru.homebudget.finkeeper.ui.components.IconPiggyBank
import ru.homebudget.finkeeper.ui.components.IconReceipt
import ru.homebudget.finkeeper.ui.components.IconSettings
import ru.homebudget.finkeeper.ui.components.PullToRefreshWrapper
import ru.homebudget.finkeeper.ui.screens.CategoriesScreen
import ru.homebudget.finkeeper.ui.screens.DashboardScreen
import ru.homebudget.finkeeper.ui.screens.MonthViewScreen
import ru.homebudget.finkeeper.ui.screens.SavingsScreen
import ru.homebudget.finkeeper.ui.screens.SettingsScreen
import ru.homebudget.finkeeper.ui.theme.AppSemanticColors
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.AuthViewModel
import ru.homebudget.finkeeper.ui.viewmodel.CategoriesViewModel
import ru.homebudget.finkeeper.ui.viewmodel.DashboardViewModel
import ru.homebudget.finkeeper.ui.viewmodel.MonthViewModel
import ru.homebudget.finkeeper.ui.viewmodel.SavingsViewModel
import ru.homebudget.finkeeper.ui.viewmodel.SettingsViewModel
import ru.homebudget.finkeeper.util.AppLogoIcon
import ru.homebudget.finkeeper.util.DraggableArea
import ru.homebudget.finkeeper.util.LocalWindowControls
import ru.homebudget.finkeeper.util.isDesktop
import ru.homebudget.finkeeper.util.formatCurrency

enum class Screen(
    val title: String,
) {
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
    onThemeModeChange: (String) -> Unit,
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val desktopPadding = if (isDesktop) 128.dp else 0.dp

    val screenContent: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(horizontal = desktopPadding)) {
            when (currentScreen) {
                Screen.Dashboard ->
                    PullToRefreshWrapper(
                        isRefreshing = dashboardState.isRefreshing,
                        onRefresh = { dashboardViewModel.refreshData() },
                    ) {
                        DashboardScreen(
                            state = dashboardState,
                            onRefresh = { dashboardViewModel.loadData() },
                        )
                    }

                Screen.MonthView ->
                    PullToRefreshWrapper(
                        isRefreshing = monthState.isRefreshing,
                        onRefresh = { monthViewModel.refreshData() },
                    ) {
                        MonthViewScreen(
                            state = monthState,
                            onPrevMonth = { monthViewModel.prevMonth() },
                            onNextMonth = { monthViewModel.nextMonth() },
                            onSetActiveTab = { monthViewModel.setActiveTab(it) },
                            onAddIncome = { source, amount -> monthViewModel.addIncome(source, amount) },
                            onAddIncomeWithSourceCheck = { source, amount -> monthViewModel.addIncomeWithSourceCheck(source, amount) },
                            onAddExpense = { catId, amount, comment -> monthViewModel.addExpense(catId, amount, comment) },
                            onUpdateIncome = { id, amount -> monthViewModel.updateIncome(id, amount) },
                            onUpdateExpense = { id, amount -> monthViewModel.updateExpense(id, amount) },
                            onDeleteIncome = { monthViewModel.deleteIncome(it) },
                            onDeleteExpense = { monthViewModel.deleteExpense(it) },
                            onSetBudget = { catId, limit -> monthViewModel.setBudget(catId, limit) },
                            onAddIncomeSource = { monthViewModel.addIncomeSource(it) },
                            onConfirmAddIncomeSource = { monthViewModel.confirmAddIncomeSource() },
                            onCancelAddIncomeSource = { monthViewModel.cancelAddIncomeSource() },
                            onReorderExpenseGroups = { monthViewModel.reorderExpenseGroups(it) },
                            onRefresh = { monthViewModel.loadData() },
                        )
                    }

                Screen.Categories ->
                    PullToRefreshWrapper(
                        isRefreshing = categoriesState.isRefreshing,
                        onRefresh = { categoriesViewModel.refreshData() },
                    ) {
                        CategoriesScreen(
                            state = categoriesState,
                            onSetActiveTab = { categoriesViewModel.setActiveTab(it) },
                            onAddCategory = { categoriesViewModel.addCategory(it) },
                            onUpdateCategory = { id, name -> categoriesViewModel.updateCategory(id, name) },
                            onDeactivateCategory = { categoriesViewModel.deactivateCategory(it) },
                            onAddIncomeSource = { categoriesViewModel.addIncomeSource(it) },
                            onUpdateIncomeSource = { id, name -> categoriesViewModel.updateIncomeSource(id, name) },
                            onDeactivateIncomeSource = { categoriesViewModel.deactivateIncomeSource(it) },
                            onRefresh = { categoriesViewModel.loadData() },
                            onToggleReorderMode = { categoriesViewModel.toggleReorderMode() },
                            onUpdateCategoriesOrder = { categoriesViewModel.updateCategoriesOrder(it) },
                            onReorderCategories = { categoriesViewModel.reorderCategories(it) },
                        )
                    }

                Screen.Savings ->
                    PullToRefreshWrapper(
                        isRefreshing = savingsState.isRefreshing,
                        onRefresh = { savingsViewModel.refreshData() },
                    ) {
                        SavingsScreen(
                            state = savingsState,
                            onCreateGoal = { name, target -> savingsViewModel.createGoal(name, target) },
                            onUpdateGoal = { id, name, target, current -> savingsViewModel.updateGoal(id, name, target, current) },
                            onDeleteGoal = { savingsViewModel.deleteGoal(it) },
                            onAddTransaction = { goalId, amount -> savingsViewModel.addTransaction(goalId, amount) },
                            onRefresh = { savingsViewModel.loadData() },
                        )
                    }

                Screen.Settings ->
                    SettingsScreen(
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
                        onThemeModeChange = onThemeModeChange,
                    )
            }
        }
    }

    if (isDesktop) {
        val windowControls = LocalWindowControls.current
        // Desktop layout: sidebar + content (undecorated window, draggable)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            DraggableArea {
                DesktopSidebar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                    onLogout = { showLogoutConfirm = true },
                    isDark = isDark,
                    semantic = semantic,
                    username = authState.user?.username ?: "",
                    totalAssets = dashboardState.totalAssets,
                    available = dashboardState.available,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                DraggableArea {
                    Box(modifier = Modifier.fillMaxSize()) {
                        screenContent()
                    }
                }
                // Custom window control buttons (top-right corner)
                DesktopWindowControls(
                    onClose = windowControls.onClose,
                    onMinimize = windowControls.onMinimize,
                    onToggleFullscreen = windowControls.onToggleFullscreen,
                    isDark = isDark,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    } else {
        // Mobile layout: bottom bar + content
        Scaffold(
            bottomBar = {
                GradientBottomBar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                    onLogout = { showLogoutConfirm = true },
                    isDark = isDark,
                    semantic = semantic,
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                screenContent()
            }
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Выход",
            message = "Вы уверены, что хотите выйти?",
            onConfirm = {
                authViewModel.logout()
                showLogoutConfirm = false
            },
            onDismiss = { showLogoutConfirm = false },
            isDestructive = true,
        )
    }
}

@Composable
private fun GradientBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean,
    semantic: AppSemanticColors,
) {
    val gradientBrush =
        if (isDark) {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        semantic.navBarColor,
                        semantic.navBarColor,
                    ),
            )
        } else {
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(0xFF064E3B), // emerald-900
                        Color(0xFF047857), // emerald-700
                        Color(0xFF134E4A), // teal-900
                    ),
            )
        }

    val activeColor = if (isDark) semantic.navBarContent else Color.White
    val inactiveColor = if (isDark) semantic.navBarContentInactive else Color(0xBBD1FAE5)

    val navigationBarInsets = WindowInsets.navigationBars
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .windowInsetsPadding(navigationBarInsets),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
                    .padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                    onClick = { onScreenSelected(screen) },
                )
            }
            // Logout button
            NavBarItem(
                icon = { IconLogOut(inactiveColor) },
                label = "Выход",
                isSelected = false,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = onLogout,
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
    onClick: () -> Unit,
) {
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isSelected) {
                        Modifier.background(activeColor.copy(alpha = 0.15f))
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(min = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

// ── Desktop Sidebar ──

@Composable
private fun DesktopSidebar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onLogout: () -> Unit,
    isDark: Boolean,
    semantic: AppSemanticColors,
    username: String,
    totalAssets: Double,
    available: Double,
) {
    val gradientBrush =
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(semantic.navBarColor, semantic.navBarColor),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF064E3B), // emerald-900
                    Color(0xFF047857), // emerald-700
                    Color(0xFF0D9488), // teal-600
                ),
            )
        }

    val activeColor = if (isDark) semantic.navBarContent else Color.White
    val inactiveColor = if (isDark) semantic.navBarContentInactive else Color(0xBBD1FAE5)

    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(gradientBrush)
            .padding(vertical = 16.dp),
    ) {
        // Logo / App name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLogoIcon()
            Text(
                text = "FinKeeper",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = activeColor,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigation items (without Settings)
        val navScreens = listOf(
            Screen.Dashboard,
            Screen.MonthView,
            Screen.Categories,
            Screen.Savings,
        )
        navScreens.forEach { screen ->
            SidebarNavItem(
                icon = { color ->
                    when (screen) {
                        Screen.Dashboard -> IconDashboard(color)
                        Screen.MonthView -> IconCalendar(color)
                        Screen.Categories -> IconReceipt(color)
                        Screen.Savings -> IconPiggyBank(color)
                        else -> {}
                    }
                },
                label = screen.title,
                isSelected = currentScreen == screen,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onScreenSelected(screen) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom section: username, settings, logout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            // Username + settings + logout row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = activeColor,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onScreenSelected(Screen.Settings) }
                            .padding(6.dp),
                    ) {
                        IconSettings(inactiveColor, size = 20.dp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onLogout)
                            .padding(6.dp),
                    ) {
                        IconLogOut(inactiveColor, size = 20.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Financial summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(activeColor.copy(alpha = 0.1f))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Всего активов",
                    style = MaterialTheme.typography.labelSmall,
                    color = inactiveColor,
                )
                Text(
                    text = formatCurrency(totalAssets),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = activeColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Доступно",
                    style = MaterialTheme.typography.labelSmall,
                    color = inactiveColor,
                )
                Text(
                    text = formatCurrency(available),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (available >= 0) activeColor else Color(0xFFEF4444),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Version
            Text(
                text = "Домашняя бухгалтерия ${ru.homebudget.finkeeper.BuildConfig.APP_VERSION}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = inactiveColor.copy(alpha = 0.6f),
            )
        }
    }
}

// ── Desktop Window Controls (close, minimize, fullscreen) ──

@Composable
private fun DesktopWindowControls(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(top = 10.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Minimize (yellow)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFBBF24))
                .clickable(onClick = onMinimize)
                .widthIn(min = 16.dp)
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "−",
                color = Color.Black,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (1).dp),
            )
        }
        // Fullscreen (green)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF34D399))
                .clickable(onClick = onToggleFullscreen)
                .widthIn(min = 16.dp)
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "☐",
                color = Color.Black,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (1).dp),
            )
        }
        // Close (red)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFEF4444))
                .clickable(onClick = onClose)
                .widthIn(min = 16.dp)
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                color = Color.Black,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (0).dp),
            )
        }
    }
}

@Composable
private fun SidebarNavItem(
    icon: @Composable (Color) -> Unit,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
) {
    val color = if (isSelected) activeColor else inactiveColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) {
                    Modifier.background(activeColor.copy(alpha = 0.15f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon(color)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = color,
        )
    }
}
