package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.core.database.entity.AppointmentHorseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AppointmentDao {

    @Query("""
        SELECT * FROM appointments
        WHERE user_id = :userId
        AND date = :date
        AND sync_status != 'PENDING_DELETE'
        ORDER BY start_time ASC
    """)
    fun getAppointmentsForDate(userId: String, date: LocalDate): Flow<List<AppointmentEntity>>

    @Query("""
        SELECT * FROM appointments
        WHERE user_id = :userId
        AND date BETWEEN :startDate AND :endDate
        AND sync_status != 'PENDING_DELETE'
        ORDER BY date ASC, start_time ASC
    """)
    fun getAppointmentsForDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<AppointmentEntity>>

    @Query("""
        SELECT * FROM appointments
        WHERE client_id = :clientId
        AND sync_status != 'PENDING_DELETE'
        ORDER BY date DESC, start_time DESC
    """)
    fun getAppointmentsForClient(clientId: String): Flow<List<AppointmentEntity>>

    @Query("""
        SELECT * FROM appointments
        WHERE client_id = :clientId
        AND date >= :today
        AND status IN ('SCHEDULED', 'CONFIRMED')
        ORDER BY date ASC, start_time ASC
        LIMIT :limit
    """)
    fun getUpcomingAppointmentsForClient(
        clientId: String,
        today: LocalDate,
        limit: Int = 5
    ): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    fun getAppointmentById(id: String): Flow<AppointmentEntity?>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentByIdOnce(id: String): AppointmentEntity?

    @Query("""
        SELECT * FROM appointments
        WHERE user_id = :userId
        AND date = :date
        AND sync_status != 'PENDING_DELETE'
        ORDER BY start_time ASC
    """)
    suspend fun getAppointmentsForDateOnce(userId: String, date: LocalDate): List<AppointmentEntity>

    @Query("""
        SELECT COUNT(*) FROM appointments
        WHERE user_id = :userId
        AND date = :date
        AND status IN ('SCHEDULED', 'CONFIRMED')
    """)
    suspend fun getAppointmentCountForDate(userId: String, date: LocalDate): Int

    @Query("""
        SELECT COUNT(*) FROM appointments
        WHERE user_id = :userId
        AND date BETWEEN :startDate AND :endDate
        AND status = 'COMPLETED'
        AND sync_status != 'PENDING_DELETE'
    """)
    suspend fun getCompletedCountForRange(userId: String, startDate: LocalDate, endDate: LocalDate): Int

    @Query("""
        SELECT COUNT(DISTINCT ah.horse_id) FROM appointment_horses ah
        INNER JOIN appointments a ON ah.appointment_id = a.id
        WHERE a.user_id = :userId
        AND a.date BETWEEN :startDate AND :endDate
        AND a.status = 'COMPLETED'
        AND a.sync_status != 'PENDING_DELETE'
    """)
    suspend fun getUniqueHorsesServicedForRange(userId: String, startDate: LocalDate, endDate: LocalDate): Int

    @Query("""
        SELECT * FROM appointments
        WHERE user_id = :userId
        AND date >= :today
        AND status IN ('SCHEDULED', 'CONFIRMED')
        ORDER BY date ASC, start_time ASC
    """)
    fun getUpcomingAppointments(userId: String, today: LocalDate): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: AppointmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appointments: List<AppointmentEntity>)

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Query("UPDATE appointments SET status = :status, updated_at = :updatedAt, sync_status = :syncStatus WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long, syncStatus: String)

    @Query("""
        UPDATE appointments
        SET status = 'COMPLETED',
            completed_at = :completedAt,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun markCompleted(id: String, completedAt: Long, updatedAt: Long, syncStatus: String)

    @Query("""
        UPDATE appointments
        SET route_order = :routeOrder,
            travel_time_minutes = :travelTime,
            travel_distance_miles = :travelDistance,
            updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateRouteInfo(
        id: String,
        routeOrder: Int,
        travelTime: Int?,
        travelDistance: Double?,
        updatedAt: Long
    )

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE appointments SET sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String, updatedAt: Long)

    // Appointment Horses
    @Query("SELECT * FROM appointment_horses WHERE appointment_id = :appointmentId")
    fun getHorsesForAppointment(appointmentId: String): Flow<List<AppointmentHorseEntity>>

    @Query("SELECT * FROM appointment_horses WHERE appointment_id = :appointmentId")
    suspend fun getHorsesForAppointmentOnce(appointmentId: String): List<AppointmentHorseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointmentHorse(appointmentHorse: AppointmentHorseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointmentHorses(appointmentHorses: List<AppointmentHorseEntity>)

    @Query("DELETE FROM appointment_horses WHERE appointment_id = :appointmentId")
    suspend fun deleteAppointmentHorses(appointmentId: String)

    @Query("DELETE FROM appointment_horses WHERE appointment_id = :appointmentId AND horse_id = :horseId")
    suspend fun deleteAppointmentHorse(appointmentId: String, horseId: String)
}
