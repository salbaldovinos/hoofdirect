package com.hoofdirect.app.feature.pricing.data

import com.hoofdirect.app.core.database.dao.ServicePriceDao
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.ServicePriceEntity
import com.hoofdirect.app.core.database.entity.ServiceTypes
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicePriceRepository @Inject constructor(
    private val servicePriceDao: ServicePriceDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler
) {
    fun getAllServices(userId: String): Flow<List<ServicePriceEntity>> {
        return servicePriceDao.getAllServicePrices(userId)
    }

    fun getActiveServices(userId: String): Flow<List<ServicePriceEntity>> {
        return servicePriceDao.getActiveServicePrices(userId)
    }

    suspend fun getServiceById(id: String): ServicePriceEntity? {
        return servicePriceDao.getById(id)
    }

    suspend fun createService(service: ServicePriceEntity): Result<ServicePriceEntity> {
        return try {
            val now = Instant.now()
            val maxOrder = servicePriceDao.getMaxDisplayOrder(service.userId) ?: -1

            val entityWithStatus = service.copy(
                displayOrder = maxOrder + 1,
                syncStatus = EntitySyncStatus.PENDING_CREATE.name,
                createdAt = now,
                updatedAt = now
            )

            servicePriceDao.insert(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "service_price",
                entityId = service.id,
                operation = SyncOperation.CREATE,
                payload = serializeService(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateService(service: ServicePriceEntity): Result<ServicePriceEntity> {
        return try {
            val entityWithStatus = service.copy(
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )

            servicePriceDao.update(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "service_price",
                entityId = service.id,
                operation = SyncOperation.UPDATE,
                payload = serializeService(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleActive(serviceId: String, isActive: Boolean): Result<Unit> {
        return try {
            servicePriceDao.updateActiveStatus(
                id = serviceId,
                isActive = isActive,
                updatedAt = Instant.now().toEpochMilli(),
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteService(serviceId: String): Result<Unit> {
        return try {
            val service = servicePriceDao.getById(serviceId)
            if (service?.isBuiltIn == true) {
                return Result.failure(Exception("Built-in services cannot be deleted"))
            }

            syncQueueManager.enqueue(
                entityType = "service_price",
                entityId = serviceId,
                operation = SyncOperation.DELETE,
                payload = """{"id":"$serviceId"}"""
            )

            servicePriceDao.delete(serviceId)
            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDisplayOrders(orders: List<Pair<String, Int>>) {
        val now = Instant.now().toEpochMilli()
        orders.forEach { (id, order) ->
            servicePriceDao.updateDisplayOrder(
                id = id,
                order = order,
                updatedAt = now,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )
        }
        syncWorkScheduler.triggerImmediateSync()
    }

    suspend fun createDefaultServicesIfNeeded(userId: String) {
        val count = servicePriceDao.getCountForUser(userId)
        if (count > 0) return

        val defaults = ServiceTypes.getDefaults()
        val now = Instant.now()

        val services = defaults.mapIndexed { index, default ->
            ServicePriceEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                serviceType = default.type,
                name = default.name,
                price = default.price,
                durationMinutes = default.durationMinutes,
                isBuiltIn = true,
                displayOrder = index,
                createdAt = now,
                updatedAt = now,
                syncStatus = EntitySyncStatus.PENDING_CREATE.name
            )
        }

        servicePriceDao.insertAll(services)
        syncWorkScheduler.triggerImmediateSync()
    }

    private fun serializeService(service: ServicePriceEntity): String {
        return """
            {
                "id": "${service.id}",
                "user_id": "${service.userId}",
                "service_type": "${service.serviceType}",
                "name": "${service.name}",
                "description": ${service.description?.let { "\"$it\"" } ?: "null"},
                "price": "${service.price}",
                "duration_minutes": ${service.durationMinutes},
                "is_active": ${service.isActive},
                "is_built_in": ${service.isBuiltIn},
                "display_order": ${service.displayOrder}
            }
        """.trimIndent()
    }
}
