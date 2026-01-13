# FRD-012: Service & Pricing

**PRD Reference**: PRD-012-service-prices.md  
**Priority**: P1  
**Phase**: 4 - Financial Tools  
**Estimated Duration**: 3 days

---

## 1. Overview

### 1.1 Purpose

This document specifies the complete functional requirements for service and pricing configuration—enabling farriers to define their service offerings, default prices, and estimated durations for consistent appointment creation and invoicing.

### 1.2 Scope

This FRD covers:
- Default service types pre-populated on account creation
- Service configuration (name, price, duration, description)
- Custom service creation
- Service activation/deactivation
- Drag-and-drop service reordering
- Price application hierarchy (default → client custom → appointment override)
- Integration with appointment creation and invoicing

### 1.3 Dependencies

| Dependency | FRD | Description |
|------------|-----|-------------|
| Authentication | FRD-001 | User account for service ownership |
| Client Management | FRD-003 | Client custom pricing |
| Appointments | FRD-005 | Service selection during appointment |
| Invoicing | FRD-013 | Price application on invoices |
| Offline Architecture | FRD-002 | Local storage and sync |

---

## 2. Default Services

### 2.1 Default Service Creation

**Trigger**: User completes account creation (after email verification)

**Default Services Created**:

| Service Type | Display Name | Default Price | Duration | Sort Order |
|--------------|--------------|---------------|----------|------------|
| `trim` | Trim | $45.00 | 30 min | 1 |
| `front_shoes` | Front Shoes | $120.00 | 45 min | 2 |
| `full_set` | Full Set | $180.00 | 60 min | 3 |
| `corrective` | Corrective | $220.00 | 75 min | 4 |

**File**: `core/service/DefaultServicesInitializer.kt`

```kotlin
class DefaultServicesInitializer @Inject constructor(
    private val servicePriceDao: ServicePriceDao
) {
    suspend fun createDefaultServices(userId: String) {
        val defaults = listOf(
            ServicePriceEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                serviceType = ServiceType.TRIM.code,
                name = "Trim",
                defaultPrice = BigDecimal("45.00"),
                durationMinutes = 30,
                description = null,
                isActive = true,
                sortOrder = 1,
                isBuiltIn = true,
                createdAt = Instant.now(),
                syncStatus = EntitySyncStatus.PENDING_CREATE
            ),
            ServicePriceEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                serviceType = ServiceType.FRONT_SHOES.code,
                name = "Front Shoes",
                defaultPrice = BigDecimal("120.00"),
                durationMinutes = 45,
                description = null,
                isActive = true,
                sortOrder = 2,
                isBuiltIn = true,
                createdAt = Instant.now(),
                syncStatus = EntitySyncStatus.PENDING_CREATE
            ),
            ServicePriceEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                serviceType = ServiceType.FULL_SET.code,
                name = "Full Set",
                defaultPrice = BigDecimal("180.00"),
                durationMinutes = 60,
                description = null,
                isActive = true,
                sortOrder = 3,
                isBuiltIn = true,
                createdAt = Instant.now(),
                syncStatus = EntitySyncStatus.PENDING_CREATE
            ),
            ServicePriceEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                serviceType = ServiceType.CORRECTIVE.code,
                name = "Corrective",
                defaultPrice = BigDecimal("220.00"),
                durationMinutes = 75,
                description = null,
                isActive = true,
                sortOrder = 4,
                isBuiltIn = true,
                createdAt = Instant.now(),
                syncStatus = EntitySyncStatus.PENDING_CREATE
            )
        )
        
        servicePriceDao.insertAll(defaults)
    }
}
```

### 2.2 Service Type Enum

**File**: `core/domain/model/ServiceType.kt`

```kotlin
enum class ServiceType(val code: String, val defaultName: String) {
    TRIM("trim", "Trim"),
    FRONT_SHOES("front_shoes", "Front Shoes"),
    FULL_SET("full_set", "Full Set"),
    CORRECTIVE("corrective", "Corrective"),
    CUSTOM("custom", "Custom");
    
    companion object {
        fun fromCode(code: String): ServiceType {
            return entries.find { it.code == code } ?: CUSTOM
        }
        
        val builtInTypes: List<ServiceType>
            get() = listOf(TRIM, FRONT_SHOES, FULL_SET, CORRECTIVE)
    }
}
```

---

## 3. Services Screen

### 3.1 Screen Layout

**Route**: `/settings/services`  
**Entry Point**: Settings → "Services & Pricing"

