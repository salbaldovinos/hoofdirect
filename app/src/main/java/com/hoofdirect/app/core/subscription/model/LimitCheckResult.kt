package com.hoofdirect.app.core.subscription.model

/**
 * Result of checking a usage limit.
 */
sealed class LimitCheckResult {
    /**
     * The action is allowed - under the limit.
     */
    data class Allowed(
        val current: Int,
        val limit: Int
    ) : LimitCheckResult()

    /**
     * The action is allowed but approaching the limit (80%+).
     * Show a soft warning to the user.
     */
    data class Warning(
        val current: Int,
        val limit: Int,
        val percentUsed: Float
    ) : LimitCheckResult() {
        val remaining: Int get() = limit - current
    }

    /**
     * The action is blocked - at or over the limit.
     * User must upgrade to continue.
     */
    data class Blocked(
        val current: Int,
        val limit: Int,
        val requiredTier: SubscriptionTier
    ) : LimitCheckResult()

    /**
     * The feature is not available on the current tier.
     */
    data class FeatureNotAvailable(
        val requiredTier: SubscriptionTier
    ) : LimitCheckResult()

    val isAllowed: Boolean
        get() = this is Allowed || this is Warning

    val isBlocked: Boolean
        get() = this is Blocked || this is FeatureNotAvailable
}
