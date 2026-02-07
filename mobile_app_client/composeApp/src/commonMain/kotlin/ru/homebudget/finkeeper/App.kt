package ru.homebudget.finkeeper

import androidx.compose.runtime.*
import org.koin.compose.koinInject
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.navigation.AppNavigation
import ru.homebudget.finkeeper.ui.screens.LoginScreen
import ru.homebudget.finkeeper.ui.theme.FinKeeperTheme
import ru.homebudget.finkeeper.ui.viewmodel.*

@Composable
fun App() {
    FinKeeperTheme {
        val authViewModel = koinInject<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()

        when {
            authState.isLoading -> LoadingScreen()
            authState.user == null -> LoginScreen(
                state = authState,
                onLogin = { u, p -> authViewModel.login(u, p) },
                onRegister = { u, p -> authViewModel.register(u, p) },
                onClearError = { authViewModel.clearError() },
                onServerUrlChange = { authViewModel.updateServerUrl(it) },
                currentServerUrl = authViewModel.currentServerUrl
            )
            else -> {
                val dashboardViewModel = koinInject<DashboardViewModel>()
                val monthViewModel = koinInject<MonthViewModel>()
                val categoriesViewModel = koinInject<CategoriesViewModel>()
                val savingsViewModel = koinInject<SavingsViewModel>()
                val settingsViewModel = koinInject<SettingsViewModel>()

                AppNavigation(
                    authViewModel = authViewModel,
                    dashboardViewModel = dashboardViewModel,
                    monthViewModel = monthViewModel,
                    categoriesViewModel = categoriesViewModel,
                    savingsViewModel = savingsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}