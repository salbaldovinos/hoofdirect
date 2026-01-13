# FRD-002: Offline-First Data Architecture

**PRD Reference:** PRD-002  
**Priority:** P0  
**Phase:** 1 - Foundation  
**Estimated Duration:** 2 weeks

---

## 1. Overview

### 1.1 Purpose

Implement a robust offline-first data architecture that enables farriers to use the app without internet connectivity. The local Room database serves as the primary data source, with background synchronization to Supabase when connectivity is available.

### 1.2 Core Principles

1. **Local First**: UI always reads from Room database, never directly from network
2. **Optimistic Updates**: Changes appear immediately in UI, sync happens in background
3. **Conflict Resolution**: Last-write-wins based on server timestamp
4. **Sync Queue**: All mutations tracked and replayed when online
5. **Graceful Degradation**: App remains fully functional offline

### 1.3 Scope

| In Scope | Out of Scope |
|----------|--------------|
| Room database as single source of truth | Real-time collaboration |
| Background sync with WorkManager | Partial sync (all-or-nothing per entity) |
| Conflict detection and resolution | Manual conflict resolution UI |
| Sync status indicators | Offline-first photo uploads |
| Network state monitoring | P2P sync between devices |
| Retry logic with exponential backoff | |

---

## 2. Architecture Overview

### 2.1 Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         OFFLINE-FIRST ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────┐        ┌──────────────┐        ┌──────────────┐          │
│  │   UI     │◀──────▶│  ViewModel   │◀──────▶│  Repository  │          │
│  │ (Compose)│        │              │        │              │          │
│  └──────────┘        └──────────────┘        └──────┬───────┘          │
│                                                     │                   │
│                                 ┌───────────────────┴───────────────┐   │
│                                 │                                   │   │
│                                 ▼                                   ▼   │
│                          ┌──────────────┐                   ┌───────────┐
│                          │  Room DB     │                   │ Remote    │
│                          │  (Primary)   │                   │ Data Src  │
│                          └──────┬───────┘                   └─────┬─────┘
│                                 │                                 │     │
│                                 │         ┌──────────────┐        │     │
│                                 │         │  SyncQueue   │        │     │
│                                 └────────▶│  (Room)      │◀───────┘     │
│                                           └──────┬───────┘              │
│                                                  │                      │
│                                                  ▼                      │
│                                           ┌──────────────┐              │
│                                           │  SyncWorker  │              │
│                                           │ (WorkManager)│              │
│                                           └──────┬───────┘              │
│                                                  │                      │
│                                                  ▼                      │
│                                           ┌──────────────┐              │
│                                           │   Supabase   │              │
│                                           │   (Server)   │              │
│                                           └──────────────┘              │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Read Path

```
User Request → ViewModel → Repository → Room DAO → Return Data
                                         ↓
                              (Never hits network for reads)
```

### 2.3 Write Path

```
User Action → ViewModel → Repository → Room DAO (immediate save)
                              ↓
                        Create SyncQueueEntry
                              ↓
                        Return Success to UI
                              ↓
                        SyncWorker (background)
                              ↓
                        Supabase API Call
                              ↓
                        Update sync_status → SYNCED
```

---

## 3. Functional Specifications

### 3.1 Network Monitoring

#### 3.1.1 NetworkMonitor Implementation

```kotlin
// NetworkMonitor.kt
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val _connectionType = MutableStateFlow<ConnectionType>(ConnectionType.NONE)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            updateConnectionType()
        }
        
        override fun onLost(network: Network) {
            _isOnline.value = false
            _connectionType.value = ConnectionType.NONE
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            updateConnectionType()
        }
    }
    
    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        // Initial state
        _isOnline.value = connectivityManager.activeNetwork != null
    }
    
    private fun updateConnectionType() {
        val capabilities = connectivityManager.getNetworkCapabilities(
            connectivityManager.activeNetwork
        )
        _connectionType.value = when {
            capabilities == null -> ConnectionType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 
                ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 
                ConnectionType.CELLULAR
            else -> ConnectionType.OTHER
        }
    }
}

enum class ConnectionType {
    NONE,
    WIFI,
    CELLULAR,
    OTHER
}
```

