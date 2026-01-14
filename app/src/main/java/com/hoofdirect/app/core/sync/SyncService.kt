package com.hoofdirect.app.core.sync

import android.util.Log
import com.hoofdirect.app.core.database.dao.AppointmentDao
import com.hoofdirect.app.core.database.dao.ClientDao
import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.dao.InvoiceDao
import com.hoofdirect.app.core.database.dao.SyncQueueDao
import com.hoofdirect.app.core.database.entity.EntitySyncStatus
import com.hoofdirect.app.core.database.entity.SyncOperation
import com.hoofdirect.app.core.database.entity.SyncQueueEntity
import com.hoofdirect.app.core.database.entity.SyncStatus
import com.hoofdirect.app.feature.appointment.data.AppointmentRemoteDataSource
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRemoteDataSource
import com.hoofdirect.app.feature.horse.data.HorseRemoteDataSource
import com.hoofdirect.app.feature.invoice.data.InvoiceRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SyncService handles the synchronization between local Room database
 * and remote Supabase backend.
 *
 * Sync Strategy:
 * 1. Push local changes (from sync queue) to server
 * 2. Pull remote changes since last sync
 * 3. Handle conflicts with "last write wins" strategy
 */
@Singleton
class SyncService @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val clientDao: ClientDao,
    private val horseDao: HorseDao,
    private val appointmentDao: AppointmentDao,
    private val invoiceDao: InvoiceDao,
    private val clientRemoteDataSource: ClientRemoteDataSource,
    private val horseRemoteDataSource: HorseRemoteDataSource,
    private val appointmentRemoteDataSource: AppointmentRemoteDataSource,
    private val invoiceRemoteDataSource: InvoiceRemoteDataSource,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SyncService"
    }

    /**
     * Performs a full sync cycle:
     * 1. Push pending local changes
     * 2. Pull remote updates
     */
    suspend fun performSync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        val userId = tokenManager.getUserId()
            ?: return@withContext Result.failure(Exception("User not authenticated"))

        Log.d(TAG, "Starting sync for user: $userId")

        try {
            // Step 1: Push local changes
            val pushResult = pushLocalChanges()
            if (pushResult.isFailure) {
                Log.e(TAG, "Push failed: ${pushResult.exceptionOrNull()}")
                return@withContext Result.failure(pushResult.exceptionOrNull()!!)
            }

            // Step 2: Pull remote changes
            val pullResult = pullRemoteChanges(userId)
            if (pullResult.isFailure) {
                Log.e(TAG, "Pull failed: ${pullResult.exceptionOrNull()}")
                return@withContext Result.failure(pullResult.exceptionOrNull()!!)
            }

            val syncResult = SyncResult(
                pushedCount = pushResult.getOrDefault(0),
                pulledCount = pullResult.getOrDefault(0)
            )

            Log.d(TAG, "Sync completed: pushed=${syncResult.pushedCount}, pulled=${syncResult.pulledCount}")
            Result.success(syncResult)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Push all pending local changes to the server
     */
    private suspend fun pushLocalChanges(): Result<Int> {
        val pendingItems = syncQueueDao.getPendingOperations()
        var pushedCount = 0

        Log.d(TAG, "Pushing ${pendingItems.size} pending items")

        for (item in pendingItems) {
            val result = pushSingleItem(item)
            if (result.isSuccess) {
                // Mark as synced and remove from queue
                syncQueueDao.updateStatus(item.id, SyncStatus.COMPLETED.name)
                syncQueueDao.delete(item.id)
                pushedCount++

                // Update the entity's sync status
                updateEntitySyncStatus(item.entityType, item.entityId, EntitySyncStatus.SYNCED)
            } else {
                // Mark as failed, will retry on next sync
                val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                if (item.retryCount >= 4) {
                    syncQueueDao.markFailed(item.id, errorMessage)
                    Log.e(TAG, "Item ${item.id} failed after 5 retries: $errorMessage")
                } else {
                    syncQueueDao.markFailed(item.id, errorMessage)
                    Log.w(TAG, "Item ${item.id} failed, retry ${item.retryCount + 1}: $errorMessage")
                }
            }
        }

        return Result.success(pushedCount)
    }

    /**
     * Push a single sync queue item to the server
     */
    private suspend fun pushSingleItem(item: SyncQueueEntity): Result<Unit> {
        return when (item.entityType) {
            "client" -> pushClient(item)
            "horse" -> pushHorse(item)
            "appointment" -> pushAppointment(item)
            "invoice" -> pushInvoice(item)
            else -> {
                Log.w(TAG, "Unknown entity type: ${item.entityType}")
                Result.success(Unit)
            }
        }
    }

    private suspend fun pushClient(item: SyncQueueEntity): Result<Unit> {
        return when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE -> {
                val client = clientDao.getClientByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Client not found"))
                clientRemoteDataSource.createClient(client).map { }
            }
            SyncOperation.UPDATE -> {
                val client = clientDao.getClientByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Client not found"))
                clientRemoteDataSource.updateClient(client).map { }
            }
            SyncOperation.DELETE -> {
                clientRemoteDataSource.deleteClient(item.entityId)
            }
        }
    }

    private suspend fun pushHorse(item: SyncQueueEntity): Result<Unit> {
        return when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE -> {
                val horse = horseDao.getHorseByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Horse not found"))
                horseRemoteDataSource.createHorse(horse).map { }
            }
            SyncOperation.UPDATE -> {
                val horse = horseDao.getHorseByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Horse not found"))
                horseRemoteDataSource.updateHorse(horse).map { }
            }
            SyncOperation.DELETE -> {
                horseRemoteDataSource.deleteHorse(item.entityId)
            }
        }
    }

    private suspend fun pushAppointment(item: SyncQueueEntity): Result<Unit> {
        return when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE -> {
                val appointment = appointmentDao.getAppointmentByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Appointment not found"))
                val horses = appointmentDao.getHorsesForAppointmentOnce(item.entityId)
                appointmentRemoteDataSource.createAppointment(appointment, horses).map { }
            }
            SyncOperation.UPDATE -> {
                val appointment = appointmentDao.getAppointmentByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Appointment not found"))
                val horses = appointmentDao.getHorsesForAppointmentOnce(item.entityId)
                appointmentRemoteDataSource.updateAppointment(appointment, horses).map { }
            }
            SyncOperation.DELETE -> {
                appointmentRemoteDataSource.deleteAppointment(item.entityId)
            }
        }
    }

    private suspend fun pushInvoice(item: SyncQueueEntity): Result<Unit> {
        return when (SyncOperation.valueOf(item.operation)) {
            SyncOperation.CREATE -> {
                val invoice = invoiceDao.getInvoiceByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Invoice not found"))
                val items = invoiceDao.getItemsForInvoiceOnce(item.entityId)
                invoiceRemoteDataSource.createInvoice(invoice, items).map { }
            }
            SyncOperation.UPDATE -> {
                val invoice = invoiceDao.getInvoiceByIdOnce(item.entityId)
                    ?: return Result.failure(Exception("Invoice not found"))
                val items = invoiceDao.getItemsForInvoiceOnce(item.entityId)
                invoiceRemoteDataSource.updateInvoice(invoice, items).map { }
            }
            SyncOperation.DELETE -> {
                invoiceRemoteDataSource.deleteInvoice(item.entityId)
            }
        }
    }

    /**
     * Pull remote changes and update local database
     */
    private suspend fun pullRemoteChanges(userId: String): Result<Int> {
        var pulledCount = 0

        // Pull clients
        pulledCount += pullClients(userId)

        // Pull horses
        pulledCount += pullHorses(userId)

        // Pull appointments
        pulledCount += pullAppointments(userId)

        // Pull invoices
        pulledCount += pullInvoices(userId)

        return Result.success(pulledCount)
    }

    private suspend fun pullClients(userId: String): Int {
        var count = 0
        val result = clientRemoteDataSource.fetchAllClients(userId)
        if (result.isSuccess) {
            val remoteClients = result.getOrDefault(emptyList())
            for (remoteClient in remoteClients) {
                val localClient = clientDao.getClientByIdOnce(remoteClient.id)

                if (localClient == null) {
                    // New client from server
                    clientDao.insert(remoteClient)
                    count++
                } else if (localClient.syncStatus == EntitySyncStatus.SYNCED.name) {
                    // Only update if local is synced (no pending changes)
                    if (remoteClient.updatedAt > localClient.updatedAt) {
                        clientDao.update(remoteClient)
                        count++
                    }
                }
                // If local has pending changes, skip - local changes take precedence
            }
        }
        return count
    }

    private suspend fun pullHorses(userId: String): Int {
        var count = 0
        val result = horseRemoteDataSource.fetchAllHorses(userId)
        if (result.isSuccess) {
            val remoteHorses = result.getOrDefault(emptyList())
            for (remoteHorse in remoteHorses) {
                val localHorse = horseDao.getHorseByIdOnce(remoteHorse.id)

                if (localHorse == null) {
                    horseDao.insert(remoteHorse)
                    count++
                } else if (localHorse.syncStatus == EntitySyncStatus.SYNCED.name) {
                    if (remoteHorse.updatedAt > localHorse.updatedAt) {
                        horseDao.update(remoteHorse)
                        count++
                    }
                }
            }
        }
        return count
    }

    private suspend fun pullAppointments(userId: String): Int {
        var count = 0
        val result = appointmentRemoteDataSource.fetchAllAppointments(userId)
        if (result.isSuccess) {
            val remoteAppointments = result.getOrDefault(emptyList())
            Log.d(TAG, "Fetched ${remoteAppointments.size} appointments from remote")
            for (remoteAppointment in remoteAppointments) {
                try {
                    val localAppointment = appointmentDao.getAppointmentByIdOnce(remoteAppointment.id)

                    if (localAppointment == null) {
                        // Check if client exists locally before inserting (foreign key constraint)
                        val clientExists = clientDao.getClientByIdOnce(remoteAppointment.clientId) != null
                        if (!clientExists) {
                            Log.w(TAG, "Skipping appointment ${remoteAppointment.id}: client ${remoteAppointment.clientId} not found locally")
                            continue
                        }

                        appointmentDao.insert(remoteAppointment)
                        Log.d(TAG, "Inserted appointment ${remoteAppointment.id}")
                        // Also pull the appointment horses
                        val horsesResult = appointmentRemoteDataSource.fetchAppointmentHorses(remoteAppointment.id)
                        if (horsesResult.isSuccess) {
                            appointmentDao.insertAppointmentHorses(horsesResult.getOrDefault(emptyList()))
                        }
                        count++
                    } else if (localAppointment.syncStatus == EntitySyncStatus.SYNCED.name) {
                        if (remoteAppointment.updatedAt > localAppointment.updatedAt) {
                            appointmentDao.update(remoteAppointment)
                            // Update appointment horses
                            appointmentDao.deleteAppointmentHorses(remoteAppointment.id)
                            val horsesResult = appointmentRemoteDataSource.fetchAppointmentHorses(remoteAppointment.id)
                            if (horsesResult.isSuccess) {
                                appointmentDao.insertAppointmentHorses(horsesResult.getOrDefault(emptyList()))
                            }
                            count++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing appointment ${remoteAppointment.id}: ${e.message}", e)
                }
            }
        } else {
            Log.e(TAG, "Failed to fetch appointments: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
        }
        return count
    }

    private suspend fun pullInvoices(userId: String): Int {
        var count = 0
        val result = invoiceRemoteDataSource.fetchAllInvoices(userId)
        if (result.isSuccess) {
            val remoteInvoices = result.getOrDefault(emptyList())
            for (remoteInvoice in remoteInvoices) {
                val localInvoice = invoiceDao.getInvoiceByIdOnce(remoteInvoice.id)

                if (localInvoice == null) {
                    invoiceDao.insert(remoteInvoice)
                    // Also pull the invoice items
                    val itemsResult = invoiceRemoteDataSource.fetchInvoiceItems(remoteInvoice.id)
                    if (itemsResult.isSuccess) {
                        invoiceDao.insertItems(itemsResult.getOrDefault(emptyList()))
                    }
                    count++
                } else if (localInvoice.syncStatus == EntitySyncStatus.SYNCED.name) {
                    if (remoteInvoice.updatedAt > localInvoice.updatedAt) {
                        invoiceDao.update(remoteInvoice)
                        // Update invoice items
                        invoiceDao.deleteItemsForInvoice(remoteInvoice.id)
                        val itemsResult = invoiceRemoteDataSource.fetchInvoiceItems(remoteInvoice.id)
                        if (itemsResult.isSuccess) {
                            invoiceDao.insertItems(itemsResult.getOrDefault(emptyList()))
                        }
                        count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Update an entity's sync status after successful push
     */
    private suspend fun updateEntitySyncStatus(
        entityType: String,
        entityId: String,
        status: EntitySyncStatus
    ) {
        val now = Instant.now().toEpochMilli()
        when (entityType) {
            "client" -> clientDao.updateSyncStatus(entityId, status.name, now)
            "horse" -> horseDao.updateSyncStatus(entityId, status.name, now)
            "appointment" -> appointmentDao.updateSyncStatus(entityId, status.name, now)
            "invoice" -> invoiceDao.updateSyncStatus(entityId, status.name, now)
        }
    }
}

data class SyncResult(
    val pushedCount: Int,
    val pulledCount: Int
)
