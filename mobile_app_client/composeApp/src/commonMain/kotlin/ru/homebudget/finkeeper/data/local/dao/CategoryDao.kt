package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Categories

/**
 * Data Access Object for Category operations.
 * Provides type-safe SQL queries for the categories table.
 */
class CategoryDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Insert a new category
     */
    fun insert(
        userId: Long,
        name: String,
        type: String,
        icon: String?,
        color: String?,
        sortOrder: Long,
        isActive: Long,
        isFixed: Long = 0L,
        fixedAmount: Long? = null,
        autoDay: Long? = null,
        requireConfirm: Long = 0L,
        createdAt: String,
        updatedAt: String,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        queries.insertCategory(
            user_id = userId,
            name = name,
            type = type,
            icon = icon,
            color = color,
            sort_order = sortOrder,
            is_active = isActive,
            is_fixed = isFixed,
            fixed_amount = fixedAmount,
            auto_day = autoDay,
            require_confirm = requireConfirm,
            created_at = createdAt,
            updated_at = updatedAt,
            server_id = serverId,
            sync_status = syncStatus
        )
    }

    /**
     * Insert a category and return its generated ID
     */
    fun insertAndReturn(
        userId: Long,
        name: String,
        type: String,
        icon: String?,
        color: String?,
        sortOrder: Long,
        isActive: Long,
        isFixed: Long = 0L,
        fixedAmount: Long? = null,
        autoDay: Long? = null,
        requireConfirm: Long = 0L,
        createdAt: String,
        updatedAt: String,
        serverId: String? = null,
        syncStatus: String = "synced"
    ): Long {
        return database.transactionWithResult {
            queries.insertCategory(
                user_id = userId,
                name = name,
                type = type,
                icon = icon,
                color = color,
                sort_order = sortOrder,
                is_active = isActive,
                is_fixed = isFixed,
                fixed_amount = fixedAmount,
                auto_day = autoDay,
                require_confirm = requireConfirm,
                created_at = createdAt,
                updated_at = updatedAt,
                server_id = serverId,
                sync_status = syncStatus
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    /**
     * Update an existing category by local ID
     */
    fun update(
        id: Long,
        name: String,
        type: String,
        icon: String?,
        color: String?,
        sortOrder: Long,
        isActive: Long,
        isFixed: Long = 0L,
        fixedAmount: Long? = null,
        autoDay: Long? = null,
        requireConfirm: Long = 0L,
        updatedAt: String,
        serverId: String? = null,
        syncStatus: String = "synced"
    ) {
        queries.updateCategoryById(
            name = name,
            type = type,
            icon = icon,
            color = color,
            sort_order = sortOrder,
            is_active = isActive,
            is_fixed = isFixed,
            fixed_amount = fixedAmount,
            auto_day = autoDay,
            require_confirm = requireConfirm,
            updated_at = updatedAt,
            server_id = serverId,
            sync_status = syncStatus,
            id = id
        )
    }

    /**
     * Update sync status by local ID
     */
    fun updateSyncStatus(id: Long, syncStatus: String, serverId: String? = null) {
        queries.updateCategorySyncStatus(
            sync_status = syncStatus,
            server_id = serverId,
            id = id
        )
    }

    /**
     * Delete a category by local ID
     */
    fun deleteById(id: Long) {
        queries.deleteCategoryById(id)
    }

    /**
     * Delete all categories for a user
     */
    fun deleteAllByUser(userId: Long) {
        queries.deleteAllCategoriesByUser(userId)
    }

    /**
     * Get a category by local ID
     */
    fun getById(id: Long): Category? {
        return queries.getCategoryById(id).executeAsOneOrNull()?.let { toCategory(it) }
    }

    /**
     * Get all categories for a user
     */
    fun getAllByUser(userId: Long): List<Category> {
        return queries.getAllCategoriesByUser(userId).executeAsList().map { toCategory(it) }
    }

    /**
     * Get all active categories for a user
     */
    fun getActiveByUser(userId: Long): List<Category> {
        return queries.getActiveCategoriesByUser(userId).executeAsList().map { toCategory(it) }
    }

    /**
     * Get expense categories for a user
     */
    fun getExpenseCategories(userId: Long): List<Category> {
        return queries.getCategoriesByType(userId, "expense").executeAsList().map { toCategory(it) }
    }

    /**
     * Get income categories for a user
     */
    fun getIncomeCategories(userId: Long): List<Category> {
        return queries.getCategoriesByType(userId, "income").executeAsList().map { toCategory(it) }
    }

    /**
     * Get categories pending sync (not synced with server)
     */
    fun getPendingSync(): List<Category> {
        return queries.getCategoriesPendingSync().executeAsList().map { toCategory(it) }
    }

    /**
     * Get count of categories pending sync
     */
    fun getPendingSyncCount(): Long {
        return queries.getCategoriesPendingSyncCount().executeAsOne()
    }

    /** Порядок списка: sort_order = позиция в [orderedIds] */
    fun applySortOrder(orderedIds: List<Long>) {
        queries.transaction {
            orderedIds.forEachIndexed { index, id -> queries.updateCategorySortOrder(sort_order = index.toLong(), id = id) }
        }
    }

    /**
     * Get max sort order for a user's categories
     */
    fun getMaxSortOrder(userId: Long): Long? {
        return queries.getMaxCategorySortOrder(userId).executeAsOneOrNull()
    }

    /**
     * Check if category exists by local ID
     */
    fun existsById(id: Long): Boolean {
        return queries.categoryExistsById(id).executeAsOne()
    }

    /**
     * Check if category exists by server ID
     */
    fun existsByServerId(serverId: String): Boolean {
        return queries.categoryExistsByServerId(serverId).executeAsOne()
    }

    /**
     * Get category by server ID
     */
    fun getByServerId(serverId: String): Category? {
        return queries.getCategoryByServerId(serverId).executeAsOneOrNull()?.let { toCategory(it) }
    }

    /**
     * Get category by user ID and name
     */
    fun getByName(userId: Long, name: String): Category? {
        // executeAsList().firstOrNull(), а не executeAsOneOrNull(): у пострадавших от бага дублей
        // юзеров локально может быть >1 строки с одинаковым (user_id, name) — executeAsOneOrNull
        // в этом случае бросает исключение. Дедуп таких строк делает runAdditiveMigrations.
        return queries.getCategoryByName(userId, name).executeAsList().firstOrNull()?.let { toCategory(it) }
    }

    /**
     * Получение фиксированных категорий пользователя
     */
    fun getFixedByUser(userId: Long): List<Category> {
        return queries.getFixedCategoriesByUser(userId).executeAsList().map { toCategory(it) }
    }

    /**
     * Delete all categories (for testing or full sync reset)
     */
    fun deleteAll() {
        queries.deleteAllCategories()
    }

    private fun toCategory(entity: Categories): Category {
        return Category(
            id = entity.id,
            userId = entity.user_id,
            name = entity.name,
            type = entity.type,
            icon = entity.icon,
            color = entity.color,
            sortOrder = entity.sort_order,
            isActive = entity.is_active,
            isFixed = entity.is_fixed,
            fixedAmount = entity.fixed_amount,
            autoDay = entity.auto_day,
            requireConfirm = entity.require_confirm,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at,
            serverId = entity.server_id,
            syncStatus = entity.sync_status
        )
    }
}

/**
 * Модель категории
 */
data class Category(
    val id: Long,
    val userId: Long,
    val name: String,
    val type: String,
    val icon: String?,
    val color: String?,
    val sortOrder: Long,
    val isActive: Long,
    val isFixed: Long = 0L,
    val fixedAmount: Long? = null,
    val autoDay: Long? = null,
    val requireConfirm: Long = 0L,
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)
