package com.hoofdirect.app.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.data.preferences.UserPreferences
import com.hoofdirect.app.core.data.preferences.UserPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val appVersion: String = "1.0.0",
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        preferences = prefs
                    )
                }
            }
        }
    }

    fun setDefaultDuration(minutes: Int) {
        viewModelScope.launch {
            preferencesManager.setDefaultDuration(minutes)
        }
    }

    fun setDefaultCycle(weeks: Int) {
        viewModelScope.launch {
            preferencesManager.setDefaultCycle(weeks)
        }
    }

    fun setReminderDays(days: Int) {
        viewModelScope.launch {
            preferencesManager.setReminderDays(days)
        }
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPushNotificationsEnabled(enabled)
        }
    }

    fun setDailyDigestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDailyDigestEnabled(enabled)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
