package com.hoofdirect.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hoofdirect.app.designsystem.component.OfflineBanner
import com.hoofdirect.app.feature.appointment.ui.AppointmentDetailScreen
import com.hoofdirect.app.feature.appointment.ui.AppointmentFormScreen
import com.hoofdirect.app.feature.client.ui.ClientDetailScreen
import com.hoofdirect.app.feature.invoice.ui.InvoiceDetailScreen
import com.hoofdirect.app.feature.invoice.ui.InvoiceFormScreen
import com.hoofdirect.app.feature.client.ui.ClientFormScreen
import com.hoofdirect.app.feature.client.ui.ClientListScreen
import com.hoofdirect.app.feature.horse.ui.HorseDetailScreen
import com.hoofdirect.app.feature.horse.ui.HorseFormScreen
import com.hoofdirect.app.feature.invoice.ui.InvoiceListScreen
import com.hoofdirect.app.feature.mileage.ui.MileageScreen
import com.hoofdirect.app.feature.more.ui.MoreScreen
import com.hoofdirect.app.feature.pricing.ui.ServicePricesScreen
import com.hoofdirect.app.feature.reports.ui.ReportsScreen
import com.hoofdirect.app.feature.settings.ui.SettingsScreen
import com.hoofdirect.app.feature.profile.ui.ProfileScreen
import com.hoofdirect.app.feature.route.ui.RouteScreen
import com.hoofdirect.app.feature.schedule.ui.ScheduleScreen
import com.hoofdirect.app.feature.subscription.ui.SubscriptionScreen

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Schedule : BottomNavItem(
        route = "schedule",
        title = "Schedule",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    )

    data object Clients : BottomNavItem(
        route = "clients",
        title = "Clients",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    )

    data object Routes : BottomNavItem(
        route = "routes",
        title = "Routes",
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route
    )

    data object Invoices : BottomNavItem(
        route = "invoices",
        title = "Invoices",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    )

    data object More : BottomNavItem(
        route = "more",
        title = "More",
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz
    )
}

// Internal routes for detail/form screens
object AppRoutes {
    const val CLIENT_DETAIL = "client/{clientId}"
    const val CLIENT_FORM = "client/form?clientId={clientId}"
    const val HORSE_DETAIL = "horse/{horseId}"
    const val HORSE_FORM = "horse/form?clientId={clientId}&horseId={horseId}"
    const val APPOINTMENT_DETAIL = "appointment/{appointmentId}"
    const val APPOINTMENT_FORM = "appointment/form?appointmentId={appointmentId}&clientId={clientId}"
    const val INVOICE_DETAIL = "invoice/{invoiceId}"
    const val INVOICE_FORM = "invoice/form?invoiceId={invoiceId}"

    // More screen routes
    const val PROFILE = "more/profile"
    const val SETTINGS = "more/settings"
    const val MILEAGE = "more/mileage"
    const val REPORTS = "more/reports"
    const val PRICING = "more/pricing"
    const val SUBSCRIPTION = "more/subscription"
    const val HELP = "more/help"
}

