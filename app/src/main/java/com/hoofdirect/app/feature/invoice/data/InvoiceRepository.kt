package com.hoofdirect.app.feature.invoice.data

import com.hoofdirect.app.core.database.dao.InvoiceDao
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceItemEntity
import com.hoofdirect.app.core.database.entity.InvoiceStatus
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceRepository @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler
) {
    fun getAllInvoices(userId: String): Flow<List<InvoiceEntity>> {
        return invoiceDao.getAllInvoices(userId)
    }

    fun getInvoicesByStatus(userId: String, status: InvoiceStatus): Flow<List<InvoiceEntity>> {
        return invoiceDao.getInvoicesByStatus(userId, status.name)
    }

    fun getInvoicesForClient(clientId: String): Flow<List<InvoiceEntity>> {
        return invoiceDao.getInvoicesForClient(clientId)
    }

    fun getInvoiceById(id: String): Flow<InvoiceEntity?> {
        return invoiceDao.getInvoiceById(id)
    }

    suspend fun getInvoiceByIdOnce(id: String): InvoiceEntity? {
        return invoiceDao.getInvoiceByIdOnce(id)
    }

    fun getItemsForInvoice(invoiceId: String): Flow<List<InvoiceItemEntity>> {
        return invoiceDao.getItemsForInvoice(invoiceId)
    }

    suspend fun getItemsForInvoiceOnce(invoiceId: String): List<InvoiceItemEntity> {
        return invoiceDao.getItemsForInvoiceOnce(invoiceId)
    }

    suspend fun getNextInvoiceNumber(userId: String): String {
        val lastNumber = invoiceDao.getLastInvoiceNumber(userId)
        return if (lastNumber != null) {
            val numericPart = lastNumber.filter { it.isDigit() }.toIntOrNull() ?: 0
            "INV-${(numericPart + 1).toString().padStart(5, '0')}"
        } else {
            "INV-00001"
        }
    }

    suspend fun createInvoice(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>
    ): Result<InvoiceEntity> {
        return try {
            val entityWithStatus = invoice.copy(
                syncStatus = EntitySyncStatus.PENDING_CREATE.name,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            invoiceDao.insert(entityWithStatus)
            invoiceDao.insertItems(items)

            syncQueueManager.enqueue(
                entityType = "invoice",
                entityId = invoice.id,
                operation = SyncOperation.CREATE,
                payload = serializeInvoice(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateInvoice(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>? = null
    ): Result<InvoiceEntity> {
        return try {
            val entityWithStatus = invoice.copy(
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name,
                updatedAt = Instant.now()
            )

            invoiceDao.update(entityWithStatus)

            if (items != null) {
                invoiceDao.deleteItemsForInvoice(invoice.id)
                invoiceDao.insertItems(items)
            }

            syncQueueManager.enqueue(
                entityType = "invoice",
                entityId = invoice.id,
                operation = SyncOperation.UPDATE,
                payload = serializeInvoice(entityWithStatus)
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(entityWithStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsSent(invoiceId: String): Result<Unit> {
        return try {
            invoiceDao.updateStatus(
                id = invoiceId,
                status = InvoiceStatus.SENT.name,
                updatedAt = Instant.now().toEpochMilli(),
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsPaid(
        invoiceId: String,
        paymentMethod: String?
    ): Result<Unit> {
        return try {
            invoiceDao.markPaid(
                id = invoiceId,
                paidAt = Instant.now().toEpochMilli(),
                paymentMethod = paymentMethod,
                updatedAt = Instant.now().toEpochMilli(),
                syncStatus = EntitySyncStatus.PENDING_UPDATE.name
            )

            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(invoiceId: String): Result<Unit> {
        return try {
            syncQueueManager.enqueue(
                entityType = "invoice",
                entityId = invoiceId,
                operation = SyncOperation.DELETE,
                payload = """{"id":"$invoiceId"}"""
            )

            invoiceDao.delete(invoiceId)
            syncWorkScheduler.triggerImmediateSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeInvoice(invoice: InvoiceEntity): String {
        return """
            {
                "id": "${invoice.id}",
                "user_id": "${invoice.userId}",
                "client_id": "${invoice.clientId}",
                "invoice_number": "${invoice.invoiceNumber}",
                "invoice_date": "${invoice.invoiceDate}",
                "due_date": "${invoice.dueDate}",
                "status": "${invoice.status}",
                "total": "${invoice.total}"
            }
        """.trimIndent()
    }
}
