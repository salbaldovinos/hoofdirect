# FRD-017: Usage Limits Enforcement

**Version:** 1.0  
**Last Updated:** January 2026  
**PRD Reference:** PRD-017-usage-limits.md  
**Priority:** P0  
**Phase:** 5 - Monetization

---

## 1. Overview

### 1.1 Purpose

Enforce tier-based usage limits throughout the app with clear messaging, soft warnings when approaching limits, hard blocks when limits are reached, and prominent upgrade prompts to drive conversions.

### 1.2 Scope

This FRD covers:
- Tier limit definitions for all resource types
- Local limit checking (offline-capable)
- Soft warning system at 80% usage
- Hard block system at 100% usage
- Upgrade prompt UI components
- Usage display in settings
- SMS usage tracking and monthly reset

### 1.3 Dependencies

| Dependency | Type | FRD Reference |
|------------|------|---------------|
| Subscription Management | Internal | FRD-016 |
| Client Management | Internal | FRD-003 |
| Horse Management | Internal | FRD-004 |
| Route Optimization | Internal | FRD-010 |
| Reminders (SMS) | Internal | FRD-008 |

---

## 2. Tier Limits

### 2.1 Limit Definitions

| Resource | Free | Solo | Growing | Multi |
|----------|------|------|---------|-------|
| Clients | 10 | ∞ | ∞ | ∞ |
| Horses | 30 | ∞ | ∞ | ∞ |
| Photos | 50 | ∞ | ∞ | ∞ |
| Route stops/day | 0 | 8 | 15 | ∞ |
| SMS/month | 0 | 50 | 200 | 500 |
| Users (team) | 1 | 1 | 2 | 5 |

### 2.2 Data Model

```kotlin
// Location: core/domain/model/TierLimits.kt
data class TierLimits(
    val clients: Int,
    val horses: Int,
    val photos: Int,
    val routeStopsPerDay: Int,
    val smsPerMonth: Int,
    val users: Int
) {
    companion object {
        val UNLIMITED = Int.MAX_VALUE
        
        fun forTier(tier: SubscriptionTier): TierLimits = when (tier) {
            SubscriptionTier.FREE -> TierLimits(
                clients = 10,
                horses = 30,
                photos = 50,
                routeStopsPerDay = 0,
                smsPerMonth = 0,
                users = 1
            )
            SubscriptionTier.SOLO -> TierLimits(
                clients = UNLIMITED,
                horses = UNLIMITED,
                photos = UNLIMITED,
                routeStopsPerDay = 8,
                smsPerMonth = 50,
                users = 1
            )
            SubscriptionTier.GROWING -> TierLimits(
                clients = UNLIMITED,
                horses = UNLIMITED,
                photos = UNLIMITED,
                routeStopsPerDay = 15,
                smsPerMonth = 200,
                users = 2
            )
            SubscriptionTier.MULTI -> TierLimits(
                clients = UNLIMITED,
                horses = UNLIMITED,
                photos = UNLIMITED,
                routeStopsPerDay = UNLIMITED,
                smsPerMonth = 500,
                users = 5
            )
        }
    }
}

// Location: core/domain/model/LimitCheckResult.kt
sealed class LimitCheckResult {
    data object Allowed : LimitCheckResult()
    
    data class Warning(
        val message: String,
        val current: Int,
        val limit: Int,
        val percentage: Float
    ) : LimitCheckResult()
    
    data class Blocked(
        val reason: String,
        val upgradeMessage: String,
        val suggestedTier: SubscriptionTier?
    ) : LimitCheckResult()
}

// Location: core/domain/model/UsageItem.kt
data class UsageItem(
    val used: Int,
    val limit: Int
) {
    val isUnlimited: Boolean
        get() = limit == TierLimits.UNLIMITED
    
    val percentage: Float
        get() = if (isUnlimited || limit == 0) 0f else used.toFloat() / limit
    
    val isAtLimit: Boolean
        get() = !isUnlimited && used >= limit
    
    val isNearLimit: Boolean
        get() = !isUnlimited && percentage >= 0.8f
    
    val remaining: Int
        get() = if (isUnlimited) Int.MAX_VALUE else (limit - used).coerceAtLeast(0)
}

// Location: core/domain/model/UsageSummary.kt
data class UsageSummary(
    val tier: SubscriptionTier,
    val clients: UsageItem,
    val horses: UsageItem,
    val photos: UsageItem,
    val routeStopsPerDay: UsageItem,
    val smsThisMonth: UsageItem,
    val users: UsageItem,
    val smsResetDate: LocalDate
)
```

