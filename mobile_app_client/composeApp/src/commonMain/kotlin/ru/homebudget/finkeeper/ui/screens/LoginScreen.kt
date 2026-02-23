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
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.isDesktop
import ru.homebudget.finkeeper.ui.theme.BackgroundDarkNight
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isDesktop) Modifier.padding(horizontal = 40.dp)
                    else Modifier.padding(24.dp)
                )
                .verticalScroll(rememberScrollState())
                .safeContentPadding(),
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
                text = Strings.APP_TITLE,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = Strings.APP_SUBTITLE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Form card
            GlassyCard(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRegisterMode) Strings.REGISTER_TITLE else Strings.LOGIN_TITLE,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AppTextField(
                        value = username,
                        onValueChange = { onClearError() },
                        label = Strings.USERNAME_LABEL,
                        imeAction = ImeAction.Next,
                        debounceMs = 300L,
                        onImmediateValueChange = { username = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppTextField(
                        value = password,
                        onValueChange = { onClearError() },
                        label = Strings.PASSWORD_LABEL,
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
                        text = if (isRegisterMode) Strings.REGISTER_BUTTON else Strings.LOGIN_BUTTON,
                        onClick = {
                            val u = username.trim()
                            val p = password.trim()
                            username = u
                            password = p
                            if (isRegisterMode) onRegister(u, p)
                            else onLogin(u, p)
                        },
                        enabled = username.isNotBlank() && password.isNotBlank(),
                        isLoading = state.isLoading,
                        contentColor = MaterialTheme.colorScheme.primary,
                        style = GlassyButtonStyle.Glassy
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            isRegisterMode = !isRegisterMode
                            onClearError()
                        }
                    ) {
                        Text(
                            text = if (isRegisterMode) Strings.ALREADY_HAVE_ACCOUNT else Strings.NO_ACCOUNT,
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
                    text = Strings.SERVER_SETTINGS,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            AnimatedVisibility(visible = showServerSettings) {
                GlassyCard(
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    baseColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        AppTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = Strings.SERVER_URL_LABEL,
                            placeholder = Strings.SERVER_URL_PLACEHOLDER,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            onImeAction = { onServerUrlChange(serverUrl) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppButton(
                            text = Strings.SAVE,
                            onClick = { onServerUrlChange(serverUrl) },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            style = GlassyButtonStyle.Glassy
                        )
                    }
                }
            }
        }
    }
}
