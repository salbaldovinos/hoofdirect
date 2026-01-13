package com.hoofdirect.app.feature.horse.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.feature.client.data.ClientRepository
import com.hoofdirect.app.feature.horse.data.HorseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

data class HorseDetailUiState(
    val horse: HorseEntity? = null,
    val client: ClientEntity? = null,
    val dueStatus: DueStatus = DueStatus.NeverServiced,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showArchiveDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val actionResult: HorseActionResult? = null
)

sealed class DueStatus {
    data class Ok(val daysUntil: Int) : DueStatus()
    data class DueSoon(val daysUntil: Int) : DueStatus()
    data object DueToday : DueStatus()
    data class Overdue(val daysOverdue: Int) : DueStatus()
    data object NeverServiced : DueStatus()
}

sealed class HorseActionResult {
    data object HorseArchived : HorseActionResult()
    data object HorseDeleted : HorseActionResult()
    data class Error(val message: String) : HorseActionResult()
}

@HiltViewModel
class HorseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val horseRepository: HorseRepository,
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val horseId: String = savedStateHandle.get<String>("horseId") ?: ""

    private val _uiState = MutableStateFlow(HorseDetailUiState())
    val uiState: StateFlow<HorseDetailUiState> = _uiState.asStateFlow()

    init {
        loadHorseDetails()
    }

    private fun loadHorseDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            horseRepository.getHorseById(horseId).collect { horse ->
                if (horse != null) {
                    // Also fetch the client
                    val client = clientRepository.getClientByIdOnce(horse.clientId)

                    _uiState.update {
                        it.copy(
                            horse = horse,
                            client = client,
                            dueStatus = calculateDueStatus(horse.nextDueDate),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Horse not found")
                    }
                }
            }
        }
    }

    private fun calculateDueStatus(nextDueDate: LocalDate?): DueStatus {
        if (nextDueDate == null) return DueStatus.NeverServiced

        val today = LocalDate.now()
        val daysUntilDue = ChronoUnit.DAYS.between(today, nextDueDate).toInt()

        return when {
            daysUntilDue < 0 -> DueStatus.Overdue(abs(daysUntilDue))
            daysUntilDue == 0 -> DueStatus.DueToday
            daysUntilDue <= 7 -> DueStatus.DueSoon(daysUntilDue)
            else -> DueStatus.Ok(daysUntilDue)
        }
    }

    fun showArchiveDialog() {
        _uiState.update { it.copy(showArchiveDialog = true) }
    }

    fun dismissArchiveDialog() {
        _uiState.update { it.copy(showArchiveDialog = false) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun archiveHorse() {
        viewModelScope.launch {
            _uiState.update { it.copy(showArchiveDialog = false) }

            horseRepository.archiveHorse(horseId)
                .onSuccess {
                    _uiState.update { it.copy(actionResult = HorseActionResult.HorseArchived) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionResult = HorseActionResult.Error(e.message ?: "Failed to archive horse"))
                    }
                }
        }
    }

    fun deleteHorse() {
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteDialog = false) }

            horseRepository.deleteHorse(horseId)
                .onSuccess {
                    _uiState.update { it.copy(actionResult = HorseActionResult.HorseDeleted) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionResult = HorseActionResult.Error(e.message ?: "Failed to delete horse"))
                    }
                }
        }
    }

    fun clearActionResult() {
        _uiState.update { it.copy(actionResult = null) }
    }
}
