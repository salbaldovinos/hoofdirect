package com.hoofdirect.app.feature.client.data

import com.hoofdirect.app.core.database.dao.ClientDao
import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.network.NetworkMonitor
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val clientDao: ClientDao,
    private val horseDao: HorseDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val networkMonitor: NetworkMonitor,
    private val json: Json
) {
    fun getClients(userId: String, isActive: Boolean = true): Flow<List<ClientEntity>> {
        return clientDao.getClients(userId, isActive)
    }

    fun searchClients(
        userId: String,
        query: String,
        isActive: Boolean = true,
        sortBy: String = "name"
    ): Flow<List<ClientEntity>> {
        return clientDao.searchClients(userId, query, isActive, sortBy)
    }

    fun getClientById(id: String): Flow<ClientEntity?> {
        return clientDao.getClientById(id)
    }

    suspend fun getClientByIdOnce(id: String): ClientEntity? {
        return clientDao.getClientByIdOnce(id)
    }

    suspend fun createClient(client: ClientEntity): Result<ClientEntity> {
        return try {
            val entityWithStatus = client.copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE.name,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            clientDao.insert(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "client",
                entityId = client.id,
                operation = SyncOperation.CREATE,
                payload = serializeClient(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateClient(client: ClientEntity): Result<ClientEntity> {
        return try {
            val entityWithStatus = client.copy(
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )
            clientDao.update(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "client",
                entityId = client.id,
                operation = SyncOperation.UPDATE,
                payload = serializeClient(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveClient(clientId: String): Result<Unit> {
        return try {
            val now = Instant.now().toEpochMilli()
            clientDao.updateActiveStatus(
                clientId,
                isActive = false,
                updatedAt = now,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            // Also archive all horses
            horseDao.updateActiveStatusForClient(
                clientId,
                isActive = false,
                updatedAt = now,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreClient(clientId: String): Result<Unit> {
        return try {
            val now = Instant.now().toEpochMilli()
            clientDao.updateActiveStatus(
                clientId,
                isActive = true,
                updatedAt = now,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            horseDao.updateActiveStatusForClient(
                clientId,
                isActive = true,
                updatedAt = now,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteClient(clientId: String): Result<Unit> {
        return try {
            // Soft delete - mark for deletion
            clientDao.updateSyncStatus(
                clientId,
                EntitySyncStatus.PENDING_DELETE.name,
                Instant.now().toEpochMilli()
            )

            syncQueueManager.enqueue(
                entityType = "client",
                entityId = clientId,
                operation = SyncOperation.DELETE,
                payload = """{"id":"$clientId"}"""
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeClient(client: ClientEntity): String {
        // Simple JSON serialization
        return """
            {
                "id": "${client.id}",
                "user_id": "${client.userId}",
                "name": "${client.name}",
                "first_name": "${client.firstName}",
                "last_name": ${client.lastName?.let { "\"$it\"" } ?: "null"},
                "business_name": ${client.businessName?.let { "\"$it\"" } ?: "null"},
                "phone": "${client.phone}",
                "email": ${client.email?.let { "\"$it\"" } ?: "null"},
                "address": ${client.address?.let { "\"$it\"" } ?: "null"},
                "city": ${client.city?.let { "\"$it\"" } ?: "null"},
                "is_active": ${client.isActive}
            }
        """.trimIndent()
    }
}
