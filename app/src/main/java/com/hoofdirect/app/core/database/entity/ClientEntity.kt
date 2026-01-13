package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["name"]),
        Index(value = ["is_active"]),
        Index(value = ["city"]),
        Index(value = ["user_id", "is_active"])
    ]
)
data class ClientEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    val name: String,

    @ColumnInfo(name = "first_name")
    val firstName: String,

    @ColumnInfo(name = "last_name")
    val lastName: String? = null,

    @ColumnInfo(name = "business_name")
    val businessName: String? = null,

    val phone: String,

    val email: String? = null,

    val address: String? = null,

    val latitude: Double? = null,

    val longitude: Double? = null,

    val city: String? = null,

    val state: String? = null,

    @ColumnInfo(name = "zip_code")
    val zipCode: String? = null,

    @ColumnInfo(name = "access_notes")
    val accessNotes: String? = null,

    val notes: String? = null,

    @ColumnInfo(name = "custom_prices")
    val customPrices: String? = null,

    @ColumnInfo(name = "reminder_preference")
    val reminderPreference: String = "SMS",

    @ColumnInfo(name = "reminder_hours")
    val reminderHours: Int = 24,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = EntitySyncStatus.SYNCED.name
)
