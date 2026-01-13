package com.hoofdirect.app.core.subscription

import com.hoofdirect.app.core.database.dao.UserDao
import com.hoofdirect.app.core.database.entity.UserEntity
import com.hoofdirect.app.core.subscription.model.BillingPeriod
import com.hoofdirect.app.core.subscription.model.SubscriptionDetails
import com.hoofdirect.app.core.subscription.model.SubscriptionStatus
import com.hoofdirect.app.core.subscription.model.SubscriptionTier
import com.hoofdirect.app.feature.auth.data.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing subscription data.
 * Handles local storage and sync with Supabase/Stripe.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) {
    /**
     * Get the current user's subscription details as a Flow.
     */
    fun observeSubscription(): Flow<SubscriptionDetails> {
        val userId = tokenManager.getUserId()
        return if (userId == null) {
            flowOf(SubscriptionDetails.FREE)
        } else {
            userDao.getById(userId).map { user ->
                user?.toSubscriptionDetails() ?: SubscriptionDetails.FREE
            }
        }
    }

    /**
     * Get the current user's subscription details.
     */
    suspend fun getSubscription(): SubscriptionDetails {
        val userId = tokenManager.getUserId() ?: return SubscriptionDetails.FREE
        return userDao.getUserByIdOnce(userId)?.toSubscriptionDetails() ?: SubscriptionDetails.FREE
    }

    /**
     * Update subscription details locally after a webhook notification.
     * This should be called when Supabase syncs subscription changes from Stripe.
     */
    suspend fun updateSubscription(
        tier: SubscriptionTier,
        status: SubscriptionStatus,
        billingPeriod: BillingPeriod,
        stripeCustomerId: String?,
        stripeSubscriptionId: String?,
        currentPeriodStart: Instant?,
        currentPeriodEnd: Instant?,
        trialEndsAt: Instant?,
        cancelAtPeriodEnd: Boolean
    ) {
        val userId = tokenManager.getUserId() ?: return
        val user = userDao.getUserByIdOnce(userId) ?: return

        val updatedUser = user.copy(
            subscriptionTier = tier.name,
            subscriptionStatus = status.name,
            billingPeriod = billingPeriod.name,
            stripeCustomerId = stripeCustomerId,
            stripeSubscriptionId = stripeSubscriptionId,
            currentPeriodStart = currentPeriodStart,
            currentPeriodEnd = currentPeriodEnd,
            trialEndsAt = trialEndsAt,
            cancelAtPeriodEnd = cancelAtPeriodEnd,
            updatedAt = Instant.now()
        )

        userDao.updateUser(updatedUser)
    }

    /**
     * Check if the user has an active paid subscription.
     */
    suspend fun hasActiveSubscription(): Boolean {
        return getSubscription().hasActiveSubscription
    }

    /**
     * Get the current subscription tier.
     */
    suspend fun getCurrentTier(): SubscriptionTier {
        return getSubscription().tier
    }

    /**
     * Generate the Stripe checkout URL for a given tier and billing period.
     * This would typically call a Supabase Edge Function.
     */
    fun getCheckoutUrl(tier: SubscriptionTier, billingPeriod: BillingPeriod): String {
        // This URL points to Supabase Edge Function that creates Stripe Checkout Session
        val priceId = getPriceId(tier, billingPeriod)
        val userId = tokenManager.getUserId() ?: ""
        return "https://your-project.supabase.co/functions/v1/create-checkout?price_id=$priceId&user_id=$userId"
    }

    /**
     * Generate the Stripe Customer Portal URL for managing subscriptions.
     */
    fun getCustomerPortalUrl(): String {
        val userId = tokenManager.getUserId() ?: ""
        return "https://your-project.supabase.co/functions/v1/customer-portal?user_id=$userId"
    }

    private fun getPriceId(tier: SubscriptionTier, billingPeriod: BillingPeriod): String {
        // These would be actual Stripe Price IDs in production
        return when (tier) {
            SubscriptionTier.FREE -> ""
            SubscriptionTier.SOLO -> if (billingPeriod == BillingPeriod.YEARLY) "price_solo_yearly" else "price_solo_monthly"
            SubscriptionTier.GROWING -> if (billingPeriod == BillingPeriod.YEARLY) "price_growing_yearly" else "price_growing_monthly"
            SubscriptionTier.MULTI -> if (billingPeriod == BillingPeriod.YEARLY) "price_multi_yearly" else "price_multi_monthly"
        }
    }

    private fun UserEntity.toSubscriptionDetails(): SubscriptionDetails {
        return SubscriptionDetails(
            tier = SubscriptionTier.fromString(subscriptionTier),
            status = SubscriptionStatus.fromString(subscriptionStatus),
            billingPeriod = BillingPeriod.fromString(billingPeriod),
            stripeCustomerId = stripeCustomerId,
            stripeSubscriptionId = stripeSubscriptionId,
            currentPeriodStart = currentPeriodStart,
            currentPeriodEnd = currentPeriodEnd,
            trialEndsAt = trialEndsAt,
            cancelAtPeriodEnd = cancelAtPeriodEnd
        )
    }
}
