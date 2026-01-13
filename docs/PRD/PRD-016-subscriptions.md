# PRD-016: Subscription Management

**Priority**: P0  
**Phase**: 5 - Monetization  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Implement Stripe-based subscription billing with tier management, enabling the freemium-to-paid conversion funnel.

### Business Value
- Revenue generation
- Clear upgrade path
- Self-service subscription management
- Automated billing and renewals

### Success Metrics
| Metric | Target |
|--------|--------|
| Free-to-paid conversion | > 10% |
| Upgrade completion rate | > 80% |
| Churn rate | < 4%/month |
| Payment success rate | > 98% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-016-01 | Farrier | See my current tier | I know my limits | P0 |
| US-016-02 | Farrier | Upgrade my subscription | I get more features | P0 |
| US-016-03 | Farrier | Manage billing | I update payment method | P0 |
| US-016-04 | Farrier | Cancel subscription | I stop being charged | P0 |
| US-016-05 | Farrier | See billing history | I have records | P1 |

---

## Subscription Tiers

| Tier | Monthly | Annual | Stripe Price ID |
|------|---------|--------|-----------------|
| Free | $0 | $0 | - |
| Solo Farrier | $29 | $278 (20% off) | price_solo_monthly, price_solo_annual |
| Growing Practice | $79 | $758 | price_growing_monthly, price_growing_annual |
| Multi-Farrier | $149 | $1,430 | price_multi_monthly, price_multi_annual |

---

## Functional Requirements

### FR-016-01: Subscription Status Display
- Current tier name
- Billing cycle (monthly/annual)
- Next billing date
- Features included
- Usage against limits

### FR-016-02: Upgrade Flow
1. User taps "Upgrade" in app
2. App generates one-time token
3. Opens browser to `hoofdirect.com/upgrade?token={token}`
4. Web validates token, shows tier selection
5. Stripe Checkout for payment
6. Webhook updates user tier in Supabase
7. App detects change on next sync

### FR-016-03: Stripe Customer Portal
- Update payment method
- View invoices
- Cancel subscription
- Switch plans
- Accessed via web link from app

### FR-016-04: Webhook Handling
```typescript
// Supabase Edge Function: handle-stripe-webhook
const webhookEvents = [
  'checkout.session.completed',    // New subscription
  'customer.subscription.updated', // Plan change
  'customer.subscription.deleted', // Cancellation
  'invoice.payment_succeeded',     // Renewal
  'invoice.payment_failed',        // Failed payment
];
```

### FR-016-05: Grace Period
- 7-day grace for failed payments
- Email notifications at day 1, 4, 7
- Downgrade to Free after grace period

---

## Technical Implementation

