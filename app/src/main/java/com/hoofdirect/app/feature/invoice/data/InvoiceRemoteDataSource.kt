package com.hoofdirect.app.feature.invoice.data

import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceItemEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class InvoiceDto(
    val id: String,
    val user_id: String,
    val client_id: String,
    val appointment_id: String? = null,
    val invoice_number: String,
    val status: String = "DRAFT",
    val invoice_date: String,
    val due_date: String,
    val subtotal: Double = 0.0,  // Supabase returns numeric as Double
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val notes: String? = null,
    val payment_method: String? = null,
    val paid_at: String? = null,
    val sent_at: String? = null,
    val pdf_path: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    fun toEntity(): InvoiceEntity = InvoiceEntity(
        id = id,
        userId = user_id,
        clientId = client_id,
        appointmentId = appointment_id,
        invoiceNumber = invoice_number,
        status = status,
        invoiceDate = LocalDate.parse(invoice_date),
        dueDate = LocalDate.parse(due_date),
        subtotal = "%.2f".format(subtotal),
        tax = "%.2f".format(tax),
        total = "%.2f".format(total),
        notes = notes,
        paymentMethod = payment_method,
        paidAt = paid_at?.let { parseSupabaseTimestamp(it) },
        sentAt = sent_at?.let { parseSupabaseTimestamp(it) },
        pdfPath = pdf_path,
        createdAt = created_at?.let { parseSupabaseTimestamp(it) } ?: Instant.now(),
        updatedAt = updated_at?.let { parseSupabaseTimestamp(it) } ?: Instant.now(),
        syncStatus = "SYNCED"
    )

    companion object {
        private fun parseSupabaseTimestamp(timestamp: String): Instant {
            return try {
                Instant.parse(timestamp)
            } catch (e: DateTimeParseException) {
                try {
                    OffsetDateTime.parse(timestamp).toInstant()
                } catch (e2: DateTimeParseException) {
                    Instant.now()
                }
            }
        }

        fun fromEntity(entity: InvoiceEntity): InvoiceDto = InvoiceDto(
            id = entity.id,
            user_id = entity.userId,
            client_id = entity.clientId,
            appointment_id = entity.appointmentId,
            invoice_number = entity.invoiceNumber,
            status = entity.status,
            invoice_date = entity.invoiceDate.toString(),
            due_date = entity.dueDate.toString(),
            subtotal = entity.subtotal.toDoubleOrNull() ?: 0.0,
            tax = entity.tax.toDoubleOrNull() ?: 0.0,
            total = entity.total.toDoubleOrNull() ?: 0.0,
            notes = entity.notes,
            payment_method = entity.paymentMethod,
            paid_at = entity.paidAt?.toString(),
            sent_at = entity.sentAt?.toString(),
            pdf_path = entity.pdfPath,
            created_at = entity.createdAt.toString(),
            updated_at = entity.updatedAt.toString()
        )
    }
}

@Serializable
data class InvoiceItemDto(
    val id: String,
    val invoice_id: String,
    val horse_name: String,
    val service_type: String,
    val description: String? = null,
    val quantity: Int = 1,
    val unit_price: Double,  // Supabase returns numeric as Double
    val total: Double
) {
    fun toEntity(): InvoiceItemEntity = InvoiceItemEntity(
        id = id,
        invoiceId = invoice_id,
        horseName = horse_name,
        serviceType = service_type,
        description = description,
        quantity = quantity,
        unitPrice = "%.2f".format(unit_price),
        total = "%.2f".format(total)
    )

    companion object {
        fun fromEntity(entity: InvoiceItemEntity): InvoiceItemDto = InvoiceItemDto(
            id = entity.id,
            invoice_id = entity.invoiceId,
            horse_name = entity.horseName,
            service_type = entity.serviceType,
            description = entity.description,
            quantity = entity.quantity,
            unit_price = entity.unitPrice.toDoubleOrNull() ?: 0.0,
            total = entity.total.toDoubleOrNull() ?: 0.0
        )
    }
}

@Singleton
class InvoiceRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val invoicesTable = "invoices"
    private val invoiceItemsTable = "invoice_items"

    suspend fun fetchAllInvoices(userId: String): Result<List<InvoiceEntity>> {
        return try {
            val response = supabaseClient.postgrest[invoicesTable]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<InvoiceDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchInvoice(invoiceId: String): Result<InvoiceEntity?> {
        return try {
            val response = supabaseClient.postgrest[invoicesTable]
                .select {
                    filter {
                        eq("id", invoiceId)
                    }
                }
                .decodeSingleOrNull<InvoiceDto>()

            Result.success(response?.toEntity())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createInvoice(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>
    ): Result<InvoiceEntity> {
        return try {
            // Insert invoice
            val invoiceDto = InvoiceDto.fromEntity(invoice)
            supabaseClient.postgrest[invoicesTable]
                .insert(invoiceDto)

            // Insert invoice items
            if (items.isNotEmpty()) {
                val itemDtos = items.map { InvoiceItemDto.fromEntity(it) }
                supabaseClient.postgrest[invoiceItemsTable]
                    .insert(itemDtos)
            }

            Result.success(invoice)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateInvoice(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>?
    ): Result<InvoiceEntity> {
        return try {
            // Update invoice
            val dto = InvoiceDto.fromEntity(invoice)
            supabaseClient.postgrest[invoicesTable]
                .update(dto) {
                    filter {
                        eq("id", invoice.id)
                    }
                }

            // Update items if provided
            if (items != null) {
                // Delete existing items
                supabaseClient.postgrest[invoiceItemsTable]
                    .delete {
                        filter {
                            eq("invoice_id", invoice.id)
                        }
                    }

                // Insert new items
                if (items.isNotEmpty()) {
                    val itemDtos = items.map { InvoiceItemDto.fromEntity(it) }
                    supabaseClient.postgrest[invoiceItemsTable]
                        .insert(itemDtos)
                }
            }

            Result.success(invoice)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(invoiceId: String): Result<Unit> {
        return try {
            // Items are deleted via cascade
            supabaseClient.postgrest[invoicesTable]
                .delete {
                    filter {
                        eq("id", invoiceId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchInvoiceItems(invoiceId: String): Result<List<InvoiceItemEntity>> {
        return try {
            val response = supabaseClient.postgrest[invoiceItemsTable]
                .select {
                    filter {
                        eq("invoice_id", invoiceId)
                    }
                }
                .decodeList<InvoiceItemDto>()

            Result.success(response.map { it.toEntity() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
