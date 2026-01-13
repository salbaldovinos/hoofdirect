package com.hoofdirect.app.feature.more.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMileage: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToPricing: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Account section
            MoreSection(title = "Account") {
                MoreMenuItem(
                    icon = Icons.Default.Person,
                    title = "Profile",
                    subtitle = "Business info, working hours",
                    onClick = onNavigateToProfile
                )
                MoreMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    subtitle = "Notifications, preferences",
                    onClick = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Business tools section
            MoreSection(title = "Business Tools") {
                MoreMenuItem(
                    icon = Icons.Default.DirectionsCar,
                    title = "Mileage Tracking",
                    subtitle = "Log trips for tax deductions",
                    onClick = onNavigateToMileage
                )
                MoreMenuItem(
                    icon = Icons.Default.Assessment,
                    title = "Reports",
                    subtitle = "Revenue, clients, services",
                    onClick = onNavigateToReports
                )
                MoreMenuItem(
                    icon = Icons.Default.AttachMoney,
                    title = "Service Prices",
                    subtitle = "Set your default rates",
                    onClick = onNavigateToPricing
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subscription section
            MoreSection(title = "Subscription") {
                MoreMenuItem(
                    icon = Icons.Default.Star,
                    title = "Upgrade Plan",
                    subtitle = "Get route optimization & more",
                    onClick = onNavigateToSubscription
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Support section
            MoreSection(title = "Support") {
                MoreMenuItem(
                    icon = Icons.Default.Help,
                    title = "Help & FAQ",
                    subtitle = "Get answers to common questions",
                    onClick = onNavigateToHelp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign out
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = onSignOut)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App version
            Text(
                text = "Hoof Direct v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun MoreSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
