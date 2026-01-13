package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.MileageLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface MileageLogDao {

    @Query("""
        SELECT * FROM mileage_logs
        WHERE user_id = :userId
        AND date = :date
        AND sync_status != 'PENDING_DELETE'
        ORDER BY created_at DESC
    """)
    fun getTripsForDate(userId: String, date: LocalDate): Flow<List<MileageLogEntity>>

    @Query("""
        SELECT * FROM mileage_logs
        WHERE user_id = :userId
        AND date BETWEEN :startDate AND :endDate
        AND sync_status != 'PENDING_DELETE'
        ORDER BY date DESC, created_at DESC
    """)
    fun getTripsInRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<MileageLogEntity>>

    @Query("SELECT * FROM mileage_logs WHERE id = :id")
    suspend fun getById(id: String): MileageLogEntity?

    @Query("""
        SELECT COALESCE(SUM(miles), 0.0) FROM mileage_logs
        WHERE user_id = :userId
        AND date BETWEEN :startDate AND :endDate
        AND sync_status != 'PENDING_DELETE'
    """)
    suspend fun getTotalMilesForRange(userId: String, startDate: LocalDate, endDate: LocalDate): Double

    @Query("""
        SELECT COUNT(*) FROM mileage_logs
        WHERE user_id = :userId
        AND date BETWEEN :startDate AND :endDate
        AND sync_status != 'PENDING_DELETE'
    """)
    suspend fun getTripCountForRange(userId: String, startDate: LocalDate, endDate: LocalDate): Int

    @Query("""
        SELECT COALESCE(SUM(miles), 0.0) FROM mileage_logs
        WHERE user_id = :userId
        AND purpose = :purpose
        AND date BETWEEN :startDate AND :endDate
        AND sync_status != 'PENDING_DELETE'
    """)
    suspend fun getTotalMilesForPurpose(
        userId: String,
        purpose: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: MileageLogEntity)

    @Update
    suspend fun update(trip: MileageLogEntity)

    @Query("""
        UPDATE mileage_logs
        SET sync_status = 'PENDING_DELETE', updated_at = :now
        WHERE id = :id
    """)
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM mileage_logs WHERE id = :id")
    suspend fun delete(id: String)
}
