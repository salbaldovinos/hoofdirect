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
    tableName = "invoices",
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
        Index(value = ["status"]),
        Index(value = ["invoice_date"])
    ]
)
data class InvoiceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "client_id")
    val clientId: String,

    @ColumnInfo(name = "appointment_id")
    val appointmentId: String? = null,

    @ColumnInfo(name = "invoice_number")
    val invoiceNumber: String,

    @ColumnInfo(name = "invoice_date")
    val invoiceDate: LocalDate,

    @ColumnInfo(name = "due_date")
    val dueDate: LocalDate,

    val subtotal: String = "0.00",

    val tax: String = "0.00",

    val total: String = "0.00",

    val status: String = InvoiceStatus.DRAFT.name,

    val notes: String? = null,

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String? = null,

    @ColumnInfo(name = "paid_at")
    val paidAt: Instant? = null,

    @ColumnInfo(name = "sent_at")
    val sentAt: Instant? = null,

    @ColumnInfo(name = "pdf_path")
    val pdfPath: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = EntitySyncStatus.SYNCED.name
)

enum class InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE,
    CANCELLED
}

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoice_id"])]
)
data class InvoiceItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "invoice_id")
    val invoiceId: String,

    @ColumnInfo(name = "horse_name")
    val horseName: String,

    @ColumnInfo(name = "service_type")
    val serviceType: String,

    val description: String? = null,

    val quantity: Int = 1,

    @ColumnInfo(name = "unit_price")
    val unitPrice: String = "0.00",

    val total: String = "0.00"
)
