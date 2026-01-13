package com.hoofdirect.app.core.subscription

import com.hoofdirect.app.core.database.dao.ClientDao
import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.dao.SmsUsageDao
import com.hoofdirect.app.core.database.dao.UserDao
import com.hoofdirect.app.core.database.entity.SmsUsageEntity
import com.hoofdirect.app.core.subscription.model.LimitCheckResult
import com.hoofdirect.app.core.subscription.model.SubscriptionTier
import com.hoofdirect.app.core.subscription.model.TierLimits
import com.hoofdirect.app.core.subscription.model.UsageItem
import com.hoofdirect.app.core.subscription.model.UsageSummary
import com.hoofdirect.app.feature.auth.data.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages usage limits based on the user's subscription tier.
 * Provides methods to check if actions are allowed and to get usage summaries.
 */
@Singleton
class UsageLimitsManager @Inject constructor(
    private val userDao: UserDao,
    private val clientDao: ClientDao,
    private val horseDao: HorseDao,
    private val smsUsageDao: SmsUsageDao,
    private val tokenManager: TokenManager
) {
    /**
     * Get the current user's subscription tier.
     */
    private suspend fun getCurrentTier(): SubscriptionTier {
        val userId = tokenManager.getUserId() ?: return SubscriptionTier.FREE
        val user = userDao.getById(userId).firstOrNull() ?: return SubscriptionTier.FREE
        return SubscriptionTier.fromString(user.subscriptionTier)
    }

    /**
     * Get the limits for the current user's tier.
     */
    private suspend fun getCurrentLimits(): TierLimits {
        return TierLimits.forTier(getCurrentTier())
    }

    /**
     * Check if the user can add a new client.
     */
    suspend fun canAddClient(): LimitCheckResult {
        val userId = tokenManager.getUserId() ?: return LimitCheckResult.Blocked(0, 0, SubscriptionTier.SOLO)
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        if (limits.isUnlimited(limits.maxClients)) {
            return LimitCheckResult.Allowed(0, Int.MAX_VALUE)
        }

        val currentCount = clientDao.getActiveClientCount(userId)
        return checkLimit(currentCount, limits.maxClients, tier)
    }

    /**
     * Check if the user can add a new horse.
     */
    suspend fun canAddHorse(): LimitCheckResult {
        val userId = tokenManager.getUserId() ?: return LimitCheckResult.Blocked(0, 0, SubscriptionTier.SOLO)
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        if (limits.isUnlimited(limits.maxHorses)) {
            return LimitCheckResult.Allowed(0, Int.MAX_VALUE)
        }

        val currentCount = horseDao.getActiveHorseCount(userId)
        return checkLimit(currentCount, limits.maxHorses, tier)
    }

    /**
     * Check if the user can send an SMS this month.
     */
    suspend fun canSendSms(): LimitCheckResult {
        val userId = tokenManager.getUserId() ?: return LimitCheckResult.FeatureNotAvailable(SubscriptionTier.SOLO)
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        if (limits.maxSmsPerMonth == 0) {
            return LimitCheckResult.FeatureNotAvailable(SubscriptionTier.SOLO)
        }

        if (limits.isUnlimited(limits.maxSmsPerMonth)) {
            return LimitCheckResult.Allowed(0, Int.MAX_VALUE)
        }

        val yearMonth = SmsUsageEntity.currentYearMonth()
        val currentCount = smsUsageDao.getSmsCount(userId, yearMonth) ?: 0
        return checkLimit(currentCount, limits.maxSmsPerMonth, tier)
    }

    /**
     * Check if route optimization is available.
     */
    suspend fun canUseRouteOptimization(stopsCount: Int): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        if (limits.maxRouteStopsPerDay == 0) {
            return LimitCheckResult.FeatureNotAvailable(SubscriptionTier.SOLO)
        }

        if (limits.isUnlimited(limits.maxRouteStopsPerDay)) {
            return LimitCheckResult.Allowed(stopsCount, Int.MAX_VALUE)
        }

        return if (stopsCount > limits.maxRouteStopsPerDay) {
            val requiredTier = findRequiredTierForRouteStops(stopsCount)
            LimitCheckResult.Blocked(stopsCount, limits.maxRouteStopsPerDay, requiredTier)
        } else {
            LimitCheckResult.Allowed(stopsCount, limits.maxRouteStopsPerDay)
        }
    }

    /**
     * Check if calendar sync is available.
     */
    suspend fun canUseCalendarSync(): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        return if (limits.calendarSyncEnabled) {
            LimitCheckResult.Allowed(0, 1)
        } else {
            LimitCheckResult.FeatureNotAvailable(SubscriptionTier.SOLO)
        }
    }

    /**
     * Check if mileage reports are available.
     */
    suspend fun canUseMileageReports(): LimitCheckResult {
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        return if (limits.mileageReportsEnabled) {
            LimitCheckResult.Allowed(0, 1)
        } else {
            LimitCheckResult.FeatureNotAvailable(SubscriptionTier.SOLO)
        }
    }

    /**
     * Record that an SMS was sent.
     */
    suspend fun recordSmsSent() {
        val userId = tokenManager.getUserId() ?: return
        val yearMonth = SmsUsageEntity.currentYearMonth()
        val existing = smsUsageDao.getByUserAndMonth(userId, yearMonth)

        if (existing != null) {
            smsUsageDao.incrementSmsCount(userId, yearMonth, Instant.now().toEpochMilli())
        } else {
            smsUsageDao.insert(
                SmsUsageEntity.createNew(userId).copy(
                    smsCount = 1,
                    lastSentAt = Instant.now()
                )
            )
        }
    }

    /**
     * Get a flow of the current usage summary.
     * Note: This returns a cold flow that computes the summary on each collection.
     */
    suspend fun getUsageSummaryFlow(): Flow<UsageSummary> {
        val userId = tokenManager.getUserId()
        return if (userId == null) {
            flowOf(UsageSummary.empty())
        } else {
            flowOf(getUsageSummary())
        }
    }

    /**
     * Get the current usage summary.
     */
    suspend fun getUsageSummary(): UsageSummary {
        val userId = tokenManager.getUserId() ?: return UsageSummary.empty()
        val tier = getCurrentTier()
        val limits = TierLimits.forTier(tier)

        val clientCount = clientDao.getActiveClientCount(userId)
        val horseCount = horseDao.getActiveHorseCount(userId)
        val yearMonth = SmsUsageEntity.currentYearMonth()
        val smsCount = smsUsageDao.getSmsCount(userId, yearMonth) ?: 0

        return UsageSummary(
            tier = tier,
            clients = UsageItem(
                name = "Clients",
                current = clientCount,
                limit = limits.maxClients,
                isUnlimited = limits.isUnlimited(limits.maxClients)
            ),
            horses = UsageItem(
                name = "Horses",
                current = horseCount,
                limit = limits.maxHorses,
                isUnlimited = limits.isUnlimited(limits.maxHorses)
            ),
            smsThisMonth = UsageItem(
                name = "SMS This Month",
                current = smsCount,
                limit = limits.maxSmsPerMonth,
                isUnlimited = limits.isUnlimited(limits.maxSmsPerMonth)
            ),
            teamUsers = UsageItem(
                name = "Team Members",
                current = 1, // Currently single user only
                limit = limits.maxTeamUsers,
                isUnlimited = limits.isUnlimited(limits.maxTeamUsers)
            )
        )
    }

    private fun checkLimit(current: Int, limit: Int, currentTier: SubscriptionTier): LimitCheckResult {
        if (current >= limit) {
            val requiredTier = findNextTier(currentTier)
            return LimitCheckResult.Blocked(current, limit, requiredTier)
        }

        val percentUsed = current.toFloat() / limit
        return if (percentUsed >= TierLimits.WARNING_THRESHOLD) {
            LimitCheckResult.Warning(current, limit, percentUsed)
        } else {
            LimitCheckResult.Allowed(current, limit)
        }
    }

    private fun findNextTier(currentTier: SubscriptionTier): SubscriptionTier {
        return when (currentTier) {
            SubscriptionTier.FREE -> SubscriptionTier.SOLO
            SubscriptionTier.SOLO -> SubscriptionTier.GROWING
            SubscriptionTier.GROWING -> SubscriptionTier.MULTI
            SubscriptionTier.MULTI -> SubscriptionTier.MULTI
        }
    }

    private fun findRequiredTierForRouteStops(stopsCount: Int): SubscriptionTier {
        return when {
            stopsCount <= 8 -> SubscriptionTier.SOLO
            stopsCount <= 15 -> SubscriptionTier.GROWING
            else -> SubscriptionTier.MULTI
        }
    }
}
