package ru.homebudget.finkeeper.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.homebudget.finkeeper.data.local.model.SyncOperation

class SyncQueuePayloadMetadataTest {

    @Test
    fun encodeDecodeSyncQueuePayloadMetadata_roundTrip_preservesFields() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-123",
                    entityUpdatedAt = "2026-03-10T09:00:00Z",
                    deleteServerId = "55",
                ),
            )

        val decoded = decodeSyncQueuePayloadMetadata(payload)

        assertNotNull(decoded)
        assertEquals("op-123", decoded.opId)
        assertEquals("2026-03-10T09:00:00Z", decoded.entityUpdatedAt)
        assertEquals("55", decoded.deleteServerId)
    }

    @Test
    fun decodeSyncQueuePayloadMetadata_plainDeletePayload_keepsBackwardCompatibility() {
        val decoded = decodeSyncQueuePayloadMetadata("777")

        assertNotNull(decoded)
        assertEquals(null, decoded.opId)
        assertEquals(null, decoded.entityUpdatedAt)
        assertEquals("777", decoded.deleteServerId)
    }

    @Test
    fun extractDeleteServerId_structuredPayload_returnsDeleteServerId() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-delete",
                    deleteServerId = "999",
                ),
            )

        assertEquals("999", extractDeleteServerId(payload))
    }

    @Test
    fun shouldSkipOutdatedQueueItem_whenEntityUpdatedAtChanged_returnsTrue() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-1",
                    entityUpdatedAt = "2026-03-10T09:00:00Z",
                ),
            )

        assertTrue(
            shouldSkipOutdatedQueueItem(
                operation = SyncOperation.UPDATE.value,
                payload = payload,
                currentUpdatedAt = "2026-03-10T09:05:00Z",
            ),
        )
    }

    @Test
    fun shouldSkipOutdatedQueueItem_whenEntityUpdatedAtSame_returnsFalse() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-2",
                    entityUpdatedAt = "2026-03-10T09:00:00Z",
                ),
            )

        assertFalse(
            shouldSkipOutdatedQueueItem(
                operation = SyncOperation.UPDATE.value,
                payload = payload,
                currentUpdatedAt = "2026-03-10T09:00:00Z",
            ),
        )
    }

    @Test
    fun shouldSkipOutdatedQueueItem_forDelete_neverSkips() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-delete",
                    entityUpdatedAt = "2026-03-10T09:00:00Z",
                    deleteServerId = "42",
                ),
            )

        assertFalse(
            shouldSkipOutdatedQueueItem(
                operation = SyncOperation.DELETE.value,
                payload = payload,
                currentUpdatedAt = "2026-03-10T09:05:00Z",
            ),
        )
    }

    @Test
    fun shouldPreserveDirtyStateAfterSuccessfulSync_whenEntityUpdatedAtChanged_returnsTrue() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-3",
                    entityUpdatedAt = "2026-03-10T09:00:00Z",
                ),
            )

        assertTrue(
            shouldPreserveDirtyStateAfterSuccessfulSync(
                operation = SyncOperation.UPDATE.value,
                payload = payload,
                currentUpdatedAt = "2026-03-10T09:10:00Z",
            ),
        )
    }

    @Test
    fun shouldPreserveDirtyStateAfterSuccessfulSync_whenEntityUpdatedAtSame_returnsFalse() {
        val payload =
            encodeSyncQueuePayloadMetadata(
                SyncQueuePayloadMetadata(
                    opId = "op-4",
                    entityUpdatedAt = "2026-03-10T09:00:00Z",
                ),
            )

        assertFalse(
            shouldPreserveDirtyStateAfterSuccessfulSync(
                operation = SyncOperation.UPDATE.value,
                payload = payload,
                currentUpdatedAt = "2026-03-10T09:00:00Z",
            ),
        )
    }
}
