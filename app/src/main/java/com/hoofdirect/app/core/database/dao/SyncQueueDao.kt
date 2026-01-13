package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hoofdirect.app.core.database.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SyncQueueDao {

    @Query("""
        SELECT * FROM sync_queue
        WHERE status = 'PENDING' OR status = 'FAILED'
        ORDER BY priority DESC, created_at ASC
        LIMIT :limit
    """)
    suspend fun getPendingOperations(limit: Int = 50): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Instant = Instant.now())

    @Query("""
        UPDATE sync_queue
        SET status = 'FAILED',
            retry_count = retry_count + 1,
            last_error = :error,
            updated_at = :now
        WHERE id = :id
    """)
    suspend fun markFailed(id: Long, error: String, now: Instant = Instant.now())

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED' AND updated_at < :before")
    suspend fun deleteCompletedBefore(before: Instant)

    @Query("""
        SELECT * FROM sync_queue
        WHERE entity_type = :entityType AND entity_id = :entityId
        ORDER BY created_at DESC LIMIT 1
    """)
    suspend fun getLatestForEntity(entityType: String, entityId: String): SyncQueueEntity?

    @Query("""
        DELETE FROM sync_queue
        WHERE entity_type = :entityType
        AND entity_id = :entityId
        AND operation = 'UPDATE'
        AND status = 'PENDING'
        AND id != :keepId
    """)
    suspend fun coalesceUpdates(entityType: String, entityId: String, keepId: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}
