package ru.homebudget.finkeeper.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.homebudget.finkeeper.data.local.dao.SyncQueueDao
import ru.homebudget.finkeeper.data.local.dao.SyncQueueItem
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncQueueStatus
import kotlin.coroutines.CoroutineContext

/**
 * Правила очереди синхронизации: постановка со слиянием и захват элемента на отправку.
 *
 * Постановки приходят из разных корутин параллельно с выгрузкой, поэтому всё чтение и изменение
 * очереди — под одним мьютексом. Мьютекс честный (FIFO): операции, захватившие его в порядке
 * вызовов, в этом же порядке попадают в очередь.
 *
 * Ключ операции (opId) элемента не меняется при слиянии в INSERT: повтор создания после
 * потерянного ответа должен прийти с тем же ключом, иначе сервер создаст дубль.
 */
internal class SyncQueueCoordinator(
    private val syncQueueDao: SyncQueueDao,
    private val entityUpdatedAt: (entityType: String, entityId: Long) -> String?,
    private val newOperationId: () -> String,
    private val dbContext: CoroutineContext = Dispatchers.Default,
) {
    private val mutex = Mutex()

    private suspend fun <T> locked(block: () -> T): T =
        mutex.withLock { withContext(dbContext) { block() } }

    /**
     * Ставит операцию в очередь, сливая её с последним ещё не отправляемым элементом той же записи.
     * [goalCurrentAmount] — явно заданная пользователем сумма копилки (абсолютная установка).
     * [reactivate] — UPDATE восстанавливает удалённую запись (категорию или источник).
     */
    suspend fun enqueue(
        userId: Long,
        entityType: String,
        entityId: Long,
        operation: String,
        deleteServerId: String? = null,
        goalCurrentAmount: Long? = null,
        reactivate: Boolean = false,
    ) = locked { enqueueLocked(userId, entityType, entityId, operation, deleteServerId, goalCurrentAmount, reactivate) }

    private fun enqueueLocked(
        userId: Long,
        entityType: String,
        entityId: Long,
        operation: String,
        deleteServerId: String?,
        goalCurrentAmount: Long?,
        reactivate: Boolean,
    ) {
        val newMeta =
            SyncQueuePayloadMetadata(
                opId = newOperationId(),
                entityUpdatedAt = entityUpdatedAt(entityType, entityId),
                deleteServerId = if (operation == SyncOperation.DELETE.value) deleteServerId else null,
                goalCurrentAmount = goalCurrentAmount,
                reactivate = reactivate && operation == SyncOperation.UPDATE.value,
            )
        // Установка суммы копилки — всегда отдельным элементом: сервер применяет пополнения и
        // установки по порядку, и слияние с более ранним элементом сдвинуло бы её раньше пополнений
        val existing = if (goalCurrentAmount != null) null else findMergeTarget(userId, entityType, entityId)
        if (existing == null) {
            syncQueueDao.insert(userId, entityType, entityId, operation, encodeSyncQueuePayloadMetadata(newMeta))
            return
        }

        var action = resolveQueueMergeAction(existing.operation, operation)
        // INSERT ещё ждёт отправки, но запись уже создана на сервере (её создала дочерняя операция):
        // удаление надо отправить, а не просто выбросить оба элемента
        if (action == QueueMergeAction.DROP_BOTH && newMeta.deleteServerId != null) {
            action = QueueMergeAction.REPLACE_WITH_NEW
        }
        // Запись удалили и тут же восстановили, а удаление ещё не отправлено: побеждает
        // восстановление (иначе ждущий DELETE поглотил бы его)
        if (existing.operation == SyncOperation.DELETE.value && newMeta.reactivate) {
            action = QueueMergeAction.REPLACE_WITH_NEW
        }
        when (action) {
            // Элемент сохраняет операцию и ключ, но снова ждёт отправки с чистым счётчиком попыток.
            // Актуальное состояние записи читается в момент отправки
            QueueMergeAction.KEEP_EXISTING -> syncQueueDao.merge(existing.id, existing.operation, existing.payload)
            QueueMergeAction.DROP_BOTH -> syncQueueDao.deleteById(existing.id)
            QueueMergeAction.REPLACE_WITH_NEW -> {
                val existingMeta = decodeSyncQueuePayloadMetadata(existing.payload)
                val merged =
                    if (operation == SyncOperation.DELETE.value) {
                        newMeta
                    } else {
                        newMeta.copy(
                            goalCurrentAmount = existingMeta?.goalCurrentAmount,
                            reactivate = newMeta.reactivate || existingMeta?.reactivate == true,
                        )
                    }
                syncQueueDao.merge(existing.id, operation, encodeSyncQueuePayloadMetadata(merged))
            }
        }
    }

    // Последний элемент записи, который ещё не отправляется (pending или failed)
    private fun findMergeTarget(userId: Long, entityType: String, entityId: Long): SyncQueueItem? =
        syncQueueDao.getAllByUser(userId).firstOrNull {
            it.entityType == entityType &&
                it.entityId == entityId &&
                (it.status == SyncQueueStatus.PENDING.value || it.status == SyncQueueStatus.FAILED.value)
        }

    /**
     * Захватывает pending-элемент на отправку: переводит в syncing и возвращает его свежую копию,
     * в которой [SyncQueuePayloadMetadata.entityUpdatedAt] — версия записи на момент отправки
     * (по ней после ответа сервера видно, менялась ли запись во время запроса).
     * `null` — элемент слать не нужно: уже не pending, ждёт более ранний элемент той же записи
     * или устарел (его закрывает более поздний элемент).
     */
    suspend fun claim(itemId: Long): SyncQueueItem? =
        locked {
            val item = syncQueueDao.getById(itemId)
            if (item == null || item.status != SyncQueueStatus.PENDING.value) return@locked null

            val siblings =
                syncQueueDao.getAllByUser(item.userId).filter {
                    it.id != item.id &&
                        it.entityType == item.entityType &&
                        it.entityId == item.entityId &&
                        it.status != SyncQueueStatus.COMPLETED.value
                }
            if (isQueueItemBlocked(item, siblings)) {
                println("[SYNC] claim: ${item.entityType}#${item.entityId} op=${item.operation} waits for an earlier operation")
                return@locked null
            }

            val currentUpdatedAt = entityUpdatedAt(item.entityType, item.entityId)
            val hasLaterItem = siblings.any { it.id > item.id }
            if (shouldSkipOutdatedQueueItem(item.operation, item.payload, currentUpdatedAt, hasLaterItem)) {
                println("[SYNC] claim: SKIPPED outdated ${item.entityType}#${item.entityId} op=${item.operation}")
                syncQueueDao.markCompleted(item.id)
                return@locked null
            }

            syncQueueDao.updateStatus(item.id, SyncQueueStatus.SYNCING.value, null)
            val meta = decodeSyncQueuePayloadMetadata(item.payload)
            if (item.operation == SyncOperation.DELETE.value || meta == null || currentUpdatedAt == null) {
                item.copy(status = SyncQueueStatus.SYNCING.value)
            } else {
                item.copy(
                    status = SyncQueueStatus.SYNCING.value,
                    payload = encodeSyncQueuePayloadMetadata(meta.copy(entityUpdatedAt = currentUpdatedAt)),
                )
            }
        }

    /**
     * После успешной отправки запись осталась несинхронизированной ([isDirty]), а других операций
     * для неё в очереди нет: ставит UPDATE, иначе изменение не ушло бы на сервер никогда.
     */
    suspend fun enqueueFollowUpIfNeeded(
        item: SyncQueueItem,
        isDirty: () -> Boolean,
    ): Boolean =
        locked {
            if (item.operation == SyncOperation.DELETE.value || !isDirty()) return@locked false
            val hasOtherItem =
                syncQueueDao.getAllByUser(item.userId).any {
                    it.id != item.id &&
                        it.entityType == item.entityType &&
                        it.entityId == item.entityId &&
                        it.status != SyncQueueStatus.COMPLETED.value
                }
            if (hasOtherItem) return@locked false
            println("[SYNC] follow-up UPDATE for dirty ${item.entityType}#${item.entityId}")
            enqueueLocked(item.userId, item.entityType, item.entityId, SyncOperation.UPDATE.value, null, null, false)
            true
        }

    /**
     * Сервер отказал: ключ операции уже использован для другого запроса (ответ на прошлую
     * попытку потерялся, а данные с тех пор изменились). Элемент получает новый ключ и снова ждёт.
     */
    suspend fun renewOperationIdAndReturnToPending(itemId: Long) =
        locked {
            val current = syncQueueDao.getById(itemId) ?: return@locked
            val meta = decodeSyncQueuePayloadMetadata(current.payload) ?: SyncQueuePayloadMetadata()
            syncQueueDao.updatePayload(itemId, encodeSyncQueuePayloadMetadata(meta.copy(opId = newOperationId())))
            syncQueueDao.returnToPending(itemId)
        }

    /**
     * Ключ ещё не закрытого INSERT записи. Им пользуется дочерняя операция, когда создаёт родителя
     * раньше его собственного элемента: сервер узнает повтор и не создаст дубль.
     */
    fun openInsertOperationId(
        userId: Long,
        entityType: String,
        entityId: Long,
    ): String? =
        syncQueueDao.getAllByUser(userId)
            .firstOrNull {
                it.entityType == entityType &&
                    it.entityId == entityId &&
                    it.operation == SyncOperation.INSERT.value &&
                    it.status != SyncQueueStatus.COMPLETED.value
            }?.let { decodeSyncQueuePayloadMetadata(it.payload)?.opId }
}

/**
 * Операции одной записи уходят по порядку. Элемент ждёт, пока не закрыт более ранний INSERT
 * (без него запись не создана) или более ранний элемент ещё в очереди/отправке.
 * Упавший более ранний UPDATE не держит: следующий элемент несёт более свежее состояние.
 */
internal fun isQueueItemBlocked(
    item: SyncQueueItem,
    siblings: List<SyncQueueItem>,
): Boolean =
    siblings.any {
        it.id < item.id &&
            it.status != SyncQueueStatus.COMPLETED.value &&
            (
                it.operation == SyncOperation.INSERT.value ||
                    it.status == SyncQueueStatus.PENDING.value ||
                    it.status == SyncQueueStatus.SYNCING.value
            )
    }
