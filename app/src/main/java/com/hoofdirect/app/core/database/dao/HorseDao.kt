package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.HorseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HorseDao {

    @Query("""
        SELECT * FROM horses
        WHERE client_id = :clientId
        AND is_active = 1
        AND sync_status != 'PENDING_DELETE'
        ORDER BY name ASC
    """)
    fun getHorsesForClient(clientId: String): Flow<List<HorseEntity>>

    @Query("""
        SELECT * FROM horses
        WHERE user_id = :userId
        AND is_active = 1
        AND sync_status != 'PENDING_DELETE'
        ORDER BY name ASC
    """)
    fun getAllHorses(userId: String): Flow<List<HorseEntity>>

    @Query("SELECT * FROM horses WHERE id = :id")
    fun getHorseById(id: String): Flow<HorseEntity?>

    @Query("SELECT * FROM horses WHERE id = :id")
    suspend fun getHorseByIdOnce(id: String): HorseEntity?

    @Query("SELECT COUNT(*) FROM horses WHERE client_id = :clientId AND is_active = 1")
    suspend fun getHorseCountForClient(clientId: String): Int

    @Query("SELECT COUNT(*) FROM horses WHERE user_id = :userId AND is_active = 1")
    suspend fun getActiveHorseCount(userId: String): Int

    @Query("""
        SELECT * FROM horses
        WHERE user_id = :userId
        AND is_active = 1
        AND next_due_date IS NOT NULL
        AND next_due_date <= :untilDate
        ORDER BY next_due_date ASC
    """)
    fun getDueSoonHorses(userId: String, untilDate: LocalDate): Flow<List<HorseEntity>>

    @Query("""
        SELECT * FROM horses
        WHERE user_id = :userId
        AND is_active = 1
        AND next_due_date IS NOT NULL
        AND next_due_date < :today
        ORDER BY next_due_date ASC
    """)
    fun getOverdueHorses(userId: String, today: LocalDate): Flow<List<HorseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(horse: HorseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(horses: List<HorseEntity>)

    @Update
    suspend fun update(horse: HorseEntity)

    @Query("""
        UPDATE horses
        SET last_service_date = :lastServiceDate,
            next_due_date = :nextDueDate,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun updateServiceDates(
        id: String,
        lastServiceDate: LocalDate,
        nextDueDate: LocalDate,
        updatedAt: Long,
        syncStatus: String
    )

    @Query("UPDATE horses SET is_active = :isActive, updated_at = :updatedAt, sync_status = :syncStatus WHERE client_id = :clientId")
    suspend fun updateActiveStatusForClient(clientId: String, isActive: Boolean, updatedAt: Long, syncStatus: String)

    @Query("DELETE FROM horses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM horses WHERE client_id = :clientId")
    suspend fun deleteAllForClient(clientId: String)

    @Query("UPDATE horses SET sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String, updatedAt: Long)
}
