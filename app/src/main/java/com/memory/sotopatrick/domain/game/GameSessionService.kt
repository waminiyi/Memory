package com.memory.sotopatrick.domain.game

import com.memory.sotopatrick.domain.result.OperationResult
import com.memory.sotopatrick.domain.card.CardId
import com.memory.sotopatrick.domain.events.GameEvent
import com.memory.sotopatrick.domain.events.SessionLeft
import com.memory.sotopatrick.domain.events.SessionReplayRequested
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface GameSessionService {
    val gameState: StateFlow<GameState?>
    val gameEvents: Flow<GameEvent>
    val sessionLeftEvents: Flow<SessionLeft>
    val sessionReplayRequestedEvents: Flow<SessionReplayRequested>
    suspend fun onCardClicked(cardId: CardId): OperationResult<Unit>
    suspend fun onReplayClicked(): OperationResult<Unit>
    suspend fun onLeaveClicked(): OperationResult<Unit>
}
