package com.hoofdirect.app.core.subscription.model

/**
 * Subscription status representing the current state of a user's subscription.
 */
enum class SubscriptionStatus(val displayName: String) {
    ACTIVE("Active"),
    TRIALING("Trial"),
    PAST_DUE("Past Due"),
    CANCELED("Canceled"),
    INCOMPLETE("Incomplete"),
    INCOMPLETE_EXPIRED("Expired"),
    UNPAID("Unpaid"),
    NONE("No Subscription");

    val isActive: Boolean
        get() = this == ACTIVE || this == TRIALING

    val requiresPaymentAction: Boolean
        get() = this == PAST_DUE || this == INCOMPLETE || this == UNPAID

    companion object {
        fun fromString(value: String?): SubscriptionStatus {
            if (value == null) return NONE
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}