```
┌─────────────────────────────────────────────────────────────┐
│                   SERVICES SCREEN                            │
├─────────────────────────────────────────────────────────────┤
│  [←] Services & Pricing                                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Configure your services and default prices.                │
│  Drag to reorder. Prices can be customized per client.     │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  [≡]  Trim                                    [✓]    │  │
│  │       $45.00 · 30 min                                 │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  [≡]  Front Shoes                             [✓]    │  │
│  │       $120.00 · 45 min                                │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  [≡]  Full Set                                [✓]    │  │
│  │       $180.00 · 60 min                                │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  [≡]  Corrective                              [✓]    │  │
│  │       $220.00 · 75 min                                │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  [≡]  Therapeutic Shoeing (Custom)            [✓]    │  │
│  │       $280.00 · 90 min                                │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  [≡]  Emergency Call                          [ ]    │  │
│  │       $350.00 · 45 min                    (Inactive) │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  [+ Add Custom Service]                                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Service List Item

**File**: `feature/settings/ui/components/ServiceListItem.kt`

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceListItem(
    service: ServicePrice,
    onServiceClick: (ServicePrice) -> Unit,
    onActiveToggle: (ServicePrice, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (service.isActive) 1f else 0.6f)
            .combinedClickable(
                onClick = { onServiceClick(service) }
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Service info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (service.isCustom) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Custom)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row {
                    Text(
                        text = service.defaultPrice.formatAsCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " · ${service.durationMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!service.isActive) {
                    Text(
                        text = "(Inactive)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Active toggle
            Checkbox(
                checked = service.isActive,
                onCheckedChange = { onActiveToggle(service, it) }
            )
        }
    }
}
```

### 3.3 Drag-and-Drop Reordering

**Library**: `org.burnoutcrew.composereorderable:reorderable`

```kotlin
@Composable
fun ServicesScreen(
    viewModel: ServicesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            viewModel.reorderService(from.index, to.index)
        },
        onDragEnd = { _, _ ->
            viewModel.saveOrder()
        }
    )
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Header text
        Text(
            text = "Configure your services and default prices.\nDrag to reorder. Prices can be customized per client.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        LazyColumn(
            state = reorderableState.listState,
            modifier = Modifier
                .fillMaxWidth()
                .reorderable(reorderableState)
        ) {
            // Built-in services section
            item {
                Text(
                    text = "Standard Services",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            itemsIndexed(
                items = state.builtInServices,
                key = { _, service -> service.id }
            ) { index, service ->
                ReorderableItem(
                    reorderableState = reorderableState,
                    key = service.id
                ) { isDragging ->
                    ServiceListItem(
                        service = service,
                        onServiceClick = { viewModel.editService(it) },
                        onActiveToggle = { s, active -> viewModel.toggleActive(s, active) },
                        isDragging = isDragging,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .detectReorderAfterLongPress(reorderableState)
                    )
                }
            }
            
            // Custom services section
            if (state.customServices.isNotEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = "Custom Services",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                itemsIndexed(
                    items = state.customServices,
                    key = { _, service -> service.id }
                ) { index, service ->
                    ReorderableItem(
                        reorderableState = reorderableState,
                        key = service.id
                    ) { isDragging ->
                        ServiceListItem(
                            service = service,
                            onServiceClick = { viewModel.editService(it) },
                            onActiveToggle = { s, active -> viewModel.toggleActive(s, active) },
                            isDragging = isDragging,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .detectReorderAfterLongPress(reorderableState)
                        )
                    }
                }
            }
            
            // Add custom service button
            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                OutlinedButton(
                    onClick = { viewModel.addCustomService() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Custom Service")
                }
            }
        }
    }
    
    // Edit service sheet
    if (state.editingService != null) {
        EditServiceSheet(
            service = state.editingService,
            onDismiss = { viewModel.dismissEdit() },
            onSave = { viewModel.saveService(it) },
            onDelete = if (state.editingService?.isCustom == true) {
                { viewModel.deleteService(state.editingService!!) }
            } else null
        )
    }
    
    // Add service sheet
    if (state.showAddSheet) {
        AddServiceSheet(
            onDismiss = { viewModel.dismissAdd() },
            onSave = { viewModel.createService(it) }
        )
    }
}
```

---

## 4. Service Configuration

### 4.1 Edit Service Sheet

**Route**: Bottom sheet overlay on Services Screen

