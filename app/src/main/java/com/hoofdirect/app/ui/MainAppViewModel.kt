package com.hoofdirect.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.network.NetworkMonitor
import com.hoofdirect.app.core.sync.SyncQueueManager
import com.hoofdirect.app.core.sync.SyncWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainAppUiState(
    val isOnline: Boolean = true,
    val pendingChanges: Int = 0,
    val isSyncing: Boolean = false
)

@HiltViewModel
class MainAppViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val syncQueueManager: SyncQueueManager,
    private val syncWorkScheduler: SyncWorkScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainAppUiState())
    val uiState: StateFlow<MainAppUiState> = _uiState.asStateFlow()

    init {
        observeNetworkState()
        observePendingChanges()
        scheduleSyncWork()
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }

                // Trigger sync when coming back online
                if (isOnline && _uiState.value.pendingChanges > 0) {
                    syncWorkScheduler.triggerImmediateSync()
                }
            }
        }
    }

    private fun observePendingChanges() {
        viewModelScope.launch {
            syncQueueManager.observePendingCount().collect { count ->
                _uiState.update { it.copy(pendingChanges = count) }
            }
        }
    }

    private fun scheduleSyncWork() {
        syncWorkScheduler.schedulePeriodicSync()
    }

    fun triggerSync() {
        syncWorkScheduler.triggerImmediateSync()
    }
}
