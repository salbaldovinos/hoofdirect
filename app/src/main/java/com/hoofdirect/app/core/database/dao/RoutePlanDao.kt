package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.RoutePlanEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RoutePlanDao {

    @Query("SELECT * FROM route_plans WHERE user_id = :userId AND date = :date LIMIT 1")
    fun getRouteForDate(userId: String, date: LocalDate): Flow<RoutePlanEntity?>

    @Query("SELECT * FROM route_plans WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getRouteForDateOnce(userId: String, date: LocalDate): RoutePlanEntity?

    @Query("SELECT * FROM route_plans WHERE id = :id")
    fun getRouteById(id: String): Flow<RoutePlanEntity?>

    @Query("SELECT * FROM route_plans WHERE id = :id")
    suspend fun getRouteByIdOnce(id: String): RoutePlanEntity?

    @Query("SELECT * FROM route_plans WHERE user_id = :userId ORDER BY date DESC")
    fun getAllRoutes(userId: String): Flow<List<RoutePlanEntity>>

    @Query("SELECT * FROM route_plans WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getRoutesForDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<RoutePlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routePlan: RoutePlanEntity)

    @Update
    suspend fun update(routePlan: RoutePlanEntity)

    @Query("UPDATE route_plans SET stops = :stops, is_manually_reordered = :isManuallyReordered, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStops(id: String, stops: String, isManuallyReordered: Boolean, updatedAt: Long)

    @Query("DELETE FROM route_plans WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM route_plans WHERE user_id = :userId AND date = :date")
    suspend fun deleteForDate(userId: String, date: LocalDate)

    @Query("DELETE FROM route_plans WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM route_plans WHERE user_id = :userId AND date = :date")
    suspend fun hasRouteForDate(userId: String, date: LocalDate): Int
}
