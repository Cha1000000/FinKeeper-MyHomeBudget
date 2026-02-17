package ru.homebudget.finkeeper.data.local.dao

import ru.homebudget.finkeeper.data.local.database.FinKeeperDatabase
import ru.homebudget.finkeeper.data.local.database.Users

/**
 * Data Access Object для операций с пользователями
 */
class UserDao(
    private val database: FinKeeperDatabase
) {
    private val queries = database.finKeeperDatabaseQueries

    /**
     * Вставка нового пользователя
     */
    fun insert(username: String, email: String, passwordHash: String): Long {
        return database.transactionWithResult {
            queries.insertUser(
                username = username,
                email = email,
                password_hash = passwordHash
            )
            queries.lastInsertRowId().executeAsOne()
        }
    }

    /**
     * Обновление пароля пользователя
     */
    fun updatePassword(id: Long, passwordHash: String) {
        queries.updateUserPassword(
            password_hash = passwordHash,
            id = id
        )
    }

    /**
     * Удаление пользователя
     */
    fun deleteById(id: Long) {
        queries.deleteUser(id)
    }

    /**
     * Получение пользователя по ID
     */
    fun getById(id: Long): User? {
        return queries.getUserById(id).executeAsOneOrNull()?.let { toUser(it) }
    }

    /**
     * Получение пользователя по имени
     */
    fun getByUsername(username: String): User? {
        return queries.getUserByUsername(username).executeAsOneOrNull()?.let { toUser(it) }
    }

    /**
     * Получение пользователя по email
     */
    fun getByEmail(email: String): User? {
        return queries.getUserByEmail(email).executeAsOneOrNull()?.let { toUser(it) }
    }

    private fun toUser(entity: Users): User {
        return User(
            id = entity.id,
            username = entity.username,
            email = entity.email,
            passwordHash = entity.password_hash,
            createdAt = entity.created_at,
            updatedAt = entity.updated_at
        )
    }
}

/**
 * Модель пользователя
 */
data class User(
    val id: Long,
    val username: String,
    val email: String,
    val passwordHash: String,
    val createdAt: String,
    val updatedAt: String
)
