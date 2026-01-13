# CLAUDE.md — Instructions for Claude

This document provides specific instructions for Claude when working on the Hoof Direct project. These guidelines supplement the general `AGENTS.md` guidelines.

---

## Project Summary

**Hoof Direct** is a native Android CRM for professional farriers (horseshoers) in the US market. The app's primary differentiator is **intelligent route optimization**—a feature no competitor offers. The app must function fully offline since farriers work in rural areas with poor connectivity.

### Key Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Platform | Native Android (Kotlin) | 70%+ of farriers use Android; better GPS/offline performance |
| UI Framework | Jetpack Compose + Material 3 | Modern declarative UI with Material You theming |
| Architecture | MVVM + Clean Architecture | Testable, maintainable, separation of concerns |
| Database | Room (SQLite) | Offline-first, local database as source of truth |
| Backend | Supabase | PostgreSQL + Auth + Edge Functions |
| DI | Hilt | Google-recommended, compile-time safe |
| Payments | Stripe | Subscription billing via web checkout |

---

## Before Writing Any Code

### 1. Read the Documentation

The project has comprehensive PRDs (Product Requirements Documents) and FRDs (Functional Requirements Documents). **Always read the relevant documents before implementing features.**

```
/mnt/project/00-PRD-INDEX.md    # PRD overview and phases
/mnt/project/00-FRD-INDEX.md    # FRD overview and tech specs
/mnt/project/PRD-XXX-*.md       # Individual feature PRDs
/mnt/project/FRD-XXX-*.md       # Individual feature FRDs
```

### 2. Understand the Feature's Phase

Development is organized into 6 phases:

| Phase | Focus | PRDs |
|-------|-------|------|
| 1 | Foundation | Authentication, Offline Architecture, Client/Horse Management |
| 2 | Scheduling | Appointments, Calendar, Reminders |
| 3 | Routes | Maps, Route Optimization, Mileage |
| 4 | Financial | Service Prices, Invoicing, Payment Preferences |
| 5 | Monetization | Marketing Site, Subscriptions, Usage Limits |
| 6 | Launch | Reporting, Settings, Onboarding, Play Store |

### 3. Check Dependencies

Each PRD/FRD lists dependencies. Ensure prerequisites are complete before implementing.

---

## Code Generation Principles

### Offline-First is Non-Negotiable

Every data operation must follow this pattern:

```kotlin
// 1. Write to local Room database
// 2. Create sync queue entry
// 3. Return success immediately
// 4. Background sync handles server communication

suspend fun createClient(client: Client): Result<Client> {
    // Local write
    val entity = client.toEntity(syncStatus = PENDING_CREATE)
    clientDao.insert(entity)
    
    // Queue for sync
    syncManager.queueChange(
        entityType = "clients",
        entityId = entity.id,
        operation = SyncOperation.INSERT,
        payload = entity.toJson()
    )
    
    // Return immediately - don't wait for server
    return Result.success(client)
}
```

### Security Guardrails

Never generate code that:

1. **Hardcodes secrets** — Use `BuildConfig` or `secrets.properties`
2. **Logs sensitive data** — No passwords, tokens, PII in logs
3. **Stores tokens insecurely** — Always use `EncryptedSharedPreferences`
4. **Bypasses input validation** — Validate all user inputs
5. **Exposes internal errors** — User-friendly error messages only

### UI Consistency

Always use Material 3 components with proper theming:

```kotlin
// Use the app's design system components
import com.hoofdirect.app.designsystem.components.*
import com.hoofdirect.app.designsystem.theme.*

@Composable
fun MyScreen() {
    HoofDirectTheme {
        // Your UI here
    }
}
```

---

## Feature Implementation Checklist

When implementing a feature, ensure:

```
□ Read the PRD for requirements and user stories
□ Read the FRD for technical implementation details
□ Check dependencies are satisfied
□ Implement offline-first (local DB first, sync queue)
□ Validate all inputs
□ Handle all error states
□ Follow Material 3 design guidelines
□ Meet accessibility requirements (48dp touch, content descriptions)
□ Write unit tests for business logic
□ Write integration tests for repository layer
□ Check tier limits if applicable
□ No hardcoded secrets
□ No PII in logs
```

