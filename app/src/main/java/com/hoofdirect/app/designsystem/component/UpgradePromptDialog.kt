package com.hoofdirect.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.core.subscription.model.SubscriptionTier
import com.hoofdirect.app.core.subscription.model.TierLimits

/**
 * Dialog shown when user tries to access a feature or exceed a limit
 * that requires an upgrade.
 */
@Composable
fun UpgradePromptDialog(
    isVisible: Boolean,
    title: String,
    message: String,
    requiredTier: SubscriptionTier,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Highlight the required tier
                TierHighlight(tier = requiredTier)
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upgrade to ${requiredTier.displayName}")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Maybe Later")
            }
        }
    )
}

@Composable
private fun TierHighlight(tier: SubscriptionTier) {
    val limits = TierLimits.forTier(tier)
    val features = buildList {
        if (limits.isUnlimited(limits.maxClients)) add("Unlimited clients")
        if (limits.isUnlimited(limits.maxHorses)) add("Unlimited horses")
        if (limits.maxRouteStopsPerDay > 0) {
            val stops = if (limits.isUnlimited(limits.maxRouteStopsPerDay)) "Unlimited" else "${limits.maxRouteStopsPerDay}"
            add("$stops route stops/day")
        }
        if (limits.maxSmsPerMonth > 0) add("${limits.maxSmsPerMonth} SMS/month")
        if (limits.calendarSyncEnabled) add("Calendar sync")
        if (limits.mileageReportsEnabled) add("Mileage reports")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${tier.displayName} Plan includes:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        features.take(4).forEach { feature ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Starting at $${tier.monthlyPrice}/month",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Convenience function to show upgrade dialog for specific scenarios.
 */
@Composable
fun FeatureLockedDialog(
    isVisible: Boolean,
    featureName: String,
    requiredTier: SubscriptionTier,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    UpgradePromptDialog(
        isVisible = isVisible,
        title = "Unlock $featureName",
        message = "$featureName is available with ${requiredTier.displayName} plan and above. Upgrade to unlock this feature and more!",
        requiredTier = requiredTier,
        onUpgrade = onUpgrade,
        onDismiss = onDismiss
    )
}

/**
 * Convenience function to show upgrade dialog when limit is reached.
 */
@Composable
fun LimitReachedDialog(
    isVisible: Boolean,
    resourceName: String,
    currentLimit: Int,
    requiredTier: SubscriptionTier,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    UpgradePromptDialog(
        isVisible = isVisible,
        title = "$resourceName Limit Reached",
        message = "You've reached your limit of $currentLimit $resourceName. Upgrade to ${requiredTier.displayName} for unlimited $resourceName!",
        requiredTier = requiredTier,
        onUpgrade = onUpgrade,
        onDismiss = onDismiss
    )
}
