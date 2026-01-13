package com.hoofdirect.app.feature.horse.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.entity.HorseEntity
import com.hoofdirect.app.core.database.entity.HorseTemperament
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.horse.data.HorseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HorseFormState(
    val name: String = "",
    val breed: String = "",
    val color: String = "",
    val age: String = "",
    val temperament: String? = null,
    val defaultServiceType: String? = null,
    val useDefaultCycle: Boolean = true,
    val shoeingCycleWeeks: Int = 6,
    val medicalNotes: String = "",
    // Validation errors
    val nameError: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank()
}

data class HorseFormUiState(
    val formState: HorseFormState = HorseFormState(),
    val isEditMode: Boolean = false,
    val horseId: String? = null,
    val clientId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val serviceTypes: List<String> = listOf("Trim", "Front Shoes", "Full Set", "Corrective"),
    val temperaments: List<HorseTemperament> = HorseTemperament.entries
)

@HiltViewModel
class HorseFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val horseRepository: HorseRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val horseId: String? = savedStateHandle.get<String>("horseId")?.takeIf { it.isNotBlank() }
    private val clientIdParam: String? = savedStateHandle.get<String>("clientId")?.takeIf { it.isNotBlank() }
    private val isEditMode = horseId != null

    private val _uiState = MutableStateFlow(
        HorseFormUiState(
            isEditMode = isEditMode,
            horseId = horseId,
            clientId = clientIdParam
        )
    )
    val uiState: StateFlow<HorseFormUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode && horseId != null) {
            loadHorse(horseId)
        }
    }

    private fun loadHorse(horseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            horseRepository.getHorseById(horseId).collect { horse ->
                if (horse != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clientId = horse.clientId,
                            formState = HorseFormState(
                                name = horse.name,
                                breed = horse.breed ?: "",
                                color = horse.color ?: "",
                                age = horse.age?.toString() ?: "",
                                temperament = horse.temperament,
                                defaultServiceType = horse.defaultServiceType,
                                useDefaultCycle = horse.shoeingCycleWeeks == null,
                                shoeingCycleWeeks = horse.shoeingCycleWeeks ?: 6,
                                medicalNotes = horse.medicalNotes ?: ""
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Horse not found") }
                }
            }
        }
    }

    fun updateName(value: String) {
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    name = value,
                    nameError = if (value.isBlank()) "Name is required" else null
                )
            )
        }
    }

    fun updateBreed(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(breed = value))
        }
    }

    fun updateColor(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(color = value))
        }
    }

    fun updateAge(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update {
                it.copy(formState = it.formState.copy(age = value))
            }
        }
    }

    fun updateTemperament(value: String?) {
        _uiState.update {
            it.copy(formState = it.formState.copy(temperament = value))
        }
    }

    fun updateDefaultServiceType(value: String?) {
        _uiState.update {
            it.copy(formState = it.formState.copy(defaultServiceType = value))
        }
    }

    fun updateUseDefaultCycle(value: Boolean) {
        _uiState.update {
            it.copy(formState = it.formState.copy(useDefaultCycle = value))
        }
    }

    fun updateShoeingCycleWeeks(value: Int) {
        _uiState.update {
            it.copy(formState = it.formState.copy(shoeingCycleWeeks = value.coerceIn(4, 16)))
        }
    }

    fun updateMedicalNotes(value: String) {
        _uiState.update {
            it.copy(formState = it.formState.copy(medicalNotes = value.take(2000)))
        }
    }

    fun validateAndSave() {
        val formState = _uiState.value.formState

        // Validate
        val nameError = if (formState.name.isBlank()) "Name is required" else null

        _uiState.update {
            it.copy(
                formState = it.formState.copy(nameError = nameError)
            )
        }

        if (nameError != null) {
            return
        }

        saveHorse()
    }

    private fun saveHorse() {
        val userId = tokenManager.getUserId()
        if (userId == null) {
            _uiState.update { it.copy(error = "Session expired. Please sign in again.") }
            return
        }
        val clientId = _uiState.value.clientId
        if (clientId == null) {
            _uiState.update { it.copy(error = "No client selected. Please try again.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val formState = _uiState.value.formState
            val horse = HorseEntity(
                id = horseId ?: UUID.randomUUID().toString(),
                userId = userId,
                clientId = clientId,
                name = formState.name,
                breed = formState.breed.ifBlank { null },
                color = formState.color.ifBlank { null },
                age = formState.age.toIntOrNull(),
                temperament = formState.temperament,
                defaultServiceType = formState.defaultServiceType,
                shoeingCycleWeeks = if (formState.useDefaultCycle) null else formState.shoeingCycleWeeks,
                medicalNotes = formState.medicalNotes.ifBlank { null }
            )

            val result = if (isEditMode) {
                horseRepository.updateHorse(horse)
            } else {
                horseRepository.createHorse(horse)
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = e.message ?: "Failed to save horse"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