---

## Common Implementation Patterns

### ViewModel with StateFlow

```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FeatureUiState())
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getData()
                .catch { e -> _uiState.update { it.copy(error = e.toAppError()) } }
                .collect { data -> _uiState.update { it.copy(data = data, isLoading = false) } }
        }
    }
}
```

### Repository with Offline Support

```kotlin
class FeatureRepository @Inject constructor(
    private val dao: FeatureDao,
    private val remoteDataSource: FeatureRemoteDataSource,
    private val syncManager: SyncManager
) {
    // Always read from local
    fun getData(): Flow<List<Feature>> = dao.getAll()
    
    // Write locally, queue sync
    suspend fun create(item: Feature): Result<Feature> {
        dao.insert(item.toEntity(syncStatus = PENDING_CREATE))
        syncManager.queueChange("features", item.id, INSERT, item.toJson())
        return Result.success(item)
    }
}
```

### Compose Screen Structure

```kotlin
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel(),
    onNavigate: (destination: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = { /* TopAppBar */ },
        floatingActionButton = { /* FAB if needed */ }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorState(uiState.error!!)
            else -> FeatureContent(uiState, Modifier.padding(padding))
        }
    }
}
```

---

## Database Conventions

### Entity Naming

```kotlin
@Entity(tableName = "clients")  // Plural, snake_case
data class ClientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "full_name") val fullName: String,  // snake_case columns
    @ColumnInfo(name = "sync_status") val syncStatus: EntitySyncStatus,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)
```

### DAO Patterns

```kotlin
@Dao
interface ClientDao {
    // Queries return Flow for reactive updates
    @Query("SELECT * FROM clients WHERE sync_status != 'PENDING_DELETE'")
    fun getAll(): Flow<List<ClientEntity>>
    
    // Single item queries
    @Query("SELECT * FROM clients WHERE id = :id")
    fun getById(id: String): Flow<ClientEntity?>
    
    // Suspend functions for writes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: ClientEntity)
    
    // Soft delete pattern
    @Query("UPDATE clients SET sync_status = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markDeleted(id: String)
}
```

---

## Subscription Tier Awareness

Many features have tier-based limits. Check `UsageLimitsManager` before performing limited operations:

```kotlin
// Route optimization example
val maxStops = usageLimitsManager.getMaxRouteStops()
if (maxStops == 0) {
    return Result.failure(FeatureNotAvailableError())
}
if (appointments.size > maxStops) {
    return Result.failure(TierLimitExceededError(appointments.size, maxStops))
}
```

### Tier Limits Reference

| Feature | Free | Solo | Growing | Multi |
|---------|------|------|---------|-------|
| Route stops/day | 0 | 8 | 15 | ∞ |
| SMS/month | 0 | 50 | 200 | 500 |
| Calendar sync | ❌ | ✅ | ✅ | ✅ |
| Mileage reports | ❌ | ✅ | ✅ | ✅ |
| Team members | 1 | 1 | 1 | 5 |

---

## Navigation Patterns

Use type-safe navigation with sealed classes:

```kotlin
sealed class NavDestination(val route: String) {
    object ClientList : NavDestination("clients")
    data class ClientDetail(val id: String) : NavDestination("clients/$id")
    object NewClient : NavDestination("clients/new")
}

// In NavHost
composable("clients/{clientId}") { backStackEntry ->
    val clientId = backStackEntry.arguments?.getString("clientId")
    ClientDetailScreen(clientId = clientId)
}
```

---

## Error Messages

Use user-friendly error messages that don't expose internal details:

```kotlin
// ❌ Don't expose internal errors
"SQLException: UNIQUE constraint failed: clients.email"

// ✅ User-friendly messages
"A client with this email already exists"
```

---

## Testing Expectations

### Unit Tests

Required for:
- ViewModels
- Use cases
- Business logic in repositories
- Utility functions

```kotlin
@Test
fun `createClient returns success when valid`() = runTest {
    val result = createClientUseCase(validClient)
    assertTrue(result.isSuccess)
}
```

### Integration Tests

