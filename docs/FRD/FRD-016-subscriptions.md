# FRD-016: Subscription Management

**Version:** 1.0  
**Last Updated:** January 2026  
**PRD Reference:** PRD-016-subscriptions.md  
**Priority:** P0  
**Phase:** 5 - Monetization

---

## 1. Overview

### 1.1 Purpose

Implement Stripe-based subscription billing with tier management, enabling the freemium-to-paid conversion funnel. Users can view their current plan, upgrade via a web-based checkout flow, and manage billing through Stripe's Customer Portal.

### 1.2 Scope

This FRD covers:
- Subscription tier definitions and limits
- In-app subscription status display
- Upgrade flow via web checkout
- Stripe Customer Portal integration
- Webhook handling for subscription lifecycle events
- Grace period handling for failed payments
- Subscription status synchronization

### 1.3 Dependencies

| Dependency | Type | FRD Reference |
|------------|------|---------------|
| Authentication | Internal | FRD-001 |
| Marketing Website | Internal | FRD-015 |
| Usage Limits | Internal | FRD-017 |
| Stripe Account | External | - |
| Supabase Edge Functions | External | - |

---

## 2. Subscription Tiers

### 2.1 Tier Definitions

| Tier | Stripe Product ID | Monthly Price | Annual Price | Annual Savings |
|------|-------------------|---------------|--------------|----------------|
| Free | - | $0 | $0 | - |
| Solo Farrier | prod_solo | $29 | $278 | $70 (20%) |
| Growing Practice | prod_growing | $79 | $758 | $190 (20%) |
| Multi-Farrier | prod_multi | $149 | $1,430 | $358 (20%) |

### 2.2 Stripe Price IDs

| Tier | Monthly Price ID | Annual Price ID |
|------|------------------|-----------------|
| Solo Farrier | price_solo_monthly | price_solo_annual |
| Growing Practice | price_growing_monthly | price_growing_annual |
| Multi-Farrier | price_multi_monthly | price_multi_annual |

### 2.3 Data Model

```kotlin
// Location: core/domain/model/SubscriptionTier.kt
enum class SubscriptionTier {
    FREE,
    SOLO,
    GROWING,
    MULTI;
    
    companion object {
        fun fromString(value: String?): SubscriptionTier =
            values().find { it.name.equals(value, ignoreCase = true) } ?: FREE
    }
    
    val displayName: String
        get() = when (this) {
            FREE -> "Free"
            SOLO -> "Solo Farrier"
            GROWING -> "Growing Practice"
            MULTI -> "Multi-Farrier"
        }
    
    val monthlyPrice: Int
        get() = when (this) {
            FREE -> 0
            SOLO -> 29
            GROWING -> 79
            MULTI -> 149
        }
    
    val annualPrice: Int
        get() = when (this) {
            FREE -> 0
            SOLO -> 278
            GROWING -> 758
            MULTI -> 1430
        }
}

// Location: core/domain/model/SubscriptionStatus.kt
enum class SubscriptionStatus {
    ACTIVE,       // Payment current, full access
    TRIALING,     // In trial period (future feature)
    PAST_DUE,     // Payment failed, in grace period
    CANCELED,     // Subscription ended
    INCOMPLETE;   // Initial payment pending
    
    companion object {
        fun fromString(value: String?): SubscriptionStatus =
            values().find { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

// Location: core/domain/model/BillingPeriod.kt
enum class BillingPeriod {
    MONTHLY,
    ANNUAL;
    
    companion object {
        fun fromString(value: String?): BillingPeriod =
            values().find { it.name.equals(value, ignoreCase = true) } ?: MONTHLY
    }
}

// Location: core/domain/model/SubscriptionDetails.kt
data class SubscriptionDetails(
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val billingPeriod: BillingPeriod?,
    val currentPeriodEnd: Instant?,
    val stripeCustomerId: String?,
    val stripeSubscriptionId: String?,
    val cancelAtPeriodEnd: Boolean = false
) {
    val isActive: Boolean
        get() = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING
    
    val isPaid: Boolean
        get() = tier != SubscriptionTier.FREE
    
    val isInGracePeriod: Boolean
        get() = status == SubscriptionStatus.PAST_DUE
    
    val daysUntilRenewal: Int?
        get() = currentPeriodEnd?.let {
            ChronoUnit.DAYS.between(Instant.now(), it).toInt().coerceAtLeast(0)
        }
    
    val willCancel: Boolean
        get() = cancelAtPeriodEnd
    
    val formattedRenewalDate: String?
        get() = currentPeriodEnd?.let {
            LocalDate.ofInstant(it, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }
}
```

