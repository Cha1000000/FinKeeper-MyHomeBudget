package ru.homebudget.finkeeper.data.local.model

/**
 * Синхронизация статусов для локальных сущностей
 */
enum class SyncStatus(val value: String) {
    SYNCED("synced"),
    PENDING("pending"),
    FAILED("failed");

    companion object {
        fun fromValue(value: String): SyncStatus {
            return values().find { it.value == value } ?: SYNCED
        }
    }
}

/**
 * Типы операций для очереди синхронизации
 */
enum class SyncOperation(val value: String) {
    INSERT("insert"),
    UPDATE("update"),
    DELETE("delete");

    companion object {
        fun fromValue(value: String): SyncOperation {
            return values().find { it.value == value } ?: INSERT
        }
    }
}

/**
 * Статусы элементов очереди синхронизации
 */
enum class SyncQueueStatus(val value: String) {
    PENDING("pending"),
    SYNCING("syncing"),
    FAILED("failed"),
    COMPLETED("completed");

    companion object {
        fun fromValue(value: String): SyncQueueStatus {
            return values().find { it.value == value } ?: PENDING
        }
    }
}

/**
 * Типы сущностей для синхронизации
 */
enum class EntityType(val value: String) {
    USER("user"),
    CATEGORY("category"),
    INCOME_SOURCE("income_source"),
    MONTH("month"),
    INCOME("income"),
    EXPENSE("expense"),
    BUDGET("budget"),
    SAVINGS_GOAL("savings_goal"),
    SAVINGS_TRANSACTION("savings_transaction");

    companion object {
        fun fromValue(value: String): EntityType {
            return values().find { it.value == value } ?: CATEGORY
        }
    }
}
