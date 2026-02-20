package com.memory.sotopatrick.ui.presentation.matching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memory.sotopatrick.domain.events.PlayerIdentity

@Composable
fun MatchScreen(
    viewModel: MatchViewModel,
    innerPaddingValues: PaddingValues,
    onReady: (List<PlayerIdentity>) -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is MatchUiState.Ready) {
            onReady((uiState as MatchUiState.Ready).players)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPaddingValues)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is MatchUiState.Connecting -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(24.dp))
                Text("Connecting...", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = { viewModel.onCancel(); onCancel() }) { Text("Cancel") }
            }

            is MatchUiState.Connected -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(Modifier.height(16.dp))
                Text("Connected!", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Exchanging player info...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is MatchUiState.WaitingForOpponent -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(24.dp))
                Text("Waiting for opponent...", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = { viewModel.onCancel(); onCancel() }) { Text("Cancel") }
            }

            is MatchUiState.Error -> {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
                Text("Error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                Text(state.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { viewModel.onCancel(); onCancel() }) { Text("Back") }
            }

            is MatchUiState.Disconnected -> LaunchedEffect(Unit) { onCancel() }
            is MatchUiState.Ready -> {} // handled by LaunchedEffect above
        }
    }
}
