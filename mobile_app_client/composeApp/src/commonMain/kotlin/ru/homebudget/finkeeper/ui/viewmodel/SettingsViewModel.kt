package ru.homebudget.finkeeper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.homebudget.finkeeper.data.model.BackupEntry
import ru.homebudget.finkeeper.data.model.DebugTokenPreview
import ru.homebudget.finkeeper.data.model.User
import ru.homebudget.finkeeper.data.network.NetworkMonitor
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.ui.Strings
import ru.homebudget.finkeeper.util.RetryConfig
import ru.homebudget.finkeeper.util.withRetry

data class SettingsState(
    val isLoading: Boolean = false,
    val isBackupsLoading: Boolean = false,
    val backupEntries: List<BackupEntry> = emptyList(),
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val pendingSyncCount: Long = 0L,
    val lastSyncError: String? = null,
    val lastSuccessfulSyncAt: String? = null,
    val emailDebugToken: DebugTokenPreview? = null,
)

class SettingsViewModel(
    private val apiClient: ApiClient,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadBackups()
        viewModelScope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                _state.value = _state.value.copy(isOnline = isOnline)
            }
        }
        viewModelScope.launch {
            syncManager.isSyncing.collectLatest { isSyncing ->
                _state.value = _state.value.copy(isSyncing = isSyncing)
            }
        }
        viewModelScope.launch {
            syncManager.pendingCount.collectLatest { pendingCount ->
                _state.value = _state.value.copy(pendingSyncCount = pendingCount)
            }
        }
        viewModelScope.launch {
            syncManager.lastSyncError.collectLatest { syncError ->
                _state.value = _state.value.copy(lastSyncError = syncError)
            }
        }
        viewModelScope.launch {
            syncManager.lastSuccessfulSyncAt.collectLatest { lastSuccessfulSyncAt ->
                _state.value = _state.value.copy(lastSuccessfulSyncAt = lastSuccessfulSyncAt)
            }
        }
    }

    fun loadBackups() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBackupsLoading = true)
            try {
                val response =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.getBackupEntries()
                    }
                _state.value =
                    _state.value.copy(
                        isBackupsLoading = false,
                        backupEntries = response.backups,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isBackupsLoading = false,
                        statusMessage =
                            if (networkMonitor.isNetworkAvailable) {
                                Strings.ERROR_LOADING_BACKUPS
                            } else {
                                Strings.OFFLINE_RETRY_LATER
                            },
                        statusIsError = true,
                    )
            }
        }
    }

    fun updateUsername(username: String, onSuccess: () -> Unit) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank()) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    statusMessage = Strings.ENTER_USERNAME_AND_PASSWORD,
                    statusIsError = true,
                )
            return
        }
        if (trimmedUsername.length > 64) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    statusMessage = Strings.USERNAME_TOO_LONG,
                    statusIsError = true,
                )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updateUsername(trimmedUsername)
                }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.USERNAME_UPDATED,
                        statusIsError = false,
                    )
                onSuccess()
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = e.message,
                        statusIsError = true,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage =
                            if (networkMonitor.isNetworkAvailable) {
                                Strings.ERROR_UPDATING_NAME
                            } else {
                                Strings.OFFLINE_RETRY_LATER
                            },
                        statusIsError = true,
                    )
            }
        }
    }

    fun updatePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit = {},
    ) {
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    statusMessage = Strings.ENTER_USERNAME_AND_PASSWORD,
                    statusIsError = true,
                )
            return
        }
        if (newPassword.length < 6) {
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    statusMessage = Strings.PASSWORD_TOO_SHORT,
                    statusIsError = true,
                )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.updatePassword(currentPassword, newPassword)
                }
                onSuccess()
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.PASSWORD_CHANGED,
                        statusIsError = false,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = e.message,
                        statusIsError = true,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage =
                            if (networkMonitor.isNetworkAvailable) {
                                Strings.ERROR_CHANGING_PASSWORD
                            } else {
                                Strings.OFFLINE_RETRY_LATER
                            },
                        statusIsError = true,
                    )
            }
        }
    }

    fun updateUserEmail(
        email: String,
        onSuccess: (User) -> Unit,
    ) {
        val normalizedEmail = email.trim().lowercase()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                val response =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.updateUserEmail(normalizedEmail)
                    }
                onSuccess(response.user)
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage =
                            if (response.verificationRequired) {
                                Strings.EMAIL_SAVE_REQUIRES_VERIFICATION
                            } else {
                                Strings.EMAIL_UPDATED
                            },
                        statusIsError = false,
                        emailDebugToken = response.debug?.emailVerification,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = e.message,
                        statusIsError = true,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.OFFLINE_RETRY_LATER,
                        statusIsError = true,
                    )
            }
        }
    }

    fun clearUserEmail(onSuccess: (User) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                val response =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.clearUserEmail()
                    }
                onSuccess(response.user)
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.EMAIL_REMOVED,
                        statusIsError = false,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = e.message,
                        statusIsError = true,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.OFFLINE_RETRY_LATER,
                        statusIsError = true,
                    )
            }
        }
    }

    fun requestEmailVerification(onSuccess: (User) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                val response =
                    withRetry(config = RetryConfig(maxAttempts = 3)) {
                        apiClient.requestEmailVerification()
                    }
                onSuccess(response.user)
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.EMAIL_VERIFICATION_SENT,
                        statusIsError = false,
                        emailDebugToken = response.debug?.emailVerification,
                    )
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = e.message,
                        statusIsError = true,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.OFFLINE_RETRY_LATER,
                        statusIsError = true,
                    )
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.createManualBackup()
                }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.BACKUP_CREATED,
                        statusIsError = false,
                    )
                loadBackups()
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage =
                            if (networkMonitor.isNetworkAvailable) {
                                Strings.ERROR_CREATING_BACKUP
                            } else {
                                Strings.OFFLINE_RETRY_LATER
                            },
                        statusIsError = true,
                    )
            }
        }
    }

    fun restoreBackup(
        backupId: Int,
        confirmationText: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusMessage = null, emailDebugToken = null)
            try {
                withRetry(config = RetryConfig(maxAttempts = 3)) {
                    apiClient.restoreBackup(backupId, confirmationText)
                }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = Strings.DATA_RESTORED,
                        statusIsError = false,
                    )
                loadBackups()
                onSuccess()
            } catch (e: ApiException) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage = e.message,
                        statusIsError = true,
                    )
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        statusMessage =
                            if (networkMonitor.isNetworkAvailable) {
                                Strings.ERROR_RESTORING_DATA
                            } else {
                                Strings.OFFLINE_RETRY_LATER
                            },
                        statusIsError = true,
                    )
            }
        }
    }

    fun retrySync() {
        if (!networkMonitor.isNetworkAvailable) {
            _state.value =
                _state.value.copy(
                    statusMessage = Strings.OFFLINE_RETRY_LATER,
                    statusIsError = true,
                )
            return
        }
        syncManager.retryFailed()
        syncManager.syncAll()
        _state.value =
            _state.value.copy(
                statusMessage = Strings.SYNC_IN_PROGRESS,
                statusIsError = false,
            )
    }

    fun clearSyncError() {
        syncManager.clearSyncError()
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null, emailDebugToken = null)
    }
}
