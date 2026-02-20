package com.memory.sotopatrick.ui.presentation.game

import com.memory.sotopatrick.domain.events.SessionConfig
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.UserId

data class GridPreset(
    val columns: Int,
    val rows: Int
) {
    val label: String = "${columns}x$rows"
    val totalCards: Int = columns * rows
}

sealed class GameSetupUiState {
    // Guest is waiting for the message
    object WaitingForHost : GameSetupUiState()

    // Host is selecting among predefined presets
    data class Configuring(
        val availablePresets: List<GridPreset> = PREDEFINED_GRID_PRESETS,
        val selectedPreset: GridPreset = PREDEFINED_GRID_PRESETS.first(),
        val canStart: Boolean = true
    ) : GameSetupUiState()

    // Both are ready to play
    data class GameReady(
        val config: SessionConfig,
        val messenger: MemoMessenger,
        val localUserId: UserId
    ) : GameSetupUiState()

    // Something went wrong
    data class Error(val message: String) : GameSetupUiState()
}

val PREDEFINED_GRID_PRESETS = listOf(
    GridPreset(columns = 3, rows = 2),
    GridPreset(columns = 4, rows = 2),
    GridPreset(columns = 3, rows = 4),
    GridPreset(columns = 4, rows = 4),
    GridPreset(columns = 5, rows = 4)
)
