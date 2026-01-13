package com.hoofdirect.app.feature.invoice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoofdirect.app.core.database.entity.InvoiceEntity
import com.hoofdirect.app.core.database.entity.InvoiceStatus
import com.hoofdirect.app.designsystem.component.EmptyState
import com.hoofdirect.app.designsystem.component.LoadingIndicator
import com.hoofdirect.app.designsystem.theme.HdStatusCancelled
import com.hoofdirect.app.designsystem.theme.HdStatusCompleted
import com.hoofdirect.app.designsystem.theme.HdStatusConfirmed
import com.hoofdirect.app.designsystem.theme.HdStatusNoShow
import com.hoofdirect.app.designsystem.theme.HdStatusScheduled
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    viewModel: InvoiceListViewModel = hiltViewModel(),
    onNavigateToInvoice: (String) -> Unit = {},
    onNavigateToNewInvoice: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoices") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewInvoice) {
                Icon(Icons.Default.Add, contentDescription = "New invoice")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == "DRAFT",
                    onClick = { viewModel.setFilter("DRAFT") },
                    label = { Text("Draft") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == "SENT",
                    onClick = { viewModel.setFilter("SENT") },
                    label = { Text("Sent") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == "PAID",
                    onClick = { viewModel.setFilter("PAID") },
                    label = { Text("Paid") }
                )
            }

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.invoices.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Receipt,
                        title = "No invoices",
                        message = "Create an invoice after completing an appointment",
                        actionLabel = "Create Invoice",
                        onAction = onNavigateToNewInvoice
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.invoices) { invoice ->
                            InvoiceCard(
                                invoice = invoice,
                                clientName = uiState.clientNames[invoice.clientId] ?: "Unknown",
                                onClick = { onNavigateToInvoice(invoice.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceCard(
    invoice: InvoiceEntity,
    clientName: String,
    onClick: () -> Unit
) {
    val statusColor = when (invoice.status) {
        "DRAFT" -> HdStatusCompleted
        "SENT" -> HdStatusScheduled
        "PAID" -> HdStatusConfirmed
        "OVERDUE" -> HdStatusNoShow
        "CANCELLED" -> HdStatusCancelled
        else -> HdStatusCompleted
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${invoice.invoiceNumber}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = invoice.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = clientName,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = invoice.invoiceDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "$${invoice.total}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