---

## 3. Usage Limits Manager

### 3.1 Core Implementation

```kotlin
// Location: core/limits/UsageLimitsManager.kt
@Singleton
class UsageLimitsManager @Inject constructor(
    private val userDao: UserDao,
    private val clientDao: ClientDao,
    private val horseDao: HorseDao,
    private val photoDao: PhotoDao,
    private val smsUsageDao: SmsUsageDao,
    private val routeUsageDao: RouteUsageDao
) {
    private val tierLimitsCache = mutableMapOf<SubscriptionTier, TierLimits>()
    
    // Synchronous tier access for immediate checks
    private var cachedTier: SubscriptionTier = SubscriptionTier.FREE
    
    init {
        // Pre-populate cache
        SubscriptionTier.values().forEach { tier ->
            tierLimitsCache[tier] = TierLimits.forTier(tier)
        }
    }
    
    fun updateCachedTier(tier: SubscriptionTier) {
        cachedTier = tier
    }
    
    private fun getLimits(tier: SubscriptionTier = cachedTier): TierLimits =
        tierLimitsCache[tier] ?: TierLimits.forTier(tier)
    
    // ─────────────────────────────────────────────────────────────
    // Client Limits
    // ─────────────────────────────────────────────────────────────
    
    suspend fun canAddClient(): LimitCheckResult {
        val limits = getLimits()
        val current = clientDao.getActiveCount()
        
        return checkLimit(
            current = current,
            limit = limits.clients,
            resourceName = "clients",
            upgradeMessage = "Upgrade for unlimited clients",
            suggestedTier = if (cachedTier == SubscriptionTier.FREE) 
                SubscriptionTier.SOLO else null
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // Horse Limits
    // ─────────────────────────────────────────────────────────────
    
    suspend fun canAddHorse(): LimitCheckResult {
        val limits = getLimits()
        val current = horseDao.getActiveCount()
        
        return checkLimit(
            current = current,
            limit = limits.horses,
            resourceName = "horses",
            upgradeMessage = "Upgrade for unlimited horses",
            suggestedTier = if (cachedTier == SubscriptionTier.FREE)
                SubscriptionTier.SOLO else null
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // Photo Limits
    // ─────────────────────────────────────────────────────────────
    
    suspend fun canAddPhoto(): LimitCheckResult {
        val limits = getLimits()
        val current = photoDao.getTotalCount()
        
        return checkLimit(
            current = current,
            limit = limits.photos,
            resourceName = "photos",
            upgradeMessage = "Upgrade for unlimited photo storage",
            suggestedTier = if (cachedTier == SubscriptionTier.FREE)
                SubscriptionTier.SOLO else null
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // Route Optimization Limits
    // ─────────────────────────────────────────────────────────────
    
    suspend fun canOptimizeRoute(stopCount: Int): LimitCheckResult {
        val limits = getLimits()
        
        // Free tier: No route optimization at all
        if (limits.routeStopsPerDay == 0) {
            return LimitCheckResult.Blocked(
                reason = "Route optimization requires a paid subscription",
                upgradeMessage = "Upgrade to Solo Farrier to optimize routes with up to 8 stops per day",
                suggestedTier = SubscriptionTier.SOLO
            )
        }
        
        // Check if stop count exceeds tier limit
        if (limits.routeStopsPerDay != TierLimits.UNLIMITED && stopCount > limits.routeStopsPerDay) {
            val nextTier = when (cachedTier) {
                SubscriptionTier.SOLO -> SubscriptionTier.GROWING
                SubscriptionTier.GROWING -> SubscriptionTier.MULTI
                else -> null
            }
            
            val upgradeMsg = when (cachedTier) {
                SubscriptionTier.SOLO -> "Upgrade to Growing Practice for routes up to 15 stops"
                SubscriptionTier.GROWING -> "Upgrade to Multi-Farrier for unlimited route stops"
                else -> "Upgrade for more route stops"
            }
            
            return LimitCheckResult.Blocked(
                reason = "Your plan supports routes up to ${limits.routeStopsPerDay} stops (you have $stopCount)",
                upgradeMessage = upgradeMsg,
                suggestedTier = nextTier
            )
        }
        
        return LimitCheckResult.Allowed
    }
    
    fun getMaxRouteStops(): Int {
        return getLimits().routeStopsPerDay
    }
    
    // ─────────────────────────────────────────────────────────────
    // SMS Limits
    // ─────────────────────────────────────────────────────────────
    
    suspend fun canSendSms(): LimitCheckResult {
        val limits = getLimits()
        
        // Free tier: No SMS
        if (limits.smsPerMonth == 0) {
            return LimitCheckResult.Blocked(
                reason = "SMS reminders require a paid subscription",
                upgradeMessage = "Upgrade to Solo Farrier for 50 SMS reminders per month",
                suggestedTier = SubscriptionTier.SOLO
            )
        }
        
        val currentMonthUsage = smsUsageDao.getCurrentMonthCount()
        
        return checkLimit(
            current = currentMonthUsage,
            limit = limits.smsPerMonth,
            resourceName = "SMS messages this month",
            upgradeMessage = getSmSUpgradeMessage(),
            suggestedTier = getNextTierForSms()
        )
    }
    
    suspend fun incrementSmsCount() {
        smsUsageDao.incrementCurrentMonth()
    }
    
    suspend fun getSmsUsage(): UsageItem {
        val limits = getLimits()
        val used = if (limits.smsPerMonth == 0) 0 else smsUsageDao.getCurrentMonthCount()
        return UsageItem(used = used, limit = limits.smsPerMonth)
    }
    
    private fun getSmSUpgradeMessage(): String = when (cachedTier) {
        SubscriptionTier.SOLO -> "Upgrade to Growing Practice for 200 SMS per month"
        SubscriptionTier.GROWING -> "Upgrade to Multi-Farrier for 500 SMS per month"
        else -> "Upgrade for SMS reminders"
    }
    
    private fun getNextTierForSms(): SubscriptionTier? = when (cachedTier) {
        SubscriptionTier.FREE -> SubscriptionTier.SOLO
        SubscriptionTier.SOLO -> SubscriptionTier.GROWING
        SubscriptionTier.GROWING -> SubscriptionTier.MULTI
        SubscriptionTier.MULTI -> null
    }
    
    // ─────────────────────────────────────────────────────────────
    // Usage Summary
    // ─────────────────────────────────────────────────────────────
    
    fun getUsageSummary(): Flow<UsageSummary> = combine(
        clientDao.getActiveCountFlow(),
        horseDao.getActiveCountFlow(),
        photoDao.getTotalCountFlow(),
        smsUsageDao.getCurrentMonthCountFlow(),
        userDao.getCurrentUser()
    ) { clients, horses, photos, sms, user ->
        val tier = SubscriptionTier.fromString(user.subscriptionTier)
        val limits = TierLimits.forTier(tier)
        
        UsageSummary(
            tier = tier,
            clients = UsageItem(clients, limits.clients),
            horses = UsageItem(horses, limits.horses),
            photos = UsageItem(photos, limits.photos),
            routeStopsPerDay = UsageItem(0, limits.routeStopsPerDay), // Per-day, not cumulative
            smsThisMonth = UsageItem(sms, limits.smsPerMonth),
            users = UsageItem(1, limits.users), // TODO: Implement team feature
            smsResetDate = getNextMonthStart()
        )
    }
    
    // ─────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────
    
    private fun checkLimit(
        current: Int,
        limit: Int,
        resourceName: String,
        upgradeMessage: String,
        suggestedTier: SubscriptionTier?
    ): LimitCheckResult {
        // Unlimited
        if (limit == TierLimits.UNLIMITED) {
            return LimitCheckResult.Allowed
        }
        
        val percentage = if (limit > 0) current.toFloat() / limit else 0f
        
        return when {
            // At or over limit
            current >= limit -> LimitCheckResult.Blocked(
                reason = "You've reached your $resourceName limit ($limit)",
                upgradeMessage = upgradeMessage,
                suggestedTier = suggestedTier
            )
            
            // Warning at 80%
            percentage >= 0.8f -> LimitCheckResult.Warning(
                message = "You're using ${(percentage * 100).toInt()}% of your $resourceName limit",
                current = current,
                limit = limit,
                percentage = percentage
            )
            
            // Under limit
            else -> LimitCheckResult.Allowed
        }
    }
    
    private fun getNextMonthStart(): LocalDate {
        val today = LocalDate.now()
        return today.plusMonths(1).withDayOfMonth(1)
    }
}
```

