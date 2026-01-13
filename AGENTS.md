# AGENTS.md — AI-Assisted Development Guidelines

This document provides guidelines for AI coding agents (Claude, Cursor, GitHub Copilot, etc.) working on the Hoof Direct Android application.

---

## Project Context

Hoof Direct is a native Android CRM for professional farriers (horseshoers). The app's key differentiator is **route optimization**—no competitor offers this feature. The app must work reliably offline since farriers often work in rural barns with no cell service.

### Critical Understanding

- **Package name**: `com.hoofdirect.app`
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin 1.9+
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture with Hilt DI
- **Database**: Room (offline-first, local database is source of truth)
- **Backend**: Supabase (PostgreSQL + Auth + Edge Functions)

---

## Documentation Structure

Before implementing any feature, **always read the relevant PRD and FRD**:

| Document Type | Purpose | Naming Convention |
|---------------|---------|-------------------|
| PRD (Product Requirements) | What to build and why | `PRD-XXX-feature.md` |
| FRD (Functional Requirements) | How to build it (implementation details) | `FRD-XXX-feature.md` |

### Document Index

- `00-PRD-INDEX.md` — Overview of all PRDs and development phases
- `00-FRD-INDEX.md` — Overview of all FRDs with technical specifications

---

## Security-First Code Generation

### Absolute Rules (Never Violate)

1. **No Hardcoded Secrets**
   ```kotlin
   // ❌ NEVER
   const val API_KEY = "sk_live_abc123"
   
   // ✅ ALWAYS
   BuildConfig.SUPABASE_ANON_KEY
   ```

2. **No Logging of Sensitive Data**
   ```kotlin
   // ❌ NEVER
   Log.d("Auth", "Password: $password")
   Log.d("User", "Token: ${user.accessToken}")
   
   // ✅ ALWAYS
   Log.d("Auth", "Login attempt for user: ${email.hashCode()}")
   ```

3. **Encrypted Storage for Tokens**
   ```kotlin
   // ❌ NEVER
   sharedPrefs.putString("access_token", token)
   
   // ✅ ALWAYS
   EncryptedSharedPreferences for tokens
   ```

4. **Input Validation**
   ```kotlin
   // ✅ ALWAYS validate before processing
   fun createClient(name: String, email: String): Result<Client> {
       if (name.isBlank()) return Result.failure(ValidationError("Name required"))
       if (!email.isValidEmail()) return Result.failure(ValidationError("Invalid email"))
       // proceed...
   }
   ```

5. **Parameterized Queries Only**
   ```kotlin
   // ❌ NEVER (SQL injection risk)
   @Query("SELECT * FROM clients WHERE name = '$name'")
   
   // ✅ ALWAYS (Room handles this automatically)
   @Query("SELECT * FROM clients WHERE name = :name")
   fun getClientByName(name: String): Flow<Client?>
   ```

---

## Code Review Checklist

Before completing any code generation, verify:

```
□ No hardcoded credentials or API keys
□ All user inputs validated
□ SQL injection prevention (Room parameterized queries)
□ Proper exception handling with user-friendly messages
□ Sensitive data uses encrypted storage
□ Network calls use HTTPS only
□ Error messages don't expose internal details
□ Logging excludes PII (personally identifiable information)
□ Authentication tokens handled securely
□ Offline functionality doesn't bypass security
```

---

## Architecture Patterns

### Repository Pattern (Required for All Data Access)

```kotlin
// Repository is the single source of truth
class ClientRepository @Inject constructor(
    private val clientDao: ClientDao,           // Local
    private val remoteDataSource: ClientRemoteDataSource,  // Remote
    private val syncManager: SyncManager
) {
    // Always read from local
    fun getClients(): Flow<List<Client>> = clientDao.getAllClients()
    
    // Always write to local first, then queue sync
    suspend fun createClient(client: Client): Result<Client> {
        val entity = client.toEntity().copy(
            syncStatus = EntitySyncStatus.PENDING_CREATE
        )
        clientDao.insert(entity)
        syncManager.queueChange("clients", entity.id, SyncOperation.INSERT, entity.toJson())
        return Result.success(client)
    }
}
```

### ViewModel Pattern

```kotlin
@HiltViewModel
class ClientListViewModel @Inject constructor(
    private val getClientsUseCase: GetClientsUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ClientListUiState())
    val uiState: StateFlow<ClientListUiState> = _uiState.asStateFlow()
    
    init {
        loadClients()
    }
    
    private fun loadClients() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getClientsUseCase()
                .catch { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = error.toAppError()
                    )}
                }
                .collect { clients ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        clients = clients,
                        error = null
                    )}
                }
        }
    }
}

data class ClientListUiState(
    val isLoading: Boolean = false,
    val clients: List<Client> = emptyList(),
    val error: AppError? = null
)
```

