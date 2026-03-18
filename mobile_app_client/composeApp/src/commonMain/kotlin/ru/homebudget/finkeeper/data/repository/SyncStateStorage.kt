package ru.homebudget.finkeeper.data.repository

import com.russhwolf.settings.Settings
import ru.homebudget.finkeeper.data.remote.TokenStorage

class SyncStateStorage(
    private val tokenStorage: TokenStorage,
    private val settings: Settings = Settings(),
) {
    var lastSuccessfulSyncAt: String?
        get() = settings.getStringOrNull(scopedKey(KEY_LAST_SUCCESSFUL_SYNC_AT))
        set(value) {
            val key = scopedKey(KEY_LAST_SUCCESSFUL_SYNC_AT)
            if (value.isNullOrBlank()) {
                settings.remove(key)
            } else {
                settings.putString(key, value)
            }
        }

    var lastDeletedRecordsSyncAt: String?
        get() = settings.getStringOrNull(scopedKey(KEY_LAST_DELETED_RECORDS_SYNC_AT))
        set(value) {
            val key = scopedKey(KEY_LAST_DELETED_RECORDS_SYNC_AT)
            if (value.isNullOrBlank()) {
                settings.remove(key)
            } else {
                settings.putString(key, value)
            }
        }

    private fun scopedKey(suffix: String): String {
        val userId = tokenStorage.userId
        val serverHash = tokenStorage.serverUrl.hashCode()
        return "sync_state_${userId}_${serverHash}_$suffix"
    }

    companion object {
        private const val KEY_LAST_SUCCESSFUL_SYNC_AT = "last_successful_sync_at"
        private const val KEY_LAST_DELETED_RECORDS_SYNC_AT = "last_deleted_records_sync_at"
    }
}