---

## 4. SMS Usage Tracking

### 4.1 Database Entity

```kotlin
// Location: core/database/entity/SmsUsageEntity.kt
@Entity(
    tableName = "sms_usage",
    indices = [Index(value = ["user_id", "year_month"], unique = true)]
)
data class SmsUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "year_month")
    val yearMonth: String,  // Format: "2026-01"
    
    @ColumnInfo(name = "count")
    val count: Int = 0,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = Instant.now().toString()
)
```

### 4.2 DAO

```kotlin
// Location: core/database/dao/SmsUsageDao.kt
@Dao
interface SmsUsageDao {
    
    @Query("""
        SELECT COALESCE(
            (SELECT count FROM sms_usage 
             WHERE user_id = :userId AND year_month = :yearMonth),
            0
        )
    """)
    suspend fun getCountForMonth(userId: String, yearMonth: String): Int
    
    @Query("""
        SELECT COALESCE(
            (SELECT count FROM sms_usage 
             WHERE user_id = :userId AND year_month = :yearMonth),
            0
        )
    """)
    fun getCountForMonthFlow(userId: String, yearMonth: String): Flow<Int>
    
    @Query("""
        INSERT INTO sms_usage (user_id, year_month, count, updated_at)
        VALUES (:userId, :yearMonth, 1, :updatedAt)
        ON CONFLICT(user_id, year_month) 
        DO UPDATE SET count = count + 1, updated_at = :updatedAt
    """)
    suspend fun increment(userId: String, yearMonth: String, updatedAt: String)
    
    @Query("SELECT * FROM sms_usage WHERE user_id = :userId ORDER BY year_month DESC")
    suspend fun getAllForUser(userId: String): List<SmsUsageEntity>
}

// Location: data/repository/SmsUsageRepository.kt
@Singleton
class SmsUsageRepository @Inject constructor(
    private val smsUsageDao: SmsUsageDao,
    private val userRepository: UserRepository
) {
    private val currentYearMonth: String
        get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
    
    suspend fun getCurrentMonthCount(): Int {
        val userId = userRepository.getCurrentUserId()
        return smsUsageDao.getCountForMonth(userId, currentYearMonth)
    }
    
    fun getCurrentMonthCountFlow(): Flow<Int> {
        return userRepository.getCurrentUserIdFlow().flatMapLatest { userId ->
            smsUsageDao.getCountForMonthFlow(userId, currentYearMonth)
        }
    }
    
    suspend fun incrementCurrentMonth() {
        val userId = userRepository.getCurrentUserId()
        smsUsageDao.increment(
            userId = userId,
            yearMonth = currentYearMonth,
            updatedAt = Instant.now().toString()
        )
    }
}
```

