package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.ServicePriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServicePriceDao {

    @Query("""
        SELECT * FROM service_prices
        WHERE user_id = :userId
        AND is_active = 1
        ORDER BY display_order ASC
    """)
    fun getActiveServicePrices(userId: String): Flow<List<ServicePriceEntity>>

    @Query("""
        SELECT * FROM service_prices
        WHERE user_id = :userId
        ORDER BY display_order ASC
    """)
    fun getAllServicePrices(userId: String): Flow<List<ServicePriceEntity>>

    @Query("SELECT * FROM service_prices WHERE id = :id")
    suspend fun getById(id: String): ServicePriceEntity?

    @Query("SELECT * FROM service_prices WHERE user_id = :userId AND service_type = :serviceType LIMIT 1")
    suspend fun getByType(userId: String, serviceType: String): ServicePriceEntity?

    @Query("SELECT MAX(display_order) FROM service_prices WHERE user_id = :userId")
    suspend fun getMaxDisplayOrder(userId: String): Int?

    @Query("SELECT COUNT(*) FROM service_prices WHERE user_id = :userId")
    suspend fun getCountForUser(userId: String): Int

    @Query("""
        UPDATE service_prices
        SET display_order = :order, updated_at = :updatedAt, sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun updateDisplayOrder(id: String, order: Int, updatedAt: Long, syncStatus: String)

    @Query("""
        UPDATE service_prices
        SET is_active = :isActive, updated_at = :updatedAt, sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun updateActiveStatus(id: String, isActive: Boolean, updatedAt: Long, syncStatus: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: ServicePriceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<ServicePriceEntity>)

    @Update
    suspend fun update(price: ServicePriceEntity)

    @Query("DELETE FROM service_prices WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM service_prices WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)
}