---

## 3. Subscription Screen

### 3.1 Route and Navigation

**Route:** `/settings/subscription`

Access via Settings → Subscription menu item.

### 3.2 Screen Layout

```
┌─────────────────────────────────────────┐
│ [←] Subscription                        │
├─────────────────────────────────────────┤
│                                         │
│  Current Plan                           │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐    │
│  │ ⭐ SOLO FARRIER                 │    │
│  │    $29/month                    │    │
│  │                                 │    │
│  │    Renews: Feb 13, 2026         │    │
│  │    Status: Active ●             │    │
│  └─────────────────────────────────┘    │
│                                         │
│  Your Limits                            │
│  ─────────────────────────────────────  │
│  Clients         Unlimited ✓           │
│  Horses          Unlimited ✓           │
│  Route Stops     8 per day             │
│  SMS             38/50 this month      │
│  ████████████████░░░░░ 76%             │
│  Resets Feb 1                          │
│  Team Members    1                     │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │        [Upgrade Plan]           │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │       [Manage Billing]          │    │
│  │  Update payment, view invoices  │    │
│  └─────────────────────────────────┘    │
│                                         │
└─────────────────────────────────────────┘
```

### 3.3 Current Plan Card

Display varies by subscription status:

**Active Paid Subscription:**
```
┌─────────────────────────────────────┐
│ ⭐ SOLO FARRIER                     │
│    $29/month (Monthly)              │
│                                     │
│    Renews: Feb 13, 2026             │
│    Status: Active ●                 │
└─────────────────────────────────────┘
```

**Free Tier:**
```
┌─────────────────────────────────────┐
│ FREE PLAN                           │
│                                     │
│ Upgrade for route optimization,     │
│ unlimited clients, and SMS reminders│
│                                     │
│ [See Plans →]                       │
└─────────────────────────────────────┘
```

**Past Due (Grace Period):**
```
┌─────────────────────────────────────┐
│ ⚠️ PAYMENT ISSUE                    │
│                                     │
│ Your payment failed. Please update  │
│ your payment method to keep access. │
│                                     │
│ Grace period ends: Jan 20, 2026     │
│                                     │
│ [Update Payment →]                  │
└─────────────────────────────────────┘
```

**Canceling at Period End:**
```
┌─────────────────────────────────────┐
│ ⭐ SOLO FARRIER                     │
│    Cancels Feb 13, 2026             │
│                                     │
│ Your subscription will end and      │
│ downgrade to Free on this date.     │
│                                     │
│ [Reactivate Subscription]           │
└─────────────────────────────────────┘
```

### 3.4 Limits Display

Show current tier limits with usage where applicable:

```kotlin
// Location: feature/settings/ui/components/LimitsSection.kt
@Composable
fun LimitsSection(
    subscriptionDetails: SubscriptionDetails,
    usage: UsageSummary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Your Limits", style = MaterialTheme.typography.titleMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Clients
        LimitRow(
            label = "Clients",
            value = if (usage.clients.isUnlimited) "Unlimited ✓" 
                    else "${usage.clients.used}/${usage.clients.limit}"
        )
        
        // Horses  
        LimitRow(
            label = "Horses",
            value = if (usage.horses.isUnlimited) "Unlimited ✓"
                    else "${usage.horses.used}/${usage.horses.limit}"
        )
        
        // Route stops
        LimitRow(
            label = "Route Stops",
            value = when {
                usage.routeStops.limit == 0 -> "Not available"
                usage.routeStops.isUnlimited -> "Unlimited ✓"
                else -> "${usage.routeStops.limit} per day"
            }
        )
        
        // SMS with progress bar
        if (usage.smsThisMonth.limit > 0) {
            LimitRowWithProgress(
                label = "SMS This Month",
                used = usage.smsThisMonth.used,
                limit = usage.smsThisMonth.limit,
                resetDate = usage.smsResetDate
            )
        } else {
            LimitRow(
                label = "SMS Reminders",
                value = "Not available"
            )
        }
        
        // Team members
        LimitRow(
            label = "Team Members",
            value = "${usage.users.limit}"
        )
    }
}
```