---

## 5. Soft Warnings (80%)

### 5.1 Warning Banner Component

```kotlin
// Location: core/ui/components/LimitWarningBanner.kt
@Composable
fun LimitWarningBanner(
    warning: LimitCheckResult.Warning,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.warningContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.warning
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${warning.current} of ${warning.limit} used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TextButton(onClick = onUpgrade) {
                Text("Upgrade")
            }
            
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
```

### 5.2 Warning Display Logic

Warnings are shown:
- Once per session per resource type
- Above the main content area
- Dismissible by user
- Include upgrade CTA

```kotlin
// Location: feature/client/ui/ClientListViewModel.kt
@HiltViewModel
class ClientListViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val usageLimits: UsageLimitsManager
) : ViewModel() {
    
    private val _showWarning = MutableStateFlow<LimitCheckResult.Warning?>(null)
    val showWarning: StateFlow<LimitCheckResult.Warning?> = _showWarning.asStateFlow()
    
    private val dismissedWarnings = mutableSetOf<String>()
    
    fun addClient(client: Client) {
        viewModelScope.launch {
            when (val result = usageLimits.canAddClient()) {
                is LimitCheckResult.Allowed -> {
                    clientRepository.create(client)
                }
                
                is LimitCheckResult.Warning -> {
                    // Show warning but allow action
                    if ("clients" !in dismissedWarnings) {
                        _showWarning.value = result
                    }
                    clientRepository.create(client)
                }
                
                is LimitCheckResult.Blocked -> {
                    _showUpgradePrompt.value = UpgradePrompt(
                        reason = result.reason,
                        message = result.upgradeMessage,
                        suggestedTier = result.suggestedTier
                    )
                }
            }
        }
    }
    
    fun dismissWarning() {
        _showWarning.value = null
        dismissedWarnings.add("clients")
    }
}
```

