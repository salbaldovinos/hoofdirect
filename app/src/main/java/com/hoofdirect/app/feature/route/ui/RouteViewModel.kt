package com.hoofdirect.app.feature.route.ui

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.route.NavigationHelper
import com.hoofdirect.app.core.route.model.OptimizedRoute
import com.hoofdirect.app.core.route.model.RouteException
import com.hoofdirect.app.core.route.model.RoutePlan
import com.hoofdirect.app.core.route.model.RouteSavings
import com.hoofdirect.app.core.route.model.RouteStats
import com.hoofdirect.app.core.route.model.RouteStop
import com.hoofdirect.app.core.subscription.model.LimitCheckResult
import com.hoofdirect.app.core.subscription.model.TierLimits
import com.hoofdirect.app.core.subscription.UsageLimitsManager
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.route.data.RoutePlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * UI State for the Route Screen.
 */
data class RouteUiState(
    val date: LocalDate = LocalDate.now(),
    val routeState: RouteState = RouteState.Loading,
    val appointmentCount: Int = 0,
    val waypointCount: Int = 0, // Appointments with valid location data
    val lockedStops: Map<String, Int> = emptyMap(),
    val isRouteMode: Boolean = false,
    val currentStopIndex: Int = 0,
    val tierLimitInfo: TierLimitInfo? = null
)

/**
 * State of the route optimization.
 */
sealed interface RouteState {
    data object Loading : RouteState
    data object NoAppointments : RouteState
    data class NoLocationData(val appointmentCount: Int) : RouteState // Appointments exist but no locations
    data object NotOptimized : RouteState
    data object Optimizing : RouteState
    data class Optimized(val route: OptimizedRoute) : RouteState
    data class Error(val message: String) : RouteState
}

/**
 * Tier limit information for display.
 */