### Error Handling Pattern

```kotlin
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val error: AppError) : Result<T>()
}

sealed class AppError {
    data class Network(val message: String, val isOffline: Boolean) : AppError()
    data class Validation(val field: String, val message: String) : AppError()
    data class Server(val code: Int, val message: String) : AppError()
    data class Auth(val type: AuthErrorType) : AppError()
    data class Unknown(val throwable: Throwable) : AppError()
}
```

### Sync Status Pattern

All syncable entities must include:

```kotlin
enum class EntitySyncStatus {
    SYNCED,           // In sync with server
    PENDING_CREATE,   // Created locally, not synced
    PENDING_UPDATE,   // Modified locally, not synced
    PENDING_DELETE,   // Deleted locally, not synced
    CONFLICT          // Server has different version
}
```

---

## Offline-First Requirements

### Critical Rules

1. **Always write locally first**
   ```kotlin
   // Write to Room → Queue sync → Return success
   // Never wait for network to confirm
   ```

2. **Always create sync queue entries**
   ```kotlin
   suspend fun updateClient(client: Client) {
       clientDao.update(client.copy(syncStatus = PENDING_UPDATE))
       syncManager.queueChange("clients", client.id, UPDATE, client.toJson())
   }
   ```

3. **Never bypass repository for writes**
   ```kotlin
   // ❌ NEVER write directly to DAO from ViewModel
   // ✅ ALWAYS go through Repository
   ```

4. **Use transactions for related writes**
   ```kotlin
   @Transaction
   suspend fun createAppointmentWithHorses(
       appointment: Appointment,
       horses: List<Horse>
   )
   ```

---

## UI/UX Guidelines

### Material 3 Components

Use Material 3 components consistently:

```kotlin
// Top App Bars
LargeTopAppBar()      // Detail screens
TopAppBar()           // List screens
CenterAlignedTopAppBar()  // Special cases

// Navigation
NavigationBar()       // Bottom navigation (5 items max)
NavigationRail()      // Tablet layouts

// Inputs
OutlinedTextField()   // Form inputs
SearchBar()           // Search functionality

// Feedback
Snackbar()           // Transient messages
AlertDialog()        // Confirmations
```

### Accessibility Requirements

```kotlin
// Minimum touch target: 48dp
Modifier.size(48.dp)

// Content descriptions for icons
Icon(
    imageVector = Icons.Default.Add,
    contentDescription = "Add new client"  // Always provide!
)

// Semantic properties
Modifier.semantics {
    heading()  // For screen reader navigation
}
```

### Field-Ready Design

Farriers use the app in barns with dirty hands and bright sunlight:

- Touch targets: Minimum 48dp, prefer 56dp for primary actions
- Contrast: WCAG AA minimum (4.5:1 for text)
- Font scaling: Support up to 200%
- Glove-friendly: Large buttons, swipe gestures

---

## Testing Requirements

### Unit Tests (Required)

```kotlin
class ClientViewModelTest {
    @Test
    fun `loadClients updates state with client list`() = runTest {
        // Given
        coEvery { getClientsUseCase() } returns flowOf(listOf(testClient))
        
        // When
        val viewModel = ClientListViewModel(getClientsUseCase)
        
        // Then
        assertEquals(listOf(testClient), viewModel.uiState.first().clients)
    }
}
```

### Integration Tests (Required for Repositories)

```kotlin
@HiltAndroidTest
class ClientRepositoryTest {
    @Test
    fun createClient_insertsLocally_and_queuesSync() = runTest {
        // Given
        val client = createTestClient()
        
        // When
        repository.createClient(client)
        
        // Then
        assertNotNull(clientDao.getById(client.id).first())
        assertEquals(1, syncQueueDao.getPendingCount().first())
    }
}
```

---

## File Organization

### Feature Module Structure

```
feature/clients/
├── data/
│   ├── local/
│   │   ├── ClientDao.kt
│   │   └── ClientEntity.kt
│   ├── remote/
│   │   └── ClientRemoteDataSource.kt
│   └── ClientRepository.kt
├── domain/
│   ├── model/
│   │   └── Client.kt
│   └── usecase/
│       ├── GetClientsUseCase.kt
│       ├── CreateClientUseCase.kt
│       └── UpdateClientUseCase.kt
└── ui/
    ├── list/
    │   ├── ClientListScreen.kt
    │   └── ClientListViewModel.kt
    └── detail/
        ├── ClientDetailScreen.kt
        └── ClientDetailViewModel.kt
```