---

## 6. Hard Blocks (100%)

### 6.1 Upgrade Prompt Dialog

```kotlin
// Location: core/ui/components/UpgradePromptDialog.kt
@Composable
fun UpgradePromptDialog(
    prompt: UpgradePrompt,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Limit Reached",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = prompt.reason,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = prompt.message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                if (prompt.suggestedTier != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TierBadge(tier = prompt.suggestedTier)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpgrade) {
                Text("Upgrade Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}

data class UpgradePrompt(
    val reason: String,
    val message: String,
    val suggestedTier: SubscriptionTier?
)
```

### 6.2 Block Implementation by Feature

#### Client Creation Block

```kotlin
// Location: feature/client/ui/AddClientScreen.kt
@Composable
fun AddClientScreen(
    viewModel: AddClientViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onNavigateToUpgrade: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Check limit on screen load
    LaunchedEffect(Unit) {
        viewModel.checkClientLimit()
    }
    
    uiState.upgradePrompt?.let { prompt ->
        UpgradePromptDialog(
            prompt = prompt,
            onUpgrade = {
                viewModel.dismissUpgradePrompt()
                onNavigateToUpgrade()
            },
            onDismiss = {
                viewModel.dismissUpgradePrompt()
                onNavigateUp()
            }
        )
    }
    
    // Only show form if not blocked
    if (uiState.upgradePrompt == null) {
        AddClientForm(
            onSave = { client ->
                viewModel.saveClient(client)
            },
            // ...
        )
    }
}
```

#### Route Optimization Block

```kotlin
// Location: feature/route/ui/RouteOptimizationViewModel.kt
@HiltViewModel
class RouteOptimizationViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val usageLimits: UsageLimitsManager
) : ViewModel() {
    
    fun optimizeRoute(stops: List<RouteStop>) {
        viewModelScope.launch {
            when (val result = usageLimits.canOptimizeRoute(stops.size)) {
                is LimitCheckResult.Allowed -> {
                    // Proceed with optimization
                    val optimizedRoute = routeRepository.optimize(stops)
                    _optimizedRoute.value = optimizedRoute
                }
                
                is LimitCheckResult.Warning -> {
                    // Route optimization doesn't have a warning state
                    // (either you can or you can't based on stop count)
                    val optimizedRoute = routeRepository.optimize(stops)
                    _optimizedRoute.value = optimizedRoute
                }
                
                is LimitCheckResult.Blocked -> {
                    _showUpgradePrompt.value = UpgradePrompt(
                        reason = result.reason,
                        message = result.upgradeMessage,
                        suggestedTier = result.suggestedTier
                    )
                }
            }
        }
    }
}
```

#### SMS Reminder Block

