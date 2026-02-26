package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.theme.AppTheme
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
    onClearStatus: () -> Unit,
    currentThemeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {}
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
        ScreenHeader(
            title = Strings.SETTINGS,
            modifier = Modifier.padding(horizontal = 0.dp),
        )

        // Status message
        AnimatedVisibility(visible = state.statusMessage != null) {
            val statusColor = if (state.statusIsError)
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary

            GlassyCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                baseColor = if (state.statusIsError)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
                highlightColor = statusColor
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
        GlassyCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            baseColor = MaterialTheme.colorScheme.surface,
            highlightColor = MaterialTheme.colorScheme.primary
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Strings.PROFILE,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it; onClearStatus() },
                    label = Strings.USERNAME_LABEL,
                    imeAction = ImeAction.Done,
                    onImeAction = { if (newUsername != username) showUsernameConfirm = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(
                    text = Strings.SAVE_NAME,
                    onClick = { showUsernameConfirm = true },
                    enabled = newUsername.isNotBlank() && newUsername != username,
                    isLoading = state.isLoading,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy
                )
            }
        }

        // Security section
        GlassyCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            baseColor = MaterialTheme.colorScheme.surface,
            highlightColor = MaterialTheme.colorScheme.primary
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Strings.SECURITY,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; onClearStatus() },
                    label = Strings.NEW_PASSWORD,
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = Strings.CONFIRM_PASSWORD,
                    isPassword = true,
                    imeAction = ImeAction.Done
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(
                    text = Strings.CHANGE_PASSWORD,
                    onClick = { showPasswordConfirm = true },
                    enabled = newPassword.isNotBlank() && newPassword == confirmPassword,
                    isLoading = state.isLoading,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy
                )
            }
        }

        // Data management section
        GlassyCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            baseColor = MaterialTheme.colorScheme.surface,
            highlightColor = MaterialTheme.colorScheme.primary
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Strings.DATA_MANAGEMENT,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                val colors = AppTheme.semanticColors
                AppButton(
                    text = Strings.CREATE_BACKUP,
                    onClick = onCreateBackup,
                    isLoading = state.isLoading,
                    containerColor = colors.backupBlue,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppButton(
                    text = Strings.RESTORE_FROM_BACKUP,
                    onClick = { showRestoreConfirm = true },
                    isLoading = state.isLoading,
                    containerColor = colors.restorePink,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy
                )
            }
        }

        // Theme section
        GlassyCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            baseColor = MaterialTheme.colorScheme.surface,
            highlightColor = MaterialTheme.colorScheme.primary
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Strings.THEME,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                val themeOptions = listOf(
                    "system" to Strings.THEME_SYSTEM,
                    "light" to Strings.THEME_LIGHT,
                    "night" to Strings.THEME_DARK,
                    "dark_night" to Strings.THEME_DARK_NIGHT,
                    "dark" to Strings.THEME_CYBERPUNK,
                )
                themeOptions.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = null
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        // Logout
        AppButton(
            text = Strings.LOGOUT,
            onClick = { showLogoutConfirm = true },
            containerColor = AppTheme.semanticColors.logoutRed,
            contentColor = Color.White,
            style = GlassyButtonStyle.Glassy
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Dialogs
    if (showUsernameConfirm) {
        ConfirmDialog(
            title = Strings.CHANGE_NAME,
            message = Strings.CHANGE_NAME_CONFIRM.replace("%1\u0024s", newUsername),
            onConfirm = {
                onUpdateUsername(newUsername) { newUsername = newUsername }
                showUsernameConfirm = false
            },
            onDismiss = { showUsernameConfirm = false }
        )
    }

    if (showPasswordConfirm) {
        ConfirmDialog(
            title = Strings.CHANGE_PASSWORD,
            message = Strings.CHANGE_PASSWORD_CONFIRM,
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
            title = Strings.BACKUP_RESTORE_CONFIRMATION_TITLE,
            message = Strings.BACKUP_RESTORE_CONFIRMATION_MESSAGE,
            confirmText = Strings.BACKUP_RESTORE_BUTTON,
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
            title = Strings.LOGOUT_TITLE,
            message = Strings.LOGOUT_CONFIRM,
            onConfirm = { onLogout(); showLogoutConfirm = false },
            onDismiss = { showLogoutConfirm = false },
            isDestructive = true
        )
    }
}
