package com.memory.sotopatrick.ui.presentation.game

import com.memory.sotopatrick.domain.error.AppError
import com.memory.sotopatrick.domain.events.GameEvent
import com.memory.sotopatrick.domain.game.GameState

data class GameSessionUiState(
    val gameState: GameState? = null,
    val eventHistory: List<GameEvent> = emptyList(),
    val isResolvingTurn: Boolean = false,
    val isTerminating: Boolean = false,
    val error: AppError? = null
)
