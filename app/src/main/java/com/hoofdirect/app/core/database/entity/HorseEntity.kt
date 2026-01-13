package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "horses",
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
        Index(value = ["next_due_date"]),
        Index(value = ["is_active"])
    ]
)
data class HorseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "client_id")
    val clientId: String,

    val name: String,

    val breed: String? = null,

    val color: String? = null,

    val age: Int? = null,

    val temperament: String? = null,

    @ColumnInfo(name = "default_service_type")
    val defaultServiceType: String? = null,

    @ColumnInfo(name = "shoeing_cycle_weeks")
    val shoeingCycleWeeks: Int? = null,

    @ColumnInfo(name = "last_service_date")
    val lastServiceDate: LocalDate? = null,

    @ColumnInfo(name = "next_due_date")
    val nextDueDate: LocalDate? = null,

    @ColumnInfo(name = "medical_notes")
    val medicalNotes: String? = null,

    @ColumnInfo(name = "primary_photo_id")
    val primaryPhotoId: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = EntitySyncStatus.SYNCED.name
)

enum class HorseTemperament(val displayName: String) {
    CALM("Calm"),
    NERVOUS("Nervous"),
    DIFFICULT("Difficult"),
    AGGRESSIVE("Aggressive"),
    YOUNG("Young/Green"),
    SENIOR("Senior/Stiff")
}
