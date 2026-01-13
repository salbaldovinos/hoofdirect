package com.hoofdirect.app.feature.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoofdirect.app.core.database.dao.HorseDao
import com.hoofdirect.app.core.database.entity.ClientEntity
import com.hoofdirect.app.feature.auth.data.TokenManager
import com.hoofdirect.app.feature.client.data.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientListUiState(
    val clients: List<ClientEntity> = emptyList(),
    val horseCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ClientListViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val horseDao: HorseDao,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientListUiState())
    val uiState: StateFlow<ClientListUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")

    init {
        loadClients()
        observeSearchQuery()
    }

    private fun loadClients() {
        val userId = tokenManager.getUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            clientRepository.getClients(userId).collect { clients ->
                // Load horse counts for each client
                val horseCounts = mutableMapOf<String, Int>()
                clients.forEach { client ->
                    val count = horseDao.getHorseCountForClient(client.id)
                    horseCounts[client.id] = count
                }

                _uiState.update {
                    it.copy(
                        clients = clients,
                        horseCounts = horseCounts,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeSearchQuery() {
        val userId = tokenManager.getUserId() ?: return

        viewModelScope.launch {
            searchQueryFlow
                .debounce(300)
                .collect { query ->
                    if (query.isBlank()) {
                        loadClients()
                    } else {
                        clientRepository.searchClients(userId, query).collect { clients ->
                            val horseCounts = mutableMapOf<String, Int>()
                            clients.forEach { client ->
                                val count = horseDao.getHorseCountForClient(client.id)
                                horseCounts[client.id] = count
                            }

                            _uiState.update {
                                it.copy(
                                    clients = clients,
                                    horseCounts = horseCounts
                                )
                            }
                        }
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }
}
