package com.hoofdirect.app.feature.route.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoofdirect.app.designsystem.component.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routes") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EmptyState(
                icon = Icons.Default.Route,
                title = "Route Optimization",
                message = "Plan your day's appointments into an optimized driving route. Select a date with appointments to get started.",
                actionLabel = "Go to Schedule"
            )
        }
    }
}
