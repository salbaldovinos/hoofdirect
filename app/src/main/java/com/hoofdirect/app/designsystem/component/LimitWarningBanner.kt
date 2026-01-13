package com.hoofdirect.app.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.core.subscription.model.LimitCheckResult

/**
 * Banner shown when user is approaching or has reached a usage limit.
 */
@Composable
fun LimitWarningBanner(
    result: LimitCheckResult,
    resourceName: String,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldShow = result is LimitCheckResult.Warning || result is LimitCheckResult.Blocked

    AnimatedVisibility(
        visible = shouldShow,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        val (backgroundColor, message) = when (result) {
            is LimitCheckResult.Warning -> {
                MaterialTheme.colorScheme.tertiaryContainer to
                    "You've used ${result.current} of ${result.limit} $resourceName (${(result.percentUsed * 100).toInt()}%)"
            }
            is LimitCheckResult.Blocked -> {
                MaterialTheme.colorScheme.errorContainer to
                    "You've reached your limit of ${result.limit} $resourceName. Upgrade to add more."
            }
            is LimitCheckResult.FeatureNotAvailable -> {
                MaterialTheme.colorScheme.errorContainer to
                    "This feature requires ${result.requiredTier.displayName} plan or higher"
            }
            else -> return@AnimatedVisibility
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(onClick = onUpgradeClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = when (result) {
                    is LimitCheckResult.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = when (result) {
                    is LimitCheckResult.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Upgrade",
                style = MaterialTheme.typography.labelLarge,
                color = when (result) {
                    is LimitCheckResult.Warning -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

/**
 * Simplified warning banner that takes direct values instead of LimitCheckResult.
 */
@Composable
fun LimitWarningBanner(
    isVisible: Boolean,
    current: Int,
    limit: Int,
    resourceName: String,
    isBlocked: Boolean = false,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        val percentUsed = if (limit > 0) (current.toFloat() / limit * 100).toInt() else 100
        val backgroundColor = if (isBlocked) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
        val contentColor = if (isBlocked) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }
        val message = if (isBlocked) {
            "You've reached your limit of $limit $resourceName. Upgrade to add more."
        } else {
            "You've used $current of $limit $resourceName ($percentUsed%)"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(onClick = onUpgradeClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Upgrade",
                style = MaterialTheme.typography.labelLarge,
                color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
