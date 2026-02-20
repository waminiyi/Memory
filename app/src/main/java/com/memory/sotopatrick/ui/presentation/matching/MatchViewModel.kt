package com.memory.sotopatrick.ui.presentation.matching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memory.sotopatrick.domain.discovery.NearbyUser
import com.memory.sotopatrick.domain.matching.MatchingStatus
import com.memory.sotopatrick.domain.matching.PlayerMatchingService
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.UserProvider
import com.memory.sotopatrick.ui.presentation.mappers.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchSessionState(
    val isHost: Boolean = false,
    val messenger: MemoMessenger? = null
)

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val matchingService: PlayerMatchingService,
    private val userProvider: UserProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchUiState>(MatchUiState.Disconnected)
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow(MatchSessionState())
    val sessionState: StateFlow<MatchSessionState> = _sessionState.asStateFlow()

    private data class MatchRequest(
        val isHost: Boolean,
        val host: NearbyUser?
    )

    private var activeRequest: MatchRequest? = null
    private var observeJob: Job? = null

    fun init(isHost: Boolean, host: NearbyUser? = null) {
        val request = MatchRequest(isHost = isHost, host = host)
        if (request == activeRequest) return

        activeRequest = request
        _sessionState.value = MatchSessionState(isHost = request.isHost, messenger = null)
        _uiState.value = MatchUiState.Connecting
        observeConnectionStatusIfNeeded()
        startMatch(request)
    }

    private fun observeConnectionStatusIfNeeded() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            matchingService.connectionStatus.collect { status ->
                _uiState.value = status.toUiState(hasActiveRequest = activeRequest != null)
            }
        }
    }

    private fun startMatch(request: MatchRequest) {
        viewModelScope.launch {
            val localPlayer = userProvider.identity
            if (request.isHost) {
                matchingService.hostMatch(localPlayer)
                    .onSuccess {
                        _sessionState.value = _sessionState.value.copy(messenger = it)
                    }
                    .onFailure { _uiState.value = MatchUiState.Error(it.message ?: "Failed") }
            } else {
                val nearbyHost = requireNotNull(request.host) { "Host required for client" }
                matchingService.joinMatch(nearbyHost, localPlayer)
                    .onSuccess {
                        _sessionState.value = _sessionState.value.copy(messenger = it)
                    }
                    .onFailure { _uiState.value = MatchUiState.Error(it.message ?: "Failed") }
            }
        }
    }

    fun onCancel() {
        viewModelScope.launch {
            matchingService.disconnect()
            activeRequest = null
            _sessionState.value = MatchSessionState()
            _uiState.value = MatchUiState.Disconnected
        }
    }

    override fun onCleared() {
        super.onCleared()
        onCancel()
    }
}

internal fun MatchingStatus.toUiState(hasActiveRequest: Boolean): MatchUiState = when (this) {
    is MatchingStatus.Connecting -> MatchUiState.Connecting
    is MatchingStatus.Connected -> MatchUiState.Connected
    is MatchingStatus.WaitingForOpponent -> MatchUiState.WaitingForOpponent
    is MatchingStatus.Ready -> MatchUiState.Ready(players)
    is MatchingStatus.Failed -> MatchUiState.Error(reason.toDisplayMessage())
    is MatchingStatus.Disconnected -> MatchUiState.Disconnected
    is MatchingStatus.Idle -> if (hasActiveRequest) MatchUiState.Connecting else MatchUiState.Disconnected
}
