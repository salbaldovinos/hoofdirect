# PRD-017: Usage Limits Enforcement

**Priority**: P0  
**Phase**: 5 - Monetization  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Enforce tier-based usage limits in the app, with clear messaging and upgrade prompts when limits are reached.

### Business Value
- Drives upgrade conversions
- Prevents abuse of free tier
- Aligns cost with API usage (routes, SMS)
- Fair pricing model

### Success Metrics
| Metric | Target |
|--------|--------|
| Limit hit → upgrade rate | > 15% |
| False limit blocks | 0 |
| Limit check latency | < 50ms |

---

## Tier Limits

| Resource | Free | Solo | Growing | Multi |
|----------|------|------|---------|-------|
| Clients | 10 | ∞ | ∞ | ∞ |
| Horses | 30 | ∞ | ∞ | ∞ |
| Photos | 50 | ∞ | ∞ | ∞ |
| Route stops/day | 0 | 8 | 15 | ∞ |
| SMS/month | 0 | 50 | 200 | 500 |
| Users | 1 | 1 | 2 | 5 |

---

## Functional Requirements

### FR-017-01: Limit Checking
- Check limits locally (offline-capable)
- Cache tier info with sync
- Refresh on app foreground
- Real-time SMS count from server

### FR-017-02: Soft Warnings (80%)
- Show warning banner
- Don't block action
- "You're approaching your limit"

### FR-017-03: Hard Blocks (100%)
- Block action entirely
- Clear explanation
- Prominent upgrade CTA
- Alternative actions if available

### FR-017-04: Limit Display
- Usage visible in Settings
- Progress bars for countable limits
- Reset date for monthly limits (SMS)

---

## Technical Implementation

```kotlin
// UsageLimitsManager.kt
@Singleton
class UsageLimitsManager @Inject constructor(
    private val userDao: UserDao,
    private val clientDao: ClientDao,
    private val horseDao: HorseDao,
    private val photoDao: PhotoDao,
    private val smsUsageDao: SmsUsageDao
) {
    private val tierLimits = mapOf(
        SubscriptionTier.FREE to TierLimits(
            clients = 10, horses = 30, photos = 50,
            routeStops = 0, smsPerMonth = 0, users = 1
        ),
        SubscriptionTier.SOLO to TierLimits(
            clients = Int.MAX_VALUE, horses = Int.MAX_VALUE, photos = Int.MAX_VALUE,
            routeStops = 8, smsPerMonth = 50, users = 1
        ),
        SubscriptionTier.GROWING to TierLimits(
            clients = Int.MAX_VALUE, horses = Int.MAX_VALUE, photos = Int.MAX_VALUE,
            routeStops = 15, smsPerMonth = 200, users = 2
        ),
        SubscriptionTier.MULTI to TierLimits(
            clients = Int.MAX_VALUE, horses = Int.MAX_VALUE, photos = Int.MAX_VALUE,
            routeStops = Int.MAX_VALUE, smsPerMonth = 500, users = 5
        )
    )
    
    suspend fun canAddClient(): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = tierLimits[tier]!!
        val current = clientDao.getActiveCount()
        
        return checkLimit(current, limits.clients, "clients")
    }
    
    suspend fun canAddHorse(): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = tierLimits[tier]!!
        val current = horseDao.getActiveCount()
        
        return checkLimit(current, limits.horses, "horses")
    }
    
    suspend fun canOptimizeRoute(stopCount: Int): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = tierLimits[tier]!!
        
        if (limits.routeStops == 0) {
            return LimitCheckResult.Blocked(
                reason = "Route optimization requires a paid subscription",
                upgradeMessage = "Upgrade to Solo to optimize routes with up to 8 stops"
            )
        }
        
        if (stopCount > limits.routeStops) {
            return LimitCheckResult.Blocked(
                reason = "Your plan supports up to ${limits.routeStops} stops",
                upgradeMessage = "Upgrade to Growing for up to 15 stops per route"
            )
        }
        
        return LimitCheckResult.Allowed
    }
    
    suspend fun canSendSms(): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = tierLimits[tier]!!
        val usage = smsUsageDao.getCurrentMonthUsage()
        
        return checkLimit(usage, limits.smsPerMonth, "SMS messages")
    }
    
    suspend fun incrementSmsCount() {
        smsUsageDao.increment()
    }
    
    fun getMaxRouteStops(): Int {
        val tier = getCurrentTierSync()
        return tierLimits[tier]!!.routeStops
    }
    
    private fun checkLimit(current: Int, limit: Int, resource: String): LimitCheckResult {
        val percentage = if (limit > 0) current.toFloat() / limit else 0f
        
        return when {
            current >= limit -> LimitCheckResult.Blocked(
                reason = "You've reached your $resource limit ($limit)",
                upgradeMessage = "Upgrade for unlimited $resource"
            )
            percentage >= 0.8f -> LimitCheckResult.Warning(
                message = "You're using ${(percentage * 100).toInt()}% of your $resource limit",
                current = current,
                limit = limit
            )
            else -> LimitCheckResult.Allowed
        }
    }
    
    fun getUsageSummary(): Flow<UsageSummary> = combine(
        clientDao.getActiveCountFlow(),
        horseDao.getActiveCountFlow(),
        photoDao.getCountFlow(),
        smsUsageDao.getCurrentMonthUsageFlow(),
        userDao.getCurrentUser()
    ) { clients, horses, photos, sms, user ->
        val tier = SubscriptionTier.fromString(user.subscriptionTier)
        val limits = tierLimits[tier]!!
        
        UsageSummary(
            tier = tier,
            clients = UsageItem(clients, limits.clients),
            horses = UsageItem(horses, limits.horses),
            photos = UsageItem(photos, limits.photos),
            routeStops = UsageItem(0, limits.routeStops), // Per-day, not cumulative
            smsThisMonth = UsageItem(sms, limits.smsPerMonth),
            smsResetDate = getNextMonthStart()
        )
    }
}

sealed class LimitCheckResult {
    data object Allowed : LimitCheckResult()
    data class Warning(
        val message: String,
        val current: Int,
        val limit: Int
    ) : LimitCheckResult()
    data class Blocked(
        val reason: String,
        val upgradeMessage: String
    ) : LimitCheckResult()
}

data class TierLimits(
    val clients: Int,
    val horses: Int,
    val photos: Int,
    val routeStops: Int,
    val smsPerMonth: Int,
    val users: Int
)

data class UsageSummary(
    val tier: SubscriptionTier,
    val clients: UsageItem,
    val horses: UsageItem,
    val photos: UsageItem,
    val routeStops: UsageItem,
    val smsThisMonth: UsageItem,
    val smsResetDate: LocalDate
)

data class UsageItem(
    val used: Int,
    val limit: Int
) {
    val isUnlimited: Boolean get() = limit == Int.MAX_VALUE
    val percentage: Float get() = if (isUnlimited) 0f else used.toFloat() / limit
    val isAtLimit: Boolean get() = !isUnlimited && used >= limit
    val isNearLimit: Boolean get() = !isUnlimited && percentage >= 0.8f
}

// Usage in ViewModel
@HiltViewModel
class ClientListViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val usageLimits: UsageLimitsManager
) : ViewModel() {
    
    fun addClient(client: Client) {
        viewModelScope.launch {
            when (val result = usageLimits.canAddClient()) {
                is LimitCheckResult.Allowed -> {
                    clientRepository.create(client)
                }
                is LimitCheckResult.Warning -> {
                    _showWarning.value = result.message
                    clientRepository.create(client)
                }
                is LimitCheckResult.Blocked -> {
                    _showUpgradePrompt.value = UpgradePrompt(
                        reason = result.reason,
                        message = result.upgradeMessage
                    )
                }
            }
        }
    }
}
```