### 3.5 ViewModel

```kotlin
// Location: feature/settings/ui/SubscriptionViewModel.kt
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val usageLimitsManager: UsageLimitsManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()
    
    init {
        loadSubscriptionDetails()
        loadUsageSummary()
    }
    
    private fun loadSubscriptionDetails() {
        viewModelScope.launch {
            subscriptionRepository.getSubscriptionDetails()
                .collect { details ->
                    _uiState.update { it.copy(subscriptionDetails = details) }
                }
        }
    }
    
    private fun loadUsageSummary() {
        viewModelScope.launch {
            usageLimitsManager.getUsageSummary()
                .collect { usage ->
                    _uiState.update { it.copy(usageSummary = usage) }
                }
        }
    }
    
    fun openUpgrade(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val token = subscriptionRepository.generateUpgradeToken()
                val url = subscriptionRepository.getUpgradeUrl(token)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unable to open upgrade page") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun openBillingPortal(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val url = subscriptionRepository.getCustomerPortalUrl()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unable to open billing portal") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun refreshSubscription() {
        viewModelScope.launch {
            subscriptionRepository.syncSubscriptionStatus()
        }
    }
}

data class SubscriptionUiState(
    val subscriptionDetails: SubscriptionDetails? = null,
    val usageSummary: UsageSummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

## 4. Upgrade Flow

### 4.1 Flow Overview

```
┌──────────────┐     ┌───────────────┐     ┌─────────────────┐
│   Android    │     │    Website    │     │     Stripe      │
│     App      │     │ (hoofdirect)  │     │    Checkout     │
└──────┬───────┘     └───────┬───────┘     └────────┬────────┘
       │                     │                      │
       │ 1. Generate token   │                      │
       │─────────────────────►                      │
       │                     │                      │
       │ 2. Open browser     │                      │
       │────────────────────────────────────────────►
       │                     │                      │
       │                     │ 3. Validate token    │
       │                     │◄─────────────────────┤
       │                     │                      │
       │                     │ 4. Create checkout   │
       │                     │──────────────────────►
       │                     │                      │
       │                     │ 5. Redirect          │
       │                     │◄─────────────────────┤
       │                     │                      │
       │                     │ 6. User pays         │
       │                     │──────────────────────►
       │                     │                      │
       │                     │ 7. Webhook           │
       │                     │◄─────────────────────┤
       │                     │                      │
       │ 8. App syncs        │                      │
       │◄────────────────────┤                      │
       │                     │                      │
```

### 4.2 Upgrade Token Generation

```kotlin
// Location: data/repository/SubscriptionRepositoryImpl.kt
class SubscriptionRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) : SubscriptionRepository {
    
    override suspend fun generateUpgradeToken(): String {
        val token = UUID.randomUUID().toString()
        val userId = getCurrentUserId()
        
        // Store token in Supabase with 15-minute expiry
        supabaseClient.from("upgrade_tokens").insert(
            mapOf(
                "token" to token,
                "user_id" to userId,
                "expires_at" to Instant.now().plusSeconds(900).toString()
            )
        )
        
        return token
    }
    
    override fun getUpgradeUrl(token: String): String {
        val baseUrl = BuildConfig.WEB_BASE_URL
        return "$baseUrl/upgrade?token=$token"
    }
    
    override fun getCustomerPortalUrl(): String {
        val baseUrl = BuildConfig.WEB_BASE_URL
        return "$baseUrl/billing/portal"
    }
}
```

### 4.3 Web Upgrade Page

```typescript
// Website: app/upgrade/page.tsx
'use client';

import { useSearchParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { createCheckoutSession } from '@/lib/stripe';

export default function UpgradePage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const token = searchParams.get('token');
  
  const [userId, setUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedTier, setSelectedTier] = useState<string | null>(null);
  const [billingPeriod, setBillingPeriod] = useState<'monthly' | 'annual'>('monthly');
  
  useEffect(() => {
    if (!token) {
      setError('Invalid upgrade link');
      return;
    }
    
    // Validate token
    validateToken(token).then(result => {
      if (result.valid) {
        setUserId(result.userId);
      } else {
        setError('Upgrade link expired. Please try again from the app.');
      }
    });
  }, [token]);
  
  const handleUpgrade = async (tier: string) => {
    if (!userId) return;
    
    const priceId = billingPeriod === 'monthly'
      ? PRICE_IDS[tier].monthly
      : PRICE_IDS[tier].annual;
    
    const { url } = await createCheckoutSession({
      userId,
      priceId,
      tier,
      successUrl: `${window.location.origin}/upgrade/success`,
      cancelUrl: `${window.location.origin}/upgrade?token=${token}`
    });
    
    window.location.href = url;
  };
  
  if (error) {
    return <ErrorState message={error} />;
  }
  
  return (
    <div className="min-h-screen bg-gray-50 py-12">
      <div className="max-w-4xl mx-auto px-4">
        <h1 className="text-3xl font-bold text-center">Choose Your Plan</h1>
        
        <BillingToggle
          value={billingPeriod}
          onChange={setBillingPeriod}
        />
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-8">
          <PricingCard
            tier="SOLO"
            price={billingPeriod === 'monthly' ? 29 : 278}
            period={billingPeriod}
            onSelect={() => handleUpgrade('SOLO')}
          />
          <PricingCard
            tier="GROWING"
            price={billingPeriod === 'monthly' ? 79 : 758}
            period={billingPeriod}
            highlighted
            onSelect={() => handleUpgrade('GROWING')}
          />
          <PricingCard
            tier="MULTI"
            price={billingPeriod === 'monthly' ? 149 : 1430}
            period={billingPeriod}
            onSelect={() => handleUpgrade('MULTI')}
          />
        </div>
      </div>
    </div>
  );
}
```

### 4.4 Stripe Checkout Session

```typescript
// Website: lib/stripe.ts
import Stripe from 'stripe';

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY!);

export async function createCheckoutSession({
  userId,
  priceId,
  tier,
  successUrl,
  cancelUrl
}: {
  userId: string;
  priceId: string;
  tier: string;
  successUrl: string;
  cancelUrl: string;
}) {
  // Get or create Stripe customer
  const { data: user } = await supabase
    .from('users')
    .select('stripe_customer_id, email')
    .eq('id', userId)
    .single();
  
  let customerId = user.stripe_customer_id;
  
  if (!customerId) {
    const customer = await stripe.customers.create({
      email: user.email,
      metadata: { user_id: userId }
    });
    customerId = customer.id;
    
    await supabase
      .from('users')
      .update({ stripe_customer_id: customerId })
      .eq('id', userId);
  }
  
  // Create checkout session
  const session = await stripe.checkout.sessions.create({
    customer: customerId,
    mode: 'subscription',
    payment_method_types: ['card'],
    line_items: [{ price: priceId, quantity: 1 }],
    success_url: successUrl,
    cancel_url: cancelUrl,
    metadata: {
      user_id: userId,
      tier: tier
    },
    subscription_data: {
      metadata: {
        user_id: userId,
        tier: tier
      }
    }
  });
  
  return { url: session.url };
}
```

---

## 5. Stripe Customer Portal

### 5.1 Portal Configuration

Configure in Stripe Dashboard → Settings → Customer Portal:

| Setting | Value |
|---------|-------|
| Allow subscription cancellation | Yes, immediately |
| Allow plan changes | Yes |
| Allow quantity changes | No |
| Show invoice history | Yes |
| Update payment method | Yes |
| Proration behavior | Always invoice immediately |

### 5.2 Portal Session Creation

```typescript
// Website: app/api/billing/portal/route.ts
import { NextRequest, NextResponse } from 'next/server';
import Stripe from 'stripe';

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY!);

export async function POST(request: NextRequest) {
  const { userId } = await request.json();
  
  // Get user's Stripe customer ID
  const { data: user } = await supabase
    .from('users')
    .select('stripe_customer_id')
    .eq('id', userId)
    .single();
  
  if (!user?.stripe_customer_id) {
    return NextResponse.json(
      { error: 'No billing account found' },
      { status: 400 }
    );
  }
  
  const session = await stripe.billingPortal.sessions.create({
    customer: user.stripe_customer_id,
    return_url: `${process.env.NEXT_PUBLIC_APP_URL}/billing/return`
  });
  
  return NextResponse.json({ url: session.url });
}
```

---

## 6. Webhook Handling

### 6.1 Webhook Events

| Event | Action |
|-------|--------|
| `checkout.session.completed` | Set tier to purchased plan, status to ACTIVE |
| `customer.subscription.updated` | Update tier, period end, billing period |
| `customer.subscription.deleted` | Set tier to FREE, status to CANCELED |
| `invoice.payment_succeeded` | Confirm status ACTIVE, update period end |
| `invoice.payment_failed` | Set status to PAST_DUE, start grace period |

### 6.2 Edge Function Implementation

```typescript
// Supabase Edge Function: supabase/functions/handle-stripe-webhook/index.ts
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import Stripe from 'https://esm.sh/stripe@13.0.0';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

