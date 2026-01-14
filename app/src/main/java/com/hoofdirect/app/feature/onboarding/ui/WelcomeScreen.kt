package com.hoofdirect.app.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoofdirect.app.feature.onboarding.model.OnboardingStep
import com.hoofdirect.app.feature.onboarding.ui.components.OnboardingScaffold

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        currentStep = OnboardingStep.WELCOME,
        onSkip = onSkip,
        primaryButtonText = "Get Started",
        onPrimaryClick = onGetStarted,
        showProgress = false
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Hero illustration placeholder - horseshoe icon
            HeroIllustration()

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Welcome to Hoof Direct",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The complete CRM for professional farriers. Manage your clients, schedule appointments, and optimize your routes.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Feature highlights
            FeatureHighlight(
                icon = Icons.Outlined.Groups,
                title = "Client Management",
                description = "Keep all your clients and their horses organized"
            )

            Spacer(modifier = Modifier.height(16.dp))

            FeatureHighlight(
                icon = Icons.Outlined.Event,
                title = "Smart Scheduling",
                description = "Schedule appointments and send reminders"
            )

            Spacer(modifier = Modifier.height(16.dp))

            FeatureHighlight(
                icon = Icons.Outlined.DirectionsCar,
                title = "Route Optimization",
                description = "Plan the most efficient routes for your day"
            )
        }
    }
}

@Composable
private fun HeroIllustration(
    modifier: Modifier = Modifier
) {
    // Placeholder icon - replace with actual horseshoe logo asset
    // TODO: Add horseshoe drawable resource (res/drawable/ic_horseshoe.xml)
    Icon(
        imageVector = Icons.Outlined.Verified,
        contentDescription = "Hoof Direct Logo",
        modifier = modifier.size(120.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun FeatureHighlight(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.padding(horizontal = 12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
