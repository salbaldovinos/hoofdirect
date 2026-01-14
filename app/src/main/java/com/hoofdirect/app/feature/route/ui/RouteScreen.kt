package com.hoofdirect.app.feature.route.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.designsystem.component.EmptyState
import com.hoofdirect.app.designsystem.component.LoadingIndicator
import com.hoofdirect.app.feature.route.ui.components.ReorderableColumn
import com.hoofdirect.app.feature.route.ui.components.rememberReorderableState
import com.hoofdirect.app.feature.route.ui.components.RouteStopCard
import com.hoofdirect.app.feature.route.ui.components.SavingsCard
import com.hoofdirect.app.feature.route.ui.components.StartEndLocationItem
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    viewModel: RouteViewModel = hiltViewModel(),
    onNavigateToMap: (String) -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToAppointment: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Refresh data when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Show error messages
    LaunchedEffect(uiState.routeState) {
        if (uiState.routeState is RouteState.Error) {
            snackbarHostState.showSnackbar((uiState.routeState as RouteState.Error).message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route") },
                actions = {
                    if (uiState.routeState is RouteState.Optimized) {
                        IconButton(onClick = { /* Navigate to map */ }) {
                            Icon(Icons.Default.Map, contentDescription = "View map")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date navigation header
            DateHeader(
                dateLabel = viewModel.getFormattedDate(),
                appointmentCount = uiState.appointmentCount,
                isToday = uiState.date == LocalDate.now(),
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onGoToToday = { viewModel.goToToday() }
            )

            // Tier limit info banner
            uiState.tierLimitInfo?.let { tierInfo ->
                if (!tierInfo.isUnlimited && tierInfo.canOptimize) {
                    TierLimitBanner(
                        tierName = tierInfo.currentTier,
                        maxStops = tierInfo.maxStops,
                        currentStops = uiState.appointmentCount
                    )
                }
            }

            // Main content based on state
            when (val routeState = uiState.routeState) {
                is RouteState.Loading -> {
                    LoadingIndicator()
                }

                is RouteState.NoAppointments -> {
                    EmptyState(
                        icon = Icons.Default.Schedule,
                        title = "No appointments",
                        message = "No appointments scheduled for ${viewModel.getFormattedDate()}. Add appointments to optimize your route.",
                        actionLabel = "Go to Schedule",
                        onAction = onNavigateToSchedule
                    )
                }

                is RouteState.NoLocationData -> {
                    EmptyState(
                        icon = Icons.Default.Route,
                        title = "Missing location data",
                        message = "You have ${routeState.appointmentCount} appointment${if (routeState.appointmentCount != 1) "s" else ""} but none have location data. Edit your clients to add their addresses with GPS coordinates.",
                        actionLabel = "Go to Clients",
                        onAction = onNavigateToSchedule // TODO: Navigate to Clients instead
                    )
                }

                is RouteState.NotOptimized -> {
                    NotOptimizedContent(
                        appointmentCount = uiState.appointmentCount,
                        tierLimitInfo = uiState.tierLimitInfo,
                        onOptimize = { viewModel.optimizeRoute() }
                    )
                }

                is RouteState.Optimizing -> {
                    OptimizingContent()
                }

                is RouteState.Optimized -> {
                    OptimizedRouteContent(
                        route = routeState.route,
                        lockedStops = uiState.lockedStops,
                        startTime = LocalTime.of(8, 0), // TODO: Make configurable
                        onStopClick = { stopId -> onNavigateToAppointment(stopId) },
                        onLockToggle = { stopId -> viewModel.toggleLock(stopId) },
                        onReorder = { from, to -> viewModel.reorderStop(from, to) },
                        onReOptimize = { viewModel.resetToOptimized() },
                        onStartNavigation = {
                            viewModel.getRouteNavigationIntent()?.let { intent ->
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                is RouteState.Error -> {
                    ErrorContent(
                        message = routeState.message,
                        onRetry = { viewModel.optimizeRoute() },
                        onGoToSchedule = onNavigateToSchedule
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(
    dateLabel: String,
    appointmentCount: Int,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onGoToToday: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousDay) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$appointmentCount appointment${if (appointmentCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onNextDay) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
            }

            if (!isToday) {
                TextButton(
                    onClick = onGoToToday,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        Icons.Default.Today,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Go to Today",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TierLimitBanner(
    tierName: String,
    maxStops: Int,
    currentStops: Int
) {
    val isNearLimit = currentStops >= maxStops - 2
    val isOverLimit = currentStops > maxStops

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOverLimit -> MaterialTheme.colorScheme.errorContainer
                isNearLimit -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$tierName Plan",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isOverLimit -> MaterialTheme.colorScheme.onErrorContainer
                    isNearLimit -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "$currentStops / $maxStops stops",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    isOverLimit -> MaterialTheme.colorScheme.onErrorContainer
                    isNearLimit -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun NotOptimizedContent(
    appointmentCount: Int,
    tierLimitInfo: TierLimitInfo?,
    onOptimize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ready to Optimize",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You have $appointmentCount appointment${if (appointmentCount != 1) "s" else ""} to optimize into the most efficient driving route.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val canOptimize = tierLimitInfo?.canOptimize ?: false
        val isWithinLimit = tierLimitInfo?.let {
            it.isUnlimited || appointmentCount <= it.maxStops
        } ?: true

        Button(
            onClick = onOptimize,
            enabled = canOptimize && isWithinLimit,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                Icons.Default.Route,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Optimize Route",
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (!canOptimize) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Route optimization requires a paid subscription",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (!isWithinLimit) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your plan allows ${tierLimitInfo?.maxStops} stops. Upgrade for more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun OptimizingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Optimizing your route...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Finding the most efficient order for your appointments",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OptimizedRouteContent(
    route: com.hoofdirect.app.core.route.model.OptimizedRoute,
    lockedStops: Map<String, Int>,
    startTime: LocalTime,
    onStopClick: (String) -> Unit,
    onLockToggle: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onReOptimize: () -> Unit,
    onStartNavigation: () -> Unit
) {
    val scrollState = rememberScrollState()
    val reorderableState = rememberReorderableState(items = route.plan.stops)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Savings card
        SavingsCard(
            savings = route.savings,
            originalStats = route.originalStats,
            optimizedStats = com.hoofdirect.app.core.route.model.RouteStats(
                totalMiles = route.plan.totalDistanceMiles,
                totalMinutes = route.plan.totalDriveMinutes
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Start location
        StartEndLocationItem(
            label = "Start",
            locationName = route.plan.startLocationName,
            onClick = { /* Edit start location */ },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Reorderable stop cards
        ReorderableColumn(
            state = reorderableState,
            onReorder = onReorder,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { index, stop, isDragging ->
            RouteStopCard(
                stop = stop,
                startTime = startTime,
                isDragging = isDragging,
                isLocked = lockedStops.containsKey(stop.appointmentId),
                onLockToggle = { onLockToggle(stop.appointmentId) },
                onClick = { onStopClick(stop.appointmentId) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // End location with drive info
        val lastStop = route.plan.stops.lastOrNull()
        val driveToEnd = if (lastStop != null) {
            val distanceToEnd = route.plan.totalDistanceMiles -
                route.plan.stops.sumOf { it.distanceMilesFromPrevious }
            "${estimateDriveMinutes(distanceToEnd)} min · ${String.format("%.1f mi", distanceToEnd)}"
        } else null

        StartEndLocationItem(
            label = "End",
            locationName = route.plan.endLocationName,
            driveInfo = driveToEnd,
            onClick = { /* Edit end location */ },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onReOptimize,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Re-optimize",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Button(
                onClick = onStartNavigation,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Start Route",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Bottom spacer
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onGoToSchedule: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Optimization Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onGoToSchedule) {
                Text("Go to Schedule")
            }

            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

private fun estimateDriveMinutes(distanceMiles: Double): Int {
    return (distanceMiles * 2).toInt() // Assumes 30 mph average
}
