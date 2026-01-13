package com.hoofdirect.app.core.subscription.model

/**
 * Summary of all usage metrics for the current user.
 */
data class UsageSummary(
    val tier: SubscriptionTier,
    val clients: UsageItem,
    val horses: UsageItem,
    val smsThisMonth: UsageItem,
    val teamUsers: UsageItem
) {
    val items: List<UsageItem>
        get() = listOf(clients, horses, smsThisMonth, teamUsers)

    val hasAnyWarnings: Boolean
        get() = items.any { it.isApproachingLimit && !it.isUnlimited }

    val hasAnyBlocked: Boolean
        get() = items.any { it.isAtLimit && !it.isUnlimited }

    companion object {
        fun empty(tier: SubscriptionTier = SubscriptionTier.FREE): UsageSummary {
            val limits = TierLimits.forTier(tier)
            return UsageSummary(
                tier = tier,
                clients = UsageItem(
                    name = "Clients",
                    current = 0,
                    limit = limits.maxClients,
                    isUnlimited = limits.isUnlimited(limits.maxClients)
                ),
                horses = UsageItem(
                    name = "Horses",
                    current = 0,
                    limit = limits.maxHorses,
                    isUnlimited = limits.isUnlimited(limits.maxHorses)
                ),
                smsThisMonth = UsageItem(
                    name = "SMS This Month",
                    current = 0,
                    limit = limits.maxSmsPerMonth,
                    isUnlimited = limits.isUnlimited(limits.maxSmsPerMonth)
                ),
                teamUsers = UsageItem(
                    name = "Team Members",
                    current = 1,
                    limit = limits.maxTeamUsers,
                    isUnlimited = limits.isUnlimited(limits.maxTeamUsers)
                )
            )
        }
    }
}
