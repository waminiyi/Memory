package com.memory.sotopatrick.ui.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memory.sotopatrick.domain.events.SessionConfig
import com.memory.sotopatrick.domain.events.SessionLeft
import com.memory.sotopatrick.domain.game.BoardGenerator
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.GamePlayer
import com.memory.sotopatrick.domain.player.UserProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GameSetupViewModel @AssistedInject constructor(
    @Assisted val isHost: Boolean,
    @Assisted val messenger: MemoMessenger,
    private val userProvider: UserProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(isHost: Boolean, messenger: MemoMessenger): GameSetupViewModel
    }

    private val _uiState = MutableStateFlow<GameSetupUiState>(GameSetupUiState.WaitingForHost)
    val uiState: StateFlow<GameSetupUiState> = _uiState.asStateFlow()

    init {
        if (isHost) {
            _uiState.value = GameSetupUiState.Configuring()
        } else {
            _uiState.value = GameSetupUiState.WaitingForHost
            listenForConfig()
        }
    }

    private fun listenForConfig() {
        viewModelScope.launch {
            try {
                val message = messenger.incomingMessages.first {
                    it is SessionConfig || it is SessionLeft
                }

                when (message) {
                    is SessionConfig -> {
                        _uiState.value =
                            GameSetupUiState.GameReady(message, messenger, userProvider.identity.userId)
                    }

                    is SessionLeft -> {
                        _uiState.value = GameSetupUiState.Error("Host left the session")
                    }

                    else -> Unit
                }
            } catch (_: Exception) {
                _uiState.value = GameSetupUiState.Error("Failed while waiting for host configuration")
            }
        }
    }

    fun selectPreset(preset: GridPreset) {
        val current = _uiState.value
        if (current is GameSetupUiState.Configuring) {
            val canStart = preset.totalCards % 2 == 0
            _uiState.value = current.copy(
                selectedPreset = preset,
                canStart = canStart
            )
        }
    }

    fun onStartGame(gamePlayers: List<GamePlayer>) {
        val current = _uiState.value as? GameSetupUiState.Configuring ?: return

        viewModelScope.launch {
            val rows = current.selectedPreset.rows
            val cols = current.selectedPreset.columns
            val symbols = BoardGenerator.generateShuffledSymbols(rows * cols)
            val config = SessionConfig(
                userId = userProvider.identity.userId,
                rows = rows,
                cols = cols,
                shuffledCardSymbols = symbols,
                gamePlayers = gamePlayers
            )

            // Notify Peer & signal ready locally
            messenger.sendMessage(config)
            _uiState.value = GameSetupUiState.GameReady(config, messenger, userProvider.identity.userId)
        }
    }
}
