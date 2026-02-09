package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.neonGlow
import ru.homebudget.finkeeper.ui.viewmodel.AuthState

@Composable
fun LoginScreen(
    state: AuthState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onClearError: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    currentServerUrl: String
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var showServerSettings by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(currentServerUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeContentPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo area
            Text(
                text = "💰",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "FinKeeper",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "Домашняя бухгалтерия",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Form card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlow(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), radius = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRegisterMode) "Регистрация" else "Вход",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AppTextField(
                        value = username,
                        onValueChange = { onClearError() },
                        label = "Имя пользователя",
                        imeAction = ImeAction.Next,
                        debounceMs = 300L,
                        onImmediateValueChange = { username = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppTextField(
                        value = password,
                        onValueChange = { onClearError() },
                        label = "Пароль",
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        debounceMs = 300L,
                        onImmediateValueChange = { password = it },
                        onImeAction = {
                            val u = username.trim()
                            val p = password.trim()
                            if (u.isNotBlank() && p.isNotBlank()) {
                                username = u
                                password = p
                                if (isRegisterMode) onRegister(u, p)
                                else onLogin(u, p)
                            }
                        }
                    )

                    AnimatedVisibility(visible = state.error != null) {
                        Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AppButton(
                        text = if (isRegisterMode) "Зарегистрироваться" else "Войти",
                        onClick = {
                            val u = username.trim()
                            val p = password.trim()
                            username = u
                            password = p
                            if (isRegisterMode) onRegister(u, p)
                            else onLogin(u, p)
                        },
                        enabled = username.isNotBlank() && password.isNotBlank(),
                        isLoading = state.isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            isRegisterMode = !isRegisterMode
                            onClearError()
                        }
                    ) {
                        Text(
                            text = if (isRegisterMode) "Уже есть аккаунт? Войти"
                            else "Нет аккаунта? Зарегистрироваться",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Server settings
            TextButton(
                onClick = { showServerSettings = !showServerSettings }
            ) {
                Text(
                    text = "⚙ Настройки сервера",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AnimatedVisibility(visible = showServerSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonGlow(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), radius = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        AppTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = "URL сервера",
                            placeholder = "http://10.0.2.2:3002",
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            onImeAction = { onServerUrlChange(serverUrl) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppButton(
                            text = "Сохранить",
                            onClick = { onServerUrlChange(serverUrl) },
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
