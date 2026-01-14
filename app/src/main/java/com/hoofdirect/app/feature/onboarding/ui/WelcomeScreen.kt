package com.hoofdirect.app.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
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
    // Placeholder for actual horseshoe illustration
    // In production, this would be an Image composable with the actual asset
    Icon(
        imageVector = HorseshoeIcon,
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
    androidx.compose.foundation.layout.Row(
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

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 12.dp))

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

// Custom horseshoe icon using vector paths
private val HorseshoeIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() {
        return androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = "Horseshoe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            androidx.compose.ui.graphics.vector.path(
                fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
                stroke = null,
                strokeLineWidth = 0f
            ) {
                // Simplified horseshoe shape
                moveTo(12f, 2f)
                curveTo(7.03f, 2f, 3f, 6.03f, 3f, 11f)
                lineTo(3f, 20f)
                lineTo(6f, 20f)
                lineTo(6f, 11f)
                curveTo(6f, 7.69f, 8.69f, 5f, 12f, 5f)
                curveTo(15.31f, 5f, 18f, 7.69f, 18f, 11f)
                lineTo(18f, 20f)
                lineTo(21f, 20f)
                lineTo(21f, 11f)
                curveTo(21f, 6.03f, 16.97f, 2f, 12f, 2f)
                close()
                // Left nail hole
                moveTo(4.5f, 14f)
                curveTo(4.5f, 14.83f, 5.17f, 15.5f, 6f, 15.5f)
                curveTo(6.83f, 15.5f, 7.5f, 14.83f, 7.5f, 14f)
                curveTo(7.5f, 13.17f, 6.83f, 12.5f, 6f, 12.5f)
                curveTo(5.17f, 12.5f, 4.5f, 13.17f, 4.5f, 14f)
                close()
                // Right nail hole
                moveTo(16.5f, 14f)
                curveTo(16.5f, 14.83f, 17.17f, 15.5f, 18f, 15.5f)
                curveTo(18.83f, 15.5f, 19.5f, 14.83f, 19.5f, 14f)
                curveTo(19.5f, 13.17f, 18.83f, 12.5f, 18f, 12.5f)
                curveTo(17.17f, 12.5f, 16.5f, 13.17f, 16.5f, 14f)
                close()
            }
        }.build()
    }
