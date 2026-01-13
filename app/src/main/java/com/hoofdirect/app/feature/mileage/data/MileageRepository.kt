package com.hoofdirect.app.feature.mileage.data

import com.hoofdirect.app.core.database.dao.MileageLogDao
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.MileageLogEntity
import com.hoofdirect.app.core.database.entity.MileagePurpose
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

data class MileageSummary(
    val totalMiles: Double,
    val tripCount: Int,
    val estimatedDeduction: Double,
    val byPurpose: Map<MileagePurpose, Double>
)

@Singleton
class MileageRepository @Inject constructor(
    private val mileageLogDao: MileageLogDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler
) {
    companion object {
        // IRS standard mileage rates
        const val IRS_RATE_2024 = 0.67
        const val IRS_RATE_2025 = 0.70
        const val IRS_RATE_2026 = 0.70 // Placeholder until announced

        fun getIrsRate(year: Int): Double = when (year) {
            2024 -> IRS_RATE_2024
            2025 -> IRS_RATE_2025
            else -> IRS_RATE_2026
        }
    }

    fun getTripsForDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<MileageLogEntity>> {
        return mileageLogDao.getTripsInRange(userId, startDate, endDate)
    }

    suspend fun getTripById(id: String): MileageLogEntity? {
        return mileageLogDao.getById(id)
    }

    suspend fun getAnnualSummary(userId: String, year: Int): MileageSummary {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        val totalMiles = mileageLogDao.getTotalMilesForRange(userId, startDate, endDate)
        val tripCount = mileageLogDao.getTripCountForRange(userId, startDate, endDate)

        val byPurpose = MileagePurpose.entries.associateWith { purpose ->
            mileageLogDao.getTotalMilesForPurpose(userId, purpose.name, startDate, endDate)
        }

        val irsRate = getIrsRate(year)
        val estimatedDeduction = totalMiles * irsRate

        return MileageSummary(
            totalMiles = totalMiles,
            tripCount = tripCount,
            estimatedDeduction = estimatedDeduction,
            byPurpose = byPurpose
        )
    }

    suspend fun saveTrip(trip: MileageLogEntity): Result<MileageLogEntity> {
        return try {
            val now = Instant.now()
            val entityWithStatus = trip.copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE.name,
                createdAt = now,
                updatedAt = now
            )

            mileageLogDao.insert(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "mileage_log",
                entityId = trip.id,
                operation = SyncOperation.CREATE,
                payload = serializeTrip(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTrip(trip: MileageLogEntity): Result<MileageLogEntity> {
        return try {
            val entityWithStatus = trip.copy(
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )

            mileageLogDao.update(entityWithStatus)

            syncQueueManager.enqueue(
                entityType = "mileage_log",
                entityId = trip.id,
                operation = SyncOperation.UPDATE,
                payload = serializeTrip(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTrip(tripId: String): Result<Unit> {
        return try {
            syncQueueManager.enqueue(
                entityType = "mileage_log",
                entityId = tripId,
                operation = SyncOperation.DELETE,
                payload = """{"id":"$tripId"}"""
            )

            mileageLogDao.delete(tripId)
            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeTrip(trip: MileageLogEntity): String {
        return """
            {
                "id": "${trip.id}",
                "user_id": "${trip.userId}",
                "date": "${trip.date}",
                "start_address": ${trip.startAddress?.let { "\"$it\"" } ?: "null"},
                "end_address": ${trip.endAddress?.let { "\"$it\"" } ?: "null"},
                "miles": ${trip.miles},
                "purpose": "${trip.purpose}",
                "appointment_id": ${trip.appointmentId?.let { "\"$it\"" } ?: "null"},
                "notes": ${trip.notes?.let { "\"$it\"" } ?: "null"}
            }
        """.trimIndent()
    }
}
