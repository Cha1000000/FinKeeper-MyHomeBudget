package ru.homebudget.finkeeper.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import finkeeper24.composeapp.generated.resources.Res
import finkeeper24.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import ru.homebudget.finkeeper.BuildConfig
import ru.homebudget.finkeeper.data.model.User
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.ui.UiScale
import ru.homebudget.finkeeper.util.isDesktop
import kotlin.math.abs
import kotlin.math.roundToInt
import ru.homebudget.finkeeper.ui.components.AppButton
import ru.homebudget.finkeeper.ui.components.AppTextField
import ru.homebudget.finkeeper.ui.components.CollapsibleGlassyCard
import ru.homebudget.finkeeper.ui.components.ConfirmDialog
import ru.homebudget.finkeeper.ui.components.GlassyButtonStyle
import ru.homebudget.finkeeper.ui.components.GlassyCard
import ru.homebudget.finkeeper.ui.components.ScreenHeader
import ru.homebudget.finkeeper.ui.theme.AppTheme
import ru.homebudget.finkeeper.ui.viewmodel.SettingsState
import ru.homebudget.finkeeper.util.formatDateTime

@Composable
fun SettingsScreen(
    state: SettingsState,
    user: User?,
    username: String,
    onUpdateUsername: (String, () -> Unit) -> Unit,
    onUpdatePassword: (String, String, () -> Unit) -> Unit,
    onUpdateEmail: (String, (User) -> Unit) -> Unit,
    onClearEmail: ((User) -> Unit) -> Unit,
    onRequestEmailVerification: ((User) -> Unit) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (Int, String, () -> Unit) -> Unit,
    onLogout: () -> Unit,
    onClearStatus: () -> Unit,
    onRetrySync: () -> Unit,
    onDismissSyncError: () -> Unit,
    currentThemeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {},
    currentUiScale: Float? = null,
    onUiScaleChange: (Float?) -> Unit = {},
    // Переход из подсказки «Добавьте email»: сразу раскрыть раздел защиты аккаунта
    expandAccountProtection: Boolean = false,
) {
    // Разделы по умолчанию свёрнуты, чтобы экран оставался компактным
    var syncExpanded by rememberSaveable { mutableStateOf(false) }
    var profileExpanded by rememberSaveable { mutableStateOf(false) }
    var protectionExpanded by rememberSaveable { mutableStateOf(expandAccountProtection) }
    var securityExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var themeExpanded by rememberSaveable { mutableStateOf(false) }
    var uiScaleExpanded by rememberSaveable { mutableStateOf(false) }
    var newUsername by remember(username) { mutableStateOf(username) }
    var email by remember(user?.email) { mutableStateOf(user?.email ?: "") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var restoreConfirmationText by remember { mutableStateOf("") }
    var showUsernameConfirm by remember { mutableStateOf(false) }
    var showPasswordConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var selectedBackupId by remember(state.backupEntries) { mutableStateOf(state.backupEntries.firstOrNull()?.id) }

    val trimmedUsername = newUsername.trim()
    val normalizedEmail = email.trim().lowercase()
    val usernameChanged = trimmedUsername != username
    val emailChanged = normalizedEmail != (user?.email ?: "")
    val usernameError = if (trimmedUsername.length > 64) Strings.USERNAME_TOO_LONG else null
    val emailLooksValid = normalizedEmail.isEmpty() || (normalizedEmail.contains("@") && normalizedEmail.contains("."))
    val passwordLengthError = if (newPassword.isNotEmpty() && newPassword.length < 6) Strings.PASSWORD_TOO_SHORT else null
    val confirmPasswordError =
        if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) Strings.PASSWORDS_DO_NOT_MATCH else null
    val restoreReady =
        selectedBackupId != null && restoreConfirmationText.trim() == Strings.BACKUP_CONFIRMATION_VALUE
    val selectedBackup = state.backupEntries.firstOrNull { it.id == selectedBackupId }
    val isAccountProtected = user?.recoverabilityStatus == "protected"

    val isServerUnreachable by koinInject<ServerLinkState>().isUnreachable.collectAsState()
    val connectionText =
        when {
            !state.isOnline -> Strings.SYNC_OFFLINE
            isServerUnreachable -> Strings.SYNC_SERVER_UNREACHABLE
            else -> Strings.SYNC_ONLINE
        }
    val syncNeedsAttention = !state.isOnline || isServerUnreachable || state.lastSyncError != null
    val syncSummary =
        connectionText + " · " +
            when {
                state.lastSyncError != null -> Strings.SYNC_SUMMARY_ERROR
                state.isSyncing -> Strings.SYNC_SUMMARY_IN_PROGRESS
                state.pendingSyncCount > 0 -> Strings.SYNC_SUMMARY_PENDING.replace("%1\$d", state.pendingSyncCount.toString())
                else -> Strings.SYNC_SUMMARY_ALL_SENT
            }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = Strings.SETTINGS,
            modifier = Modifier.padding(horizontal = 0.dp),
        )

        AppInfoCard()

        CollapsibleGlassyCard(
            title = Strings.SYNC_STATUS,
            expanded = syncExpanded,
            onExpandedChange = { syncExpanded = it },
            summary = syncSummary,
            summaryColor = if (syncNeedsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            highlightColor = if (state.lastSyncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = connectionText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.isOnline && !isServerUnreachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    when {
                        state.isSyncing -> Strings.SYNC_IN_PROGRESS
                        state.pendingSyncCount > 0 -> Strings.SYNC_PENDING_COUNT.replace("%1\$d", state.pendingSyncCount.toString())
                        else -> Strings.SYNC_ALL_SENT
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    state.lastSuccessfulSyncAt?.let {
                        Strings.SYNC_LAST_SUCCESS.replace("%1\$s", formatDateTime(it))
                    } ?: Strings.SYNC_LAST_SUCCESS_UNKNOWN,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(visible = state.lastSyncError != null) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Strings.SYNC_ERROR_PREFIX.replace("%1\$s", state.lastSyncError ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                AppButton(
                    text = Strings.SYNC_RETRY,
                    onClick = onRetrySync,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = state.isOnline && !state.isSyncing,
                    isLoading = state.isSyncing,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy,
                )
                if (state.lastSyncError != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AppButton(
                        text = Strings.SYNC_DISMISS_ERROR,
                        onClick = onDismissSyncError,
                        modifier = Modifier.weight(1f).height(48.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = GlassyButtonStyle.Glassy,
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.statusMessage != null) {
            val statusColor = if (state.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

            GlassyCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                baseColor =
                    if (state.statusIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                highlightColor = statusColor,
            ) {
                Text(
                    text = state.statusMessage ?: "",
                    modifier = Modifier.padding(16.dp),
                    color =
                        if (state.statusIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        CollapsibleGlassyCard(
            title = Strings.PROFILE,
            expanded = profileExpanded,
            onExpandedChange = { profileExpanded = it },
            summary = username.ifBlank { null },
            highlightColor = MaterialTheme.colorScheme.primary,
        ) {
            AppTextField(
                value = newUsername,
                onValueChange = { newUsername = it; onClearStatus() },
                label = Strings.USERNAME_LABEL,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (trimmedUsername.isNotBlank() && usernameChanged && usernameError == null) {
                        showUsernameConfirm = true
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = usernameError ?: Strings.USERNAME_HELPER,
                style = MaterialTheme.typography.bodySmall,
                color = if (usernameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                text = Strings.SAVE_NAME,
                onClick = { showUsernameConfirm = true },
                enabled = trimmedUsername.isNotBlank() && usernameChanged && usernameError == null && !state.isLoading,
                isLoading = state.isLoading,
                contentColor = Color.White,
                style = GlassyButtonStyle.Glassy,
            )
        }

        CollapsibleGlassyCard(
            title = Strings.ACCOUNT_PROTECTION,
            expanded = protectionExpanded,
            onExpandedChange = { protectionExpanded = it },
            summary = if (isAccountProtected) Strings.ACCOUNT_PROTECTED else Strings.ACCOUNT_UNPROTECTED,
            summaryColor = if (isAccountProtected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            highlightColor = MaterialTheme.colorScheme.primary,
        ) {
            GlassyCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                baseColor =
                    if (isAccountProtected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                highlightColor =
                    if (isAccountProtected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isAccountProtected) Strings.ACCOUNT_PROTECTED else Strings.ACCOUNT_UNPROTECTED,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                            if (isAccountProtected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            if (isAccountProtected) {
                                Strings.ACCOUNT_PROTECTED_HELPER
                            } else {
                                Strings.ACCOUNT_UNPROTECTED_HELPER
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isAccountProtected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(
                value = email,
                onValueChange = { email = it; onClearStatus() },
                label = Strings.RECOVERY_EMAIL_LABEL,
                imeAction = ImeAction.Done,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    if (user?.emailConfirmed == true) {
                        Strings.EMAIL_CONFIRMED_HELPER
                    } else {
                        Strings.EMAIL_UNCONFIRMED_HELPER
                    },
                style = MaterialTheme.typography.bodySmall,
                color = if (emailLooksValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            AnimatedVisibility(visible = state.emailDebugToken != null) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassyCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        baseColor = MaterialTheme.colorScheme.secondaryContainer,
                        highlightColor = MaterialTheme.colorScheme.secondary,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = Strings.EMAIL_DEBUG_TITLE,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.emailDebugToken?.token ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppButton(
                    text = if (normalizedEmail.isEmpty()) Strings.REMOVE_EMAIL else Strings.SAVE_EMAIL,
                    onClick = {
                        if (normalizedEmail.isEmpty()) {
                            onClearEmail { updatedUser ->
                                email = updatedUser.email ?: ""
                            }
                        } else {
                            onUpdateEmail(normalizedEmail) { updatedUser ->
                                email = updatedUser.email ?: ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = emailChanged && emailLooksValid && !state.isLoading,
                    isLoading = state.isLoading,
                    contentColor = Color.White,
                    style = GlassyButtonStyle.Glassy,
                )
                AppButton(
                    text = Strings.RESEND_EMAIL_VERIFICATION,
                    onClick = {
                        onRequestEmailVerification { updatedUser ->
                            email = updatedUser.email ?: ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !user?.email.isNullOrBlank() && user?.emailConfirmed != true && !state.isLoading,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = GlassyButtonStyle.Glassy,
                )
            }
        }

        CollapsibleGlassyCard(
            title = Strings.SECURITY,
            expanded = securityExpanded,
            onExpandedChange = { securityExpanded = it },
            highlightColor = MaterialTheme.colorScheme.primary,
        ) {
            AppTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it; onClearStatus() },
                label = Strings.CURRENT_PASSWORD,
                isPassword = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppTextField(
                value = newPassword,
                onValueChange = { newPassword = it; onClearStatus() },
                label = Strings.NEW_PASSWORD,
                isPassword = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = passwordLengthError ?: Strings.PASSWORD_HELPER,
                style = MaterialTheme.typography.bodySmall,
                color = if (passwordLengthError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = Strings.CONFIRM_PASSWORD,
                isPassword = true,
                imeAction = ImeAction.Done,
            )
            if (confirmPasswordError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = confirmPasswordError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                text = Strings.CHANGE_PASSWORD,
                onClick = { showPasswordConfirm = true },
                enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && passwordLengthError == null && confirmPasswordError == null && !state.isLoading,
                isLoading = state.isLoading,
                contentColor = Color.White,
                style = GlassyButtonStyle.Glassy,
            )
        }

        CollapsibleGlassyCard(
            title = Strings.DATA_MANAGEMENT,
            expanded = dataExpanded,
            onExpandedChange = { dataExpanded = it },
            summary = state.backupEntries.firstOrNull()?.let { Strings.BACKUP_LAST_SUMMARY.replace("%1\$s", formatDateTime(it.createdAt)) },
            highlightColor = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = Strings.BACKUP_HELPER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val colors = AppTheme.semanticColors
            AppButton(
                text = Strings.CREATE_BACKUP,
                onClick = onCreateBackup,
                enabled = !state.isLoading,
                isLoading = state.isLoading,
                containerColor = colors.backupBlue,
                contentColor = Color.White,
                style = GlassyButtonStyle.Glassy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Strings.RESTORE_HELPER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.isBackupsLoading) {
                Text(
                    text = Strings.BACKUP_LIST_LOADING,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (state.backupEntries.isEmpty()) {
                Text(
                    text = Strings.BACKUP_LIST_EMPTY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = Strings.BACKUP_SELECT_LABEL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                state.backupEntries.forEach { backup ->
                    val isSelected = selectedBackupId == backup.id
                    GlassyCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        baseColor =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        highlightColor =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBackupId = backup.id }
                                    .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedBackupId = backup.id },
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = formatDateTime(backup.createdAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text =
                                        "Месяцев: ${backup.summary.months} · Доходов: ${backup.summary.incomes} · " +
                                            "Расходов: ${backup.summary.expenses} · Копилок: ${backup.summary.savingsGoals}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            AppTextField(
                value = restoreConfirmationText,
                onValueChange = { restoreConfirmationText = it; onClearStatus() },
                label = Strings.BACKUP_CONFIRMATION_LABEL,
                imeAction = ImeAction.Done,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Strings.BACKUP_CONFIRMATION_HELPER.replace("%1\$s", Strings.BACKUP_CONFIRMATION_VALUE),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (restoreConfirmationText.isNotBlank() && !restoreReady) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                text = Strings.RESTORE_FROM_BACKUP,
                onClick = { showRestoreConfirm = true },
                enabled = !state.isLoading && !state.isBackupsLoading && state.backupEntries.isNotEmpty() && restoreReady,
                isLoading = state.isLoading,
                containerColor = colors.restorePink,
                contentColor = Color.White,
                style = GlassyButtonStyle.Glassy,
            )
        }

        CollapsibleGlassyCard(
            title = Strings.THEME,
            expanded = themeExpanded,
            onExpandedChange = { themeExpanded = it },
            summary = themeOptions.firstOrNull { (mode, _) -> mode == currentThemeMode }?.second,
            highlightColor = MaterialTheme.colorScheme.primary,
        ) {
            themeOptions.forEach { (mode, label) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentThemeMode == mode,
                        onClick = null,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (isDesktop) {
            CollapsibleGlassyCard(
                title = Strings.UI_SCALE,
                expanded = uiScaleExpanded,
                onExpandedChange = { uiScaleExpanded = it },
                summary = uiScaleOptions.firstOrNull { (_, value) -> isUiScaleSelected(currentUiScale, value) }?.first,
                highlightColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = Strings.UI_SCALE_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                uiScaleOptions.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUiScaleChange(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isUiScaleSelected(currentUiScale, value),
                            onClick = null,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        AppButton(
            text = Strings.LOGOUT,
            onClick = { showLogoutConfirm = true },
            containerColor = AppTheme.semanticColors.logoutRed,
            contentColor = Color.White,
            style = GlassyButtonStyle.Glassy,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showUsernameConfirm) {
        ConfirmDialog(
            title = Strings.CHANGE_NAME,
            message = Strings.CHANGE_NAME_CONFIRM.replace("%1\u0024s", trimmedUsername),
            onConfirm = {
                onUpdateUsername(trimmedUsername) { newUsername = trimmedUsername }
                showUsernameConfirm = false
            },
            onDismiss = { showUsernameConfirm = false },
        )
    }

    if (showPasswordConfirm) {
        ConfirmDialog(
            title = Strings.CHANGE_PASSWORD,
            message = Strings.CHANGE_PASSWORD_CONFIRM,
            onConfirm = {
                onUpdatePassword(currentPassword, newPassword) {
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                }
                showPasswordConfirm = false
            },
            onDismiss = { showPasswordConfirm = false },
        )
    }

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = Strings.BACKUP_RESTORE_CONFIRMATION_TITLE,
            message =
                selectedBackup?.createdAt?.let {
                    Strings.BACKUP_RESTORE_SELECTED_MESSAGE.replace("%1\$s", formatDateTime(it))
                } ?: Strings.BACKUP_RESTORE_CONFIRMATION_MESSAGE,
            confirmText = Strings.BACKUP_RESTORE_BUTTON,
            onConfirm = {
                selectedBackupId?.let { backupId ->
                    onRestoreBackup(backupId, restoreConfirmationText.trim()) {}
                }
                showRestoreConfirm = false
            },
            onDismiss = { showRestoreConfirm = false },
            isDestructive = true,
        )
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = Strings.LOGOUT_TITLE,
            message = Strings.LOGOUT_CONFIRM,
            onConfirm = { onLogout(); showLogoutConfirm = false },
            onDismiss = { showLogoutConfirm = false },
            isDestructive = true,
        )
    }
}

private val themeOptions =
    listOf(
        "system" to Strings.THEME_SYSTEM,
        "light" to Strings.THEME_LIGHT,
        "night" to Strings.THEME_DARK,
        "dark_night" to Strings.THEME_DARK_NIGHT,
        "dark" to Strings.THEME_CYBERPUNK,
        "blue_ocean" to Strings.THEME_BLUE_OCEAN,
    )

// «Авто» + дискретные шаги из единого источника правды
private val uiScaleOptions: List<Pair<String, Float?>> =
    buildList {
        add(Strings.UI_SCALE_AUTO to null)
        UiScale.steps.forEach { step ->
            add("${(step * 100).roundToInt()}%" to step)
        }
    }

private fun isUiScaleSelected(current: Float?, value: Float?): Boolean =
    if (value == null) current == null else current != null && abs(current - value) < 0.001f

/** Шапка настроек: иконка, название приложения и установленная версия. */
@Composable
private fun AppInfoCard() {
    GlassyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        baseColor = MaterialTheme.colorScheme.surface,
        highlightColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Strings.APP_TITLE,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Strings.APP_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Strings.SETTINGS_APP_VERSION.replace("%1\$s", BuildConfig.APP_VERSION.removePrefix("v")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
