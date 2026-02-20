package com.memory.sotopatrick.data.game

import com.memory.sotopatrick.domain.error.FailureContext
import com.memory.sotopatrick.domain.error.GameRuleViolationError
import com.memory.sotopatrick.domain.result.OperationResult
import com.memory.sotopatrick.domain.game.GameSessionService
import com.memory.sotopatrick.domain.card.CardId
import com.memory.sotopatrick.domain.card.CardState
import com.memory.sotopatrick.domain.events.CardRevealed
import com.memory.sotopatrick.domain.events.GameEvent
import com.memory.sotopatrick.domain.events.GameFinished
import com.memory.sotopatrick.domain.events.PairResolved
import com.memory.sotopatrick.domain.events.SessionLeft
import com.memory.sotopatrick.domain.events.SessionReplayRequested
import com.memory.sotopatrick.domain.events.TurnChanged
import com.memory.sotopatrick.domain.game.GameState
import com.memory.sotopatrick.domain.game.P2PGameManager
import com.memory.sotopatrick.domain.player.UserId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class GameSessionOrchestrator(
    private val gameManager: P2PGameManager,
    private val revealDelayMs: Long = DEFAULT_REVEAL_DELAY_MS
) : GameSessionService {

    override val gameState: StateFlow<GameState?> = gameManager.gameState
    override val gameEvents: Flow<GameEvent> = gameManager.gameEvents
    override val sessionLeftEvents: Flow<SessionLeft> = gameManager.sessionLeftEvents
    override val sessionReplayRequestedEvents: Flow<SessionReplayRequested> =
        gameManager.sessionReplayRequestedEvents

    override suspend fun onCardClicked(cardId: CardId): OperationResult<Unit> {
        val state = gameManager.gameState.value ?: return failure("game_state_missing", "Game state not ready")
        if (!canHandleClick(state, gameManager.localUserId, cardId)) {
            return failure("invalid_click", "Cannot reveal this card now")
        }

        val revealEvent = CardRevealed(
            senderId = gameManager.localUserId,
            cardId = cardId
        )
        if (!sendEvent(revealEvent)) return failure("reveal_failed", "Failed to reveal card")

        val afterReveal = gameManager.gameState.value ?: return success()
        val revealed = afterReveal.currentTurn?.revealedCards.orEmpty()
        if (revealed.size != 2) return success()

        delay(revealDelayMs)

        val resolutionState = gameManager.gameState.value ?: return success()
        val resolutionCards = resolutionState.currentTurn?.revealedCards.orEmpty()
        if (resolutionCards.size != 2) return success()

        val first = resolutionState.cards.find { it.id == resolutionCards[0] }
            ?: return failure("card_not_found", "First selected card not found")
        val second = resolutionState.cards.find { it.id == resolutionCards[1] }
            ?: return failure("card_not_found", "Second selected card not found")

        val matched = first.symbol == second.symbol
        val pairResolved = PairResolved(
            senderId = gameManager.localUserId,
            card1 = first.id,
            card2 = second.id,
            matched = matched
        )
        if (!sendEvent(pairResolved)) return failure("pair_resolve_failed", "Failed to resolve pair")

        val afterResolution = gameManager.gameState.value ?: return success()
        if (afterResolution.cards.all { it.state == CardState.MATCHED }) {
            val winnerId = selectWinnerId(afterResolution, gameManager.localUserId)
            val gameFinished = GameFinished(
                senderId = gameManager.localUserId,
                winnerId = winnerId
            )
            if (!sendEvent(gameFinished)) return failure("finish_failed", "Failed to finish game")
            return success()
        }

        if (!matched) {
            val nextUserId = selectNextPlayerId(afterResolution, gameManager.localUserId)
            if (nextUserId != null) {
                val turnChanged = TurnChanged(
                    senderId = gameManager.localUserId,
                    nextUserId = nextUserId
                )
                if (!sendEvent(turnChanged)) return failure("turn_change_failed", "Failed to change turn")
            }
        }

        return success()
    }

    override suspend fun onLeaveClicked(): OperationResult<Unit> {
        return if (gameManager.sendLeave().isSuccess) {
            success()
        } else {
            failure("leave_failed", "Failed to notify opponent before leaving")
        }
    }

    override suspend fun onReplayClicked(): OperationResult<Unit> {
        return if (gameManager.sendReplayRequested().isSuccess) {
            success()
        } else {
            failure("replay_failed", "Failed to notify opponent for replay")
        }
    }

    private suspend fun sendEvent(event: GameEvent): Boolean = gameManager.processAction(event).isSuccess

    private fun success(): OperationResult.Success<Unit> = OperationResult.Success(Unit)

    private fun failure(code: String, message: String): OperationResult.Failure {
        return OperationResult.Failure(
            GameRuleViolationError(
                context = FailureContext(
                    code = code,
                    message = message,
                    recoverable = true
                )
            )
        )
    }

    companion object {
        const val DEFAULT_REVEAL_DELAY_MS = 700L

        fun canHandleClick(
            state: GameState,
            localUserId: UserId,
            cardId: CardId
        ): Boolean {
            if (!state.isPlayerTurn(localUserId)) return false
            val selectedCard = state.cards.find { it.id == cardId } ?: return false
            return selectedCard.state == CardState.HIDDEN
        }

        fun selectNextPlayerId(state: GameState, fallback: UserId): UserId? {
            val players = state.gamePlayers
            if (players.isEmpty()) return null
            if (players.size == 1) return players.first().id

            val currentId = state.currentTurn?.userId ?: fallback
            val currentIndex = players.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
            return players[(currentIndex + 1) % players.size].id
        }

        fun selectWinnerId(state: GameState, fallback: UserId): UserId {
            return state.gamePlayers.maxByOrNull { it.score }?.id ?: fallback
        }
    }
}