#### 3.1.2 Network State UI Indicator

**Behavior:**

| Network State | UI Indicator |
|---------------|--------------|
| Online, synced | No indicator (clean state) |
| Online, sync in progress | Small sync icon in app bar |
| Online, sync pending | Yellow dot badge on menu |
| Offline | "Offline" banner at top of screen |
| Offline, changes pending | "Offline - X changes pending" banner |

**Offline Banner Specification:**

```kotlin
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    pendingChanges: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (pendingChanges > 0) {
                        "Offline • $pendingChanges changes will sync when online"
                    } else {
                        "You're offline"
                    },
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

### 3.2 Sync Queue

#### 3.2.1 SyncQueue Entity

```kotlin
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
    val entityType: String, // "client", "horse", "appointment", etc.
    
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    
    @ColumnInfo(name = "operation")
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    
    @ColumnInfo(name = "payload")
    val payload: String, // JSON of the entity data
    
    @ColumnInfo(name = "status")
    val status: String = "PENDING", // "PENDING", "IN_PROGRESS", "FAILED", "COMPLETED"
    
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "priority")
    val priority: Int = 0 // Higher = more urgent
)
```

#### 3.2.2 SyncQueue DAO

```kotlin
@Dao
interface SyncQueueDao {
    @Query("""
        SELECT * FROM sync_queue 
        WHERE status = 'PENDING' OR status = 'FAILED'
        ORDER BY priority DESC, created_at ASC
        LIMIT :limit
    """)
    suspend fun getPendingOperations(limit: Int = 50): List<SyncQueueEntity>
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    fun observePendingCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    suspend fun getPendingCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncQueueEntity): Long
    
    @Query("UPDATE sync_queue SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Instant = Instant.now())
    
    @Query("""
        UPDATE sync_queue 
        SET status = 'FAILED', 
            retry_count = retry_count + 1, 
            last_error = :error,
            updated_at = :now 
        WHERE id = :id
    """)
    suspend fun markFailed(id: Long, error: String, now: Instant = Instant.now())
    
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED' AND updated_at < :before")
    suspend fun deleteCompletedBefore(before: Instant)
    
    @Query("""
        SELECT * FROM sync_queue 
        WHERE entity_type = :entityType AND entity_id = :entityId
        ORDER BY created_at DESC LIMIT 1
    """)
    suspend fun getLatestForEntity(entityType: String, entityId: String): SyncQueueEntity?
    
    // Coalesce multiple updates into one
    @Query("""
        DELETE FROM sync_queue 
        WHERE entity_type = :entityType 
        AND entity_id = :entityId 
        AND operation = 'UPDATE'
        AND status = 'PENDING'
        AND id != :keepId
    """)
    suspend fun coalesceUpdates(entityType: String, entityId: String, keepId: Long)
}
```

#### 3.2.3 Sync Queue Operations

**Operation Priority:**

| Operation | Priority | Rationale |
|-----------|----------|-----------|
| DELETE | 0 (lowest) | Process last to avoid orphan issues |
| CREATE | 1 | Process before updates |
| UPDATE | 2 (highest) | Most recent state matters most |

**Operation Coalescing:**

When multiple updates to the same entity are pending:
1. Keep only the most recent pending UPDATE
2. DELETE previous pending UPDATEs for same entity
3. If CREATE then UPDATE pending, merge into single CREATE with latest data
4. If CREATE then DELETE pending, remove both (entity never synced)

```kotlin
// SyncQueueManager.kt
class SyncQueueManager @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val json: Json
) {
    suspend fun <T : SyncableEntity> enqueue(
        entity: T,
        operation: SyncOperation
    ) {
        val entityType = entity.syncEntityType
        val entityId = entity.syncEntityId
        
        // Check for existing pending operations
        val existing = syncQueueDao.getLatestForEntity(entityType, entityId)
        
        when {
            // CREATE followed by DELETE = remove both
            existing?.operation == "CREATE" && operation == SyncOperation.DELETE -> {
                syncQueueDao.delete(existing.id)
                return
            }
            
            // CREATE followed by UPDATE = update the CREATE payload
            existing?.operation == "CREATE" && operation == SyncOperation.UPDATE -> {
                syncQueueDao.insert(existing.copy(
                    payload = json.encodeToString(entity),
                    updatedAt = Instant.now()
                ))
                return
            }
            
            // UPDATE followed by UPDATE = keep only latest
            existing?.operation == "UPDATE" && operation == SyncOperation.UPDATE -> {
                // Insert new, then coalesce
                val newId = syncQueueDao.insert(SyncQueueEntity(
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation.name,
                    payload = json.encodeToString(entity),
                    priority = operation.priority
                ))
                syncQueueDao.coalesceUpdates(entityType, entityId, newId)
                return
            }
        }
        
        // Default: insert new operation
        syncQueueDao.insert(SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            operation = operation.name,
            payload = json.encodeToString(entity),
            priority = operation.priority
        ))
    }
}

