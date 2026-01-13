# Hoof Direct - Functional Requirements Documents (FRD) Index

**Version:** 1.0  
**Created:** January 2026  
**Status:** ✅ All 21 FRDs Complete  
**Package:** com.hoofdirect.app  
**Target Platform:** Android 8.0+ (API 26+)

---

## Document Purpose

These Functional Requirements Documents (FRDs) translate the Product Requirements Documents (PRDs) into implementation-ready specifications. Following the Technical Spec Writing Guide principles, each FRD:

- **Documents behavior, not just structure** — Every user action, system response, and error state is specified
- **Provides testable acceptance criteria** — Specific values, measurable outcomes, and verification methods
- **Includes negative scenarios** — What happens when things fail, not just when they succeed
- **Specifies state transitions** — Complete state machines for complex workflows
- **Is AI-assistant friendly** — Explicit file references, code patterns, and implementation tasks

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 1.9+ |
| UI Framework | Jetpack Compose | 1.5+ |
| Design System | Material 3 | 1.2+ |
| Architecture | MVVM + Clean Architecture | — |
| DI | Hilt | 2.48+ |
| Local Database | Room | 2.6+ |
| Backend | Supabase | — |
| Network | Retrofit + Ktor | 2.9+ / 2.3+ |
| Background Jobs | WorkManager | 2.9+ |
| Maps | Google Maps SDK | 18.2+ |
| Routes | Google Routes API | — |
| SMS | Twilio | — |
| PDF | iText | 8.0+ |
| Payments | Stripe | — |

---

## FRD Documents by Phase

### Phase 1: Foundation (Weeks 1-4)

| FRD | Title | PRD | Priority |
|-----|-------|-----|----------|
| [FRD-001](./FRD-001-authentication.md) | Authentication & User Profiles | PRD-001 | P0 |
| [FRD-002](./FRD-002-offline-architecture.md) | Offline-First Data Architecture | PRD-002 | P0 |
| [FRD-003](./FRD-003-client-management.md) | Client Management | PRD-003 | P0 |
| [FRD-004](./FRD-004-horse-management.md) | Horse Management | PRD-004 | P0 |

### Phase 2: Scheduling Core (Weeks 5-8)

| FRD | Title | PRD | Priority |
|-----|-------|-----|----------|
| [FRD-005](./FRD-005-appointments.md) | Appointment Management | PRD-005 | P0 |
| [FRD-006](./FRD-006-calendar-views.md) | Calendar Views | PRD-006 | P0 |
| [FRD-007](./FRD-007-calendar-sync.md) | Device Calendar Sync | PRD-007 | P1 |
| [FRD-008](./FRD-008-reminders.md) | Reminders & Confirmations | PRD-008 | P1 |

### Phase 3: Route Intelligence (Weeks 9-11)

| FRD | Title | PRD | Priority |
|-----|-------|-----|----------|
| [FRD-009](./FRD-009-maps.md) | Maps Integration | PRD-009 | P0 |
| [FRD-010](./FRD-010-route-optimization.md) | Route Optimization | PRD-010 | P0 |
| [FRD-011](./FRD-011-mileage-tracking.md) | Mileage Tracking | PRD-011 | P1 |

### Phase 4: Financial Tools (Weeks 12-14)

| FRD | Title | PRD | Priority |
|-----|-------|-----|----------|
| [FRD-012](./FRD-012-service-prices.md) | Service Price List | PRD-012 | P0 |
| [FRD-013](./FRD-013-invoicing.md) | Invoicing | PRD-013 | P0 |
| [FRD-014](./FRD-014-payment-preferences.md) | Payment Preferences | PRD-014 | P1 |

### Phase 5: Monetization (Weeks 15-17)

| FRD | Title | PRD | Priority |
|-----|-------|-----|----------|
| [FRD-015](./FRD-015-marketing-site.md) | Marketing Website | PRD-015 | P1 |
| [FRD-016](./FRD-016-subscriptions.md) | Subscription Management | PRD-016 | P0 |
| [FRD-017](./FRD-017-usage-limits.md) | Usage Limits Enforcement | PRD-017 | P0 |

### Phase 6: Polish & Launch (Weeks 18-20)

