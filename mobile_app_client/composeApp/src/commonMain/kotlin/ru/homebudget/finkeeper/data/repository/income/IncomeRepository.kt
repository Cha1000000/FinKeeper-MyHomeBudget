package ru.homebudget.finkeeper.data.repository.income

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.IncomeDao
import ru.homebudget.finkeeper.data.local.dao.IncomeSourceDao
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.shouldApplyRemoteServerSnapshot
import ru.homebudget.finkeeper.data.model.Income as RemoteIncome

/**
 * Репозиторий для работы с доходами
 * Реализует offline-first логику
 */
class IncomeRepository(
    private val incomeDao: IncomeDao,
    private val monthDao: MonthDao,
    private val incomeSourceDao: IncomeSourceDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }
    private val syncMutex = Mutex()

    /**
     * Получение всех доходов пользователя
     */
    suspend fun getAllIncomes(userId: Long): Result<List<RemoteIncome>> =
        withContext(Dispatchers.Default) {
            try {
                val localIncomes = incomeDao.getAllByUser(userId)

                val result =
                    localIncomes.map { local ->
                        RemoteIncome(
                            id = local.id.toInt(),
                            monthId = local.monthId.toInt(),
                            source = local.incomeSourceId.toString(),
                            amount = local.amount.toDouble(),
                            date = local.date,
                            description = local.description,
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Получение доходов по месяцу
     */
    suspend fun getIncomesByMonth(monthId: Long): Result<List<RemoteIncome>> =
        withContext(Dispatchers.Default) {
            try {
                val localIncomes = incomeDao.getByMonth(monthId)

                val result =
                    localIncomes.map { local ->
                        RemoteIncome(
                            id = local.id.toInt(),
                            monthId = local.monthId.toInt(),
                            source = local.incomeSourceId.toString(),
                            amount = local.amount.toDouble(),
                            date = local.date,
                            description = local.description,
                        )
                    }

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Создание нового дохода
     */
    suspend fun createIncome(
        userId: Long,
        monthId: Long,
        incomeSourceId: Long,
        amount: Double,
        description: String? = null,
        date: String,
    ): Result<RemoteIncome> =
        withContext(Dispatchers.Default) {
            try {
                val localId =
                    incomeDao.insertAndReturn(
                        userId = userId,
                        monthId = monthId,
                        incomeSourceId = incomeSourceId,
                        amount = amount.toLong(),
                        description = description,
                        date = date,
                        serverId = null,
                        syncStatus = SyncStatus.PENDING.value,
                    )

                val localIncome = incomeDao.getById(localId)!!
                println("[INCOME] Created locally: id=$localId, monthId=$monthId, sourceId=$incomeSourceId, userId=$userId")

                val result =
                    RemoteIncome(
                        id = localIncome.id.toInt(),
                        monthId = localIncome.monthId.toInt(),
                        source = localIncome.incomeSourceId.toString(),
                        amount = localIncome.amount.toDouble(),
                        date = localIncome.date,
                    )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = userId,
                    entityType = EntityType.INCOME.value,
                    entityId = localId,
                    operation = SyncOperation.INSERT.value,
                    payload = null,
                )

                Result.success(result)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Обновление дохода
     */
    suspend fun updateIncome(
        id: Long,
        amount: Double? = null,
        description: String? = null,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = incomeDao.getById(id) ?: return@withContext Result.error(Exception("Income not found"))

                incomeDao.update(
                    id = id,
                    monthId = existing.monthId,
                    incomeSourceId = existing.incomeSourceId,
                    amount = amount?.toLong() ?: existing.amount,
                    description = description ?: existing.description,
                    date = existing.date,
                    serverId = existing.serverId,
                    syncStatus = SyncStatus.PENDING.value,
                )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = existing.userId,
                    entityType = EntityType.INCOME.value,
                    entityId = id,
                    operation = SyncOperation.UPDATE.value,
                    payload = null,
                )

                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Удаление дохода
     */
    suspend fun deleteIncome(id: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val income = incomeDao.getById(id)
                val serverId = income?.serverId

                incomeDao.deleteById(id)

                if (serverId != null) {
                    syncManager.enqueueSync(
                        userId = income.userId,
                        entityType = EntityType.INCOME.value,
                        entityId = id,
                        operation = SyncOperation.DELETE.value,
                        payload = serverId,
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Синхронизация с сервером
     */
    // Pull-синхронизация сериализуется мьютексом: Dashboard и Month ViewModel стартуют
    // параллельно, и две гонящиеся insert-ветки дублировали локальные записи
    private val syncPullMutex = Mutex()

    suspend fun syncWithServer(userId: Long, monthId: Long,): Result<Unit> =
        syncPullMutex.withLock { syncWithServerInternal(userId, monthId) }

    private suspend fun syncWithServerInternal(userId: Long, monthId: Long,): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                // monthId здесь — локальный ID, нужен serverId для API
                val month = monthDao.getById(monthId)
                val serverMonthId = month?.serverId?.toIntOrNull() ?: return@withContext Result.success(Unit)

                syncMutex.withLock {
                    val remoteIncomes = apiClient.getIncomes(serverMonthId)

                    // Предзагрузка источников дохода для резолва имени → localId
                    val allSources = incomeSourceDao.getAllByUser(userId)

                    // Получаем serverId записей, ожидающих удаления на сервере
                    val pendingDeleteServerIds = syncManager.getPendingDeleteServerIds(EntityType.INCOME.value)

                    for (remote in remoteIncomes) {
                        // Не восстанавливаем записи, которые удалены локально и ждут синхронизации
                        if (remote.id.toString() in pendingDeleteServerIds) {
                            continue
                        }

                        val existing = incomeDao.getByServerId(remote.id.toString())

                        if (
                            existing != null &&
                            (
                                existing.syncStatus != SyncStatus.SYNCED.value ||
                                    syncManager.hasActiveQueueOperation(EntityType.INCOME.value, existing.id)
                            )
                        ) {
                            continue
                        }

                        // Доход, ссылающийся на несуществующий локальный источник (раньше удалённый
                        // источник стирался локально), пересопоставляем по имени даже без изменений
                        val orphaned = existing != null && existing.incomeSourceId != 0L &&
                            allSources.none { it.id == existing.incomeSourceId }
                        if (existing != null && !orphaned && !shouldApplyRemoteServerSnapshot(existing.updatedAt, remote.updatedAt)) {
                            continue
                        }

                        // remote.source — это имя источника ("Зарплата" или "💸 Зарплата")
                        // Ищем локальный ID: сначала точное совпадение, потом нечёткое (с/без эмодзи)
                        val matchedSource = allSources
                            .find { it.name.equals(remote.source, ignoreCase = true) }
                            ?: allSources.find { it.name.contains(remote.source, ignoreCase = true) }
                            ?: allSources.find { remote.source.contains(it.name, ignoreCase = true) }
                        val sourceId = matchedSource?.id ?: 0L
                        // Если источник не найден, сохраняем оригинальное имя в description
                        val description = if (matchedSource == null) remote.source else null

                        if (existing != null) {
                            incomeDao.update(
                                id = existing.id,
                                monthId = monthId,
                                incomeSourceId = sourceId,
                                amount = remote.amount.toLong(),
                                description = description,
                                date = remote.date,
                                updatedAt = remote.updatedAt ?: existing.updatedAt,
                                serverId = remote.id.toString(),
                                syncStatus = SyncStatus.SYNCED.value,
                            )
                        } else {
                            incomeDao.insertAndReturn(
                                userId = userId,
                                monthId = monthId,
                                incomeSourceId = sourceId,
                                amount = remote.amount.toLong(),
                                description = description,
                                date = remote.date,
                                createdAt = remote.createdAt,
                                updatedAt = remote.updatedAt,
                                serverId = remote.id.toString(),
                                syncStatus = SyncStatus.SYNCED.value,
                            )
                        }
                    }

                    // Удаляем призрачные записи: только SYNCED-записи, отсутствующие на сервере
                    // Не трогаем PENDING записи — они содержат локальные изменения
                    val remoteServerIds = remoteIncomes.map { it.id.toString() }.toSet()
                    val localSyncedIncomes = incomeDao.getByMonth(monthId).filter {
                        it.serverId != null &&
                            it.syncStatus == SyncStatus.SYNCED.value &&
                            !syncManager.hasActiveQueueOperation(EntityType.INCOME.value, it.id)
                    }
                    for (local in localSyncedIncomes) {
                        if (local.serverId !in remoteServerIds) {
                            incomeDao.deleteById(local.id)
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }
}