enum class SyncOperation(val priority: Int) {
    CREATE(1),
    UPDATE(2),
    DELETE(0)
}
```

### 3.3 Sync Worker

#### 3.3.1 SyncWorker Implementation

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "sync_worker"
        const val TAG_PERIODIC = "sync_periodic"
        const val TAG_IMMEDIATE = "sync_immediate"
        
        private const val MAX_RETRY_COUNT = 5
        private const val BATCH_SIZE = 20
    }
    
    override suspend fun doWork(): Result {
        // Check network availability
        if (!networkMonitor.isOnline.value) {
            return Result.retry()
        }
        
        return try {
            val syncResult = syncManager.performSync(BATCH_SIZE)
            
            when (syncResult) {
                is SyncResult.Success -> {
                    if (syncResult.hasMore) {
                        // More items to sync, schedule immediate follow-up
                        enqueueImmediateSync(applicationContext)
                    }
                    Result.success()
                }
                is SyncResult.PartialSuccess -> {
                    // Some items failed, retry later
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        // Log failures for manual review
                        Result.failure()
                    }
                }
                is SyncResult.Failure -> {
                    if (syncResult.isRetryable && runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
```

#### 3.3.2 WorkManager Configuration

```kotlin
// SyncWorkScheduler.kt
@Singleton
class SyncWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule periodic sync every 15 minutes
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val periodicWork = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG_PERIODIC)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
    
    /**
     * Trigger immediate sync (e.g., on app foreground, connectivity restored)
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val immediateWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG_IMMEDIATE)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        
        workManager.enqueue(immediateWork)
    }
    
    /**
     * Cancel all sync work
     */
    fun cancelAllSync() {
        workManager.cancelAllWorkByTag(SyncWorker.TAG_PERIODIC)
        workManager.cancelAllWorkByTag(SyncWorker.TAG_IMMEDIATE)
    }
    
    /**
     * Observe sync work status
     */
    fun observeSyncStatus(): Flow<SyncStatus> {
        return workManager.getWorkInfosByTagFlow(SyncWorker.TAG_IMMEDIATE)
            .map { workInfos ->
                when {
                    workInfos.any { it.state == WorkInfo.State.RUNNING } -> 
                        SyncStatus.SYNCING
                    workInfos.any { it.state == WorkInfo.State.ENQUEUED } -> 
                        SyncStatus.PENDING
                    workInfos.any { it.state == WorkInfo.State.FAILED } -> 
                        SyncStatus.FAILED
                    else -> 
                        SyncStatus.IDLE
                }
            }
    }
}

enum class SyncStatus {
    IDLE,
    PENDING,
    SYNCING,
    FAILED
}
```

#### 3.3.3 Sync Triggers

| Trigger | Action |
|---------|--------|
| App comes to foreground | Immediate sync if pending items |
| Network connectivity restored | Immediate sync if pending items |
| User performs mutation | Add to queue, trigger immediate sync |
| Periodic (every 15 minutes) | Sync all pending items |
| User pull-to-refresh | Force sync current screen's data |
| User taps "Sync Now" in settings | Force full sync |

### 3.4 Conflict Resolution

#### 3.4.1 Conflict Detection

