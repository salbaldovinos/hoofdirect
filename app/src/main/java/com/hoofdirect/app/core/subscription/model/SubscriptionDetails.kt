package com.hoofdirect.app.core.subscription.model

import java.time.Instant

/**
 * Complete subscription details for a user.
 */
data class SubscriptionDetails(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val status: SubscriptionStatus = SubscriptionStatus.NONE,
    val billingPeriod: BillingPeriod = BillingPeriod.NONE,
    val stripeCustomerId: String? = null,
    val stripeSubscriptionId: String? = null,
    val currentPeriodStart: Instant? = null,
    val currentPeriodEnd: Instant? = null,
    val trialEndsAt: Instant? = null,
    val cancelAtPeriodEnd: Boolean = false
) {
    /**
     * Whether the subscription allows full access to features.
     */
    val hasActiveSubscription: Boolean
        get() = status.isActive && tier != SubscriptionTier.FREE

    /**
     * Whether the user is currently in a trial period.
     */
    val isTrialing: Boolean
        get() = status == SubscriptionStatus.TRIALING && trialEndsAt != null && trialEndsAt.isAfter(Instant.now())

    /**
     * Days remaining in the current billing period.
     */
    val daysRemaining: Int
        get() {
            if (currentPeriodEnd == null) return 0
            val now = Instant.now()
            if (currentPeriodEnd.isBefore(now)) return 0
            return ((currentPeriodEnd.epochSecond - now.epochSecond) / 86400).toInt()
        }

    /**
     * Days remaining in trial.
     */
    val trialDaysRemaining: Int
        get() {
            if (trialEndsAt == null) return 0
            val now = Instant.now()
            if (trialEndsAt.isBefore(now)) return 0
            return ((trialEndsAt.epochSecond - now.epochSecond) / 86400).toInt()
        }

    /**
     * Get the current price based on tier and billing period.
     */
    val currentPrice: Int
        get() = when (billingPeriod) {
            BillingPeriod.MONTHLY -> tier.monthlyPrice
            BillingPeriod.YEARLY -> tier.yearlyPrice
            BillingPeriod.NONE -> 0
        }

    companion object {
        val FREE = SubscriptionDetails(
            tier = SubscriptionTier.FREE,
            status = SubscriptionStatus.NONE,
            billingPeriod = BillingPeriod.NONE
        )
    }
}