---

## Common Patterns Reference

### Database Entity

```kotlin
@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String?,
    val phone: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)
```

### DAO Interface

```kotlin
@Dao
interface ClientDao {
    @Query("SELECT * FROM clients WHERE sync_status != 'PENDING_DELETE' ORDER BY name")
    fun getAllClients(): Flow<List<ClientEntity>>
    
    @Query("SELECT * FROM clients WHERE id = :id")
    fun getById(id: String): Flow<ClientEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity)
    
    @Update
    suspend fun update(client: ClientEntity)
    
    @Query("UPDATE clients SET sync_status = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)
}
```

### Compose Screen

```kotlin
@Composable
fun ClientListScreen(
    viewModel: ClientListViewModel = hiltViewModel(),
    onClientClick: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Clients") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("New Client") }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorState(uiState.error!!)
            uiState.clients.isEmpty() -> EmptyState("No clients yet")
            else -> ClientList(
                clients = uiState.clients,
                onClientClick = onClientClick,
                modifier = Modifier.padding(padding)
            )
        }
    }
}
```

---

## Tier Limits Reference

When implementing features, respect subscription tier limits:

| Feature | Free | Solo ($29) | Growing ($79) | Multi ($149) |
|---------|------|------------|---------------|--------------|
| Clients | Unlimited | Unlimited | Unlimited | Unlimited |
| Route Optimization | View only | 8 stops/day | 15 stops/day | Unlimited |
| SMS Reminders | 0 | 50/month | 200/month | 500/month |
| Calendar Sync | ❌ | ✅ | ✅ | ✅ |
| Mileage Reports | ❌ | ✅ | ✅ | ✅ |
| Team Members | 1 | 1 | 1 | 5 |

---

## Questions to Ask Before Coding

1. Have I read the relevant PRD and FRD?
2. Does this feature work offline?
3. Am I writing to local database first?
4. Have I created sync queue entries for writes?
5. Are all inputs validated?
6. Does the UI follow Material 3 guidelines?
7. Have I handled all error states?
8. Are accessibility requirements met?
9. Have I written tests for business logic?
10. Are there any tier limits to enforce?

---

## FRD Implementation Status

### Completed Features

| FRD | Feature | Key Files |
|-----|---------|-----------|
| FRD-001 | Authentication | `feature/auth/`, `TokenManager.kt` |
| FRD-002 | Offline Sync | `core/sync/`, `SyncQueueEntity.kt` |
| FRD-003 | Clients | `feature/client/`, `ClientEntity.kt` |
| FRD-004 | Horses | `feature/horse/`, `HorseEntity.kt` |
| FRD-005 | Appointments | `feature/appointment/`, `AppointmentEntity.kt` |
| FRD-006 | Calendar | `feature/schedule/`, `ScheduleScreen.kt` |
| FRD-011 | Mileage | `feature/mileage/`, `MileageLogEntity.kt` |
| FRD-012 | Service Prices | `feature/pricing/`, `ServicePriceEntity.kt` |
| FRD-013 | Invoicing | `feature/invoice/`, `InvoiceEntity.kt` |
| FRD-016 | Subscriptions | `core/subscription/`, `feature/subscription/` |
| FRD-017 | Usage Limits | `UsageLimitsManager.kt`, `TierLimits.kt` |
| FRD-018 | Reporting | `feature/reports/`, `ReportsScreen.kt` |
| FRD-019 | Settings | `feature/settings/`, `UserPreferencesManager.kt` |

### Not Yet Implemented

| FRD | Feature | Priority | Dependencies |
|-----|---------|----------|--------------|
| FRD-007 | Calendar Sync | Medium | Google Calendar API |
| FRD-008 | Reminders | High | Push notifications, SMS provider |
| FRD-009 | Maps | High | Google Maps SDK |
| FRD-010 | Route Optimization | **Critical** | Maps (FRD-009) |
| FRD-014 | Payment Preferences | Low | Invoicing complete |
| FRD-020 | Onboarding | High | Core features complete |
| FRD-021 | Play Store Launch | Final | All features complete |

### Next Priority Features

1. **FRD-020 Onboarding** - Critical for user activation
2. **FRD-009 Maps + FRD-010 Route Optimization** - Core differentiator
3. **FRD-008 Reminders** - Key engagement feature