```kotlin
// ConflictDetector.kt
class ConflictDetector {
    /**
     * Detect if local changes conflict with server state
     * 
     * Conflict exists when:
     * - Local entity has been modified since last sync
     * - Server entity has been modified since last sync
     * - Both modifications occurred after the last known sync point
     */
    fun <T : SyncableEntity> detectConflict(
        local: T,
        server: T,
        lastSyncTimestamp: Instant
    ): ConflictResult<T> {
        val localModified = local.updatedAt > lastSyncTimestamp
        val serverModified = server.updatedAt > lastSyncTimestamp
        
        return when {
            !localModified && !serverModified -> ConflictResult.NoConflict(server)
            localModified && !serverModified -> ConflictResult.LocalWins(local)
            !localModified && serverModified -> ConflictResult.ServerWins(server)
            else -> {
                // Both modified - last write wins
                if (local.updatedAt > server.updatedAt) {
                    ConflictResult.LocalWins(local)
                } else {
                    ConflictResult.ServerWins(server)
                }
            }
        }
    }
}

sealed class ConflictResult<T> {
    data class NoConflict<T>(val data: T) : ConflictResult<T>()
    data class LocalWins<T>(val data: T) : ConflictResult<T>()
    data class ServerWins<T>(val data: T) : ConflictResult<T>()
}
```

#### 3.4.2 Conflict Resolution Strategy

**Default: Last-Write-Wins (LWW)**

| Scenario | Resolution | Rationale |
|----------|------------|-----------|
| Client A updates, Client B updates later | Client B's changes win | Most recent data is likely correct |
| Offline edit, online edit after | Compare timestamps | Later edit wins |
| Create conflict (same ID) | Server wins | Unlikely with UUIDs, but server is authoritative |
| Delete vs Update | Delete wins | Explicit deletion intent takes precedence |

**Resolution Flow:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                     CONFLICT RESOLUTION FLOW                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐                                                    │
│  │ Push local  │                                                    │
│  │ changes     │                                                    │
│  └──────┬──────┘                                                    │
│         │                                                           │
│         ▼                                                           │
│  ┌─────────────────────┐                                            │
│  │ Server responds     │                                            │
│  └──────────┬──────────┘                                            │
│             │                                                       │
│    ┌────────┴────────┐                                              │
│    ▼                 ▼                                              │
│ ┌──────────┐   ┌───────────┐                                        │
│ │ 200 OK   │   │ 409       │                                        │
│ │ Success  │   │ Conflict  │                                        │
│ └────┬─────┘   └─────┬─────┘                                        │
│      │               │                                              │
│      ▼               ▼                                              │
│ ┌──────────┐   ┌───────────────┐                                    │
│ │ Update   │   │ Fetch server  │                                    │
│ │ local    │   │ version       │                                    │
│ │ sync_    │   └───────┬───────┘                                    │
│ │ status   │           │                                            │
│ │ = SYNCED │           ▼                                            │
│ └──────────┘   ┌───────────────┐                                    │
│                │ Compare       │                                    │
│                │ timestamps    │                                    │
│                └───────┬───────┘                                    │
│                        │                                            │
│           ┌────────────┴────────────┐                               │
│           ▼                         ▼                               │
│    ┌─────────────┐          ┌─────────────┐                         │
│    │ Local newer │          │ Server newer│                         │
│    │             │          │             │                         │
│    │ Retry push  │          │ Update local│                         │
│    │ with force  │          │ with server │                         │
│    │ flag        │          │ data        │                         │
│    └─────────────┘          └─────────────┘                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### 3.4.3 Entity Sync Status

```kotlin
enum class EntitySyncStatus {
    SYNCED,           // Local matches server
    PENDING_CREATE,   // Created locally, not yet on server
    PENDING_UPDATE,   // Updated locally, not yet synced
    PENDING_DELETE,   // Marked for deletion, not yet synced
    CONFLICT          // Conflict detected, needs resolution
}
```

**Status Display:**

| Status | Icon | Tooltip |
|--------|------|---------|
| SYNCED | None | — |
| PENDING_CREATE | Cloud with up arrow | "Saving..." |
| PENDING_UPDATE | Cloud with up arrow | "Saving..." |
| PENDING_DELETE | Cloud with X | "Deleting..." |
| CONFLICT | Warning triangle | "Sync conflict - tap to resolve" |

