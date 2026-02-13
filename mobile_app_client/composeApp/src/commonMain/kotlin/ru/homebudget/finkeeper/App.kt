package ru.homebudget.finkeeper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.ui.components.LoadingScreen
import ru.homebudget.finkeeper.ui.navigation.AppNavigation
import ru.homebudget.finkeeper.ui.screens.LoginScreen
import ru.homebudget.finkeeper.ui.screens.SplashScreen
import ru.homebudget.finkeeper.ui.theme.FinKeeperTheme
import ru.homebudget.finkeeper.ui.theme.ThemePalette
import ru.homebudget.finkeeper.ui.viewmodel.*

@Composable
fun App() {
    val tokenStorage = koinInject<TokenStorage>()
    var themeMode by remember { mutableStateOf(tokenStorage.themeMode) }

    val palette = when (themeMode) {
        "light" -> ThemePalette.Light
        "dark" -> ThemePalette.Cyberpunk
        "night" -> ThemePalette.Dark
        "dark_night" -> ThemePalette.DarkNight
        else -> if (isSystemInDarkTheme()) ThemePalette.Dark else ThemePalette.Light
    }

    FinKeeperTheme(palette = palette) {
        val authViewModel = koinInject<AuthViewModel>()
        val authState by authViewModel.state.collectAsState()

        var isSplashTimeFinished by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(2000)
            isSplashTimeFinished = true
        }

        when {
            authState.isLoading || !isSplashTimeFinished -> SplashScreen()
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
                    settingsViewModel = settingsViewModel,
                    currentThemeMode = themeMode,
                    onThemeModeChange = { mode ->
                        tokenStorage.themeMode = mode
                        themeMode = mode
                    }
                )
            }
        }
    }
}