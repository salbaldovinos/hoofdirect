package com.hoofdirect.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface InvoiceDao {

    @Query("""
        SELECT * FROM invoices
        WHERE user_id = :userId
        AND sync_status != 'PENDING_DELETE'
        ORDER BY invoice_date DESC
    """)
    fun getAllInvoices(userId: String): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE user_id = :userId
        AND status = :status
        AND sync_status != 'PENDING_DELETE'
        ORDER BY invoice_date DESC
    """)
    fun getInvoicesByStatus(userId: String, status: String): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE client_id = :clientId
        AND sync_status != 'PENDING_DELETE'
        ORDER BY invoice_date DESC
    """)
    fun getInvoicesForClient(clientId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    fun getInvoiceById(id: String): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceByIdOnce(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE appointment_id = :appointmentId LIMIT 1")
    suspend fun getInvoiceForAppointment(appointmentId: String): InvoiceEntity?

    @Query("""
        SELECT * FROM invoices
        WHERE user_id = :userId
        AND status = 'SENT'
        AND due_date < :today
    """)
    fun getOverdueInvoices(userId: String, today: LocalDate): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT SUM(CAST(total AS REAL)) FROM invoices
        WHERE user_id = :userId
        AND status = 'PAID'
        AND invoice_date BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalRevenue(userId: String, startDate: LocalDate, endDate: LocalDate): Double?

    @Query("SELECT MAX(invoice_number) FROM invoices WHERE user_id = :userId")
    suspend fun getLastInvoiceNumber(userId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: InvoiceEntity)

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET status = :status, updated_at = :updatedAt, sync_status = :syncStatus WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long, syncStatus: String)

    @Query("""
        UPDATE invoices
        SET status = 'PAID',
            paid_at = :paidAt,
            payment_method = :paymentMethod,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE id = :id
    """)
    suspend fun markPaid(id: String, paidAt: Long, paymentMethod: String?, updatedAt: Long, syncStatus: String)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE invoices SET sync_status = :syncStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String, updatedAt: Long)

    // Invoice Items
    @Query("SELECT * FROM invoice_items WHERE invoice_id = :invoiceId")
    fun getItemsForInvoice(invoiceId: String): Flow<List<InvoiceItemEntity>>

    @Query("SELECT * FROM invoice_items WHERE invoice_id = :invoiceId")
    suspend fun getItemsForInvoiceOnce(invoiceId: String): List<InvoiceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InvoiceItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItemEntity>)

    @Query("DELETE FROM invoice_items WHERE invoice_id = :invoiceId")
    suspend fun deleteItemsForInvoice(invoiceId: String)
}
