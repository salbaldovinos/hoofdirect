package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "mileage_logs",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["date"]),
        Index(value = ["appointment_id"])
    ]
)
data class MileageLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    val date: LocalDate,

    @ColumnInfo(name = "start_address")
    val startAddress: String? = null,

    @ColumnInfo(name = "end_address")
    val endAddress: String? = null,

    val miles: Double,

    val purpose: String = MileagePurpose.CLIENT_VISIT.name,

    @ColumnInfo(name = "appointment_id")
    val appointmentId: String? = null,

    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = EntitySyncStatus.SYNCED.name
)

enum class MileagePurpose(val displayName: String) {
    CLIENT_VISIT("Client Visit"),
    SUPPLY_RUN("Supply Run"),
    TRAINING("Training"),
    OTHER("Other")
}