const stripe = new Stripe(Deno.env.get('STRIPE_SECRET_KEY')!, {
  apiVersion: '2023-10-16',
});

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
);

serve(async (req: Request) => {
  const signature = req.headers.get('stripe-signature')!;
  const body = await req.text();
  
  let event: Stripe.Event;
  
  try {
    event = stripe.webhooks.constructEvent(
      body,
      signature,
      Deno.env.get('STRIPE_WEBHOOK_SECRET')!
    );
  } catch (err) {
    console.error('Webhook signature verification failed:', err);
    return new Response(JSON.stringify({ error: 'Invalid signature' }), {
      status: 400
    });
  }
  
  console.log('Processing event:', event.type);
  
  switch (event.type) {
    case 'checkout.session.completed': {
      const session = event.data.object as Stripe.Checkout.Session;
      await handleCheckoutCompleted(session);
      break;
    }
    
    case 'customer.subscription.updated': {
      const subscription = event.data.object as Stripe.Subscription;
      await handleSubscriptionUpdated(subscription);
      break;
    }
    
    case 'customer.subscription.deleted': {
      const subscription = event.data.object as Stripe.Subscription;
      await handleSubscriptionDeleted(subscription);
      break;
    }
    
    case 'invoice.payment_succeeded': {
      const invoice = event.data.object as Stripe.Invoice;
      await handlePaymentSucceeded(invoice);
      break;
    }
    
    case 'invoice.payment_failed': {
      const invoice = event.data.object as Stripe.Invoice;
      await handlePaymentFailed(invoice);
      break;
    }
  }
  
  return new Response(JSON.stringify({ received: true }), { status: 200 });
});

async function handleCheckoutCompleted(session: Stripe.Checkout.Session) {
  const userId = session.metadata?.user_id;
  const tier = session.metadata?.tier;
  
  if (!userId || !tier) {
    console.error('Missing metadata in checkout session');
    return;
  }
  
  const subscription = await stripe.subscriptions.retrieve(
    session.subscription as string
  );
  
  const { error } = await supabase
    .from('users')
    .update({
      subscription_tier: tier.toLowerCase(),
      subscription_status: 'active',
      billing_period: subscription.items.data[0].price.recurring?.interval === 'year' 
        ? 'annual' 
        : 'monthly',
      stripe_customer_id: session.customer as string,
      stripe_subscription_id: session.subscription as string,
      current_period_end: new Date(subscription.current_period_end * 1000).toISOString(),
      cancel_at_period_end: false,
      updated_at: new Date().toISOString()
    })
    .eq('id', userId);
  
  if (error) {
    console.error('Failed to update user:', error);
  }
}

async function handleSubscriptionUpdated(subscription: Stripe.Subscription) {
  const userId = subscription.metadata?.user_id;
  
  if (!userId) {
    // Try to find user by customer ID
    const { data: user } = await supabase
      .from('users')
      .select('id')
      .eq('stripe_customer_id', subscription.customer)
      .single();
    
    if (!user) {
      console.error('Could not find user for subscription');
      return;
    }
  }
  
  const tier = subscription.metadata?.tier || 'free';
  
  const { error } = await supabase
    .from('users')
    .update({
      subscription_tier: subscription.status === 'active' ? tier.toLowerCase() : 'free',
      subscription_status: subscription.status,
      current_period_end: new Date(subscription.current_period_end * 1000).toISOString(),
      cancel_at_period_end: subscription.cancel_at_period_end,
      updated_at: new Date().toISOString()
    })
    .eq('stripe_subscription_id', subscription.id);
  
  if (error) {
    console.error('Failed to update subscription:', error);
  }
}

async function handleSubscriptionDeleted(subscription: Stripe.Subscription) {
  const { error } = await supabase
    .from('users')
    .update({
      subscription_tier: 'free',
      subscription_status: 'canceled',
      current_period_end: null,
      cancel_at_period_end: false,
      updated_at: new Date().toISOString()
    })
    .eq('stripe_subscription_id', subscription.id);
  
  if (error) {
    console.error('Failed to cancel subscription:', error);
  }
}

