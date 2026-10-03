package ru.homebudget.finkeeper.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.homebudget.finkeeper.data.local.model.SyncOperation

class SyncQueueMergePolicyTest {

    @Test
    fun resolveQueueMergeAction_insertThenUpdate_keepsInsert() {
        val action =
            resolveQueueMergeAction(
                existingOperation = SyncOperation.INSERT.value,
                newOperation = SyncOperation.UPDATE.value,
            )

        assertEquals(QueueMergeAction.KEEP_EXISTING, action)
    }

    @Test
    fun resolveQueueMergeAction_insertThenDelete_dropsBoth() {
        val action =
            resolveQueueMergeAction(
                existingOperation = SyncOperation.INSERT.value,
                newOperation = SyncOperation.DELETE.value,
            )

        assertEquals(QueueMergeAction.DROP_BOTH, action)
    }

    @Test
    fun resolveQueueMergeAction_updateThenUpdate_replacesWithLatest() {
        val action =
            resolveQueueMergeAction(
                existingOperation = SyncOperation.UPDATE.value,
                newOperation = SyncOperation.UPDATE.value,
            )

        assertEquals(QueueMergeAction.REPLACE_WITH_NEW, action)
    }

    @Test
    fun resolveQueueMergeAction_noExisting_replacesWithNew() {
        val action =
            resolveQueueMergeAction(
                existingOperation = null,
                newOperation = SyncOperation.INSERT.value,
            )

        assertEquals(QueueMergeAction.REPLACE_WITH_NEW, action)
    }

    @Test
    fun resolveQueueMergeAction_deleteThenUpdate_keepsDelete() {
        // Удаление побеждает правку: запись уже удалена
        assertEquals(
            QueueMergeAction.KEEP_EXISTING,
            resolveQueueMergeAction(SyncOperation.DELETE.value, SyncOperation.UPDATE.value),
        )
    }

    @Test
    fun resolveQueueMergeAction_updateThenDelete_replacesWithDelete() {
        assertEquals(
            QueueMergeAction.REPLACE_WITH_NEW,
            resolveQueueMergeAction(SyncOperation.UPDATE.value, SyncOperation.DELETE.value),
        )
    }
}