### 3.5 Full Sync (Pull)

#### 3.5.1 Initial Sync

**Triggered:** First app launch after sign-in

**Flow:**

1. Show "Syncing your data..." loading screen
2. Fetch all entities in parallel:
   - Clients
   - Horses
   - Appointments
   - Service prices
   - User profile
3. Insert all into Room database
4. Track sync timestamp
5. Navigate to home screen

**Error Handling:**

| Error | Behavior |
|-------|----------|
| Network timeout | Retry 3 times, then show "Unable to sync. Retry?" |
| Partial failure | Insert what succeeded, show "Some data couldn't sync" |
| Auth error | Sign out user, navigate to sign-in |

#### 3.5.2 Incremental Sync

**Triggered:** Periodic sync, app foreground, connectivity restored

**Strategy:** Fetch only entities modified since last sync timestamp

```kotlin
// SyncManager.kt - Incremental pull
suspend fun pullChanges(): Result<PullResult> {
    val lastSync = syncMetadataDao.getLastSyncTimestamp() 
        ?: Instant.EPOCH
    
    return try {
        // Parallel fetch of all entity types
        coroutineScope {
            val clientsDeferred = async { 
                supabaseClient.from("clients")
                    .select()
                    .filter { gt("updated_at", lastSync.toString()) }
                    .decodeList<ClientDto>()
            }
            val horsesDeferred = async { 
                supabaseClient.from("horses")
                    .select()
                    .filter { gt("updated_at", lastSync.toString()) }
                    .decodeList<HorseDto>()
            }
            // ... other entities
            
            val clients = clientsDeferred.await()
            val horses = horsesDeferred.await()
            
            // Upsert into local database
            clientDao.upsertAll(clients.map { it.toEntity() })
            horseDao.upsertAll(horses.map { it.toEntity() })
            
            // Update sync timestamp
            syncMetadataDao.updateLastSyncTimestamp(Instant.now())
            
            Result.success(PullResult(
                clientsUpdated = clients.size,
                horsesUpdated = horses.size
            ))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 3.6 Entity-Specific Sync Rules

#### 3.6.1 Sync Order (Dependencies)

```
1. Users (no dependencies)
2. Clients (depends on Users)
3. Service Prices (depends on Users)
4. Horses (depends on Clients)
5. Appointments (depends on Clients, Horses)
6. Appointment Horses (depends on Appointments, Horses)
7. Invoices (depends on Appointments, Clients)
8. Mileage Logs (depends on Users, optionally Appointments)
```

#### 3.6.2 Cascade Handling

| Action | Cascade Behavior |
|--------|------------------|
| Delete Client | Delete all horses, appointments, invoices for client |
| Delete Horse | Remove from future appointments, keep history |
| Delete Appointment | Keep invoice if generated, update horse next_due_date |

---

## 4. Data Models

### 4.1 Sync Metadata Entity

```kotlin
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String, // "last_sync_timestamp", "initial_sync_complete", etc.
    
    val value: String,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
```

### 4.2 Syncable Entity Interface

```kotlin
interface SyncableEntity {
    val syncEntityType: String
    val syncEntityId: String
    val updatedAt: Instant
    val syncStatus: EntitySyncStatus
}
```

---

## 5. Error Handling

### 5.1 Sync Error Classification

| Error Type | Retryable | Max Retries | Backoff |
|------------|-----------|-------------|---------|
| Network timeout | Yes | 5 | Exponential (1s, 2s, 4s, 8s, 16s) |
| 5xx Server error | Yes | 3 | Exponential |
| 429 Rate limited | Yes | 5 | Use Retry-After header |
| 400 Bad request | No | 0 | — |
| 401 Unauthorized | No | 0 | Trigger re-auth |
| 403 Forbidden | No | 0 | — |
| 404 Not found | No | 0 | Remove from queue |
| 409 Conflict | Yes* | 1 | Resolve, then retry |

### 5.2 Error Recovery

```kotlin
// SyncErrorHandler.kt
class SyncErrorHandler @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend fun handleError(error: SyncError, entry: SyncQueueEntity): ErrorAction {
        return when (error) {
            is SyncError.Network -> {
                if (entry.retryCount < 5) ErrorAction.Retry(
                    delay = calculateBackoff(entry.retryCount)
                ) else ErrorAction.MarkFailed
            }
            
            is SyncError.Server -> {
                when (error.code) {
                    in 500..599 -> ErrorAction.Retry(
                        delay = calculateBackoff(entry.retryCount)
                    )
                    429 -> ErrorAction.Retry(
                        delay = error.retryAfter ?: 60_000L
                    )
                    401 -> {
                        authRepository.refreshTokenIfNeeded()
                        ErrorAction.Retry(delay = 1000)
                    }
                    404 -> ErrorAction.Remove
                    409 -> ErrorAction.ResolveConflict
                    else -> ErrorAction.MarkFailed
                }
            }
            
            is SyncError.Validation -> ErrorAction.MarkFailed
        }
    }
    
    private fun calculateBackoff(retryCount: Int): Long {
        return minOf(1000L * (1 shl retryCount), 60_000L) // Max 60 seconds
    }
}

