package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["client_id"]),
        Index(value = ["user_id"]),
        Index(value = ["date"]),
        Index(value = ["status"]),
        Index(value = ["user_id", "date"])
    ]
)
data class AppointmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "client_id")
    val clientId: String,

    val date: LocalDate,

    @ColumnInfo(name = "start_time")
    val startTime: LocalTime,

    @ColumnInfo(name = "end_time")
    val endTime: LocalTime? = null,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int = 45,

    val status: String = AppointmentStatus.SCHEDULED.name,

    val address: String? = null,

    val latitude: Double? = null,

    val longitude: Double? = null,

    val notes: String? = null,

    @ColumnInfo(name = "total_price")
    val totalPrice: String = "0.00", // Stored as string for BigDecimal

    @ColumnInfo(name = "reminder_sent")
    val reminderSent: Boolean = false,

    @ColumnInfo(name = "confirmation_received")
    val confirmationReceived: Boolean = false,

    @ColumnInfo(name = "completed_at")
    val completedAt: Instant? = null,

    @ColumnInfo(name = "cancelled_at")
    val cancelledAt: Instant? = null,

    @ColumnInfo(name = "cancellation_reason")
    val cancellationReason: String? = null,

    @ColumnInfo(name = "route_order")
    val routeOrder: Int? = null,

    @ColumnInfo(name = "travel_time_minutes")
    val travelTimeMinutes: Int? = null,

    @ColumnInfo(name = "travel_distance_miles")
    val travelDistanceMiles: Double? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = EntitySyncStatus.SYNCED.name
)

enum class AppointmentStatus {
    SCHEDULED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}

@Entity(
    tableName = "appointment_horses",
    primaryKeys = ["appointment_id", "horse_id"],
    foreignKeys = [
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HorseEntity::class,
            parentColumns = ["id"],
            childColumns = ["horse_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["appointment_id"]),
        Index(value = ["horse_id"])
    ]
)
data class AppointmentHorseEntity(
    @ColumnInfo(name = "appointment_id")
    val appointmentId: String,

    @ColumnInfo(name = "horse_id")
    val horseId: String,

    @ColumnInfo(name = "service_type")
    val serviceType: String,

    val price: String = "0.00",

    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now()
)
