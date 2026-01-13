package com.hoofdirect.app.feature.horse.data

import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HorseRepository @Inject constructor(
    private val horseDao: HorseDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler
) {
    fun getHorsesForClient(clientId: String): Flow<List<HorseEntity>> {
        return horseDao.getHorsesForClient(clientId)
    }

    fun getAllHorses(userId: String): Flow<List<HorseEntity>> {
        return horseDao.getAllHorses(userId)
    }

    fun getHorseById(id: String): Flow<HorseEntity?> {
        return horseDao.getHorseById(id)
    }

    suspend fun getHorseByIdOnce(id: String): HorseEntity? {
        return horseDao.getHorseByIdOnce(id)
    }

    fun getDueSoonHorses(userId: String, days: Int = 7): Flow<List<HorseEntity>> {
        val untilDate = LocalDate.now().plusDays(days.toLong())
        return horseDao.getDueSoonHorses(userId, untilDate)
    }

    fun getOverdueHorses(userId: String): Flow<List<HorseEntity>> {
        return horseDao.getOverdueHorses(userId, LocalDate.now())
    }

    suspend fun createHorse(horse: HorseEntity): Result<HorseEntity> {
        return try {
            val entityWithStatus = horse.copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE.name,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            horseDao.insert(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "horse",
                entityId = horse.id,
                operation = SyncOperation.CREATE,
                payload = serializeHorse(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateHorse(horse: HorseEntity): Result<HorseEntity> {
        return try {
            val entityWithStatus = horse.copy(
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )
            horseDao.update(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "horse",
                entityId = horse.id,
                operation = SyncOperation.UPDATE,
                payload = serializeHorse(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateServiceDates(
        horseId: String,
        lastServiceDate: LocalDate,
        cycleWeeks: Int
    ): Result<Unit> {
        return try {
            val nextDueDate = lastServiceDate.plusWeeks(cycleWeeks.toLong())
            horseDao.updateServiceDates(
                id = horseId,
                lastServiceDate = lastServiceDate,
                nextDueDate = nextDueDate,
                updatedAt = Instant.now().toEpochMilli(),
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveHorse(horseId: String): Result<Unit> {
        return try {
            val horse = horseDao.getHorseByIdOnce(horseId) ?: return Result.failure(Exception("Horse not found"))
            val updated = horse.copy(
                isActive = false,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )
            horseDao.update(updated)

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHorse(horseId: String): Result<Unit> {
        return try {
            syncQueueManager.enqueue(
                entityType = "horse",
                entityId = horseId,
                operation = SyncOperation.DELETE,
                payload = """{"id":"$horseId"}"""
            )

            horseDao.delete(horseId)
            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeHorse(horse: HorseEntity): String {
        return """
            {
                "id": "${horse.id}",
                "user_id": "${horse.userId}",
                "client_id": "${horse.clientId}",
                "name": "${horse.name}",
                "breed": ${horse.breed?.let { "\"$it\"" } ?: "null"},
                "color": ${horse.color?.let { "\"$it\"" } ?: "null"},
                "age": ${horse.age ?: "null"},
                "temperament": ${horse.temperament?.let { "\"$it\"" } ?: "null"},
                "is_active": ${horse.isActive}
            }
        """.trimIndent()
    }
}