async function handlePaymentSucceeded(invoice: Stripe.Invoice) {
  if (!invoice.subscription) return;
  
  const subscription = await stripe.subscriptions.retrieve(
    invoice.subscription as string
  );
  
  const { error } = await supabase
    .from('users')
    .update({
      subscription_status: 'active',
      current_period_end: new Date(subscription.current_period_end * 1000).toISOString(),
      updated_at: new Date().toISOString()
    })
    .eq('stripe_subscription_id', subscription.id);
  
  if (error) {
    console.error('Failed to update payment status:', error);
  }
}

async function handlePaymentFailed(invoice: Stripe.Invoice) {
  if (!invoice.subscription) return;
  
  const { data: user } = await supabase
    .from('users')
    .select('id, email')
    .eq('stripe_subscription_id', invoice.subscription)
    .single();
  
  if (!user) return;
  
  // Update status to past_due
  await supabase
    .from('users')
    .update({
      subscription_status: 'past_due',
      updated_at: new Date().toISOString()
    })
    .eq('id', user.id);
  
  // Record grace period start
  await supabase
    .from('payment_failures')
    .insert({
      user_id: user.id,
      invoice_id: invoice.id,
      failed_at: new Date().toISOString(),
      grace_period_ends: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
    });
  
  // Send notification (handled by separate function)
  await sendPaymentFailedNotification(user.email, user.id);
}
```

---

## 7. Grace Period Handling

### 7.1 Grace Period Rules

- **Duration:** 7 days from first failed payment
- **Access:** Full feature access maintained during grace period
- **Notifications:** Email sent on day 1, 4, and 7
- **Outcome:** If not resolved by day 7, downgrade to Free tier

### 7.2 Notification Schedule

| Day | Action | Template |
|-----|--------|----------|
| 1 | Email notification | "Payment failed - please update your payment method" |
| 4 | Email reminder | "Your account will be downgraded in 3 days" |
| 7 | Final warning | "Last chance to update payment" |
| 8 | Downgrade | Status → CANCELED, Tier → FREE |

### 7.3 Grace Period Worker

```typescript
// Supabase Edge Function: check-grace-periods
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!,
  Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
);

