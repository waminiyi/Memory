package com.memory.sotopatrick.ui.presentation.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memory.sotopatrick.domain.error.AppError
import com.memory.sotopatrick.domain.card.Card
import com.memory.sotopatrick.domain.card.CardId
import com.memory.sotopatrick.domain.card.CardState
import com.memory.sotopatrick.domain.events.GameEvent
import com.memory.sotopatrick.domain.events.PairResolved
import com.memory.sotopatrick.domain.game.GameStatus
import com.memory.sotopatrick.domain.game.GameState
import com.memory.sotopatrick.domain.player.GamePlayer
import com.memory.sotopatrick.domain.player.UserId

@Composable
fun GamePartyScreen(
    localUserId: UserId,
    gameState: GameState,
    eventHistory: List<GameEvent>,
    isResolvingTurn: Boolean,
    error: AppError?,
    onCardClicked: (CardId) -> Unit,
    onDisconnect: () -> Unit,
    onNewGame: () -> Unit
) {
    val playerColorMap = remember(gameState.gamePlayers) {
        val palette = listOf(
            Color(0xFFB2DFDB),
            Color(0xFFFFE082),
            Color(0xFFB39DDB),
            Color(0xFFFFAB91)
        )
        gameState.gamePlayers.mapIndexed { index, player ->
            player.id to palette[index % palette.size]
        }.toMap()
    }
    val matchedOwnerByCard = remember(eventHistory) {
        buildMatchedOwnerMap(eventHistory)
    }
    val playerStats = remember(gameState.gamePlayers, eventHistory) {
        buildPlayerStats(gameState.gamePlayers, eventHistory)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val isMyTurn = gameState.isPlayerTurn(localUserId)
        Surface(
            color = if (isMyTurn) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (gameState.status) {
                        GameStatus.FINISHED -> "Game Finished"
                        else -> if (isMyTurn) "Your Turn" else "Opponent's Turn"
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = onDisconnect) {
                    Text("Quit", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (error != null) {
            Text(
                text = error.context.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        val columns = gameState.cols.coerceAtLeast(1)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            items(gameState.cards) { card ->
                val matchedColor = matchedOwnerByCard[card.id]?.let { playerColorMap[it] }
                CardItem(
                    card = card,
                    isEnabled = isMyTurn && !isResolvingTurn && gameState.status != GameStatus.FINISHED,
                    matchedColor = matchedColor
                ) {
                    onCardClicked(card.id)
                }
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 220.dp)
                .padding(8.dp)
        ) {
            Text(
                "Game Stats",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            playerStats.forEach { stats ->
                PlayerStatsRow(
                    stats = stats,
                    color = playerColorMap[stats.player.id] ?: MaterialTheme.colorScheme.surfaceVariant,
                    isWinner = gameState.winnerId == stats.player.id
                )
            }
        }
    }

    if (gameState.status == GameStatus.FINISHED) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Match Complete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val winner = gameState.gamePlayers.find { it.id == gameState.winnerId }
                    Text("Winner: ${winner?.name ?: "Unknown"}")
                    playerStats.forEach { stats ->
                        Text("${stats.player.name}: ${stats.player.score} pts, ${stats.attempts} attempts")
                    }
                }
            },
            confirmButton = {
                Button(onClick = onNewGame) {
                    Text("New Game")
                }
            },
            dismissButton = {
                TextButton(onClick = onDisconnect) {
                    Text("Leave")
                }
            }
        )
    }
}

private data class PlayerStats(
    val player: GamePlayer,
    val attempts: Int
)

private fun buildPlayerStats(players: List<GamePlayer>, history: List<GameEvent>): List<PlayerStats> {
    val attemptsByPlayer = history
        .filterIsInstance<PairResolved>()
        .groupingBy { it.senderId }
        .eachCount()

    return players.map { player ->
        PlayerStats(
            player = player,
            attempts = attemptsByPlayer[player.id] ?: 0
        )
    }
}

private fun buildMatchedOwnerMap(history: List<GameEvent>): Map<CardId, UserId> {
    val ownerByCard = mutableMapOf<CardId, UserId>()
    history.filterIsInstance<PairResolved>().forEach { event ->
        if (event.matched) {
            ownerByCard[event.card1] = event.senderId
            ownerByCard[event.card2] = event.senderId
        }
    }
    return ownerByCard
}

@Composable
private fun PlayerStatsRow(stats: PlayerStats, color: Color, isWinner: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isWinner) 4.dp else 0.dp,
        color = if (isWinner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = color,
                    modifier = Modifier.size(12.dp)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(stats.player.name)
            }
            Text("${stats.player.score} pts | ${stats.attempts} att")
        }
    }
}

@Composable
fun CardItem(card: Card, isEnabled: Boolean, matchedColor: Color?, onCardClicked: (CardId) -> Unit) {
    val isFaceUp = card.state != CardState.HIDDEN
    val canClick = isEnabled && card.state == CardState.HIDDEN
    val backgroundColor = when (card.state) {
        CardState.HIDDEN -> MaterialTheme.colorScheme.surfaceVariant
        CardState.REVEALED -> MaterialTheme.colorScheme.primaryContainer
        CardState.MATCHED -> matchedColor ?: MaterialTheme.colorScheme.tertiaryContainer
    }

    Card(
        modifier = Modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .fillMaxWidth()
            .let { base ->
                if (canClick) base.clickable { onCardClicked(card.id) } else base
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFaceUp) card.symbol else "?",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