```kotlin
// Location: feature/reminders/ReminderService.kt
@Singleton
class ReminderService @Inject constructor(
    private val usageLimits: UsageLimitsManager,
    private val smsGateway: SmsGateway
) {
    suspend fun sendSmsReminder(
        phoneNumber: String,
        message: String
    ): ReminderResult {
        // Check SMS limit first
        when (val result = usageLimits.canSendSms()) {
            is LimitCheckResult.Allowed -> {
                // Send SMS
                smsGateway.send(phoneNumber, message)
                usageLimits.incrementSmsCount()
                return ReminderResult.Sent
            }
            
            is LimitCheckResult.Warning -> {
                // Send but flag warning
                smsGateway.send(phoneNumber, message)
                usageLimits.incrementSmsCount()
                return ReminderResult.SentWithWarning(
                    message = result.message,
                    remaining = result.limit - result.current - 1
                )
            }
            
            is LimitCheckResult.Blocked -> {
                return ReminderResult.LimitReached(
                    reason = result.reason,
                    upgradeMessage = result.upgradeMessage
                )
            }
        }
    }
}

sealed class ReminderResult {
    data object Sent : ReminderResult()
    
    data class SentWithWarning(
        val message: String,
        val remaining: Int
    ) : ReminderResult()
    
    data class LimitReached(
        val reason: String,
        val upgradeMessage: String
    ) : ReminderResult()
}
```

---

## 7. Usage Display Screen

### 7.1 Route and Layout

**Route:** `/settings/usage`

```
┌─────────────────────────────────────────┐
│ [←] Usage                               │
├─────────────────────────────────────────┤
│                                         │
│  SOLO FARRIER                           │
│                                         │
│  Clients                                │
│  █████████████████████████████░░  87    │
│  Unlimited ✓                           │
│                                         │
│  Horses                                 │
│  █████████████████████████████░░  142   │
│  Unlimited ✓                           │
│                                         │
│  Photos                                 │
│  ████████████████████████░░░░░░  312    │
│  Unlimited ✓                           │
│                                         │
│  Route Stops                            │
│  Up to 8 per day                       │
│                                         │
│  SMS This Month                         │
│  █████████████████░░░░░░░░░░░░  38/50  │
│  ████████████████░░░░░░░░░░░░░  76%    │
│  Resets Feb 1                          │
│                                         │
│  Team Members                           │
│  1 user                                │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Need more? [Upgrade Plan]              │
│                                         │
└─────────────────────────────────────────┘
```

### 7.2 Usage Screen Implementation