sealed class ErrorAction {
    data class Retry(val delay: Long) : ErrorAction()
    object Remove : ErrorAction()
    object MarkFailed : ErrorAction()
    object ResolveConflict : ErrorAction()
}
```

---

## 6. Acceptance Criteria

### 6.1 Offline Operations

| ID | Given | When | Then |
|----|-------|------|------|
| AC-002-01 | Device is offline | User creates a new client | Client saved locally, sync_status = PENDING_CREATE, appears in client list |
| AC-002-02 | Device is offline | User edits a client | Client updated locally, sync_status = PENDING_UPDATE |
| AC-002-03 | Device is offline | User deletes a client | Client hidden from list, sync_status = PENDING_DELETE |
| AC-002-04 | Device is offline | User views client list | All local clients displayed normally |
| AC-002-05 | Device was offline with pending changes | Device comes online | SyncWorker triggers within 30 seconds |

### 6.2 Sync Operations

| ID | Given | When | Then |
|----|-------|------|------|
| AC-002-06 | 5 pending creates exist | Sync runs | All 5 created on server, sync_status = SYNCED for all |
| AC-002-07 | Network fails during sync | Sync retried | Retry with exponential backoff up to 5 times |
| AC-002-08 | Server returns 409 Conflict | Local is newer | Local data pushed with force flag |
| AC-002-09 | Server returns 409 Conflict | Server is newer | Local updated with server data |
| AC-002-10 | App in foreground | 15 minutes pass | Periodic sync runs |

### 6.3 UI Feedback

| ID | Given | When | Then |
|----|-------|------|------|
| AC-002-11 | Device offline | Any screen | Offline banner visible at top |
| AC-002-12 | 3 pending changes exist | User views offline banner | Banner shows "3 changes will sync when online" |
| AC-002-13 | Sync in progress | User views app bar | Small sync icon visible |
| AC-002-14 | Entity has PENDING_UPDATE status | User views entity | Subtle "Saving..." indicator visible |
| AC-002-15 | Sync fails after retries | User in settings | "Sync failed" message with retry option |

### 6.4 Data Integrity

| ID | Given | When | Then |
|----|-------|------|------|
| AC-002-16 | Same entity updated twice offline | Sync runs | Only final state synced (coalescing) |
| AC-002-17 | Entity created then deleted offline | Sync runs | Nothing synced (both operations cancelled) |
| AC-002-18 | Client deleted | Sync runs | All horses, appointments cascade deleted |
| AC-002-19 | App force-closed during sync | App reopens | Sync resumes from where it left off |
| AC-002-20 | User signs out | — | All local data cleared, sync queue cleared |

---

## 7. Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Sync queue insert | < 50ms | Time to add to queue |
| Single entity sync | < 2 seconds | Network + DB update |
| Batch sync (20 items) | < 10 seconds | All items synced |
| Initial full sync | < 30 seconds | For typical user (100 clients, 200 horses) |
| Offline read latency | < 100ms | Room query to render |
| Network state detection | < 500ms | Time to detect connectivity change |

---

## 8. Test Specifications

### 8.1 Unit Tests

```kotlin
class SyncQueueManagerTest {
    @Test
    fun `CREATE followed by DELETE removes both from queue`() = runTest {
        // Arrange
        val entity = ClientEntity(id = "123", name = "Test")
        manager.enqueue(entity, SyncOperation.CREATE)
        
        // Act
        manager.enqueue(entity, SyncOperation.DELETE)
        
        // Assert
        val pending = syncQueueDao.getPendingOperations()
        assertTrue(pending.isEmpty())
    }
    
