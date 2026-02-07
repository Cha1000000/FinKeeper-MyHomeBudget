package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.viewmodel.SettingsState

@Composable
fun SettingsScreen(
    state: SettingsState,
    username: String,
    onUpdateUsername: (String, () -> Unit) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (() -> Unit) -> Unit,
    onLogout: () -> Unit,
    onClearStatus: () -> Unit
) {
    var newUsername by remember { mutableStateOf(username) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showUsernameConfirm by remember { mutableStateOf(false) }
    var showPasswordConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Status message
        AnimatedVisibility(visible = state.statusMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.statusIsError)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = state.statusMessage ?: "",
                    modifier = Modifier.padding(16.dp),
                    color = if (state.statusIsError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Profile section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Профиль",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it; onClearStatus() },
                    label = "Имя пользователя",
                    imeAction = ImeAction.Done,
                    onImeAction = { if (newUsername != username) showUsernameConfirm = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(
                    text = "Сохранить имя",
                    onClick = { showUsernameConfirm = true },
                    enabled = newUsername.isNotBlank() && newUsername != username,
                    isLoading = state.isLoading
                )
            }
        }

        // Security section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Безопасность",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; onClearStatus() },
                    label = "Новый пароль",
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Подтвердите пароль",
                    isPassword = true,
                    imeAction = ImeAction.Done
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(
                    text = "Сменить пароль",
                    onClick = { showPasswordConfirm = true },
                    enabled = newPassword.isNotBlank() && newPassword == confirmPassword,
                    isLoading = state.isLoading
                )
            }
        }

        // Data management section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Управление данными",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppButton(
                    text = "Создать резервную копию",
                    onClick = onCreateBackup,
                    isLoading = state.isLoading,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(
                    text = "Восстановить из копии",
                    onClick = { showRestoreConfirm = true },
                    isLoading = state.isLoading,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            }
        }

        // Logout
        AppButton(
            text = "Выйти из аккаунта",
            onClick = { showLogoutConfirm = true },
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Dialogs
    if (showUsernameConfirm) {
        ConfirmDialog(
            title = "Сменить имя",
            message = "Изменить имя пользователя на \"$newUsername\"?",
            onConfirm = {
                onUpdateUsername(newUsername) { newUsername = newUsername }
                showUsernameConfirm = false
            },
            onDismiss = { showUsernameConfirm = false }
        )
    }

    if (showPasswordConfirm) {
        ConfirmDialog(
            title = "Сменить пароль",
            message = "Вы уверены, что хотите сменить пароль?",
            onConfirm = {
                onUpdatePassword(newPassword)
                newPassword = ""
                confirmPassword = ""
                showPasswordConfirm = false
            },
            onDismiss = { showPasswordConfirm = false }
        )
    }

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = "Восстановить данные",
            message = "Все текущие данные будут заменены данными из последней резервной копии. Продолжить?",
            onConfirm = {
                onRestoreBackup {}
                showRestoreConfirm = false
            },
            onDismiss = { showRestoreConfirm = false },
            isDestructive = true
        )
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Выход",
            message = "Вы уверены, что хотите выйти?",
            onConfirm = { onLogout(); showLogoutConfirm = false },
            onDismiss = { showLogoutConfirm = false },
            isDestructive = true
        )
    }
}
