package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "service_prices",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["service_type"])
    ]
)
data class ServicePriceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "service_type")
    val serviceType: String,

    val name: String,

    val description: String? = null,

    val price: String = "0.00",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int = 45,

    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = EntitySyncStatus.SYNCED.name
)

object ServiceTypes {
    const val TRIM = "TRIM"
    const val FRONT_SHOES = "FRONT_SHOES"
    const val FULL_SET = "FULL_SET"
    const val CORRECTIVE = "CORRECTIVE"
    const val RESET = "RESET"
    const val PULL_SHOES = "PULL_SHOES"
    const val CUSTOM = "CUSTOM"

    data class ServiceDefaults(
        val type: String,
        val name: String,
        val price: String,
        val durationMinutes: Int
    )

    fun getDefaults(): List<ServiceDefaults> = listOf(
        ServiceDefaults(TRIM, "Trim", "45.00", 30),
        ServiceDefaults(FRONT_SHOES, "Front Shoes", "120.00", 45),
        ServiceDefaults(FULL_SET, "Full Set", "180.00", 60),
        ServiceDefaults(CORRECTIVE, "Corrective", "220.00", 75),
        ServiceDefaults(RESET, "Reset", "100.00", 45),
        ServiceDefaults(PULL_SHOES, "Pull Shoes", "25.00", 15)
    )

    fun getDisplayName(type: String): String = when (type) {
        TRIM -> "Trim"
        FRONT_SHOES -> "Front Shoes"
        FULL_SET -> "Full Set"
        CORRECTIVE -> "Corrective"
        RESET -> "Reset"
        PULL_SHOES -> "Pull Shoes"
        CUSTOM -> "Custom"
        else -> type
    }
}