    @Test
    fun `multiple UPDATEs coalesce into one`() = runTest {
        // Arrange
        val entity = ClientEntity(id = "123", name = "Original")
        manager.enqueue(entity, SyncOperation.UPDATE)
        
        val updated = entity.copy(name = "Updated")
        manager.enqueue(updated, SyncOperation.UPDATE)
        
        val final = updated.copy(name = "Final")
        manager.enqueue(final, SyncOperation.UPDATE)
        
        // Act
        val pending = syncQueueDao.getPendingOperations()
        
        // Assert
        assertEquals(1, pending.size)
        assertTrue(pending[0].payload.contains("Final"))
    }
}

class ConflictDetectorTest {
    @Test
    fun `server newer wins in conflict`() {
        val lastSync = Instant.parse("2025-01-01T00:00:00Z")
        val local = entity.copy(updatedAt = Instant.parse("2025-01-01T01:00:00Z"))
        val server = entity.copy(updatedAt = Instant.parse("2025-01-01T02:00:00Z"))
        
        val result = detector.detectConflict(local, server, lastSync)
        
        assertTrue(result is ConflictResult.ServerWins)
    }
    
    @Test
    fun `local newer wins in conflict`() {
        val lastSync = Instant.parse("2025-01-01T00:00:00Z")
        val local = entity.copy(updatedAt = Instant.parse("2025-01-01T03:00:00Z"))
        val server = entity.copy(updatedAt = Instant.parse("2025-01-01T02:00:00Z"))
        
        val result = detector.detectConflict(local, server, lastSync)
        
        assertTrue(result is ConflictResult.LocalWins)
    }
}
```

### 8.2 Integration Tests

```kotlin
@HiltAndroidTest
class SyncIntegrationTest {
    @Test
    fun offlineCreation_syncsWhenOnline() = runTest {
        // Arrange: Go offline
        networkMonitor.setOffline()
        
        // Act: Create client offline
        val client = ClientEntity(name = "Offline Client")
        clientRepository.create(client)
        
        // Assert: In queue
        assertEquals(1, syncQueueDao.getPendingCount())
        assertEquals(EntitySyncStatus.PENDING_CREATE, client.syncStatus)
        
        // Act: Go online
        networkMonitor.setOnline()
        advanceTimeBy(35_000) // Wait for sync trigger
        
        // Assert: Synced
        assertEquals(0, syncQueueDao.getPendingCount())
        val synced = clientDao.getById(client.id)
        assertEquals(EntitySyncStatus.SYNCED, synced?.syncStatus)
    }
}
```

---

## 9. File References

| Purpose | File Path |
|---------|-----------|
| Network Monitor | `core/network/NetworkMonitor.kt` |
| Sync Queue Entity | `core/database/entity/SyncQueueEntity.kt` |
| Sync Queue DAO | `core/database/dao/SyncQueueDao.kt` |
| Sync Queue Manager | `core/sync/SyncQueueManager.kt` |
| Sync Worker | `core/sync/SyncWorker.kt` |
| Sync Work Scheduler | `core/sync/SyncWorkScheduler.kt` |
| Sync Manager | `core/sync/SyncManager.kt` |
| Conflict Detector | `core/sync/ConflictDetector.kt` |
| Sync Error Handler | `core/sync/SyncErrorHandler.kt` |
| Offline Banner | `core/designsystem/component/OfflineBanner.kt` |

---

## 10. Dependencies

| Dependency | Purpose | Version |
|------------|---------|---------|
| Room | Local database | 2.6+ |
| WorkManager | Background sync | 2.9+ |
| Supabase-kt | Remote data source | 2.0+ |
| kotlinx-serialization | JSON serialization | 1.6+ |
| kotlinx-coroutines | Async operations | 1.7+ |