serve(async () => {
  const now = new Date();
  
  // Find expired grace periods
  const { data: expiredUsers } = await supabase
    .from('payment_failures')
    .select('user_id')
    .lt('grace_period_ends', now.toISOString())
    .eq('resolved', false);
  
  if (!expiredUsers?.length) {
    return new Response(JSON.stringify({ processed: 0 }));
  }
  
  // Downgrade expired users
  for (const { user_id } of expiredUsers) {
    await supabase
      .from('users')
      .update({
        subscription_tier: 'free',
        subscription_status: 'canceled',
        current_period_end: null,
        updated_at: now.toISOString()
      })
      .eq('id', user_id);
    
    await supabase
      .from('payment_failures')
      .update({ resolved: true, resolved_reason: 'downgraded' })
      .eq('user_id', user_id);
    
    // Send downgrade notification
    await sendDowngradeNotification(user_id);
  }
  
  return new Response(JSON.stringify({ processed: expiredUsers.length }));
});
```

---

## 8. Subscription Sync

### 8.1 Sync Triggers

Subscription data syncs in these scenarios:
1. App launch (foreground)
2. After returning from upgrade/billing portal
3. Manual refresh (pull-to-refresh)
4. Background sync (every 24 hours)

### 8.2 Repository Implementation

```kotlin
// Location: data/repository/SubscriptionRepositoryImpl.kt
class SubscriptionRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val supabaseClient: SupabaseClient
) : SubscriptionRepository {
    
    override fun getSubscriptionDetails(): Flow<SubscriptionDetails> =
        userDao.getCurrentUser().map { user ->
            SubscriptionDetails(
                tier = SubscriptionTier.fromString(user.subscriptionTier),
                status = SubscriptionStatus.fromString(user.subscriptionStatus),
                billingPeriod = BillingPeriod.fromString(user.billingPeriod),
                currentPeriodEnd = user.currentPeriodEnd?.let { Instant.parse(it) },
                stripeCustomerId = user.stripeCustomerId,
                stripeSubscriptionId = user.stripeSubscriptionId,
                cancelAtPeriodEnd = user.cancelAtPeriodEnd ?: false
            )
        }
    
    override fun getCurrentTier(): Flow<SubscriptionTier> =
        userDao.getCurrentUser().map { user ->
            SubscriptionTier.fromString(user.subscriptionTier)
        }
    
    override suspend fun syncSubscriptionStatus() {
        val userId = getCurrentUserId()
        
        // Fetch latest from server
        val response = supabaseClient.from("users")
            .select(columns = Columns.list(
                "subscription_tier",
                "subscription_status",
                "billing_period",
                "current_period_end",
                "cancel_at_period_end"
            ))
            .eq("id", userId)
            .decodeSingle<UserSubscriptionResponse>()
        
        // Update local database
        userDao.updateSubscription(
            userId = userId,
            tier = response.subscriptionTier,
            status = response.subscriptionStatus,
            billingPeriod = response.billingPeriod,
            periodEnd = response.currentPeriodEnd,
            cancelAtPeriodEnd = response.cancelAtPeriodEnd
        )
    }
}
```

### 8.3 User Entity Fields

```kotlin
// Location: core/database/entity/UserEntity.kt
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    // ... other fields
    
    // Subscription fields
    @ColumnInfo(name = "subscription_tier")
    val subscriptionTier: String = "free",
    
    @ColumnInfo(name = "subscription_status")
    val subscriptionStatus: String = "active",
    
    @ColumnInfo(name = "billing_period")
    val billingPeriod: String? = null,
    
    @ColumnInfo(name = "current_period_end")
    val currentPeriodEnd: String? = null,
    
    @ColumnInfo(name = "stripe_customer_id")
    val stripeCustomerId: String? = null,
    
    @ColumnInfo(name = "stripe_subscription_id")
    val stripeSubscriptionId: String? = null,
    
    @ColumnInfo(name = "cancel_at_period_end")
    val cancelAtPeriodEnd: Boolean? = null
)
```

---

## 9. Acceptance Criteria

### 9.1 Subscription Display

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-01 | Free tier user sees "FREE PLAN" card with upgrade prompt | UI test |
| AC-016-02 | Solo user sees "$29/month" with renewal date Feb 13, 2026 | UI test |
| AC-016-03 | Annual subscriber sees "$278/year" with correct renewal | UI test |
| AC-016-04 | Past due user sees warning banner with grace period end date | UI test |
| AC-016-05 | Canceling user sees "Cancels [date]" with reactivate option | UI test |

### 9.2 Limits Display

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-06 | Free tier shows "10" for clients limit | UI test |
| AC-016-07 | Paid tier shows "Unlimited ✓" for clients | UI test |
| AC-016-08 | SMS usage shows "38/50" with 76% progress bar | UI test |
| AC-016-09 | Route stops shows "8 per day" for Solo tier | UI test |

### 9.3 Upgrade Flow

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-10 | Tap "Upgrade" → generates token and opens browser | E2E test |
| AC-016-11 | Token expires after 15 minutes → shows error on web | Integration test |
| AC-016-12 | Select Solo $29/month → redirects to Stripe checkout | Manual test |
| AC-016-13 | Complete payment → status updates within 30 seconds | Integration test |
| AC-016-14 | Cancel at checkout → returns to app with no changes | Manual test |

### 9.4 Billing Portal

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-15 | Tap "Manage Billing" → opens Stripe Customer Portal | Manual test |
| AC-016-16 | Update payment method → saved successfully | Manual test |
| AC-016-17 | Cancel subscription → status shows "Cancels [date]" | Integration test |
| AC-016-18 | View invoice history → shows past invoices | Manual test |

### 9.5 Webhook Processing

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-19 | checkout.session.completed → tier updates to SOLO | Integration test |
| AC-016-20 | invoice.payment_failed → status changes to PAST_DUE | Integration test |
| AC-016-21 | customer.subscription.deleted → tier resets to FREE | Integration test |
| AC-016-22 | Payment after past_due → status returns to ACTIVE | Integration test |

### 9.6 Grace Period

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-23 | Payment fails → email sent within 1 hour | Integration test |
| AC-016-24 | Day 4 of grace period → reminder email sent | Integration test |
| AC-016-25 | Day 8 without resolution → tier downgrades to FREE | Integration test |
| AC-016-26 | Payment fixed on day 5 → status returns to ACTIVE | Integration test |

### 9.7 Sync

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-016-27 | App foreground → subscription syncs within 5 seconds | Integration test |
| AC-016-28 | Return from billing portal → immediate refresh | Manual test |
| AC-016-29 | Offline → shows cached subscription status | Integration test |

---

## 10. Security Considerations

### 10.1 Webhook Security

- Verify Stripe signature on all webhooks
- Use `STRIPE_WEBHOOK_SECRET` from environment
- Reject requests without valid signature
- Log all webhook events for audit

### 10.2 Token Security

- Upgrade tokens expire in 15 minutes
- One-time use (invalidated after checkout)
- Stored in database with user association
- Cannot be reused or guessed (UUID v4)

### 10.3 API Key Management

- Stripe Secret Key only in server-side code (Edge Functions)
- Stripe Publishable Key can be in web frontend
- Never expose secret key in mobile app
- Service role key only in Edge Functions

---

## 11. Analytics Events

| Event | Trigger | Properties |
|-------|---------|------------|
| `subscription_screen_viewed` | Screen opened | current_tier |
| `upgrade_started` | Upgrade button tapped | current_tier |
| `upgrade_completed` | Webhook: checkout.completed | new_tier, billing_period, amount |
| `upgrade_abandoned` | Checkout cancelled | current_tier, selected_tier |
| `billing_portal_opened` | Manage Billing tapped | current_tier |
| `subscription_canceled` | Cancellation confirmed | tier, months_subscribed |
| `payment_failed` | Webhook: payment_failed | tier, failure_count |
| `grace_period_expired` | Auto-downgrade | tier, days_past_due |

---

## 12. File References

### 12.1 Android App

```
feature/settings/
├── ui/
│   ├── SubscriptionScreen.kt
│   ├── SubscriptionViewModel.kt
│   └── components/
│       ├── CurrentPlanCard.kt
│       ├── LimitsSection.kt
│       ├── LimitRow.kt
│       └── LimitRowWithProgress.kt

