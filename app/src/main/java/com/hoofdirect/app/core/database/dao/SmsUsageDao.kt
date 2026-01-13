package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.SmsUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsUsageDao {

    @Query("SELECT * FROM sms_usage WHERE user_id = :userId AND year_month = :yearMonth LIMIT 1")
    suspend fun getByUserAndMonth(userId: String, yearMonth: String): SmsUsageEntity?

    @Query("SELECT * FROM sms_usage WHERE user_id = :userId AND year_month = :yearMonth LIMIT 1")
    fun observeByUserAndMonth(userId: String, yearMonth: String): Flow<SmsUsageEntity?>

    @Query("SELECT sms_count FROM sms_usage WHERE user_id = :userId AND year_month = :yearMonth")
    suspend fun getSmsCount(userId: String, yearMonth: String): Int?

    @Query("SELECT sms_count FROM sms_usage WHERE user_id = :userId AND year_month = :yearMonth")
    fun observeSmsCount(userId: String, yearMonth: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SmsUsageEntity)

    @Update
    suspend fun update(entity: SmsUsageEntity)

    @Query("UPDATE sms_usage SET sms_count = sms_count + 1, last_sent_at = :timestamp, updated_at = :timestamp WHERE user_id = :userId AND year_month = :yearMonth")
    suspend fun incrementSmsCount(userId: String, yearMonth: String, timestamp: Long)

    @Query("DELETE FROM sms_usage WHERE user_id = :userId")
    suspend fun deleteByUser(userId: String)
}
