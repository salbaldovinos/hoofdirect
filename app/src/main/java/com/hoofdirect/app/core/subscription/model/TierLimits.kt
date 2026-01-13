package com.hoofdirect.app.core.subscription.model

/**
 * Defines usage limits for each subscription tier.
 * A value of Int.MAX_VALUE represents unlimited.
 */
data class TierLimits(
    val maxClients: Int,
    val maxHorses: Int,
    val maxPhotosPerHorse: Int,
    val maxRouteStopsPerDay: Int,
    val maxSmsPerMonth: Int,
    val maxTeamUsers: Int,
    val calendarSyncEnabled: Boolean,
    val mileageReportsEnabled: Boolean
) {
    companion object {
        private const val UNLIMITED = Int.MAX_VALUE

        fun forTier(tier: SubscriptionTier): TierLimits {
            return when (tier) {
                SubscriptionTier.FREE -> TierLimits(
                    maxClients = 10,
                    maxHorses = 30,
                    maxPhotosPerHorse = 50,
                    maxRouteStopsPerDay = 0,
                    maxSmsPerMonth = 0,
                    maxTeamUsers = 1,
                    calendarSyncEnabled = false,
                    mileageReportsEnabled = false
                )
                SubscriptionTier.SOLO -> TierLimits(
                    maxClients = UNLIMITED,
                    maxHorses = UNLIMITED,
                    maxPhotosPerHorse = UNLIMITED,
                    maxRouteStopsPerDay = 8,
                    maxSmsPerMonth = 50,
                    maxTeamUsers = 1,
                    calendarSyncEnabled = true,
                    mileageReportsEnabled = true
                )
                SubscriptionTier.GROWING -> TierLimits(
                    maxClients = UNLIMITED,
                    maxHorses = UNLIMITED,
                    maxPhotosPerHorse = UNLIMITED,
                    maxRouteStopsPerDay = 15,
                    maxSmsPerMonth = 200,
                    maxTeamUsers = 1,
                    calendarSyncEnabled = true,
                    mileageReportsEnabled = true
                )
                SubscriptionTier.MULTI -> TierLimits(
                    maxClients = UNLIMITED,
                    maxHorses = UNLIMITED,
                    maxPhotosPerHorse = UNLIMITED,
                    maxRouteStopsPerDay = UNLIMITED,
                    maxSmsPerMonth = 500,
                    maxTeamUsers = 5,
                    calendarSyncEnabled = true,
                    mileageReportsEnabled = true
                )
            }
        }

        /**
         * Warning threshold percentage (80%).
         */
        const val WARNING_THRESHOLD = 0.80f
    }

    fun isUnlimited(value: Int): Boolean = value == UNLIMITED
}