@Composable
fun MainAppShell(
    viewModel: MainAppViewModel = hiltViewModel(),
    onSignOut: () -> Unit = {}
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    val navItems = listOf(
        BottomNavItem.Schedule,
        BottomNavItem.Clients,
        BottomNavItem.Routes,
        BottomNavItem.Invoices,
        BottomNavItem.More
    )

    // Track if we're on a bottom nav route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnBottomNavRoute = navItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isOnBottomNavRoute) {
                NavigationBar {
                    navItems.forEach { item ->
                        val selected = currentRoute == item.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineBanner(
                isOffline = !uiState.isOnline,
                pendingChanges = uiState.pendingChanges
            )

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Schedule.route
                ) {
                    // Bottom nav tabs
                    composable(BottomNavItem.Schedule.route) {
                        ScheduleScreen(
                            onNavigateToAppointment = { appointmentId ->
                                navController.navigate("appointment/$appointmentId")
                            },
                            onNavigateToNewAppointment = {
                                navController.navigate("appointment/form?appointmentId=&clientId=")
                            }
                        )
                    }

                    composable(BottomNavItem.Clients.route) {
                        ClientListScreen(
                            onNavigateToClient = { clientId ->
                                navController.navigate("client/$clientId")
                            },
                            onNavigateToNewClient = {
                                navController.navigate("client/form?clientId=")
                            }
                        )
                    }

                    composable(BottomNavItem.Routes.route) {
                        RouteScreen()
                    }

                    composable(BottomNavItem.Invoices.route) {
                        InvoiceListScreen(
                            onNavigateToInvoice = { invoiceId ->
                                navController.navigate("invoice/$invoiceId")
                            },
                            onNavigateToNewInvoice = {
                                navController.navigate("invoice/form?invoiceId=")
                            }
                        )
                    }

                    composable(BottomNavItem.More.route) {
                        MoreScreen(
                            onNavigateToProfile = { navController.navigate(AppRoutes.PROFILE) },
                            onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                            onNavigateToMileage = { navController.navigate(AppRoutes.MILEAGE) },
                            onNavigateToReports = { navController.navigate(AppRoutes.REPORTS) },
                            onNavigateToPricing = { navController.navigate(AppRoutes.PRICING) },
                            onNavigateToSubscription = { navController.navigate(AppRoutes.SUBSCRIPTION) },
                            onNavigateToHelp = { navController.navigate(AppRoutes.HELP) },
                            onSignOut = onSignOut
                        )
                    }

                    // Client routes
                    composable(AppRoutes.CLIENT_DETAIL) { backStackEntry ->
                        val clientId = backStackEntry.arguments?.getString("clientId") ?: ""
                        ClientDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEdit = { id ->
                                navController.navigate("client/form?clientId=$id")
                            },
                            onNavigateToHorse = { horseId ->
                                navController.navigate("horse/$horseId")
                            },
                            onNavigateToAddHorse = { id ->
                                navController.navigate("horse/form?clientId=$id&horseId=")
                            },
                            onNavigateToAppointment = { appointmentId ->
                                navController.navigate("appointment/$appointmentId")
                            },
                            onNavigateToNewAppointment = { id ->
                                navController.navigate("appointment/form?appointmentId=&clientId=$id")
                            },
                            onClientDeleted = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.CLIENT_FORM) {
                        ClientFormScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveSuccess = { navController.popBackStack() }
                        )
                    }

                    // Horse routes
                    composable(AppRoutes.HORSE_DETAIL) { backStackEntry ->
                        val horseId = backStackEntry.arguments?.getString("horseId") ?: ""
                        HorseDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEdit = { id ->
                                // Need clientId from somewhere - could get from ViewModel
                                navController.navigate("horse/form?clientId=&horseId=$id")
                            },
                            onNavigateToClient = { clientId ->
                                navController.navigate("client/$clientId")
                            },
                            onNavigateToNewAppointment = { id ->
                                navController.navigate("appointment/form?appointmentId=&clientId=")
                            },
                            onHorseDeleted = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.HORSE_FORM) {
                        HorseFormScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveSuccess = { navController.popBackStack() }
                        )
                    }

                    // Appointment routes
                    composable(AppRoutes.APPOINTMENT_DETAIL) { backStackEntry ->
                        val appointmentId = backStackEntry.arguments?.getString("appointmentId") ?: ""
                        AppointmentDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEdit = { id ->
                                navController.navigate("appointment/form?appointmentId=$id&clientId=")
                            },
                            onNavigateToClient = { clientId ->
                                navController.navigate("client/$clientId")
                            },
                            onAppointmentDeleted = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.APPOINTMENT_FORM) {
                        AppointmentFormScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveSuccess = { navController.popBackStack() }
                        )
                    }

                    // Invoice routes
                    composable(AppRoutes.INVOICE_DETAIL) { backStackEntry ->
                        val invoiceId = backStackEntry.arguments?.getString("invoiceId") ?: ""
                        InvoiceDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEdit = { id ->
                                navController.navigate("invoice/form?invoiceId=$id")
                            },
                            onNavigateToClient = { clientId ->
                                navController.navigate("client/$clientId")
                            },
                            onInvoiceDeleted = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.INVOICE_FORM) {
                        InvoiceFormScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveSuccess = { navController.popBackStack() }
                        )
                    }

                    // More screen sub-routes
                    composable(AppRoutes.PROFILE) {
                        ProfileScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.MILEAGE) {
                        MileageScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.REPORTS) {
                        ReportsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.PRICING) {
                        ServicePricesScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.SUBSCRIPTION) {
                        SubscriptionScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.HELP) {
                        PlaceholderScreen(
                            title = "Help & FAQ",
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = "$title - Coming Soon",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
        }
    }
}
