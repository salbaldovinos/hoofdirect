package com.hoofdirect.app.feature.onboarding.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.feature.onboarding.model.AppPermission
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep
import com.hoofdirect.app.feature.onboarding.ui.components.OnboardingScaffold

@Composable
fun LocationPermissionScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        viewModel.onPermissionResult(AppPermission.LOCATION, granted)
        onContinue()
    }

    PermissionScreenContent(
        step = OnboardingStep.PERMISSIONS_LOCATION,
        icon = Icons.Outlined.LocationOn,
        title = "Enable Location",
        description = "Location access allows Hoof Direct to optimize your daily routes and track mileage automatically.",
        benefits = listOf(
            "Get turn-by-turn directions to clients",
            "Automatic mileage tracking for tax deductions",
            "Optimized routes save time and fuel"
        ),
        onBack = onBack,
        onAllow = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        },
        onSkip = {
            viewModel.skipPermission(AppPermission.LOCATION)
            onContinue()
        },
        modifier = modifier
    )
}

@Composable
fun CalendarPermissionScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        viewModel.onPermissionResult(AppPermission.CALENDAR, granted)
        onContinue()
    }

    PermissionScreenContent(
        step = OnboardingStep.PERMISSIONS_CALENDAR,
        icon = Icons.Outlined.CalendarMonth,
        title = "Sync Calendar",
        description = "Calendar sync keeps your appointments in sync with Google Calendar so you never double-book.",
        benefits = listOf(
            "See Hoof Direct appointments in your calendar",
            "Avoid scheduling conflicts",
            "Share availability with family"
        ),
        onBack = onBack,
        onAllow = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        },
        onSkip = {
            viewModel.skipPermission(AppPermission.CALENDAR)
            onContinue()
        },
        modifier = modifier
    )
}

@Composable
fun NotificationsPermissionScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(AppPermission.NOTIFICATIONS, granted)
        onContinue()
    }

    PermissionScreenContent(
        step = OnboardingStep.PERMISSIONS_NOTIFICATIONS,
        icon = Icons.Outlined.Notifications,
        title = "Enable Notifications",
        description = "Get timely reminders about upcoming appointments and never miss a client.",
        benefits = listOf(
            "Appointment reminders",
            "Daily schedule summary",
            "Client message notifications"
        ),
        onBack = onBack,
        onAllow = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // For older Android versions, notifications are enabled by default
                viewModel.onPermissionResult(AppPermission.NOTIFICATIONS, true)
                onContinue()
            }
        },
        onSkip = {
            viewModel.skipPermission(AppPermission.NOTIFICATIONS)
            onContinue()
        },
        modifier = modifier
    )
}

@Composable
private fun PermissionScreenContent(
    step: OnboardingStep,
    icon: ImageVector,
    title: String,
    description: String,
    benefits: List<String>,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        currentStep = step,
        onBack = onBack,
        primaryButtonText = "Allow",
        onPrimaryClick = onAllow,
        secondaryButtonText = "Not Now",
        onSecondaryClick = onSkip
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Benefits list
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                benefits.forEach { benefit ->
                    BenefitItem(text = benefit)
                }
            }
        }
    }
}

@Composable
private fun BenefitItem(
    text: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val Icons.Outlined.Check: ImageVector
    get() = androidx.compose.material.icons.Icons.Outlined.Check
