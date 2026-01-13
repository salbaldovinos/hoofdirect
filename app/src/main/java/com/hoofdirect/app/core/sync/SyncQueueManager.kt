package com.hoofdirect.app.core.sync

import com.hoofdirect.app.core.database.dao.SyncQueueDao
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.database.entity.SyncQueueEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncQueueManager @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val json: Json
) {
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: SyncOperation,
        payload: String
    ) {
        val existing = syncQueueDao.getLatestForEntity(entityType, entityId)

        when {
            // CREATE followed by DELETE = remove both
            existing?.operation == "CREATE" && operation == SyncOperation.DELETE -> {
                syncQueueDao.delete(existing.id)
                return
            }

            // CREATE followed by UPDATE = update the CREATE payload
            existing?.operation == "CREATE" && operation == SyncOperation.UPDATE -> {
                syncQueueDao.insert(
                    existing.copy(
                        payload = payload,
                        updatedAt = Instant.now()
                    )
                )
                return
            }

            // UPDATE followed by UPDATE = keep only latest
            existing?.operation == "UPDATE" && operation == SyncOperation.UPDATE -> {
                val newId = syncQueueDao.insert(
                    SyncQueueEntity(
                        entityType = entityType,
                        entityId = entityId,
                        operation = operation.name,
                        payload = payload,
                        priority = operation.priority
                    )
                )
                syncQueueDao.coalesceUpdates(entityType, entityId, newId)
                return
            }
        }

        // Default: insert new operation
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = entityType,
                entityId = entityId,
                operation = operation.name,
                payload = payload,
                priority = operation.priority
            )
        )
    }

    suspend fun getPendingCount(): Int = syncQueueDao.getPendingCount()

    fun observePendingCount() = syncQueueDao.observePendingCount()

    suspend fun getPendingOperations(limit: Int = 50) = syncQueueDao.getPendingOperations(limit)

    suspend fun markCompleted(id: Long) {
        syncQueueDao.updateStatus(id, "COMPLETED")
    }

    suspend fun markFailed(id: Long, error: String) {
        syncQueueDao.markFailed(id, error)
    }

    suspend fun deleteCompleted() {
        val sevenDaysAgo = Instant.now().minusSeconds(7 * 24 * 60 * 60)
        syncQueueDao.deleteCompletedBefore(sevenDaysAgo)
    }
}