```
┌─────────────────────────────────────────────────────────────┐
│                   EDIT SERVICE SHEET                         │
├─────────────────────────────────────────────────────────────┤
│  [×] Edit Service                              [Save]       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Service Name *                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Full Set                                              │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Default Price *                                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ $  180.00                                             │  │
│  └───────────────────────────────────────────────────────┘  │
│  This is your standard rate. You can set different prices  │
│  for specific clients.                                      │
│                                                              │
│  Estimated Duration *                                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 60                                              min   │  │
│  └───────────────────────────────────────────────────────┘  │
│  Used for scheduling and route planning.                   │
│                                                              │
│  Description (optional)                                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Four shoes with standard fitting                      │  │
│  │                                                        │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Status                                                     │
│  Active                                            [ON ]    │
│  Inactive services won't appear in appointment creation.   │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│  [Delete Service]   (only for custom services)             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Field Specifications

#### Service Name
- **Required**: Yes
- **Max Length**: 50 characters
- **Character Counter**: Shows "15/50"
- **Validation**: Non-empty, unique among user's services
- **Built-in Services**: Name is editable (user can rename "Trim" to "Basic Trim")

#### Default Price
- **Required**: Yes
- **Type**: Currency input (BigDecimal)
- **Format**: Dollar sign prefix, two decimal places
- **Minimum**: $0.00
- **Maximum**: $9,999.99
- **Keyboard**: Decimal number pad

#### Estimated Duration
- **Required**: Yes
- **Type**: Integer minutes
- **Minimum**: 5 minutes
- **Maximum**: 480 minutes (8 hours)
- **Presets**: Quick select chips for 15, 30, 45, 60, 90, 120 min
- **Increment**: 5-minute increments suggested

#### Description
- **Required**: No
- **Max Length**: 200 characters
- **Purpose**: Internal note, shown in service selection dropdown

#### Active Toggle
- **Default**: ON for new services
- **Behavior**: Inactive services hidden from:
  - Appointment service selection
  - Invoice line item selection
- **Visibility**: Still visible in Services & Pricing list (grayed out)

### 4.3 Edit Service Sheet Component

**File**: `feature/settings/ui/components/EditServiceSheet.kt`

```kotlin
@Composable
fun EditServiceSheet(
    service: ServicePrice?,
    onDismiss: () -> Unit,
    onSave: (ServicePrice) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember(service) { mutableStateOf(service?.name ?: "") }
    var price by remember(service) { mutableStateOf(service?.defaultPrice?.toPlainString() ?: "") }
    var duration by remember(service) { mutableStateOf(service?.durationMinutes?.toString() ?: "30") }
    var description by remember(service) { mutableStateOf(service?.description ?: "") }
    var isActive by remember(service) { mutableStateOf(service?.isActive ?: true) }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var durationError by remember { mutableStateOf<String?>(null) }
    
    val isValid = name.isNotBlank() && 
                  price.toDoubleOrNull() != null && 
                  duration.toIntOrNull() != null
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
                Text(
                    text = if (service != null) "Edit Service" else "Add Service",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = {
                        val updated = ServicePrice(
                            id = service?.id ?: UUID.randomUUID().toString(),
                            serviceType = service?.serviceType ?: ServiceType.CUSTOM.code,
                            name = name.trim(),
                            defaultPrice = BigDecimal(price),
                            durationMinutes = duration.toInt(),
                            description = description.takeIf { it.isNotBlank() },
                            isActive = isActive,
                            sortOrder = service?.sortOrder ?: Int.MAX_VALUE,
                            isBuiltIn = service?.isBuiltIn ?: false
                        )
                        onSave(updated)
                    },
                    enabled = isValid
                ) {
                    Text("Save")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Service Name
            OutlinedTextField(
                value = name,
                onValueChange = { 
                    if (it.length <= 50) {
                        name = it
                        nameError = null
                    }
                },
                label = { Text("Service Name *") },
                isError = nameError != null,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(nameError ?: "")
                        Text("${name.length}/50")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Price
            OutlinedTextField(
                value = price,
                onValueChange = { 
                    if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        price = it
                        priceError = null
                    }
                },
                label = { Text("Default Price *") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = priceError != null,
                supportingText = { 
                    Text(priceError ?: "This is your standard rate. You can set different prices for specific clients.")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Duration
            OutlinedTextField(
                value = duration,
                onValueChange = { 
                    if (it.matches(Regex("^\\d{0,3}$"))) {
                        duration = it
                        durationError = null
                    }
                },
                label = { Text("Estimated Duration *") },
                suffix = { Text("min") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = durationError != null,
                supportingText = { 
                    Text(durationError ?: "Used for scheduling and route planning.")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Duration presets
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(listOf(15, 30, 45, 60, 90, 120)) { minutes ->
                    FilterChip(
                        selected = duration == minutes.toString(),
                        onClick = { duration = minutes.toString() },
                        label = { Text("$minutes min") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { 
                    if (it.length <= 200) description = it 
                },
                label = { Text("Description (optional)") },
                supportingText = { Text("${description.length}/200") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Active toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Inactive services won't appear in appointment creation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )
            }
            
            // Delete button for custom services
            if (onDelete != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Service")
                }
            }
        }
    }
}
```

### 4.4 Validation Rules

```
┌─────────────────────────────────────────────────────────────┐
│                   VALIDATION RULES                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Name Validation:                                           │
│  • Required (non-empty after trim)                          │
│  • Max 50 characters                                        │
│  • Must be unique among user's services (case-insensitive) │
│  • Error: "Service name is required"                        │
│  • Error: "A service with this name already exists"         │
│                                                              │
│  Price Validation:                                          │
│  • Required                                                 │
│  • Must be valid decimal number                             │
│  • Range: $0.00 - $9,999.99                                │
│  • Error: "Price is required"                               │
│  • Error: "Price must be between $0 and $9,999.99"         │
│                                                              │
│  Duration Validation:                                       │
│  • Required                                                 │
│  • Must be valid integer                                    │
│  • Range: 5 - 480 minutes                                  │
│  • Error: "Duration is required"                            │
│  • Error: "Duration must be between 5 and 480 minutes"     │
│                                                              │
│  Description Validation:                                    │
│  • Optional                                                 │
│  • Max 200 characters                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Custom Services

### 5.1 Add Custom Service

**Trigger**: Tap "+ Add Custom Service" button

**Behavior**:
1. Opens Add Service sheet (same as Edit Service sheet, empty fields)
2. Service type set to `custom`
3. `isBuiltIn = false`
4. `sortOrder` set to highest + 1 (appears at bottom)

### 5.2 Delete Custom Service

**Constraint**: Only custom services (`isBuiltIn = false`) can be deleted

**Delete Flow**:
1. User taps "Delete Service" in edit sheet
2. Confirmation dialog: "Delete Service?"
   - Message: "This will remove 'Therapeutic Shoeing' from your services. Any invoices using this service will retain the service name."
   - Actions: "Cancel", "Delete"
3. On confirm:
   - Mark entity with `syncStatus = PENDING_DELETE`
   - Remove from local display
   - Queue sync operation

**Built-in Services**: Cannot be deleted, only deactivated

---

## 6. ServicesViewModel

**File**: `feature/settings/ui/ServicesViewModel.kt`

```kotlin
@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val servicePriceRepository: ServicePriceRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(ServicesUiState())
    val state: StateFlow<ServicesUiState> = _state.asStateFlow()
    
    init {
        loadServices()
    }
    
    private fun loadServices() {
        viewModelScope.launch {
            servicePriceRepository.getAllServices()
                .collect { services ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            builtInServices = services
                                .filter { s -> s.isBuiltIn }
                                .sortedBy { s -> s.sortOrder },
                            customServices = services
                                .filter { s -> !s.isBuiltIn }
                                .sortedBy { s -> s.sortOrder }
                        )
                    }
                }
        }
    }
    
    fun reorderService(fromIndex: Int, toIndex: Int) {
        val allServices = _state.value.builtInServices + _state.value.customServices
        val mutableList = allServices.toMutableList()
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        
        // Update sort orders
        val reordered = mutableList.mapIndexed { index, service ->
            service.copy(sortOrder = index)
        }
        
        _state.update {
            it.copy(
                builtInServices = reordered.filter { s -> s.isBuiltIn },
                customServices = reordered.filter { s -> !s.isBuiltIn }
            )
        }
    }
    
    fun saveOrder() {
        viewModelScope.launch {
            val allServices = _state.value.builtInServices + _state.value.customServices
            servicePriceRepository.updateSortOrders(
                allServices.map { it.id to it.sortOrder }
            )
        }
    }
    
    fun toggleActive(service: ServicePrice, active: Boolean) {
        viewModelScope.launch {
            servicePriceRepository.updateService(
                service.copy(isActive = active)
            )
        }
    }
    
    fun editService(service: ServicePrice) {
        _state.update { it.copy(editingService = service) }
    }
    
    fun dismissEdit() {
        _state.update { it.copy(editingService = null) }
    }
    
    fun saveService(service: ServicePrice) {
        viewModelScope.launch {
            servicePriceRepository.updateService(service)
            _state.update { it.copy(editingService = null) }
        }
    }
    
    fun addCustomService() {
        _state.update { it.copy(showAddSheet = true) }
    }
    
    fun dismissAdd() {
        _state.update { it.copy(showAddSheet = false) }
    }
    
    fun createService(service: ServicePrice) {
        viewModelScope.launch {
            servicePriceRepository.createService(service)
            _state.update { it.copy(showAddSheet = false) }
        }
    }
    
    fun deleteService(service: ServicePrice) {
        viewModelScope.launch {
            servicePriceRepository.deleteService(service.id)
            _state.update { it.copy(editingService = null) }
        }
    }
}

data class ServicesUiState(
    val isLoading: Boolean = true,
    val builtInServices: List<ServicePrice> = emptyList(),
    val customServices: List<ServicePrice> = emptyList(),
    val editingService: ServicePrice? = null,
    val showAddSheet: Boolean = false,
    val error: String? = null
)
```

---

## 7. Price Application Hierarchy

### 7.1 Price Resolution Order

```
┌─────────────────────────────────────────────────────────────┐
│               PRICE RESOLUTION HIERARCHY                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  When determining price for a service:                      │
│                                                              │
│  1. APPOINTMENT OVERRIDE (highest priority)                 │
│     └─ Price manually entered on this specific appointment  │
│                                                              │
│  2. CLIENT CUSTOM PRICE                                     │
│     └─ Price set in client's custom pricing settings        │
│                                                              │
│  3. SERVICE DEFAULT PRICE (lowest priority)                 │
│     └─ Price from Services & Pricing configuration          │
│                                                              │
│  Resolution Flow:                                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Has appointment override?                           │    │
│  │    YES → Use override price                          │    │
│  │    NO  ↓                                             │    │
│  │  Has client custom price for service?                │    │
│  │    YES → Use client price                            │    │
│  │    NO  ↓                                             │    │
│  │  Use service default price                           │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 PriceResolver

**File**: `core/service/PriceResolver.kt`

```kotlin
class PriceResolver @Inject constructor(
    private val servicePriceDao: ServicePriceDao,
    private val clientCustomPriceDao: ClientCustomPriceDao
) {
    suspend fun resolvePrice(
        serviceType: String,
        clientId: String?,
        appointmentOverride: BigDecimal? = null
    ): ResolvedPrice {
        // 1. Check appointment override
        if (appointmentOverride != null) {
            return ResolvedPrice(
                amount = appointmentOverride,
                source = PriceSource.APPOINTMENT_OVERRIDE
            )
        }
        
        // 2. Check client custom price
        if (clientId != null) {
            val clientPrice = clientCustomPriceDao.getCustomPrice(clientId, serviceType)
            if (clientPrice != null) {
                return ResolvedPrice(
                    amount = clientPrice,
                    source = PriceSource.CLIENT_CUSTOM
                )
            }
        }
        
        // 3. Use service default
        val defaultPrice = servicePriceDao.getDefaultPrice(serviceType)
        return ResolvedPrice(
            amount = defaultPrice ?: BigDecimal.ZERO,
            source = PriceSource.SERVICE_DEFAULT
        )
    }
    
    fun resolvePriceFlow(
        serviceType: String,
        clientId: String?
    ): Flow<ResolvedPrice> = flow {
        // Similar logic but as Flow for reactive updates
        combine(
            servicePriceDao.getServiceByType(serviceType),
            clientId?.let { clientCustomPriceDao.getCustomPriceFlow(it, serviceType) }
                ?: flowOf(null)
        ) { service, clientPrice ->
            if (clientPrice != null) {
                ResolvedPrice(clientPrice, PriceSource.CLIENT_CUSTOM)
            } else {
                ResolvedPrice(service?.defaultPrice ?: BigDecimal.ZERO, PriceSource.SERVICE_DEFAULT)
            }
        }.collect { emit(it) }
    }
}

data class ResolvedPrice(
    val amount: BigDecimal,
    val source: PriceSource
)

enum class PriceSource {
    SERVICE_DEFAULT,
    CLIENT_CUSTOM,
    APPOINTMENT_OVERRIDE
}
```

### 7.3 Client Custom Pricing Reference

**See**: FRD-003 (Client Management) Section on Custom Pricing

Client custom prices are stored in `client_custom_prices` table:

```kotlin
@Entity(
    tableName = "client_custom_prices",
    primaryKeys = ["client_id", "service_type"],
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ClientCustomPriceEntity(
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "service_type") val serviceType: String,
    val price: BigDecimal,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now()
)
```

---

## 8. Integration Points

### 8.1 Appointment Creation

**When adding horse to appointment**:
1. User selects service type for horse
2. System calls `PriceResolver.resolvePrice(serviceType, clientId)`
3. Price auto-populated in service field
4. Price indicator shows source: "(Default)" or "(Client rate)" or no indicator if overridden

**UI Indication**:
```
Service: Full Set
Price: $180.00 (Default)
        $160.00 (Client rate)    ← If client has custom price
        $200.00                  ← If user manually changed
```

### 8.2 Invoice Line Items

**When creating invoice from appointment**:
1. For each horse service, resolve price using hierarchy
2. Pre-populate line item with resolved price
3. User can still override on invoice

### 8.3 Duration for Scheduling

**Appointment duration calculation**:
```kotlin
fun calculateAppointmentDuration(horses: List<HorseService>): Int {
    return horses.sumOf { horse ->
        servicePriceRepository.getService(horse.serviceType)?.durationMinutes ?: 30
    }
}
```

---

## 9. Data Models

### 9.1 ServicePriceEntity

**File**: `core/database/entity/ServicePriceEntity.kt`

```kotlin
@Entity(
    tableName = "service_prices",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["service_type"]),
        Index(value = ["user_id", "service_type"], unique = true)
    ]
)
data class ServicePriceEntity(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "user_id") 
    val userId: String,
    
    @ColumnInfo(name = "service_type") 
    val serviceType: String,
    
    val name: String,
    
    @ColumnInfo(name = "default_price") 
    val defaultPrice: BigDecimal,
    
    @ColumnInfo(name = "duration_minutes") 
    val durationMinutes: Int,
    
    val description: String?,
    
    @ColumnInfo(name = "is_active") 
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "sort_order") 
    val sortOrder: Int,
    
    @ColumnInfo(name = "is_built_in") 
    val isBuiltIn: Boolean = false,
    
    @ColumnInfo(name = "created_at") 
    val createdAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "updated_at") 
    val updatedAt: Instant = Instant.now(),
    
    @ColumnInfo(name = "sync_status") 
    val syncStatus: EntitySyncStatus = EntitySyncStatus.SYNCED
)
```

### 9.2 ServicePriceDao

**File**: `core/database/dao/ServicePriceDao.kt`

```kotlin
@Dao
interface ServicePriceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ServicePriceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ServicePriceEntity>)
    
    @Update
    suspend fun update(entity: ServicePriceEntity)
    
    @Query("DELETE FROM service_prices WHERE id = :id")
    suspend fun delete(id: String)
    
    @Query("SELECT * FROM service_prices WHERE user_id = :userId ORDER BY sort_order ASC")
    fun getAllServices(userId: String): Flow<List<ServicePriceEntity>>
    
    @Query("SELECT * FROM service_prices WHERE user_id = :userId AND is_active = 1 ORDER BY sort_order ASC")
    fun getActiveServices(userId: String): Flow<List<ServicePriceEntity>>
    
    @Query("SELECT * FROM service_prices WHERE user_id = :userId AND service_type = :serviceType")
    fun getServiceByType(userId: String, serviceType: String): Flow<ServicePriceEntity?>
    
    @Query("SELECT default_price FROM service_prices WHERE user_id = :userId AND service_type = :serviceType")
    suspend fun getDefaultPrice(userId: String, serviceType: String): BigDecimal?
    
    @Query("SELECT duration_minutes FROM service_prices WHERE user_id = :userId AND service_type = :serviceType")
    suspend fun getDuration(userId: String, serviceType: String): Int?
    
    @Query("UPDATE service_prices SET sort_order = :sortOrder, updated_at = :updatedAt, sync_status = :syncStatus WHERE id = :id")
    suspend fun updateSortOrder(
        id: String, 
        sortOrder: Int, 
        updatedAt: Instant = Instant.now(),
        syncStatus: EntitySyncStatus = EntitySyncStatus.PENDING_UPDATE
    )
    
    @Query("SELECT * FROM service_prices WHERE sync_status != 'SYNCED'")
    suspend fun getPendingSync(): List<ServicePriceEntity>
    
    @Query("SELECT MAX(sort_order) FROM service_prices WHERE user_id = :userId")
    suspend fun getMaxSortOrder(userId: String): Int?
}
```

### 9.3 Domain Model

**File**: `core/domain/model/ServicePrice.kt`

```kotlin
data class ServicePrice(
    val id: String,
    val serviceType: String,
    val name: String,
    val defaultPrice: BigDecimal,
    val durationMinutes: Int,
    val description: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
    val isCustom: Boolean = !isBuiltIn
)

