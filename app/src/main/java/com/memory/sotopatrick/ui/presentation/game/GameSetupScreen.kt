package com.memory.sotopatrick.ui.presentation.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memory.sotopatrick.domain.events.SessionConfig
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.GamePlayer
import com.memory.sotopatrick.domain.player.UserId

@Composable
fun GameSetupScreen(
    viewModel: GameSetupViewModel,
    gamePlayers: List<GamePlayer>,
    onGameReady: (SessionConfig, MemoMessenger, UserId) -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val readyState = uiState as? GameSetupUiState.GameReady

    readyState?.let { ready ->
        LaunchedEffect(ready) {
            onGameReady(ready.config, ready.messenger, ready.localUserId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎮 Game Lobby", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            is GameSetupUiState.WaitingForHost -> {
                WaitingView()
            }

            is GameSetupUiState.Configuring -> {
                ConfiguringView(
                    state = state,
                    onPresetSelected = viewModel::selectPreset,
                    onStart = { viewModel.onStartGame(gamePlayers) }
                )
            }

            is GameSetupUiState.GameReady -> {
                Text("Game is starting...", color = MaterialTheme.colorScheme.primary)
            }

            is GameSetupUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onCancel) { Text("Go Back") }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text("Leave Lobby", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun WaitingView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Waiting for host to configure game...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfiguringView(
    state: GameSetupUiState.Configuring,
    onPresetSelected: (GridPreset) -> Unit,
    onStart: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Configure the Board", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.availablePresets) { preset ->
                FilterChip(
                    selected = preset == state.selectedPreset,
                    onClick = { onPresetSelected(preset) },
                    label = { Text(preset.label) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (state.canStart)
                    MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Text(
                text = "Selected: ${state.selectedPreset.label} (${state.selectedPreset.totalCards} cards)",
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onStart,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Game")
        }
    }
}
