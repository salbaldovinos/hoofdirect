package com.hoofdirect.app.core.subscription.model

/**
 * Subscription tier levels for Hoof Direct.
 * Each tier has different limits and features.
 */
enum class SubscriptionTier(
    val displayName: String,
    val monthlyPrice: Int,
    val yearlyPrice: Int
) {
    FREE(
        displayName = "Free",
        monthlyPrice = 0,
        yearlyPrice = 0
    ),
    SOLO(
        displayName = "Solo",
        monthlyPrice = 29,
        yearlyPrice = 290
    ),
    GROWING(
        displayName = "Growing",
        monthlyPrice = 79,
        yearlyPrice = 790
    ),
    MULTI(
        displayName = "Multi",
        monthlyPrice = 149,
        yearlyPrice = 1490
    );

    companion object {
        fun fromString(value: String): SubscriptionTier {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: FREE
        }
    }
}
