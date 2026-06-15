package ru.homebudget.finkeeper.data.repository.month

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.homebudget.finkeeper.data.local.dao.MonthDao
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.Result
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.model.Month as RemoteMonth

/**
 * Модель месяца с разделением локального и серверного ID
 */
data class MonthData(
    val localId: Long,
    val serverId: Int?,
    val userId: Long,
    val year: Int,
    val month: Int,
)

/**
 * Репозиторий для работы с месяцами
 * Реализует offline-first логику
 */
class MonthRepository(
    private val monthDao: MonthDao,
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : KoinComponent {
    private val currentUserId: Long get() = tokenStorage.userId
    private val syncManager: SyncManager by lazy { get() }

    /**
     * Получение или создание месяца
     */
    suspend fun getOrCreateMonth(
        userId: Long,
        year: Int,
        month: Int,
    ): Result<MonthData> =
        withContext(Dispatchers.Default) {
            try {
                val localMonth = monthDao.getByUserAndYearMonth(userId, year.toLong(), month.toLong())

                if (localMonth != null) {
                    // Месяц уже есть локально, обновляем serverId если нужно
                    if (localMonth.serverId == null) {
                        try {
                            val remoteMonth = apiClient.ensureMonth(year, month)
                            monthDao.updateServerId(localMonth.id, remoteMonth.id.toString())
                        } catch (e: Exception) {
                            // Игнорируем ошибку синхронизации месяца
                        }
                    }

                    // Получаем обновленный месяц
                    val updatedMonth = monthDao.getById(localMonth.id)!!

                    Result.success(
                        MonthData(
                            localId = updatedMonth.id,
                            serverId = updatedMonth.serverId?.toIntOrNull(),
                            userId = updatedMonth.userId,
                            year = updatedMonth.year.toInt(),
                            month = updatedMonth.month.toInt(),
                        ),
                    )
                } else {
                    // Создаём месяц локально
                    val newId =
                        monthDao.insert(
                            userId = userId,
                            year = year.toLong(),
                            month = month.toLong(),
                        )

                    // Синхронизируем с сервером
                    try {
                        val remoteMonth = apiClient.ensureMonth(year, month)
                        monthDao.updateServerId(newId, remoteMonth.id.toString())
                    } catch (e: Exception) {
                        // Если не удалось синхронизировать, оставляем serverId как null
                    }

                    // Получаем созданный месяц
                    val createdMonth = monthDao.getById(newId)!!

                    Result.success(
                        MonthData(
                            localId = createdMonth.id,
                            serverId = createdMonth.serverId?.toIntOrNull(),
                            userId = createdMonth.userId,
                            year = createdMonth.year.toInt(),
                            month = createdMonth.month.toInt(),
                        ),
                    )
                }
            } catch (e: Exception) {
                Result.error(e)
            }
        }

    /**
     * Получение всех месяцев пользователя
     */
    suspend fun getAllMonths(userId: Long): Result<List<RemoteMonth>> =
        withContext(Dispatchers.Default) {
            try {
                val localMonths = monthDao.getAllByUser(userId)

                val result =
                    localMonths.map { local ->
                        RemoteMonth(
                            id = local.id.toInt(),
                            userId = local.userId.toInt(),
                            year = local.year.toInt(),
                            month = local.month.toInt(),
                        )
                    }

                Result.success(result)
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

    suspend fun syncWithServer(userId: Long): Result<Unit> =
        syncPullMutex.withLock { syncWithServerInternal(userId) }

    private suspend fun syncWithServerInternal(userId: Long): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                Result.success(Unit)
            } catch (e: Exception) {
                Result.error(e)
            }
        }
}
