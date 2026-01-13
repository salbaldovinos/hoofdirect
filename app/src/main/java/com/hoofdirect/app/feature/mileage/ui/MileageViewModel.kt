package com.hoofdirect.app.feature.mileage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.MileageLogEntity
import com.hoofdirect.app.core.database.entity.MileagePurpose
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.mileage.data.MileageRepository
import com.hoofdirect.app.feature.mileage.data.MileageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import javax.inject.Inject

data class MileageUiState(
    val isLoading: Boolean = true,
    val selectedYear: Int = Year.now().value,
    val summary: MileageSummary? = null,
    val trips: List<MileageLogEntity> = emptyList(),
    val showAddSheet: Boolean = false,
    val editingTrip: MileageLogEntity? = null,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MileageViewModel @Inject constructor(
    private val repository: MileageRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MileageUiState())
    val uiState: StateFlow<MileageUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val year = _uiState.value.selectedYear
                val summary = repository.getAnnualSummary(userId, year)

                val startDate = LocalDate.of(year, 1, 1)
                val endDate = LocalDate.of(year, 12, 31)

                repository.getTripsForDateRange(userId, startDate, endDate)
                    .collect { trips ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                summary = summary,
                                trips = trips
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load mileage data"
                    )
                }
            }
        }
    }

    fun selectYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
        loadData()
    }

    fun showAddSheet() {
        _uiState.update { it.copy(showAddSheet = true) }
    }

    fun dismissAddSheet() {
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun editTrip(trip: MileageLogEntity) {
        _uiState.update { it.copy(editingTrip = trip) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editingTrip = null) }
    }

    fun saveTrip(
        date: LocalDate,
        startAddress: String?,
        endAddress: String?,
        miles: Double,
        purpose: MileagePurpose,
        notes: String?
    ) {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(error = "Not logged in") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                val existingTrip = _uiState.value.editingTrip

                if (existingTrip != null) {
                    // Update existing trip
                    val updated = existingTrip.copy(
                        date = date,
                        startAddress = startAddress,
                        endAddress = endAddress,
                        miles = miles,
                        purpose = purpose.name,
                        notes = notes
                    )
                    repository.updateTrip(updated)
                } else {
                    // Create new trip
                    val newTrip = MileageLogEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        date = date,
                        startAddress = startAddress,
                        endAddress = endAddress,
                        miles = miles,
                        purpose = purpose.name,
                        notes = notes
                    )
                    repository.saveTrip(newTrip)
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showAddSheet = false,
                        editingTrip = null
                    )
                }

                // Reload to get updated summary
                loadData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save trip"
                    )
                }
            }
        }
    }

    fun deleteTrip(trip: MileageLogEntity) {
        viewModelScope.launch {
            try {
                repository.deleteTrip(trip.id)
                _uiState.update { it.copy(editingTrip = null) }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete trip") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
