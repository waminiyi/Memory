package com.memory.sotopatrick.ui.presentation.matching

import com.memory.sotopatrick.domain.events.PlayerIdentity

sealed class MatchUiState {
    object Connecting : MatchUiState()
    object Connected : MatchUiState()
    object WaitingForOpponent : MatchUiState()
    object Disconnected : MatchUiState()
    data class Ready(val players: List<PlayerIdentity>) : MatchUiState()
    data class Error(val message: String) : MatchUiState()
}