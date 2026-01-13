package com.hoofdirect.app.feature.subscription.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.subscription.SubscriptionRepository
import com.hoofdirect.app.core.subscription.UsageLimitsManager
import com.hoofdirect.app.core.subscription.model.BillingPeriod
import com.hoofdirect.app.core.subscription.model.SubscriptionDetails
import com.hoofdirect.app.core.subscription.model.SubscriptionTier
import com.hoofdirect.app.core.subscription.model.TierLimits
import com.hoofdirect.app.core.subscription.model.UsageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val isLoading: Boolean = true,
    val subscription: SubscriptionDetails = SubscriptionDetails.FREE,
    val usageSummary: UsageSummary = UsageSummary.empty(),
    val selectedBillingPeriod: BillingPeriod = BillingPeriod.MONTHLY,
    val availableTiers: List<TierInfo> = emptyList(),
    val error: String? = null
)

data class TierInfo(
    val tier: SubscriptionTier,
    val limits: TierLimits,
    val isCurrentTier: Boolean,
    val monthlyPrice: Int,
    val yearlyPrice: Int
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val usageLimitsManager: UsageLimitsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val subscription = subscriptionRepository.getSubscription()
                val usageSummary = usageLimitsManager.getUsageSummary()

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        subscription = subscription,
                        usageSummary = usageSummary,
                        availableTiers = buildTierList(subscription.tier)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load subscription") }
            }
        }
    }

    fun selectBillingPeriod(period: BillingPeriod) {
        _uiState.update { it.copy(selectedBillingPeriod = period) }
    }

    fun getCheckoutUrl(tier: SubscriptionTier): String {
        return subscriptionRepository.getCheckoutUrl(tier, _uiState.value.selectedBillingPeriod)
    }

    fun getCustomerPortalUrl(): String {
        return subscriptionRepository.getCustomerPortalUrl()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }

    private fun buildTierList(currentTier: SubscriptionTier): List<TierInfo> {
        return SubscriptionTier.entries.map { tier ->
            TierInfo(
                tier = tier,
                limits = TierLimits.forTier(tier),
                isCurrentTier = tier == currentTier,
                monthlyPrice = tier.monthlyPrice,
                yearlyPrice = tier.yearlyPrice
            )
        }
    }
}
