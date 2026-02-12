package ru.homebudget.finkeeper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
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

/**
 * Индикатор состояния сети и синхронизации
 * Отображает:
 * - Зеленый круг когда есть интернет
 * - Красный круг когда офлайн
 * - Индикатор синхронизации когда идет синхронизация
 * - Badge с количеством не синхронизированных операций
 */
@Composable
fun NetworkStatusIndicator(modifier: Modifier = Modifier) {
    val networkMonitor = koinInject<NetworkMonitor>()
    val syncManager = koinInject<SyncManager>()

    val isOnline by networkMonitor.isOnline.collectAsState()
    val isSyncing by syncManager.isSyncing.collectAsState()
    val pendingCount by syncManager.pendingCount.collectAsState()

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Индикатор синхронизации
        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Badge с количеством не синхронизированных операций
        if (pendingCount > 0 && !isSyncing) {
            Box(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = pendingCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        // Индикатор сети - цветной круг
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)),
        )
    }
}

/**
 * Плавающий индикатор статуса сети для отображения в Scaffold
 */
@Composable
fun FloatingNetworkStatusIndicator(modifier: Modifier = Modifier) {
    val networkMonitor = koinInject<NetworkMonitor>()
    val syncManager = koinInject<SyncManager>()

    val isOnline by networkMonitor.isOnline.collectAsState()
    val isSyncing by syncManager.isSyncing.collectAsState()
    val pendingCount by syncManager.pendingCount.collectAsState()

    // Показываем только если есть что показать
    if (!isOnline || isSyncing || pendingCount > 0) {
        Surface(
            modifier =
                modifier
                    .padding(16.dp),
            shape = MaterialTheme.shapes.small,
            color =
                when {
                    !isOnline -> Color(0xFFF44336).copy(alpha = 0.9f)
                    isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    pendingCount > 0 -> Color(0xFFFF9800).copy(alpha = 0.9f)
                    else -> MaterialTheme.colorScheme.surface
                },
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    !isOnline -> {
                        // Красный круг индикатор
                        Box(
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                        )
                        Text(
                            text = "Офлайн режим",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    isSyncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Text(
                            text = "Синхронизация...",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    pendingCount > 0 -> {
                        // Оранжевый круг индикатор
                        Box(
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                        )
                        Text(
                            text = "$pendingCount операций в очереди",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
