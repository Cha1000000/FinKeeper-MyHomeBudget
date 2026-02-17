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

    /**
     * Update only sort_order for a category
     */
    fun updateSortOrder(id: Long, sortOrder: Long) {
        queries.updateCategorySortOrder(sort_order = sortOrder, id = id)
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
    val createdAt: String,
    val updatedAt: String,
    val serverId: String?,
    val syncStatus: String
)
