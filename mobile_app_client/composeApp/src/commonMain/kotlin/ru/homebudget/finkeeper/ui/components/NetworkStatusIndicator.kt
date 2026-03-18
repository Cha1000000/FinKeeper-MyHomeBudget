package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import ru.homebudget.finkeeper.data.network.NetworkMonitor
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.ui.Strings

/**
 * Компактный индикатор состояния сети и синхронизации.
 * Отображает:
 * - Зеленый круг когда есть интернет
 * - Красный круг когда офлайн
 * - Спиннер когда идет синхронизация
 * - Badge с количеством не синхронизированных операций
 */
@Composable
fun NetworkStatusIndicator(modifier: Modifier = Modifier) {
    val networkMonitor = koinInject<NetworkMonitor>()
    val syncManager = koinInject<SyncManager>()

    val isOnline by networkMonitor.isOnline.collectAsState()
    val isSyncing by syncManager.isSyncing.collectAsState()
    val pendingCount by syncManager.pendingCount.collectAsState()
    val lastSyncError by syncManager.lastSyncError.collectAsState()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (pendingCount > 0 && !isSyncing) {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = pendingCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }

            Text(
                text = if (isOnline) Strings.SYNC_ONLINE else Strings.SYNC_OFFLINE,
                style = MaterialTheme.typography.labelMedium,
                color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
            )

            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)),
            )
        }

        if (!lastSyncError.isNullOrBlank()) {
            val syncErrorText = lastSyncError ?: ""
            Text(
                text = Strings.SYNC_ERROR_PREFIX.replace("%1\$s", syncErrorText.take(90)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable {
                    syncManager.clearSyncError()
                },
            )
        }
    }
}

/**
 * Переиспользуемый заголовок экрана с индикатором статуса сети.
 * Используется на каждом экране приложения.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions()
            NetworkStatusIndicator()
        }
    }
}
