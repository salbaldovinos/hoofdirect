package com.hoofdirect.app.feature.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.AppointmentEntity
import com.hoofdirect.app.feature.appointment.data.AppointmentRepository
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val appointments: List<AppointmentEntity> = emptyList(),
    val weekAppointmentCounts: Map<LocalDate, Int> = emptyMap(),
    val clientNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var appointmentCollectionJob: kotlinx.coroutines.Job? = null

    init {
        loadAppointments()
    }

    private fun loadAppointments() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Session expired. Please sign in again."
                )
            }
            return
        }

        // Cancel any existing collection to prevent duplicate collectors
        appointmentCollectionJob?.cancel()

        appointmentCollectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val selectedDate = _uiState.value.selectedDate
            val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
            val endOfWeek = startOfWeek.plusDays(6)

            // Get all appointments for the week to show indicators
            appointmentRepository.getAppointmentsForDateRange(userId, startOfWeek, endOfWeek)
                .collect { weekAppointments ->
                    // Count appointments per day
                    val appointmentCounts = weekAppointments.groupBy { it.date }
                        .mapValues { it.value.size }

                    // Filter to selected date
                    val todayAppointments = weekAppointments.filter { it.date == selectedDate }

                    // Load client names for the appointments
                    val clientNames = mutableMapOf<String, String>()
                    todayAppointments.forEach { appointment ->
                        if (!clientNames.containsKey(appointment.clientId)) {
                            clientRepository.getClientByIdOnce(appointment.clientId)?.let { client ->
                                clientNames[client.id] = client.name
                            }
                        }
                    }

                    _uiState.update {
                        it.copy(
                            appointments = todayAppointments,
                            weekAppointmentCounts = appointmentCounts,
                            clientNames = clientNames,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    fun refresh() {
        loadAppointments()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadAppointments()
    }

    fun previousWeek() {
        selectDate(_uiState.value.selectedDate.minusWeeks(1))
    }

    fun nextWeek() {
        selectDate(_uiState.value.selectedDate.plusWeeks(1))
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
