# PRD-002: Offline-First Data Architecture

**Priority**: P0  
**Phase**: 1 - Foundation  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Implement a robust offline-first data synchronization system ensuring the app works reliably in areas with poor or no connectivity—critical for farriers working in rural barns.

### Business Value
- Differentiator vs. competitors with unreliable offline modes
- Reduces user frustration in low-connectivity environments
- Prevents data loss from network failures
- Builds trust through consistent behavior

### Success Metrics
| Metric | Target |
|--------|--------|
| Data loss incidents | Zero |
| Sync completion time | < 30 seconds after connectivity |
| Sync conflict rate | < 1% |
| Core feature offline availability | 100% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-002-01 | Farrier | Use all core features without internet | I can work in barns with no signal | P0 |
| US-002-02 | Farrier | See my data sync status clearly | I know if my data is up to date | P0 |
| US-002-03 | Farrier | Have my changes sync automatically | I don't have to manually sync | P0 |
| US-002-04 | Farrier | Manually trigger a sync | I can force sync when I get signal | P1 |
| US-002-05 | Farrier | Know if there are pending changes | I'm aware of unsaved cloud data | P0 |
| US-002-06 | Farrier | Not lose data if the app crashes | My work is always preserved | P0 |
| US-002-07 | Farrier | Access my data on multiple devices | I can use tablet and phone | P1 |

---

## Functional Requirements

### FR-002-01: Local-First Data Storage
- All data stored in Room database as primary source
- UI always reads from local database
- All write operations go to local database first
- Sync queue tracks pending server operations
- Data available immediately after write (no server wait)

### FR-002-02: Sync Queue Management
- Queue entry created for every local change
- Queue persists across app restarts
- Queue entries: entity type, entity ID, operation, payload, timestamp
- Failed syncs increment retry counter
- Maximum 5 retry attempts before requiring manual intervention
- Queue processing is FIFO with priority for critical operations

### FR-002-03: Background Sync
- WorkManager handles background synchronization
- Periodic sync every 15 minutes when backgrounded
- Immediate sync triggered on:
  - App foregrounding
  - Network connectivity restored
  - Manual sync request
- Expedited work request for critical changes

### FR-002-04: Network State Detection
- Real-time connectivity monitoring
- Graceful degradation to offline mode
- Automatic queue processing on connectivity restore

### FR-002-05: Conflict Resolution
- Last-write-wins based on `updated_at` timestamp
- Server timestamp is authoritative
- Conflicts logged for debugging
- User notified only for significant conflicts

### FR-002-06: Sync Status UI
- Banner shown when changes pending
- Pending change count displayed
- "Sync Now" button when online
- "Offline" indicator when no connectivity
- Last successful sync timestamp

---

## Technical Implementation

### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                    OFFLINE-FIRST ARCHITECTURE                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  UI Layer → ViewModel → Use Case → Repository                  │
│                                        ↓                        │
│                            ┌──────────┴──────────┐              │
│                            ↓                     ↓              │
│                      Room DAO              Remote Source        │
│                      (Primary)                   │               │
│                            ↓                     │               │
│                      SQLite                      │               │
│                            ↓                     │               │
│                      Sync Queue ───→ SyncWorker ─┘               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

```kotlin
// SyncQueueEntity.kt
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: SyncOperation, // INSERT, UPDATE, DELETE
    val payload: String, // JSON
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    val status: SyncStatus = SyncStatus.PENDING
)

// SyncManager.kt
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val syncQueueDao: SyncQueueDao,
    private val syncProcessor: SyncProcessor
) {
    private val workManager = WorkManager.getInstance(context)
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    val pendingCount: Flow<Int> = syncQueueDao.getPendingCount()
    
    fun initialize() {
        schedulePeriodicSync()
        observeNetworkChanges()
    }
    
    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
    
    suspend fun queueChange(
        entityType: String,
        entityId: String,
        operation: SyncOperation,
        payload: String
    ) {
        syncQueueDao.insert(SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payload = payload
        ))
        if (networkMonitor.isOnline.value) triggerExpedited()
    }
}

// SyncWorker.kt
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncProcessor: SyncProcessor
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val result = syncProcessor.processQueue()
            if (result.failedCount > 0 && runAttemptCount < 3) Result.retry()
            else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

// ConflictResolver.kt
class ConflictResolver {
    fun shouldOverwrite(localItem: SyncQueueEntity, serverEntity: Any?): Boolean {
        if (serverEntity == null) return true
        val localTimestamp = localItem.createdAt
        val serverTimestamp = extractTimestamp(serverEntity)
        return localTimestamp.isAfter(serverTimestamp) // Last-write-wins
    }
}

// NetworkMonitor.kt
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = 
        context.getSystemService<ConnectivityManager>()!!
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isOnline.value = true }
        override fun onLost(network: Network) { _isOnline.value = false }
    }
    
    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        _isOnline.value = connectivityManager.activeNetwork != null
    }
}
```

