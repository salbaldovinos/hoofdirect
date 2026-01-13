package com.hoofdirect.app.feature.subscription.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.subscription.model.BillingPeriod
import com.hoofdirect.app.core.subscription.model.SubscriptionStatus
import com.hoofdirect.app.core.subscription.model.SubscriptionTier
import com.hoofdirect.app.core.subscription.model.TierLimits
import com.hoofdirect.app.core.subscription.model.UsageItem
import com.hoofdirect.app.designsystem.component.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Plan Card
                CurrentPlanCard(
                    tier = uiState.subscription.tier,
                    status = uiState.subscription.status,
                    daysRemaining = uiState.subscription.daysRemaining,
                    trialDaysRemaining = uiState.subscription.trialDaysRemaining,
                    cancelAtPeriodEnd = uiState.subscription.cancelAtPeriodEnd,
                    onManageSubscription = {
                        val url = viewModel.getCustomerPortalUrl()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )

                // Usage Summary
                if (uiState.subscription.tier != SubscriptionTier.FREE ||
                    uiState.usageSummary.clients.current > 0) {
                    UsageSummaryCard(
                        clients = uiState.usageSummary.clients,
                        horses = uiState.usageSummary.horses,
                        smsThisMonth = uiState.usageSummary.smsThisMonth
                    )
                }

                // Billing Period Toggle
                BillingPeriodSelector(
                    selectedPeriod = uiState.selectedBillingPeriod,
                    onPeriodSelected = { viewModel.selectBillingPeriod(it) }
                )

                // Tier Cards
                uiState.availableTiers.forEach { tierInfo ->
                    TierCard(
                        tierInfo = tierInfo,
                        selectedBillingPeriod = uiState.selectedBillingPeriod,
                        onSelect = {
                            if (!tierInfo.isCurrentTier && tierInfo.tier != SubscriptionTier.FREE) {
                                val url = viewModel.getCheckoutUrl(tierInfo.tier)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CurrentPlanCard(
    tier: SubscriptionTier,
    status: SubscriptionStatus,
    daysRemaining: Int,
    trialDaysRemaining: Int,
    cancelAtPeriodEnd: Boolean,
    onManageSubscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = tier.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (tier != SubscriptionTier.FREE) {
                    StatusBadge(status = status)
                }
            }

            if (status == SubscriptionStatus.TRIALING && trialDaysRemaining > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$trialDaysRemaining days left in trial",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (cancelAtPeriodEnd && daysRemaining > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Subscription ends in $daysRemaining days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (tier != SubscriptionTier.FREE) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onManageSubscription,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Subscription")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SubscriptionStatus) {
    val (backgroundColor, textColor) = when {
        status.isActive -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        status.requiresPaymentAction -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun UsageSummaryCard(
    clients: UsageItem,
    horses: UsageItem,
    smsThisMonth: UsageItem
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            UsageItemRow(item = clients)
            Spacer(modifier = Modifier.height(8.dp))
            UsageItemRow(item = horses)
            Spacer(modifier = Modifier.height(8.dp))
            UsageItemRow(item = smsThisMonth)
        }
    }
}

@Composable
private fun UsageItemRow(item: UsageItem) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when {
                    item.isAtLimit -> MaterialTheme.colorScheme.error
                    item.isApproachingLimit -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        if (!item.isUnlimited) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { item.percentUsed.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    item.isAtLimit -> MaterialTheme.colorScheme.error
                    item.isApproachingLimit -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun BillingPeriodSelector(
    selectedPeriod: BillingPeriod,
    onPeriodSelected: (BillingPeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedPeriod == BillingPeriod.MONTHLY,
            onClick = { onPeriodSelected(BillingPeriod.MONTHLY) },
            label = { Text("Monthly") }
        )

        Spacer(modifier = Modifier.width(8.dp))

        FilterChip(
            selected = selectedPeriod == BillingPeriod.YEARLY,
            onClick = { onPeriodSelected(BillingPeriod.YEARLY) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Yearly")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save 17%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

@Composable
private fun TierCard(
    tierInfo: TierInfo,
    selectedBillingPeriod: BillingPeriod,
    onSelect: () -> Unit
) {
    val price = if (selectedBillingPeriod == BillingPeriod.YEARLY) {
        tierInfo.yearlyPrice / 12
    } else {
        tierInfo.monthlyPrice
    }

    val isPopular = tierInfo.tier == SubscriptionTier.GROWING

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (tierInfo.isCurrentTier) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else if (isPopular) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tierInfo.tier.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onTertiary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }
                    }
                }

                if (tierInfo.isCurrentTier) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Current",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (price == 0) "Free" else "$$price",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (price > 0) {
                    Text(
                        text = "/month",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            if (selectedBillingPeriod == BillingPeriod.YEARLY && tierInfo.yearlyPrice > 0) {
                Text(
                    text = "Billed as $${tierInfo.yearlyPrice}/year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Features
            TierFeatures(limits = tierInfo.limits)

            Spacer(modifier = Modifier.height(16.dp))

            if (!tierInfo.isCurrentTier && tierInfo.tier != SubscriptionTier.FREE) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isPopular) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text("Upgrade to ${tierInfo.tier.displayName}")
                }
            }
        }
    }
}

@Composable
private fun TierFeatures(limits: TierLimits) {
    val features = buildList {
        add(FeatureItem(
            "Clients",
            if (limits.isUnlimited(limits.maxClients)) "Unlimited" else "${limits.maxClients}"
        ))
        add(FeatureItem(
            "Horses",
            if (limits.isUnlimited(limits.maxHorses)) "Unlimited" else "${limits.maxHorses}"
        ))
        add(FeatureItem(
            "Route Stops/Day",
            when {
                limits.maxRouteStopsPerDay == 0 -> "Not included"
                limits.isUnlimited(limits.maxRouteStopsPerDay) -> "Unlimited"
                else -> "${limits.maxRouteStopsPerDay}"
            }
        ))
        add(FeatureItem(
            "SMS/Month",
            when {
                limits.maxSmsPerMonth == 0 -> "Not included"
                limits.isUnlimited(limits.maxSmsPerMonth) -> "Unlimited"
                else -> "${limits.maxSmsPerMonth}"
            }
        ))
        add(FeatureItem("Calendar Sync", if (limits.calendarSyncEnabled) "Included" else "Not included"))
        add(FeatureItem("Mileage Reports", if (limits.mileageReportsEnabled) "Included" else "Not included"))
        add(FeatureItem(
            "Team Members",
            if (limits.isUnlimited(limits.maxTeamUsers)) "Unlimited" else "${limits.maxTeamUsers}"
        ))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        features.forEach { feature ->
            FeatureRow(
                name = feature.name,
                value = feature.value,
                isIncluded = feature.value != "Not included"
            )
        }
    }
}

private data class FeatureItem(val name: String, val value: String)

@Composable
private fun FeatureRow(
    name: String,
    value: String,
    isIncluded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isIncluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isIncluded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isIncluded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}
