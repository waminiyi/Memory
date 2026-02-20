package com.memory.sotopatrick.ui.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memory.sotopatrick.domain.result.OperationResult
import com.memory.sotopatrick.domain.game.GameSessionService
import com.memory.sotopatrick.domain.card.CardId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class GameSessionViewModel(
    private val gameSessionService: GameSessionService,
    private val onDisconnectAction: () -> Unit,
    private val onReplayAction: () -> Unit,
    private val onClearedAction: (() -> Unit)? = null
) : ViewModel() {

    private val actionMutex = Mutex()
    private val terminationTriggered = AtomicBoolean(false)
    private val replayTriggered = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(
        GameSessionUiState(gameState = gameSessionService.gameState.value)
    )
    val uiState: StateFlow<GameSessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var previousState = gameSessionService.gameState.value
            gameSessionService.gameState.collect { state ->
                val shouldResetHistory = previousState?.status == com.memory.sotopatrick.domain.game.GameStatus.FINISHED &&
                    state != null &&
                    state.status == com.memory.sotopatrick.domain.game.GameStatus.IN_PROGRESS &&
                    state.version.value == 1L
                _uiState.update {
                    if (shouldResetHistory) {
                        it.copy(
                            gameState = state,
                            eventHistory = emptyList(),
                            error = null,
                            isResolvingTurn = false
                        )
                    } else {
                        it.copy(gameState = state)
                    }
                }
                previousState = state
            }
        }
        viewModelScope.launch {
            gameSessionService.gameEvents.collect { event ->
                _uiState.update { current ->
                    current.copy(eventHistory = current.eventHistory + event)
                }
            }
        }
        viewModelScope.launch {
            gameSessionService.sessionLeftEvents.collect {
                _uiState.update { current ->
                    current.copy(isTerminating = true, error = null)
                }
                terminateOnce()
            }
        }
        viewModelScope.launch {
            gameSessionService.sessionReplayRequestedEvents.collect {
                replayOnce()
            }
        }
    }

    fun onCardClicked(cardId: CardId) {
        viewModelScope.launch {
            actionMutex.withLock {
                if (_uiState.value.isResolvingTurn) return@withLock
                if (hasBlockingSyncError(_uiState.value.error)) return@withLock

                _uiState.update { it.copy(isResolvingTurn = true, error = null) }
                try {
                    when (val result = gameSessionService.onCardClicked(cardId)) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> {
                            _uiState.update { it.copy(error = result.error) }
                        }
                    }
                } finally {
                    _uiState.update { it.copy(isResolvingTurn = false) }
                }
            }
        }
    }

    fun onDisconnect() {
        viewModelScope.launch {
            actionMutex.withLock {
                if (_uiState.value.isTerminating) return@withLock
                _uiState.update { it.copy(isTerminating = true, error = null) }
                when (val result = gameSessionService.onLeaveClicked()) {
                    is OperationResult.Success -> Unit
                    is OperationResult.Failure -> {
                        _uiState.update { it.copy(error = result.error) }
                    }
                }
                terminateOnce()
            }
        }
    }

    fun onReplay() {
        viewModelScope.launch {
            actionMutex.withLock {
                if (_uiState.value.isTerminating) return@withLock
                _uiState.update { it.copy(error = null) }
                when (val result = gameSessionService.onReplayClicked()) {
                    is OperationResult.Success -> replayOnce()
                    is OperationResult.Failure -> _uiState.update { it.copy(error = result.error) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        onClearedAction?.invoke()
    }

    private fun hasBlockingSyncError(error: com.memory.sotopatrick.domain.error.AppError?): Boolean {
        val code = error?.context?.code ?: return false
        return code == "turn_change_failed" || code == "pair_resolve_failed" || code == "finish_failed"
    }

    private fun terminateOnce() {
        if (terminationTriggered.compareAndSet(false, true)) {
            onDisconnectAction()
        }
    }

    private fun replayOnce() {
        if (replayTriggered.compareAndSet(false, true) && !terminationTriggered.get()) {
            onReplayAction()
        }
    }
}
