package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Tracks SMS usage for billing and limit enforcement.
 */
@Entity(
    tableName = "sms_usage",
    indices = [
        Index("user_id"),
        Index("year_month"),
        Index(value = ["user_id", "year_month"], unique = true)
    ]
)
data class SmsUsageEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    /**
     * Format: "YYYY-MM" (e.g., "2025-01")
     */
    @ColumnInfo(name = "year_month")
    val yearMonth: String,

    @ColumnInfo(name = "sms_count")
    val smsCount: Int = 0,

    @ColumnInfo(name = "last_sent_at")
    val lastSentAt: Instant? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
) {
    companion object {
        fun currentYearMonth(): String {
            val now = YearMonth.now(ZoneId.systemDefault())
            return "${now.year}-${now.monthValue.toString().padStart(2, '0')}"
        }

        fun createNew(userId: String): SmsUsageEntity {
            return SmsUsageEntity(
                id = java.util.UUID.randomUUID().toString(),
                userId = userId,
                yearMonth = currentYearMonth()
            )
        }
    }
}
