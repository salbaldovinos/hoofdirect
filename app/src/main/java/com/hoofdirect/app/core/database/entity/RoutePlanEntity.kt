package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "route_plans")
data class RoutePlanEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    val date: LocalDate,

    @ColumnInfo(name = "start_lat")
    val startLat: Double,

    @ColumnInfo(name = "start_lng")
    val startLng: Double,

    @ColumnInfo(name = "start_name")
    val startName: String,

    @ColumnInfo(name = "end_lat")
    val endLat: Double,

    @ColumnInfo(name = "end_lng")
    val endLng: Double,

    @ColumnInfo(name = "end_name")
    val endName: String,

    // JSON array of RouteStop objects
    val stops: String,

    // Encoded polyline for map display
    @ColumnInfo(name = "polyline_points")
    val polylinePoints: String? = null,

    @ColumnInfo(name = "total_distance_miles")
    val totalDistanceMiles: Double,

    @ColumnInfo(name = "total_drive_minutes")
    val totalDriveMinutes: Int,

    // Original (unoptimized) stats for comparison
    @ColumnInfo(name = "original_distance_miles")
    val originalDistanceMiles: Double? = null,

    @ColumnInfo(name = "original_drive_minutes")
    val originalDriveMinutes: Int? = null,

    @ColumnInfo(name = "optimized_at")
    val optimizedAt: Instant,

    @ColumnInfo(name = "is_manually_reordered")
    val isManuallyReordered: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "SYNCED"
)