```kotlin
// SubscriptionRepository.kt
class SubscriptionRepository @Inject constructor(
    private val userDao: UserDao,
    private val supabaseClient: SupabaseClient
) {
    fun getCurrentTier(): Flow<SubscriptionTier> =
        userDao.getCurrentUser().map { user ->
            SubscriptionTier.fromString(user.subscriptionTier)
        }
    
    fun getSubscriptionDetails(): Flow<SubscriptionDetails> =
        userDao.getCurrentUser().map { user ->
            SubscriptionDetails(
                tier = SubscriptionTier.fromString(user.subscriptionTier),
                status = SubscriptionStatus.fromString(user.subscriptionStatus),
                currentPeriodEnd = user.currentPeriodEnd,
                stripeCustomerId = user.stripeCustomerId
            )
        }
    
    suspend fun generateUpgradeToken(): String {
        val token = UUID.randomUUID().toString()
        val userId = getCurrentUserId()
        
        // Store token in Supabase with 15-min expiry
        supabaseClient.from("upgrade_tokens").insert(
            UpgradeToken(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(900)
            )
        )
        return token
    }
    
    fun getUpgradeUrl(token: String): String =
        "${BuildConfig.WEB_BASE_URL}/upgrade?token=$token"
    
    fun getCustomerPortalUrl(): String =
        "${BuildConfig.WEB_BASE_URL}/billing/portal"
}

// SubscriptionViewModel.kt
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    
    val subscriptionDetails = subscriptionRepository.getSubscriptionDetails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    
    fun openUpgrade(context: Context) {
        viewModelScope.launch {
            val token = subscriptionRepository.generateUpgradeToken()
            val url = subscriptionRepository.getUpgradeUrl(token)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    
    fun openBillingPortal(context: Context) {
        val url = subscriptionRepository.getCustomerPortalUrl()
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

// Edge Function: handle-stripe-webhook
import Stripe from 'stripe';
import { createClient } from '@supabase/supabase-js';

const stripe = new Stripe(Deno.env.get('STRIPE_SECRET_KEY'));
const supabase = createClient(
  Deno.env.get('SUPABASE_URL'),
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
);

Deno.serve(async (req) => {
  const signature = req.headers.get('stripe-signature');
  const body = await req.text();
  
  const event = stripe.webhooks.constructEvent(
    body,
    signature,
    Deno.env.get('STRIPE_WEBHOOK_SECRET')
  );
  
  switch (event.type) {
    case 'checkout.session.completed': {
      const session = event.data.object;
      const userId = session.metadata.user_id;
      const tier = session.metadata.tier;
      
      await supabase.from('users').update({
        subscription_tier: tier,
        subscription_status: 'active',
        stripe_customer_id: session.customer,
        current_period_end: new Date(session.subscription.current_period_end * 1000)
      }).eq('id', userId);
      break;
    }
    
    case 'customer.subscription.deleted': {
      const subscription = event.data.object;
      const { data: user } = await supabase
        .from('users')
        .select('id')
        .eq('stripe_customer_id', subscription.customer)
        .single();
      
      await supabase.from('users').update({
        subscription_tier: 'free',
        subscription_status: 'canceled',
        current_period_end: null
      }).eq('id', user.id);
      break;
    }
    
    case 'invoice.payment_failed': {
      const invoice = event.data.object;
      const { data: user } = await supabase
        .from('users')
        .select('id, email')
        .eq('stripe_customer_id', invoice.customer)
        .single();
      
      await supabase.from('users').update({
        subscription_status: 'past_due'
      }).eq('id', user.id);
      
      // Send email notification
      await sendPaymentFailedEmail(user.email);
      break;
    }
  }
  
  return new Response(JSON.stringify({ received: true }), { status: 200 });
});
```

---

## Data Model

```kotlin
enum class SubscriptionTier {
    FREE, SOLO, GROWING, MULTI;
    
    companion object {
        fun fromString(value: String): SubscriptionTier =
            values().find { it.name.lowercase() == value.lowercase() } ?: FREE
    }
}

enum class SubscriptionStatus {
    ACTIVE, PAST_DUE, CANCELED, TRIALING
}

data class SubscriptionDetails(
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val currentPeriodEnd: Instant?,
    val stripeCustomerId: String?
) {
    val isActive: Boolean get() = status == SubscriptionStatus.ACTIVE
    val daysUntilRenewal: Int get() = currentPeriodEnd?.let {
        ChronoUnit.DAYS.between(Instant.now(), it).toInt()
    } ?: 0
}
```

---

## UI Specifications

### Subscription Screen
```
┌─────────────────────────────────────────┐
│ [←] Subscription                        │
├─────────────────────────────────────────┤
│                                         │
│  Current Plan                           │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ 🌟 SOLO FARRIER                 │   │
│  │    $29/month                    │   │
│  │                                 │   │
│  │    Renews: Feb 13, 2024         │   │
│  │    Status: Active               │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Your Limits                            │
│  ─────────────────────────────────────  │
│  Clients: Unlimited ✓                  │
│  Route Stops: 8/day                    │
│  SMS: 38/50 used this month            │
│  Users: 1                              │
│                                         │
│  [Upgrade Plan]                         │
│                                         │
│  [Manage Billing]                       │
│  Update payment method, view invoices   │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing Requirements

```kotlin
class SubscriptionRepositoryTest {
    @Test
    fun `generateUpgradeToken creates valid token`() = runTest {
        val token = repository.generateUpgradeToken()
        assertNotNull(token)
        assertEquals(36, token.length) // UUID format
    }
}

// Stripe webhook tests using Stripe CLI
// stripe trigger checkout.session.completed
```

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-01 | Upgrade flow completes | E2E test |
| AC-016-02 | Webhook updates tier | Integration test |
| AC-016-03 | Customer portal accessible | Manual test |
| AC-016-04 | Grace period works | Integration test |
| AC-016-05 | Tier reflects in app | Integration test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| PRD-015 (Website) | Internal | Required |
| Stripe Account | External | Required |
| Supabase Edge Functions | External | Required |

---

## Security Considerations

- Webhook signature verification required
- Upgrade tokens expire in 15 minutes
- Service role key only in Edge Functions
- No Stripe secret key in mobile app
