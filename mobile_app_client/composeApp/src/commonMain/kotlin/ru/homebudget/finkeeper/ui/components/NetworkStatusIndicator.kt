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
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.ui.Strings

/**
 * Компактный индикатор состояния сети и синхронизации.
 * Отображает:
 * - Зеленый круг когда есть интернет и сервер отвечает
 * - Оранжевый круг когда интернет есть, а сервер не отвечает
 * - Красный круг когда офлайн
 * - Спиннер когда идет синхронизация
 * - Badge с количеством не синхронизированных операций
 */
@Composable
fun NetworkStatusIndicator(modifier: Modifier = Modifier) {
    val networkMonitor = koinInject<NetworkMonitor>()
    val syncManager = koinInject<SyncManager>()
    val serverLinkState = koinInject<ServerLinkState>()

    val isOnline by networkMonitor.isOnline.collectAsState()
    val isServerUnreachable by serverLinkState.isUnreachable.collectAsState()
    // Сеть есть, а сервер молчит — не показываем «В сети»
    val statusColor =
        when {
            !isOnline -> Color(0xFFF44336)
            isServerUnreachable -> Color(0xFFFF9800)
            else -> Color(0xFF4CAF50)
        }
    val statusText =
        when {
            !isOnline -> Strings.SYNC_OFFLINE
            isServerUnreachable -> Strings.SYNC_SERVER_UNREACHABLE
            else -> Strings.SYNC_ONLINE
        }
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
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )

            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
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