---

## UI Specifications

### Usage in Settings
```
┌─────────────────────────────────────────┐
│ Usage                                   │
├─────────────────────────────────────────┤
│                                         │
│  SOLO FARRIER                           │
│                                         │
│  Clients                                │
│  ██████████████████████████░░  87       │
│  Unlimited ✓                           │
│                                         │
│  Horses                                 │
│  ██████████████████████████░░  142      │
│  Unlimited ✓                           │
│                                         │
│  Route Stops                            │
│  Up to 8 per day                       │
│                                         │
│  SMS This Month                         │
│  ████████████████░░░░░░░░░░░░  38/50   │
│  Resets Feb 1                          │
│                                         │
│  Need more? [Upgrade Plan]             │
│                                         │
└─────────────────────────────────────────┘
```

### Limit Reached Dialog
```
┌─────────────────────────────────────────┐
│                                         │
│            ⚠️ Limit Reached             │
│                                         │
│  You've reached your client limit       │
│  (10 clients on Free plan)              │
│                                         │
│  Upgrade to Solo Farrier for unlimited  │
│  clients plus route optimization.       │
│                                         │
│  [Upgrade Now]        [Maybe Later]     │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class UsageLimitsManagerTest {
    @Test
    fun `canAddClient blocks at limit`() = runTest {
        every { userDao.getTier() } returns SubscriptionTier.FREE
        every { clientDao.getActiveCount() } returns 10
        
        val result = limitsManager.canAddClient()
        
        assertTrue(result is LimitCheckResult.Blocked)
    }
    
    @Test
    fun `canAddClient warns at 80 percent`() = runTest {
        every { userDao.getTier() } returns SubscriptionTier.FREE
        every { clientDao.getActiveCount() } returns 8
        
        val result = limitsManager.canAddClient()
        
        assertTrue(result is LimitCheckResult.Warning)
    }
    
    @Test
    fun `paid tier has unlimited clients`() = runTest {
        every { userDao.getTier() } returns SubscriptionTier.SOLO
        every { clientDao.getActiveCount() } returns 500
        
        val result = limitsManager.canAddClient()
        
        assertEquals(LimitCheckResult.Allowed, result)
    }
}
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-017-01 | Free tier blocked at 10 clients | Unit test |
| AC-017-02 | Warning shown at 80% | UI test |
| AC-017-03 | Upgrade prompt on block | UI test |
| AC-017-04 | SMS limit enforced | Integration test |
| AC-017-05 | Route stops limited correctly | Unit test |
| AC-017-06 | Limits work offline | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-016 (Subscriptions) | Internal | Required |
