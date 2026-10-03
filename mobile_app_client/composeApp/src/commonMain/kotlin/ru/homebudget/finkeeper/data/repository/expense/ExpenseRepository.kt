package ru.homebudget.finkeeper.data.repository.expense

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.CategoryDao
import ru.homebudget.finkeeper.data.local.dao.ExpenseDao
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.local.model.EntityType
import ru.homebudget.finkeeper.data.local.model.SyncOperation
import ru.homebudget.finkeeper.data.local.model.SyncStatus
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.shouldApplyRemoteServerSnapshot
import ru.homebudget.finkeeper.data.model.Expense as RemoteExpense

/**
 * Репозиторий для работы с расходами
 * Реализует offline-first логику
 */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val monthDao: MonthDao,
    private val categoryDao: CategoryDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }
    private val syncMutex = Mutex()

    /**
     * Получение всех расходов пользователя
     */
    suspend fun getAllExpenses(userId: Long): Result<List<RemoteExpense>> =
        withContext(Dispatchers.Default) {
            try {
                val localExpenses = expenseDao.getAllByUser(userId)

                val result =
                    localExpenses.map { local ->
                        RemoteExpense(
                            id = local.id.toInt(),
                            monthId = local.monthId.toInt(),
                            categoryId = local.categoryId.toInt(),
                            amount = local.amount.toDouble(),
                            comment = local.description,
                            date = local.date,
                            categoryName = null,
                        )
                    }

                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Получение расходов по месяцу
     */
    suspend fun getExpensesByMonth(monthId: Long): Result<List<RemoteExpense>> =
        withContext(Dispatchers.Default) {
            try {
                val localExpenses = expenseDao.getByMonth(monthId)

                val result =
                    localExpenses.map { local ->
                        RemoteExpense(
                            id = local.id.toInt(),
                            monthId = local.monthId.toInt(),
                            categoryId = local.categoryId.toInt(),
                            amount = local.amount.toDouble(),
                            comment = local.description,
                            date = local.date,
                            categoryName = null,
                        )
                    }

                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Создание нового расхода
     */
    suspend fun createExpense(
        userId: Long,
        monthId: Long,
        categoryId: Long,
        amount: Double,
        description: String? = null,
        date: String,
    ): Result<RemoteExpense> =
        withContext(Dispatchers.Default) {
            println("[EXPENSE] createExpense START: userId=$userId, monthId=$monthId, categoryId=$categoryId, amount=$amount")
            try {
                val now = Clock.System.now().toString()
                val localId =
                    expenseDao.insertAndReturn(
                        userId = userId,
                        monthId = monthId,
                        categoryId = categoryId,
                        amount = amount.toLong(),
                        description = description,
                        date = date,
                        createdAt = now,
                        updatedAt = now,
                        serverId = null,
                        syncStatus = SyncStatus.PENDING.value,
                        isHidden = 0L,
                    )

                val localExpense = expenseDao.getById(localId)!!
                syncManager.enqueueSync(
                    userId = userId,
                    entityType = EntityType.EXPENSE.value,
                    entityId = localId,
                    operation = SyncOperation.INSERT.value,
                    payload = null,
                )

                val result =
                    RemoteExpense(
                        id = localExpense.id.toInt(),
                        monthId = localExpense.monthId.toInt(),
                        categoryId = localExpense.categoryId.toInt(),
                        amount = localExpense.amount.toDouble(),
                        comment = localExpense.description,
                        date = localExpense.date,
                        categoryName = null,
                    )

                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[EXPENSE] createExpense ERROR: ${e.message}")
                e.printStackTrace()
                Result.error(e)
            }
        }

    /**
     * Обновление расхода
     */
    suspend fun updateExpense(
        id: Long,
        amount: Double? = null,
        description: String? = null,
        // true — выставить description ровно как передано (null = очистить);
        // false — не трогать комментарий (обновление только суммы)
        updateDescription: Boolean = false,
    ): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val existing = expenseDao.getById(id) ?: return@withContext Result.error(Exception("Expense not found"))
                val now = Clock.System.now().toString()

                expenseDao.update(
                    id = id,
                    monthId = existing.monthId,
                    categoryId = existing.categoryId,
                    amount = amount?.toLong() ?: existing.amount,
                    description = if (updateDescription) description else existing.description,
                    date = existing.date,
                    updatedAt = now,
                    serverId = existing.serverId,
                    syncStatus = SyncStatus.PENDING.value,
                    isHidden = existing.isHidden,
                )

                // Добавляем операцию в очередь синхронизации
                syncManager.enqueueSync(
                    userId = existing.userId,
                    entityType = EntityType.EXPENSE.value,
                    entityId = id,
                    operation = SyncOperation.UPDATE.value,
                    payload = null,
                )

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Удаление расхода
     */
    suspend fun deleteExpense(id: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                // Сохраняем serverId до удаления записи
                val expense = expenseDao.getById(id)
                val serverId = expense?.serverId

                expenseDao.deleteById(id)

                // Добавляем операцию в очередь синхронизации с serverId в payload
                if (serverId != null) {
                    syncManager.enqueueSync(
                        userId = expense.userId,
                        entityType = EntityType.EXPENSE.value,
                        entityId = id,
                        operation = SyncOperation.DELETE.value,
                        payload = serverId,
                    )
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
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
                    val remoteExpenses = apiClient.getExpenses(serverMonthId)

                    // Предзагрузка категорий для резолва серверного categoryId → локальный
                    val allCategories = categoryDao.getAllByUser(userId)

                    // Получаем serverId записей, ожидающих удаления на сервере
                    val pendingDeleteServerIds = syncManager.getPendingDeleteServerIds(EntityType.EXPENSE.value)

                    for (remote in remoteExpenses) {
                        // Не восстанавливаем записи, которые удалены локально и ждут синхронизации
                        if (remote.id.toString() in pendingDeleteServerIds) {
                            continue
                        }

                        val existing = expenseDao.getByServerId(remote.id.toString())

                        if (
                            existing != null &&
                            (
                                existing.syncStatus != SyncStatus.SYNCED.value ||
                                    syncManager.hasActiveQueueOperation(EntityType.EXPENSE.value, existing.id)
                            )
                        ) {
                            continue
                        }

                        // remote.categoryId — серверный ID, находим локальный
                        val localCategory = allCategories
                            .find { it.serverId == remote.categoryId.toString() }

                        // Неизменённую запись пропускаем, только если она ссылается на верную
                        // категорию: раньше удалённая категория пересоздавалась под новым id,
                        // и расходы оставались «сиротами» — так они перепривязываются
                        // (если категория локально не найдена — ведём себя как раньше, не трогаем)
                        val linkedCorrectly = localCategory == null || existing?.categoryId == localCategory.id
                        if (existing != null && linkedCorrectly && !shouldApplyRemoteServerSnapshot(existing.updatedAt, remote.updatedAt)) {
                            continue
                        }
                        // Категории синхронизируются вместе с неактивными, так что сюда
                        // попадаем только при рассинхроне — такой расход пропускаем
                        if (localCategory == null) {
                            // Если запись уже была в локальной БД — удаляем её
                            if (existing != null) {
                                expenseDao.deleteById(existing.id)
                            }
                            continue
                        }
                        val localCategoryId = localCategory.id

                        if (existing != null) {
                            expenseDao.update(
                                id = existing.id,
                                monthId = monthId,
                                categoryId = localCategoryId,
                                amount = remote.amount.toLong(),
                                description = remote.comment,
                                date = remote.date,
                                updatedAt = remote.updatedAt ?: existing.updatedAt,
                                serverId = remote.id.toString(),
                                syncStatus = SyncStatus.SYNCED.value,
                                isHidden = existing.isHidden,
                            )
                        } else {
                            val remoteCreatedAt = remote.createdAt ?: remote.updatedAt ?: Clock.System.now().toString()
                            val remoteUpdatedAt = remote.updatedAt ?: remote.createdAt ?: remoteCreatedAt
                            expenseDao.insert(
                                userId = userId,
                                monthId = monthId,
                                categoryId = localCategoryId,
                                amount = remote.amount.toLong(),
                                description = remote.comment,
                                date = remote.date,
                                createdAt = remoteCreatedAt,
                                updatedAt = remoteUpdatedAt,
                                serverId = remote.id.toString(),
                                syncStatus = SyncStatus.SYNCED.value,
                                isHidden = 0L,
                            )
                        }
                    }

                    // Удаляем призрачные записи: локальные synced-записи, отсутствующие на сервере
                    val remoteServerIds = remoteExpenses.map { it.id.toString() }.toSet()
                    val allLocalExpenses = expenseDao.getByMonth(monthId)
                    val localSyncedExpenses = allLocalExpenses.filter {
                        it.serverId != null &&
                            it.syncStatus == SyncStatus.SYNCED.value &&
                            !syncManager.hasActiveQueueOperation(EntityType.EXPENSE.value, it.id)
                    }
                    for (local in localSyncedExpenses) {
                        if (local.serverId !in remoteServerIds) {
                            expenseDao.deleteById(local.id)
                        }
                    }
                    // Pending-расходы без serverId здесь не трогаем: даже если на сервере есть
                    // расход с той же категорией, суммой и комментарием, это может быть другая
                    // покупка. Повторную отправку уже доехавшего расхода закрывает X-Operation-Id.
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.error(e)
            }
        }
}
