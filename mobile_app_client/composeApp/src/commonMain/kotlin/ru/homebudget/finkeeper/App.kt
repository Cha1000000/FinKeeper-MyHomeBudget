package ru.homebudget.finkeeper

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.homebudget.finkeeper.util.DraggableArea
import ru.homebudget.finkeeper.util.LocalWindowControls
import ru.homebudget.finkeeper.util.isDesktop
import ru.homebudget.finkeeper.ui.navigation.DesktopWindowControls
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import ru.homebudget.finkeeper.data.remote.TokenStorage
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

        val content: @Composable () -> Unit = {
            when {
                authState.isLoading || !isSplashTimeFinished -> SplashScreen(version = BuildConfig.APP_VERSION)
                authState.user == null -> LoginScreen(
                    state = authState,
                    onLogin = { u, p -> authViewModel.login(u, p) },
                    onRegister = { u, p -> authViewModel.register(u, p) },
                    onLoginWithSocial = { provider -> authViewModel.loginWithSocial(provider) },
                    onRequestPasswordRecovery = { email -> authViewModel.requestPasswordRecovery(email) },
                    onConfirmPasswordRecovery = { token, newPassword, onSuccess ->
                        authViewModel.confirmPasswordRecovery(token, newPassword, onSuccess)
                    },
                    onClearError = { authViewModel.clearError() },
                    onServerUrlChange = { authViewModel.updateServerUrl(it) },
                    currentServerUrl = authViewModel.currentServerUrl,
                    showServerSettingsEnabled = BuildConfig.SHOW_SERVER_SETTINGS,
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

        if (isDesktop) {
            val windowControls = LocalWindowControls.current
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                DraggableArea {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
                
                // Глобальные кнопки управления окном для десктопа
                DesktopWindowControls(
                    onClose = windowControls.onClose,
                    onMinimize = windowControls.onMinimize,
                    onToggleFullscreen = windowControls.onToggleFullscreen,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        } else {
            content()
        }
    }
}