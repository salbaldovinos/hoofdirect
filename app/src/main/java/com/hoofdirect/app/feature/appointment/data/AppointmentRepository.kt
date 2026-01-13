package com.hoofdirect.app.feature.appointment.data

import com.hoofdirect.app.core.database.dao.AppointmentDao
import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.AppointmentHorseEntity
import com.hoofdirect.app.core.database.entity.AppointmentStatus
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentRepository @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val horseDao: HorseDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler
) {
    fun getAppointmentsForDate(userId: String, date: LocalDate): Flow<List<AppointmentEntity>> {
        return appointmentDao.getAppointmentsForDate(userId, date)
    }

    fun getAppointmentsForDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<AppointmentEntity>> {
        return appointmentDao.getAppointmentsForDateRange(userId, startDate, endDate)
    }

    fun getAppointmentsForClient(clientId: String): Flow<List<AppointmentEntity>> {
        return appointmentDao.getAppointmentsForClient(clientId)
    }

    fun getUpcomingAppointmentsForClient(clientId: String, limit: Int = 5): Flow<List<AppointmentEntity>> {
        return appointmentDao.getUpcomingAppointmentsForClient(clientId, LocalDate.now(), limit)
    }

    fun getAppointmentById(id: String): Flow<AppointmentEntity?> {
        return appointmentDao.getAppointmentById(id)
    }

    suspend fun getAppointmentByIdOnce(id: String): AppointmentEntity? {
        return appointmentDao.getAppointmentByIdOnce(id)
    }

    fun getUpcomingAppointments(userId: String): Flow<List<AppointmentEntity>> {
        return appointmentDao.getUpcomingAppointments(userId, LocalDate.now())
    }

    fun getHorsesForAppointment(appointmentId: String): Flow<List<AppointmentHorseEntity>> {
        return appointmentDao.getHorsesForAppointment(appointmentId)
    }

    suspend fun createAppointment(
        appointment: AppointmentEntity,
        horses: List<AppointmentHorseEntity>
    ): Result<AppointmentEntity> {
        return try {
            val entityWithStatus = appointment.copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE.name,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            appointmentDao.insert(entityWithStatus)
            appointmentDao.insertAppointmentHorses(horses)

            syncQueueManager.enqueue(
                entityType = "appointment",
                entityId = appointment.id,
                operation = SyncOperation.CREATE,
                payload = serializeAppointment(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAppointment(
        appointment: AppointmentEntity,
        horses: List<AppointmentHorseEntity>? = null
    ): Result<AppointmentEntity> {
        return try {
            val entityWithStatus = appointment.copy(
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )

            appointmentDao.update(entityWithStatus)

            if (horses != null) {
                appointmentDao.deleteAppointmentHorses(appointment.id)
                appointmentDao.insertAppointmentHorses(horses)
            }

            syncQueueManager.enqueue(
                entityType = "appointment",
                entityId = appointment.id,
                operation = SyncOperation.UPDATE,
                payload = serializeAppointment(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStatus(appointmentId: String, status: AppointmentStatus): Result<Unit> {
        return try {
            appointmentDao.updateStatus(
                id = appointmentId,
                status = status.name,
                updatedAt = Instant.now().toEpochMilli(),
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeAppointment(appointmentId: String, defaultCycleWeeks: Int): Result<Unit> {
        return try {
            val now = Instant.now()
            appointmentDao.markCompleted(
                id = appointmentId,
                completedAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli(),
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            // Update horse service dates
            val appointmentHorses = appointmentDao.getHorsesForAppointmentOnce(appointmentId)
            appointmentHorses.forEach { ah ->
                val horse = horseDao.getHorseByIdOnce(ah.horseId)
                if (horse != null) {
                    val cycleWeeks = horse.shoeingCycleWeeks ?: defaultCycleWeeks
                    val today = LocalDate.now()
                    horseDao.updateServiceDates(
                        id = horse.id,
                        lastServiceDate = today,
                        nextDueDate = today.plusWeeks(cycleWeeks.toLong()),
                        updatedAt = now.toEpochMilli(),
                        syncStatus = EntitySyncStatus.PENDING_UPDATE.name
                    )
                }
            }

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelAppointment(appointmentId: String, reason: String?): Result<Unit> {
        return try {
            val appointment = appointmentDao.getAppointmentByIdOnce(appointmentId)
                ?: return Result.failure(Exception("Appointment not found"))

            val updated = appointment.copy(
                status = AppointmentStatus.CANCELLED.name,
                cancelledAt = Instant.now(),
                cancellationReason = reason,
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )

            appointmentDao.update(updated)
            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAppointment(appointmentId: String): Result<Unit> {
        return try {
            syncQueueManager.enqueue(
                entityType = "appointment",
                entityId = appointmentId,
                operation = SyncOperation.DELETE,
                payload = """{"id":"$appointmentId"}"""
            )

            appointmentDao.delete(appointmentId)
            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeAppointment(appointment: AppointmentEntity): String {
        return """
            {
                "id": "${appointment.id}",
                "user_id": "${appointment.userId}",
                "client_id": "${appointment.clientId}",
                "date": "${appointment.date}",
                "start_time": "${appointment.startTime}",
                "status": "${appointment.status}",
                "total_price": "${appointment.totalPrice}"
            }
        """.trimIndent()
    }
}