data class TierLimitInfo(
    val currentTier: String,
    val maxStops: Int,
    val isUnlimited: Boolean,
    val canOptimize: Boolean
)

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routePlanRepository: RoutePlanRepository,
    private val usageLimitsManager: UsageLimitsManager,
    private val tokenManager: TokenManager,
    private val navigationHelper: NavigationHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private val userId: String?
        get() = tokenManager.getUserId()

    init {
        // Check if date was passed in navigation
        val dateStr = savedStateHandle.get<String>("date")
        if (dateStr != null) {
            try {
                val date = LocalDate.parse(dateStr)
                _uiState.update { it.copy(date = date) }
            } catch (e: Exception) {
                // Use today's date
            }
        }
        loadRoute()
        loadTierInfo()
    }

    /**
     * Refresh route data - call when screen becomes visible.
     */
    fun refresh() {
        loadRoute()
    }

    /**
     * Load cached route or check for appointments.
     */
    private fun loadRoute() {
        viewModelScope.launch {
            val currentUserId = userId ?: return@launch
            val date = _uiState.value.date

            _uiState.update { it.copy(routeState = RouteState.Loading) }

            // Get total appointment count and waypoints with valid locations
            val totalAppointments = routePlanRepository.getAppointmentCountForDate(currentUserId, date)
            val waypoints = routePlanRepository.getWaypointsForDate(currentUserId, date)

            _uiState.update {
                it.copy(
                    appointmentCount = totalAppointments,
                    waypointCount = waypoints.size
                )
            }

            if (totalAppointments == 0) {
                _uiState.update { it.copy(routeState = RouteState.NoAppointments) }
                return@launch
            }

            if (waypoints.isEmpty()) {
                // Appointments exist but none have location data
                _uiState.update { it.copy(routeState = RouteState.NoLocationData(totalAppointments)) }
                return@launch
            }

            // Check for cached route
            val cachedRoute = routePlanRepository.getRouteForDateOnce(currentUserId, date)
            if (cachedRoute != null) {
                _uiState.update {
                    it.copy(
                        routeState = RouteState.Optimized(
                            OptimizedRoute(
                                plan = cachedRoute,
                                originalStats = RouteStats(
                                    totalMiles = cachedRoute.originalDistanceMiles ?: cachedRoute.totalDistanceMiles,
                                    totalMinutes = cachedRoute.originalDriveMinutes ?: cachedRoute.totalDriveMinutes
                                ),
                                savings = RouteSavings(
                                    milesSaved = (cachedRoute.originalDistanceMiles ?: cachedRoute.totalDistanceMiles) - cachedRoute.totalDistanceMiles,
                                    minutesSaved = (cachedRoute.originalDriveMinutes ?: cachedRoute.totalDriveMinutes) - cachedRoute.totalDriveMinutes
                                )
                            )
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(routeState = RouteState.NotOptimized) }
            }
        }
    }

    /**
     * Load tier limit information.
     */
    private fun loadTierInfo() {
        viewModelScope.launch {
            val usageSummary = usageLimitsManager.getUsageSummary()
            val tierLimits = TierLimits.forTier(usageSummary.tier)
            val maxStops = tierLimits.maxRouteStopsPerDay

            _uiState.update {
                it.copy(
                    tierLimitInfo = TierLimitInfo(
                        currentTier = usageSummary.tier.displayName,
                        maxStops = maxStops,
                        isUnlimited = maxStops == Int.MAX_VALUE,
                        canOptimize = maxStops > 0
                    )
                )
            }
        }
    }

    /**
     * Change the date and reload route.
     */
    fun setDate(date: LocalDate) {
        _uiState.update { it.copy(date = date, lockedStops = emptyMap()) }
        loadRoute()
    }

    /**
     * Navigate to previous day.
     */
    fun previousDay() {
        setDate(_uiState.value.date.minusDays(1))
    }

    /**
     * Navigate to next day.
     */
    fun nextDay() {
        setDate(_uiState.value.date.plusDays(1))
    }

    /**
     * Navigate to today.
     */
    fun goToToday() {
        setDate(LocalDate.now())
    }

    /**
     * Optimize the route for the current date.
     */
    fun optimizeRoute() {
        viewModelScope.launch {
            val currentUserId = userId ?: return@launch
            val date = _uiState.value.date
            val lockedStops = _uiState.value.lockedStops

            // Check tier limits first
            val appointmentCount = _uiState.value.appointmentCount
            val limitCheck = usageLimitsManager.canUseRouteOptimization(appointmentCount)

            when (limitCheck) {
                is LimitCheckResult.FeatureNotAvailable -> {
                    _uiState.update {
                        it.copy(routeState = RouteState.Error("Route optimization requires a paid subscription. Upgrade to Solo or higher."))
                    }
                    return@launch
                }
                is LimitCheckResult.Blocked -> {
                    _uiState.update {
                        it.copy(routeState = RouteState.Error("Your ${_uiState.value.tierLimitInfo?.currentTier ?: "plan"} allows ${limitCheck.limit} stops, but you have $appointmentCount appointments. Upgrade for more."))
                    }
                    return@launch
                }
                else -> { /* Proceed */ }
            }

            _uiState.update { it.copy(routeState = RouteState.Optimizing) }

            val result = routePlanRepository.optimizeRoute(
                userId = currentUserId,
                date = date,
                lockedStops = lockedStops
            )

            result.fold(
                onSuccess = { optimizedRoute ->
                    _uiState.update {
                        it.copy(routeState = RouteState.Optimized(optimizedRoute))
                    }
                },
                onFailure = { exception ->
                    val message = when (exception) {
                        is RouteException.FeatureNotAvailable -> "Route optimization requires a paid subscription"
                        is RouteException.TierLimitExceeded -> "Your plan allows ${exception.allowed} stops, but you have ${exception.requested}"
                        is RouteException.NoValidLocations -> "No appointments with valid addresses found"
                        is RouteException.NoHomeLocation -> "Please set your home location in Settings first"
                        is RouteException.NetworkError -> "Route optimization requires internet connection"
                        is RouteException.OptimizationFailed -> exception.message ?: "Optimization failed"
                        else -> exception.message ?: "An error occurred"
                    }
                    _uiState.update {
                        it.copy(routeState = RouteState.Error(message))
                    }
                }
            )
        }
    }

    /**
     * Toggle lock on a stop.
     */
    fun toggleLock(appointmentId: String) {
        val currentState = _uiState.value.routeState
        if (currentState !is RouteState.Optimized) return

        val stop = currentState.route.plan.stops.find { it.appointmentId == appointmentId } ?: return

        val currentLocks = _uiState.value.lockedStops.toMutableMap()

        if (currentLocks.containsKey(appointmentId)) {
            currentLocks.remove(appointmentId)
        } else {
            currentLocks[appointmentId] = stop.order
        }

        _uiState.update { it.copy(lockedStops = currentLocks) }
    }

    /**
     * Reorder a stop manually.
     */
    fun reorderStop(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value.routeState
            if (currentState !is RouteState.Optimized) return@launch

            val stops = currentState.route.plan.stops.toMutableList()
            if (fromIndex < 0 || fromIndex >= stops.size || toIndex < 0 || toIndex >= stops.size) return@launch

            // Move the stop
            val movedStop = stops.removeAt(fromIndex)
            stops.add(toIndex, movedStop)

            // Renumber stops
            val renumberedStops = stops.mapIndexed { index, stop ->
                stop.copy(order = index + 1)
            }

            // Update in database
            routePlanRepository.updateRouteStops(
                routeId = currentState.route.plan.id,
                stops = renumberedStops,
                isManuallyReordered = true
            )

            // Update UI state
            val updatedPlan = currentState.route.plan.copy(
                stops = renumberedStops,
                isManuallyReordered = true
            )
            val updatedRoute = currentState.route.copy(plan = updatedPlan)

            _uiState.update {
                it.copy(routeState = RouteState.Optimized(updatedRoute))
            }
        }
    }

    /**
     * Reset to optimized order (re-optimize).
     */
    fun resetToOptimized() {
        _uiState.update { it.copy(lockedStops = emptyMap()) }
        optimizeRoute()
    }

    /**
     * Start route navigation mode.
     */
    fun startRouteMode() {
        _uiState.update {
            it.copy(isRouteMode = true, currentStopIndex = 0)
        }
    }

    /**
     * Exit route navigation mode.
     */
    fun exitRouteMode() {
        _uiState.update {
            it.copy(isRouteMode = false, currentStopIndex = 0)
        }
    }

    /**
     * Mark current stop as complete and advance.
     */
    fun completeCurrentStop() {
        viewModelScope.launch {
            val currentState = _uiState.value.routeState
            if (currentState !is RouteState.Optimized) return@launch

            val currentIndex = _uiState.value.currentStopIndex
            val stops = currentState.route.plan.stops.toMutableList()

            if (currentIndex < stops.size) {
                // Mark as completed
                stops[currentIndex] = stops[currentIndex].copy(isCompleted = true)

                // Update in database
                routePlanRepository.updateRouteStops(
                    routeId = currentState.route.plan.id,
                    stops = stops,
                    isManuallyReordered = currentState.route.plan.isManuallyReordered
                )

                // Update UI state
                val updatedPlan = currentState.route.plan.copy(stops = stops)
                val updatedRoute = currentState.route.copy(plan = updatedPlan)

                val newIndex = if (currentIndex < stops.size - 1) currentIndex + 1 else currentIndex
                val isComplete = currentIndex >= stops.size - 1

                _uiState.update {
                    it.copy(
                        routeState = RouteState.Optimized(updatedRoute),
                        currentStopIndex = newIndex,
                        isRouteMode = !isComplete
                    )
                }
            }
        }
    }

    /**
     * Get formatted date string.
     */
    fun getFormattedDate(): String {
        val date = _uiState.value.date
        val today = LocalDate.now()

        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }
    }

    /**
     * Get the current stop in route mode.
     */
    fun getCurrentStop(): RouteStop? {
        val currentState = _uiState.value.routeState
        if (currentState !is RouteState.Optimized) return null

        val index = _uiState.value.currentStopIndex
        return currentState.route.plan.stops.getOrNull(index)
    }

    /**
     * Get remaining stops after current.
     */
    fun getRemainingStops(): List<RouteStop> {
        val currentState = _uiState.value.routeState
        if (currentState !is RouteState.Optimized) return emptyList()

        val index = _uiState.value.currentStopIndex
        return currentState.route.plan.stops.drop(index + 1)
    }

    /**
     * Get navigation intent for full route.
     */
    fun getRouteNavigationIntent(): Intent? {
        val currentState = _uiState.value.routeState
        if (currentState !is RouteState.Optimized) return null

        return navigationHelper.startRouteNavigation(currentState.route.plan)
    }

    /**
     * Get navigation intent for next stop.
     */
    fun getNextStopNavigationIntent(): Intent? {
        val currentStop = getCurrentStop() ?: return null
        return navigationHelper.navigateToStop(currentStop)
    }

    /**
     * Get navigation intent for a specific stop.
     */
    fun getStopNavigationIntent(stop: RouteStop): Intent {
        return navigationHelper.navigateToStop(stop)
    }

    /**
     * Get available navigation apps.
     */
    fun getAvailableNavigationApps(): List<NavigationHelper.NavigationApp> {
        return navigationHelper.getAvailableApps()
    }
}
