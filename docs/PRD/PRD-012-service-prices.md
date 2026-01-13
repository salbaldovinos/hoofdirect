# PRD-012: Service Price List

**Priority**: P1  
**Phase**: 4 - Financial Tools  
**Estimated Duration**: 3 days

---

## Overview

### Purpose
Allow farriers to configure their service types and default prices, which auto-populate during appointment creation and invoicing.

### Business Value
- Speeds up appointment creation
- Ensures consistent pricing
- Supports custom pricing per client
- Foundation for invoicing accuracy

### Success Metrics
| Metric | Target |
|--------|--------|
| Price auto-population accuracy | 100% |
| Setup time | < 2 minutes |
| Price lookup time | < 50ms |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-012-01 | Farrier | Configure my service types | Prices are consistent | P0 |
| US-012-02 | Farrier | Set prices per service | Appointments auto-price | P0 |
| US-012-03 | Farrier | Add custom services | I offer specialized work | P1 |
| US-012-04 | Farrier | Set estimated duration | Scheduling is accurate | P1 |
| US-012-05 | Farrier | Reorder services | Most common are first | P2 |

---

## Functional Requirements

### FR-012-01: Default Services
Pre-populated on account creation:
```kotlin
Service Type        Default Price  Duration
─────────────────────────────────────────────
Trim                $45.00         30 min
Front Shoes         $120.00        45 min
Full Set            $180.00        60 min
Corrective          $220.00        75 min
```

### FR-012-02: Service Configuration
- Name (required, max 50 chars)
- Default price (required, USD)
- Estimated duration (minutes)
- Description (optional)
- Active/Inactive toggle
- Sort order (drag to reorder)

### FR-012-03: Custom Services
- Add unlimited custom services
- Same fields as default services
- Cannot delete built-in types (can deactivate)

### FR-012-04: Price Application
- Default price used when adding horse to appointment
- Client custom pricing overrides default
- Editable per appointment

---

## Technical Implementation

```kotlin
@Entity(tableName = "service_prices")
data class ServicePriceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "service_type") val serviceType: String,
    val name: String,
    @ColumnInfo(name = "default_price") val defaultPrice: BigDecimal,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val description: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false
)

class ServicePriceRepository @Inject constructor(
    private val servicePriceDao: ServicePriceDao
) {
    fun getActiveServices(): Flow<List<ServicePrice>> =
        servicePriceDao.getActiveServices()
            .map { entities -> entities.map { it.toDomain() } }
    
    fun getPriceForService(
        serviceType: String,
        clientId: String?
    ): Flow<BigDecimal> = flow {
        val clientCustomPrice = clientId?.let {
            clientDao.getCustomPrice(it, serviceType)
        }
        emit(clientCustomPrice ?: servicePriceDao.getDefaultPrice(serviceType))
    }
    
    suspend fun createDefaultServices(userId: String) {
        val defaults = listOf(
            ServicePriceEntity(userId = userId, serviceType = "trim", 
                name = "Trim", defaultPrice = BigDecimal("45.00"), 
                durationMinutes = 30, sortOrder = 1),
            ServicePriceEntity(userId = userId, serviceType = "front_shoes",
                name = "Front Shoes", defaultPrice = BigDecimal("120.00"),
                durationMinutes = 45, sortOrder = 2),
            ServicePriceEntity(userId = userId, serviceType = "full_set",
                name = "Full Set", defaultPrice = BigDecimal("180.00"),
                durationMinutes = 60, sortOrder = 3),
            ServicePriceEntity(userId = userId, serviceType = "corrective",
                name = "Corrective", defaultPrice = BigDecimal("220.00"),
                durationMinutes = 75, sortOrder = 4)
        )
        servicePriceDao.insertAll(defaults)
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-012-01 | Default services created on signup | Integration test |
| AC-012-02 | Prices populate in appointment | Integration test |
| AC-012-03 | Custom services can be added | E2E test |
| AC-012-04 | Inactive services hidden | UI test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-001 (Auth) | Internal | Required |