core/domain/
├── model/
│   ├── SubscriptionTier.kt
│   ├── SubscriptionStatus.kt
│   ├── BillingPeriod.kt
│   └── SubscriptionDetails.kt
├── repository/
│   └── SubscriptionRepository.kt

core/database/
├── entity/
│   └── UserEntity.kt (subscription fields)
├── dao/
│   └── UserDao.kt (subscription queries)

data/repository/
└── SubscriptionRepositoryImpl.kt
```

### 12.2 Website

```
hoofdirect-web/
├── app/
│   ├── upgrade/
│   │   ├── page.tsx
│   │   └── success/page.tsx
│   ├── billing/
│   │   └── portal/page.tsx
│   └── api/
│       ├── checkout/route.ts
│       └── billing/portal/route.ts
├── lib/
│   └── stripe.ts
```

### 12.3 Supabase Edge Functions

```
supabase/functions/
├── handle-stripe-webhook/
│   └── index.ts
├── check-grace-periods/
│   └── index.ts
└── send-payment-notifications/
    └── index.ts
```

---

## 13. Error Handling

### 13.1 Upgrade Errors

| Error | User Message | Action |
|-------|--------------|--------|
| Token generation failed | "Unable to start upgrade. Please try again." | Show retry button |
| Token expired | "This link has expired. Please try again from the app." | Redirect to app |
| Checkout failed | "Payment could not be processed." | Show Stripe error |
| Webhook timeout | (silent) | Auto-retry via Stripe |

### 13.2 Sync Errors

| Error | Handling |
|-------|----------|
| Network unavailable | Use cached subscription data |
| Server error | Retry with exponential backoff |
| Invalid response | Log error, continue with cached data |

---

## 14. Testing

### 14.1 Stripe Test Mode

Use Stripe test mode for all development and testing:

| Card Number | Scenario |
|-------------|----------|
| 4242424242424242 | Successful payment |
| 4000000000000341 | Card declined |
| 4000000000009995 | Insufficient funds |
| 4000002500003155 | Requires authentication |

### 14.2 Webhook Testing

Use Stripe CLI to forward webhooks locally:

```bash
stripe listen --forward-to localhost:54321/functions/v1/handle-stripe-webhook
```

Test specific events:

```bash
stripe trigger checkout.session.completed
stripe trigger invoice.payment_failed
stripe trigger customer.subscription.deleted
```
