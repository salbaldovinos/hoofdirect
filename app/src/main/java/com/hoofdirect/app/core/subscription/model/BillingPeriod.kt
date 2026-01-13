package com.hoofdirect.app.core.subscription.model

/**
 * Billing period for subscriptions.
 */
enum class BillingPeriod(val displayName: String) {
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    NONE("N/A");

    companion object {
        fun fromString(value: String?): BillingPeriod {
            if (value == null) return NONE
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}
