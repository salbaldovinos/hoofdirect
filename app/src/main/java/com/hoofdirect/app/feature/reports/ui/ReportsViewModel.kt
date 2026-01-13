package com.hoofdirect.app.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.reports.data.DashboardStats
import com.hoofdirect.app.feature.reports.data.ReportPeriod
import com.hoofdirect.app.feature.reports.data.ReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportsUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: ReportPeriod = ReportPeriod.THIS_WEEK,
    val stats: DashboardStats = DashboardStats(),
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ReportsRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val stats = repository.getDashboardStats(userId, _uiState.value.selectedPeriod)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        stats = stats
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load reports"
                    )
                }
            }
        }
    }

    fun selectPeriod(period: ReportPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadStats()
    }

    fun refresh() {
        loadStats()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