---

## Data Model

```kotlin
// Base for all syncable entities
abstract class SyncableEntity {
    abstract val id: String
    abstract val syncStatus: EntitySyncStatus
    abstract val updatedAt: Instant
}

enum class EntitySyncStatus {
    SYNCED,           // In sync with server
    PENDING_CREATE,   // Created locally
    PENDING_UPDATE,   // Modified locally
    PENDING_DELETE,   // Deleted locally
    CONFLICT          // Conflict detected
}

enum class SyncOperation {
    INSERT, UPDATE, DELETE
}

enum class SyncStatus {
    PENDING, PROCESSING, FAILED, COMPLETED
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Offline : SyncState
    data class Error(val message: String) : SyncState
    data class Success(val timestamp: Long, val count: Int) : SyncState
}
```

---

## Non-Functional Requirements

### Performance
- Local database read: < 50ms
- Local database write: < 100ms
- Sync batch size: 50 records max
- Full sync (1000 records): < 60 seconds
- Background sync battery: < 1%/hour

### Reliability
- Zero data loss guarantee
- Sync queue survives app crashes
- Database survives unclean shutdown
- Automatic recovery from corrupted entries

### Storage
- Database size limit: 500MB warning, 1GB hard limit
- Old sync logs pruned after 30 days

---

## Testing Requirements

### Unit Tests
```kotlin
class SyncManagerTest {
    @Test
    fun `queueChange creates sync queue entry`() = runTest {
        syncManager.queueChange("clients", "123", SyncOperation.INSERT, "{}")
        coVerify { syncQueueDao.insert(match { it.entityId == "123" }) }
    }
    
    @Test
    fun `syncNow returns offline error when no network`() = runTest {
        every { networkMonitor.isOnline } returns MutableStateFlow(false)
        val result = syncManager.syncNow()
        assertTrue(result.exceptionOrNull() is SyncException.NoNetwork)
    }
}

class ConflictResolverTest {
    @Test
    fun `shouldOverwrite returns true when local is newer`() {
        val local = createItem(Instant.parse("2024-01-15T10:00:00Z"))
        val server = mapOf("updated_at" to "2024-01-15T09:00:00Z")
        assertTrue(resolver.shouldOverwrite(local, server))
    }
}
```

### Integration Tests
```kotlin
@HiltAndroidTest
class SyncIntegrationTest {
    @Test
    fun `offline changes sync when network restored`() = runTest {
        fakeNetworkMonitor.setOnline(false)
        clientRepository.create(client)
        assertEquals(1, syncQueueDao.getPendingItems().size)
        
        fakeNetworkMonitor.setOnline(true)
        advanceUntilIdle()
        
        assertEquals(0, syncQueueDao.getPendingItems().size)
    }
}
```

---

## AI-Assisted Development Guidelines

### Code Generation Guardrails
1. **ALWAYS** use transactions for related writes
2. **ALWAYS** include sync queue entry for any write
3. **NEVER** bypass repository to write directly to DAO
4. **ALWAYS** check network state before sync
5. **NEVER** delete sync queue entries before confirmation
6. **ALWAYS** log sync operations for debugging

### Review Checklist
- [ ] All writes create sync queue entries
- [ ] Network state checked before remote calls
- [ ] Retry logic with backoff
- [ ] Transactions for related operations
- [ ] UI responsive during sync

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-002-01 | App functions fully offline for CRUD | Integration test |
| AC-002-02 | Changes sync within 30s of connectivity | Integration test |
| AC-002-03 | Pending change count accurate | UI test |
| AC-002-04 | Manual sync works | E2E test |
| AC-002-05 | Sync survives app crash | Integration test |
| AC-002-06 | Conflicts resolved with last-write-wins | Unit test |
| AC-002-07 | Background sync runs every 15 min | WorkManager test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-001 (Auth) | Internal | Required |
| Room database | Library | Available |
| WorkManager | Library | Available |
| Supabase client | Library | Available |

---

## Out of Scope
- Real-time sync (WebSocket)
- Selective sync (choose what to sync)
- Peer-to-peer sync
- Offline-only mode (requires initial online setup)
