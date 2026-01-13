package com.hoofdirect.app.core.subscription.model

/**
 * Represents a single usage metric for display.
 */
data class UsageItem(
    val name: String,
    val current: Int,
    val limit: Int,
    val isUnlimited: Boolean = false
) {
    val percentUsed: Float
        get() = if (isUnlimited || limit == 0) 0f else (current.toFloat() / limit)

    val remaining: Int
        get() = if (isUnlimited) Int.MAX_VALUE else (limit - current).coerceAtLeast(0)

    val isAtLimit: Boolean
        get() = !isUnlimited && current >= limit

    val isApproachingLimit: Boolean
        get() = !isUnlimited && percentUsed >= TierLimits.WARNING_THRESHOLD

    val displayValue: String
        get() = if (isUnlimited) "$current / ∞" else "$current / $limit"
}