| FRD | Title | PRD | Priority |
|-----|-------|-----|----------|
| [FRD-018](./FRD-018-reporting.md) | Reporting & Analytics | PRD-018 | P1 |
| [FRD-019](./FRD-019-settings.md) | Settings & Preferences | PRD-019 | P1 |
| [FRD-020](./FRD-020-onboarding.md) | Onboarding Flow | PRD-020 | P0 |
| [FRD-021](./FRD-021-play-store-launch.md) | Play Store Launch | PRD-021 | P0 |

---

## Project Structure

```
com.hoofdirect.app/
├── app/                           # Application module
│   ├── src/main/
│   │   ├── java/com/hoofdirect/app/
│   │   │   ├── HoofDirectApp.kt   # Application class
│   │   │   ├── MainActivity.kt    # Single activity
│   │   │   └── navigation/        # Navigation graph
│   │   └── res/                   # Resources
│   └── build.gradle.kts
├── core/                          # Core modules
│   ├── database/                  # Room database, DAOs, entities
│   ├── network/                   # Supabase client, API services
│   ├── sync/                      # Offline sync engine
│   ├── designsystem/              # Theme, components
│   └── common/                    # Utilities, extensions
├── feature/                       # Feature modules
│   ├── auth/                      # Authentication (FRD-001)
│   ├── clients/                   # Client management (FRD-003)
│   ├── horses/                    # Horse management (FRD-004)
│   ├── appointments/              # Appointments (FRD-005)
│   ├── calendar/                  # Calendar views (FRD-006, FRD-007)
│   ├── routes/                    # Maps & optimization (FRD-009, FRD-010)
│   ├── mileage/                   # Mileage tracking (FRD-011)
│   ├── invoices/                  # Invoicing (FRD-012, FRD-013)
│   ├── settings/                  # Settings (FRD-019)
│   └── onboarding/                # Onboarding (FRD-020)
└── build.gradle.kts
```

---

## Database Schema Overview

### Core Tables

| Table | Description | FRD Reference |
|-------|-------------|---------------|
| users | User profiles and preferences | FRD-001 |
| clients | Client records | FRD-003 |
| horses | Horse records linked to clients | FRD-004 |
| horse_photos | Photos for horses | FRD-004 |
| appointments | Scheduled appointments | FRD-005 |
| appointment_horses | Junction: appointments ↔ horses | FRD-005 |
| invoices | Generated invoices | FRD-013 |
| invoice_line_items | Line items for invoices | FRD-013 |
| mileage_logs | Mileage tracking entries | FRD-011 |
| service_prices | Service types and prices | FRD-012 |
| sync_queue | Pending sync operations | FRD-002 |

---

## Cross-Cutting Concerns

### Error Handling Pattern

All features must implement this error handling pattern:

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

### UI State Pattern

All ViewModels must expose UI state as:

```kotlin
data class FeatureUiState(
    val isLoading: Boolean = false,
    val data: Data? = null,
    val error: AppError? = null
)

// StateFlow in ViewModel
private val _uiState = MutableStateFlow(FeatureUiState())
val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()
```

### Sync Status Pattern

All syncable entities must include:

```kotlin
enum class EntitySyncStatus {
    SYNCED,           // Entity matches server
    PENDING_CREATE,   // Created locally, not synced
    PENDING_UPDATE,   // Updated locally, not synced
    PENDING_DELETE,   // Deleted locally, not synced
    CONFLICT          // Server has different version
}
```

---

## Definition of Done

A feature is complete when:

- [ ] All acceptance criteria verified
- [ ] Unit tests written and passing (≥80% coverage)
- [ ] Integration tests passing
- [ ] Error states implemented and tested
- [ ] Edge cases covered
- [ ] Offline behavior verified
- [ ] Accessibility requirements met (TalkBack, contrast, touch targets)
- [ ] Code reviewed and approved
- [ ] Documentation updated
- [ ] Deployed to test environment and verified

---

## Navigation Deep Links

| Deep Link | Destination | FRD |
|-----------|-------------|-----|
| `hoofdirect://appointment/{id}` | Appointment detail | FRD-005 |
| `hoofdirect://client/{id}` | Client detail | FRD-003 |
| `hoofdirect://horse/{id}` | Horse detail | FRD-004 |
| `hoofdirect://invoice/{id}` | Invoice detail | FRD-013 |
| `hoofdirect://confirm/{appointmentId}` | Confirmation handler | FRD-008 |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | January 2026 | Initial FRD documentation |
