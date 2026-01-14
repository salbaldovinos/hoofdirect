package com.hoofdirect.app.feature.route.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.hoofdirect.app.core.route.model.RouteStop
import com.hoofdirect.app.designsystem.component.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: RouteViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToStop: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val routeState = uiState.routeState
    if (routeState !is RouteState.Optimized) {
        LoadingIndicator()
        return
    }

    val route = routeState.route
    val plan = route.plan

    // Camera position state
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(plan.startLatitude, plan.startLongitude),
            10f
        )
    }

    // Calculate bounds to fit all markers
    var hasFittedBounds by remember { mutableStateOf(false) }

    LaunchedEffect(plan) {
        if (!hasFittedBounds) {
            val boundsBuilder = LatLngBounds.builder()
            boundsBuilder.include(LatLng(plan.startLatitude, plan.startLongitude))
            boundsBuilder.include(LatLng(plan.endLatitude, plan.endLongitude))
            plan.stops.forEach { stop ->
                boundsBuilder.include(LatLng(stop.latitude, stop.longitude))
            }
            val bounds = boundsBuilder.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            hasFittedBounds = true
        }
    }

    // Selected stop for info card
    var selectedStop by remember { mutableStateOf<RouteStop?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route Map") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = false
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = false
                ),
                onMapClick = {
                    selectedStop = null
                }
            ) {
                // Start marker (Home)
                Marker(
                    state = MarkerState(position = LatLng(plan.startLatitude, plan.startLongitude)),
                    title = plan.startLocationName,
                    snippet = "Start",
                    onClick = {
                        selectedStop = null
                        true
                    }
                )

                // Stop markers
                plan.stops.forEach { stop ->
                    Marker(
                        state = MarkerState(position = LatLng(stop.latitude, stop.longitude)),
                        title = "${stop.order}. ${stop.clientBusinessName ?: stop.clientName}",
                        snippet = stop.address,
                        onClick = {
                            selectedStop = stop
                            true
                        }
                    )
                }

                // End marker (if different from start)
                if (plan.endLatitude != plan.startLatitude || plan.endLongitude != plan.startLongitude) {
                    Marker(
                        state = MarkerState(position = LatLng(plan.endLatitude, plan.endLongitude)),
                        title = plan.endLocationName,
                        snippet = "End"
                    )
                }

                // Route polyline connecting all points
                val routePoints = buildList {
                    add(LatLng(plan.startLatitude, plan.startLongitude))
                    plan.stops.forEach { stop ->
                        add(LatLng(stop.latitude, stop.longitude))
                    }
                    add(LatLng(plan.endLatitude, plan.endLongitude))
                }

                Polyline(
                    points = routePoints,
                    color = MaterialTheme.colorScheme.primary,
                    width = 8f
                )
            }

            // Fit bounds button
            SmallFloatingActionButton(
                onClick = {
                    val boundsBuilder = LatLngBounds.builder()
                    boundsBuilder.include(LatLng(plan.startLatitude, plan.startLongitude))
                    boundsBuilder.include(LatLng(plan.endLatitude, plan.endLongitude))
                    plan.stops.forEach { stop ->
                        boundsBuilder.include(LatLng(stop.latitude, stop.longitude))
                    }
                    val bounds = boundsBuilder.build()
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Fit route",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Stats card at bottom
            RouteStatsCard(
                totalDistance = plan.totalDistanceMiles,
                totalTime = plan.totalDriveMinutes,
                stopCount = plan.stops.size,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )

            // Selected stop info card
            selectedStop?.let { stop ->
                StopInfoCard(
                    stop = stop,
                    onNavigate = {
                        viewModel.getStopNavigationIntent(stop).let { intent ->
                            context.startActivity(intent)
                        }
                    },
                    onViewDetails = { onNavigateToStop(stop.appointmentId) },
                    onDismiss = { selectedStop = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            // Start navigation FAB
            if (selectedStop == null) {
                FloatingActionButton(
                    onClick = {
                        viewModel.getRouteNavigationIntent()?.let { intent ->
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = "Start navigation")
                }
            }
        }
    }
}

@Composable
private fun RouteStatsCard(
    totalDistance: Double,
    totalTime: Int,
    stopCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = String.format("%.1f mi", totalDistance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = formatDuration(totalTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Drive time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = stopCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stops",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StopInfoCard(
    stop: RouteStop,
    onNavigate: () -> Unit,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop number badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stop.order.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.clientBusinessName ?: stop.clientName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stop.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate")
                }

                Button(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Details")
                }
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