```kotlin
// Location: feature/settings/ui/UsageScreen.kt
@Composable
fun UsageScreen(
    viewModel: UsageViewModel = hiltViewModel(),
    onNavigateToUpgrade: () -> Unit
) {
    val usage by viewModel.usageSummary.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Usage") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tier badge
            item {
                TierHeader(tier = usage?.tier ?: SubscriptionTier.FREE)
            }
            
            // Usage items
            usage?.let { summary ->
                item {
                    UsageRow(
                        label = "Clients",
                        usage = summary.clients,
                        showProgress = !summary.clients.isUnlimited
                    )
                }
                
                item {
                    UsageRow(
                        label = "Horses",
                        usage = summary.horses,
                        showProgress = !summary.horses.isUnlimited
                    )
                }
                
                item {
                    UsageRow(
                        label = "Photos",
                        usage = summary.photos,
                        showProgress = !summary.photos.isUnlimited
                    )
                }
                
                item {
                    UsageRowStatic(
                        label = "Route Stops",
                        value = when {
                            summary.routeStopsPerDay.limit == 0 -> "Not available"
                            summary.routeStopsPerDay.isUnlimited -> "Unlimited"
                            else -> "Up to ${summary.routeStopsPerDay.limit} per day"
                        }
                    )
                }
                
                if (summary.smsThisMonth.limit > 0) {
                    item {
                        UsageRowWithReset(
                            label = "SMS This Month",
                            usage = summary.smsThisMonth,
                            resetDate = summary.smsResetDate
                        )
                    }
                } else {
                    item {
                        UsageRowStatic(
                            label = "SMS Reminders",
                            value = "Not available"
                        )
                    }
                }
                
                item {
                    UsageRowStatic(
                        label = "Team Members",
                        value = "${summary.users.limit} user${if (summary.users.limit > 1) "s" else ""}"
                    )
                }
            }
            
            // Upgrade CTA
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onNavigateToUpgrade,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Need more? Upgrade Plan")
                }
            }
        }
    }
}

@Composable
private fun UsageRow(
    label: String,
    usage: UsageItem,
    showProgress: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (usage.isUnlimited) {
                Text(
                    text = "${usage.used}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "${usage.used}/${usage.limit}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        if (showProgress && !usage.isUnlimited) {
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = usage.percentage.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    usage.isAtLimit -> MaterialTheme.colorScheme.error
                    usage.isNearLimit -> MaterialTheme.colorScheme.warning
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
        
        if (usage.isUnlimited) {
            Text(
                text = "Unlimited ✓",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UsageRowWithReset(
    label: String,
    usage: UsageItem,
    resetDate: LocalDate
) {
    Column {
        UsageRow(label = label, usage = usage, showProgress = true)
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Resets ${resetDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

---

## 8. Feature-Specific UI Restrictions

### 8.1 Route Optimization Button State

```kotlin
// Location: feature/calendar/ui/components/RouteOptimizeButton.kt
@Composable
fun RouteOptimizeButton(
    stopCount: Int,
    maxStops: Int,
    onOptimize: () -> Unit,
    onShowUpgrade: () -> Unit
) {
    val isAllowed = maxStops == TierLimits.UNLIMITED || 
                    (maxStops > 0 && stopCount <= maxStops)
    val isDisabled = maxStops == 0
    
    when {
        isDisabled -> {
            // Free tier - show upgrade prompt
            OutlinedButton(
                onClick = onShowUpgrade,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upgrade to Optimize")
            }
        }
        
        !isAllowed -> {
            // Over stop limit
            OutlinedButton(
                onClick = onShowUpgrade,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("$stopCount stops (max $maxStops)")
            }
        }
        
        else -> {
            // Allowed
            Button(onClick = onOptimize) {
                Icon(Icons.Default.Route, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Optimize Route")
            }
        }
    }
}
```

### 8.2 SMS Toggle State

```kotlin
// Location: feature/reminders/ui/components/SmsReminderToggle.kt
@Composable
fun SmsReminderToggle(
    enabled: Boolean,
    smsAvailable: Boolean,
    smsRemaining: Int?,
    onToggle: (Boolean) -> Unit,
    onShowUpgrade: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SMS Reminder", style = MaterialTheme.typography.bodyLarge)
            
            if (smsAvailable && smsRemaining != null) {
                Text(
                    text = "$smsRemaining remaining this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (smsRemaining <= 5) 
                        MaterialTheme.colorScheme.error 
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!smsAvailable) {
                TextButton(
                    onClick = onShowUpgrade,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Upgrade to enable →",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        Switch(
            checked = enabled && smsAvailable,
            onCheckedChange = { newValue ->
                if (smsAvailable) {
                    onToggle(newValue)
                } else {
                    onShowUpgrade()
                }
            },
            enabled = smsAvailable
        )
    }
}
```

---

## 9. Offline Limit Checking

### 9.1 Offline Behavior

All limit checks work offline using locally cached data:
- User tier stored in UserEntity
- Client/horse/photo counts from local database
- SMS usage tracked in local sms_usage table
- No network required for limit checks

### 9.2 Sync on Reconnect

When network becomes available:
1. Sync subscription status (tier may have changed)
2. Update cached tier in UsageLimitsManager
3. Re-check any pending actions

```kotlin
// Location: core/sync/SyncManager.kt
class SyncManager @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val usageLimitsManager: UsageLimitsManager
) {
    suspend fun onNetworkAvailable() {
        // Sync subscription status first
        subscriptionRepository.syncSubscriptionStatus()
        
        // Update limits manager cache
        val tier = subscriptionRepository.getCurrentTier().first()
        usageLimitsManager.updateCachedTier(tier)
    }
}
```

---

## 10. Acceptance Criteria

### 10.1 Client Limits

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-01 | Free tier user with 10 clients → blocked when adding 11th | Unit test |
| AC-017-02 | Free tier user with 8 clients → warning banner shown | UI test |
| AC-017-03 | Solo tier user with 100 clients → no limit, allowed | Unit test |
| AC-017-04 | Blocked dialog shows "Upgrade to Solo" suggestion | UI test |

### 10.2 Horse Limits

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-05 | Free tier user with 30 horses → blocked when adding 31st | Unit test |
| AC-017-06 | Free tier user with 24 horses (80%) → warning shown | Unit test |
| AC-017-07 | Paid tier user with 200 horses → no limit | Unit test |

### 10.3 Route Optimization Limits

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-08 | Free tier user taps "Optimize" → upgrade prompt shown | UI test |
| AC-017-09 | Solo user with 8 stops → optimization allowed | Unit test |
| AC-017-10 | Solo user with 9 stops → blocked with "Upgrade to Growing" | Unit test |
| AC-017-11 | Growing user with 15 stops → allowed | Unit test |
| AC-017-12 | Growing user with 16 stops → blocked with "Upgrade to Multi" | Unit test |
| AC-017-13 | Multi user with 50 stops → unlimited, allowed | Unit test |

### 10.4 SMS Limits

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-14 | Free tier user → SMS toggle disabled with upgrade prompt | UI test |
| AC-017-15 | Solo user sends SMS → count incremented in sms_usage | Unit test |
| AC-017-16 | Solo user at 50 SMS → blocked for rest of month | Integration test |
| AC-017-17 | Solo user at 40 SMS (80%) → warning shown, still sent | Unit test |
| AC-017-18 | Month changes → SMS count resets to 0 | Integration test |

### 10.5 Warning Behavior

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-19 | Warning banner is dismissible | UI test |
| AC-017-20 | Dismissed warning doesn't reappear in same session | UI test |
| AC-017-21 | Warning shows percentage and counts | UI test |
| AC-017-22 | Warning includes "Upgrade" button | UI test |

### 10.6 Block Behavior

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-23 | Block dialog prevents action from completing | Integration test |
| AC-017-24 | Block dialog shows reason and upgrade message | UI test |
| AC-017-25 | "Upgrade Now" button navigates to subscription screen | UI test |
| AC-017-26 | "Maybe Later" dismisses dialog | UI test |

### 10.7 Usage Display

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-27 | Usage screen shows all resource counts | UI test |
| AC-017-28 | Progress bars show correct percentages | UI test |
| AC-017-29 | SMS shows reset date correctly | UI test |
| AC-017-30 | "Unlimited ✓" shown for unlimited resources | UI test |

### 10.8 Offline

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-31 | Limit checks work without network | Integration test |
| AC-017-32 | Usage counts accurate when offline | Integration test |

---

## 11. Performance Requirements

| Metric | Target |
|--------|--------|
| Limit check latency | < 50ms |
| Usage summary load | < 100ms |
| SMS count query | < 20ms |

---

## 12. Analytics Events

| Event | Trigger | Properties |
|-------|---------|------------|
| `limit_warning_shown` | 80% warning displayed | resource, current, limit, percentage |
| `limit_warning_dismissed` | User dismisses warning | resource |
| `limit_blocked` | Hard block triggered | resource, current, limit, tier |
| `limit_upgrade_clicked` | Upgrade button in limit UI | resource, source (warning/block) |
| `usage_screen_viewed` | Usage screen opened | tier, clients, horses, sms_used |

---

## 13. File References

```
core/domain/model/
├── TierLimits.kt
├── LimitCheckResult.kt
├── UsageItem.kt
└── UsageSummary.kt

core/limits/
└── UsageLimitsManager.kt

core/database/
├── entity/
│   └── SmsUsageEntity.kt
└── dao/
    └── SmsUsageDao.kt

data/repository/
└── SmsUsageRepository.kt

core/ui/components/
├── LimitWarningBanner.kt
├── UpgradePromptDialog.kt
└── TierBadge.kt

feature/settings/ui/
├── UsageScreen.kt
├── UsageViewModel.kt
└── components/
    ├── UsageRow.kt
    └── UsageRowWithReset.kt

feature/route/ui/components/
└── RouteOptimizeButton.kt

feature/reminders/ui/components/
└── SmsReminderToggle.kt
```

---

## 14. Error Handling

### 14.1 Database Errors

If count queries fail:
- Return 0 as safe default (allows action)
- Log error for debugging
- Sync on next network availability

### 14.2 Tier Resolution Errors

If tier cannot be determined:
- Default to FREE tier (most restrictive)
- Show error in UI if persistent
- Prompt to sync subscription status