// Extension for display
fun ServicePrice.displayDuration(): String {
    return when {
        durationMinutes >= 60 -> {
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60
            if (minutes > 0) "${hours}h ${minutes}min" else "${hours}h"
        }
        else -> "$durationMinutes min"
    }
}
```

---

## 10. ServicePriceRepository

**File**: `core/data/repository/ServicePriceRepositoryImpl.kt`

```kotlin
class ServicePriceRepositoryImpl @Inject constructor(
    private val servicePriceDao: ServicePriceDao,
    private val syncManager: SyncManager,
    private val userRepository: UserRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ServicePriceRepository {
    
    override fun getAllServices(): Flow<List<ServicePrice>> {
        return flow {
            val userId = userRepository.getCurrentUserId() ?: return@flow
            servicePriceDao.getAllServices(userId)
                .map { entities -> entities.map { it.toDomain() } }
                .collect { emit(it) }
        }.flowOn(ioDispatcher)
    }
    
    override fun getActiveServices(): Flow<List<ServicePrice>> {
        return flow {
            val userId = userRepository.getCurrentUserId() ?: return@flow
            servicePriceDao.getActiveServices(userId)
                .map { entities -> entities.map { it.toDomain() } }
                .collect { emit(it) }
        }.flowOn(ioDispatcher)
    }
    
    override suspend fun getService(serviceType: String): ServicePrice? {
        return withContext(ioDispatcher) {
            val userId = userRepository.getCurrentUserId() ?: return@withContext null
            servicePriceDao.getServiceByType(userId, serviceType)
                .first()
                ?.toDomain()
        }
    }
    
    override suspend fun getDefaultPrice(serviceType: String): BigDecimal? {
        return withContext(ioDispatcher) {
            val userId = userRepository.getCurrentUserId() ?: return@withContext null
            servicePriceDao.getDefaultPrice(userId, serviceType)
        }
    }
    
    override suspend fun getDuration(serviceType: String): Int? {
        return withContext(ioDispatcher) {
            val userId = userRepository.getCurrentUserId() ?: return@withContext null
            servicePriceDao.getDuration(userId, serviceType)
        }
    }
    
    override suspend fun createService(service: ServicePrice): Result<ServicePrice> {
        return withContext(ioDispatcher) {
            try {
                val userId = userRepository.getCurrentUserId()
                    ?: return@withContext Result.failure(Exception("Not logged in"))
                
                val maxOrder = servicePriceDao.getMaxSortOrder(userId) ?: 0
                
                val entity = service.toEntity(userId).copy(
                    sortOrder = maxOrder + 1,
                    syncStatus = EntitySyncStatus.PENDING_CREATE
                )
                
                servicePriceDao.insert(entity)
                
                syncManager.queueChange(
                    entityType = "service_prices",
                    entityId = entity.id,
                    operation = SyncOperation.INSERT,
                    payload = Json.encodeToString(entity)
                )
                
                Result.success(entity.toDomain())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateService(service: ServicePrice): Result<ServicePrice> {
        return withContext(ioDispatcher) {
            try {
                val userId = userRepository.getCurrentUserId()
                    ?: return@withContext Result.failure(Exception("Not logged in"))
                
                val entity = service.toEntity(userId).copy(
                    updatedAt = Instant.now(),
                    syncStatus = EntitySyncStatus.PENDING_UPDATE
                )
                
                servicePriceDao.update(entity)
                
                syncManager.queueChange(
                    entityType = "service_prices",
                    entityId = entity.id,
                    operation = SyncOperation.UPDATE,
                    payload = Json.encodeToString(entity)
                )
                
                Result.success(service)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun deleteService(serviceId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                servicePriceDao.delete(serviceId)
                
                syncManager.queueChange(
                    entityType = "service_prices",
                    entityId = serviceId,
                    operation = SyncOperation.DELETE,
                    payload = null
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateSortOrders(orders: List<Pair<String, Int>>) {
        withContext(ioDispatcher) {
            orders.forEach { (id, order) ->
                servicePriceDao.updateSortOrder(id, order)
            }
            
            // Queue sync for all updated items
            orders.forEach { (id, _) ->
                syncManager.queueChange(
                    entityType = "service_prices",
                    entityId = id,
                    operation = SyncOperation.UPDATE,
                    payload = null  // Just sort order change
                )
            }
        }
    }
}
```

---

## 11. Acceptance Criteria

### Service Configuration
| ID | Criteria | Test |
|----|----------|------|
| AC-012-01 | New user completes signup → 4 default services created (Trim, Front Shoes, Full Set, Corrective) | Integration |
| AC-012-02 | User views Services & Pricing → all services displayed in sort order with name, price, duration | UI |
| AC-012-03 | User taps service → edit sheet opens with all fields pre-filled | UI |
| AC-012-04 | User changes name to "Basic Trim" and saves → list shows updated name | Integration |
| AC-012-05 | User changes price from $45 to $50 → price updated, syncs to server | Integration |
| AC-012-06 | User changes duration from 30 to 45 min → duration updated | Integration |
| AC-012-07 | User enters name > 50 characters → input stops at 50 characters | UI |
| AC-012-08 | User enters price > $9,999.99 → validation error shown | Unit |
| AC-012-09 | User enters duration > 480 min → validation error shown | Unit |
| AC-012-10 | User leaves name empty and taps Save → "Service name is required" error | Unit |

### Active/Inactive
| ID | Criteria | Test |
|----|----------|------|
| AC-012-11 | User toggles service inactive → service shows "(Inactive)" label, grayed out | UI |
| AC-012-12 | Inactive service → not shown in appointment service dropdown | Integration |
| AC-012-13 | Inactive service → still visible in Services & Pricing list | UI |
| AC-012-14 | User toggles service active again → service available in appointment creation | Integration |

### Custom Services
| ID | Criteria | Test |
|----|----------|------|
| AC-012-15 | User taps "+ Add Custom Service" → add sheet opens with empty fields | UI |
| AC-012-16 | User creates "Therapeutic Shoeing" at $280, 90 min → service appears in list with "(Custom)" label | Integration |
| AC-012-17 | User taps custom service → edit sheet shows "Delete Service" button | UI |
| AC-012-18 | User taps built-in service → edit sheet does NOT show delete button | UI |
| AC-012-19 | User deletes custom service → confirmation dialog, then service removed | Integration |
| AC-012-20 | User creates service with duplicate name "Trim" → error "A service with this name already exists" | Unit |

### Reordering
| ID | Criteria | Test |
|----|----------|------|
| AC-012-21 | User long-presses service, drags to new position → order updates visually during drag | UI |
| AC-012-22 | User drops service in new position → sort orders saved to database | Integration |
| AC-012-23 | User reopens Services screen after reorder → order persisted | Integration |

### Price Application
| ID | Criteria | Test |
|----|----------|------|
| AC-012-24 | User adds horse to appointment, selects "Full Set" for client with no custom pricing → $180.00 auto-populated, shows "(Default)" | Integration |
| AC-012-25 | User adds horse to appointment, selects "Full Set" for client with $160 custom price → $160.00 auto-populated, shows "(Client rate)" | Integration |
| AC-012-26 | User manually changes price on appointment → override price saved, no indicator shown | Integration |

### Duration Integration
| ID | Criteria | Test |
|----|----------|------|
| AC-012-27 | Appointment with 2 horses (Full Set 60min, Trim 30min) → total duration shows 90 min | Integration |

### Offline
| ID | Criteria | Test |
|----|----------|------|
| AC-012-28 | User edits service while offline → changes saved locally, syncStatus = PENDING_UPDATE | Integration |
| AC-012-29 | User comes online → pending service changes sync to server | Integration |

---

## 12. Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Service list load | < 100ms | Profiler |
| Price lookup | < 50ms | Instrumentation |
| Service save | < 200ms | Instrumentation |
| Drag-and-drop reorder | 60 FPS | Frame profiler |
| Sort order save | < 300ms | Instrumentation |

---

## 13. File Reference Summary

| Component | File Path |
|-----------|-----------|
| Services Screen | `feature/settings/ui/ServicesScreen.kt` |
| Services ViewModel | `feature/settings/ui/ServicesViewModel.kt` |
| Service List Item | `feature/settings/ui/components/ServiceListItem.kt` |
| Edit Service Sheet | `feature/settings/ui/components/EditServiceSheet.kt` |
| Add Service Sheet | `feature/settings/ui/components/AddServiceSheet.kt` |
| Service Entity | `core/database/entity/ServicePriceEntity.kt` |
| Service DAO | `core/database/dao/ServicePriceDao.kt` |
| Client Custom Price Entity | `core/database/entity/ClientCustomPriceEntity.kt` |
| Service Type Enum | `core/domain/model/ServiceType.kt` |
| Service Price Model | `core/domain/model/ServicePrice.kt` |
| Repository Interface | `core/domain/repository/ServicePriceRepository.kt` |
| Repository Impl | `core/data/repository/ServicePriceRepositoryImpl.kt` |
| Default Services Init | `core/service/DefaultServicesInitializer.kt` |
| Price Resolver | `core/service/PriceResolver.kt` |

---

## 14. Open Questions

1. **Service Categories**: Should services be groupable into categories (e.g., "Basic", "Specialty")?
2. **Price History**: Should price change history be tracked for reporting purposes?
3. **Multi-Currency**: Is USD-only sufficient for MVP, or do international users need other currencies?
4. **Bulk Price Update**: Should there be a "increase all prices by X%" feature?
5. **Service Templates**: Should users be able to import service lists from templates or other users?
