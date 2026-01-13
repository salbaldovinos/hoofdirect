package com.hoofdirect.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "status")
    val status: String = "PENDING",

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "priority")
    val priority: Int = 0
)

enum class SyncOperation(val priority: Int) {
    CREATE(1),
    UPDATE(2),
    DELETE(0)
}

enum class SyncStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    COMPLETED
}

enum class EntitySyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT
}