Required for:
- Repository + DAO interactions
- Sync queue operations

```kotlin
@HiltAndroidTest
class ClientRepositoryTest {
    @Test
    fun createClient_insertsToDatabase_and_queuesSync() = runTest {
        repository.createClient(testClient)
        
        assertNotNull(clientDao.getById(testClient.id).first())
        assertEquals(1, syncQueueDao.pendingCount())
    }
}
```

---

## Quick Reference Commands

### Common File Locations

```
/hoofdirect/                    # Project documentation
/hoofdirect/PRD/PRD-XXX-*.md        # Product requirements
/hoofdirect/FRD/FRD-XXX-*.md        # Functional requirements
/hoofdirect/PRD/00-PRD-INDEX.md     # PRD index
/hoofdirect/FRD/00-FRD-INDEX.md     # FRD index
```

### Key Documents by Feature

| Feature | PRD | FRD | Status |
|---------|-----|-----|--------|
| Authentication | PRD-001 | FRD-001 | ✅ Complete |
| Offline Sync | PRD-002 | FRD-002 | ✅ Complete |
| Clients | PRD-003 | FRD-003 | ✅ Complete |
| Horses | PRD-004 | FRD-004 | ✅ Complete |
| Appointments | PRD-005 | FRD-005 | ✅ Complete |
| Calendar | PRD-006 | FRD-006 | ✅ Complete |
| Calendar Sync | PRD-007 | FRD-007 | ⏳ Not Started |
| Reminders | PRD-008 | FRD-008 | ⏳ Not Started |
| Maps | PRD-009 | FRD-009 | ⏳ Not Started |
| Route Optimization | PRD-010 | FRD-010 | ⏳ Not Started |
| Mileage | PRD-011 | FRD-011 | ✅ Complete |
| Service Prices | PRD-012 | FRD-012 | ✅ Complete |
| Invoicing | PRD-013 | FRD-013 | ✅ Complete |
| Payment Preferences | PRD-014 | FRD-014 | ⏳ Not Started |
| Marketing Site | PRD-015 | FRD-015 | ⏳ Not Started (Web) |
| Subscriptions | PRD-016 | FRD-016 | ✅ Complete |
| Usage Limits | PRD-017 | FRD-017 | ✅ Complete |
| Reporting | PRD-018 | FRD-018 | ✅ Complete |
| Settings | PRD-019 | FRD-019 | ✅ Complete |
| Onboarding | PRD-020 | FRD-020 | ⏳ Not Started |
| Play Store Launch | PRD-021 | FRD-021 | ⏳ Not Started |

### Implementation Progress Summary

**Completed (14/21):**
- Phase 1: Authentication, Offline Sync, Clients, Horses
- Phase 2: Appointments, Calendar
- Phase 3: Mileage
- Phase 4: Service Prices, Invoicing
- Phase 5: Subscriptions, Usage Limits
- Phase 6: Reporting, Settings

**Remaining (7/21):**
- Calendar Sync (FRD-007) - External calendar integration
- Reminders (FRD-008) - Push notifications & SMS
- Maps (FRD-009) - Google Maps integration
- Route Optimization (FRD-010) - Core differentiator feature
- Payment Preferences (FRD-014) - Client payment methods
- Onboarding (FRD-020) - New user flow
- Play Store Launch (FRD-021) - Release preparation

---

## When in Doubt

1. **Read the FRD** — It contains implementation details, code patterns, and acceptance criteria
2. **Check the data model** — Entity definitions are in each FRD
3. **Look at existing patterns** — Follow established patterns in the codebase
4. **Ask for clarification** — Better to ask than assume incorrectly
5. **Prioritize offline functionality** — This is the app's core differentiator

---

## Summary

- **Always read PRD/FRD before implementing**
- **Offline-first is mandatory** — Local DB first, sync queue, no network waits
- **Security is non-negotiable** — No secrets in code, encrypted storage, validate inputs
- **Material 3 consistently** — Use the design system throughout
- **Test business logic** — Unit tests for ViewModels and use cases
- **Respect tier limits** — Check usage limits for gated features
- **User-friendly errors** — Never expose internal error details
