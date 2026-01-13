package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("""
        SELECT * FROM clients
        WHERE user_id = :userId
        AND is_active = :isActive
        AND sync_status != 'PENDING_DELETE'
        ORDER BY name ASC
    """)
    fun getClients(userId: String, isActive: Boolean = true): Flow<List<ClientEntity>>

    @Query("""
        SELECT * FROM clients
        WHERE user_id = :userId
        AND is_active = :isActive
        AND sync_status != 'PENDING_DELETE'
        AND (
            name LIKE '%' || :query || '%'
            OR business_name LIKE '%' || :query || '%'
            OR phone LIKE '%' || :query || '%'
            OR city LIKE '%' || :query || '%'
        )
        ORDER BY
            CASE WHEN :sortBy = 'name' THEN name END ASC,
            CASE WHEN :sortBy = 'city' THEN city END ASC,
            CASE WHEN :sortBy = 'recent' THEN updated_at END DESC
    """)
    fun searchClients(
        userId: String,
        query: String,
        isActive: Boolean = true,
        sortBy: String = "name"
    ): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun getClientById(id: String): Flow<ClientEntity?>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientByIdOnce(id: String): ClientEntity?

    @Query("SELECT COUNT(*) FROM clients WHERE user_id = :userId AND is_active = 1")
    suspend fun getActiveClientCount(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<ClientEntity>)

    @Update
    suspend fun update(client: ClientEntity)

    @Query("UPDATE clients SET is_active = :isActive, updated_at = :updatedAt, sync_status = :syncStatus WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean, updatedAt: Long, syncStatus: String)

    @Query("UPDATE clients SET sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String, updatedAt: Long)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM clients WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
