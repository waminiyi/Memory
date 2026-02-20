package com.memory.sotopatrick.domain.game

import com.memory.sotopatrick.domain.card.*
import com.memory.sotopatrick.domain.events.*
import com.memory.sotopatrick.domain.turn.Turn

/**
 * The Domain Reducer.
 * Responsible for the "Transition" logic of the Game Aggregate.
 */
class GameEventHandler {

    fun apply(state: GameState, event: GameEvent): GameState {
        return when (event) {
            is GameStarted -> handleGameStarted(state)
            is CardRevealed -> handleCardRevealed(state, event)
            is PairResolved -> handlePairResolved(state, event)
            is TurnChanged -> handleTurnChanged(state, event)
            is GameFinished -> handleGameFinished(state, event)
        }
    }

    private fun handleGameStarted(state: GameState): GameState {
        return state.copy(
            status = GameStatus.IN_PROGRESS,
        ).nextVersion()
    }

    private fun handleCardRevealed(state: GameState, event: CardRevealed): GameState {
        val updatedCards = state.cards.map {
            if (it.id == event.cardId) it.copy(state = CardState.REVEALED) else it
        }
        val updatedTurn = state.currentTurn?.let {
            it.copy(revealedCards = it.revealedCards + event.cardId)
        }
        return state.copy(cards = updatedCards, currentTurn = updatedTurn).nextVersion()
    }

    private fun handlePairResolved(state: GameState, event: PairResolved): GameState {
        val updatedCards = state.cards.map { card ->
            when (card.id) {
                event.card1, event.card2 -> {
                    if (event.matched) card.copy(state = CardState.MATCHED)
                    else card.copy(state = CardState.HIDDEN)
                }

                else -> card
            }
        }

        val updatedPlayers = if (event.matched) {
            state.gamePlayers.map {
                if (it.id == event.senderId) it.copy(score = it.score + 1) else it
            }
        } else state.gamePlayers

        val updatedTurn = state.currentTurn?.copy(revealedCards = emptyList())

        return state.copy(
            cards = updatedCards,
            gamePlayers = updatedPlayers,
            currentTurn = updatedTurn
        ).nextVersion()
    }

    private fun handleTurnChanged(state: GameState, event: TurnChanged): GameState {
        return state.copy(currentTurn = Turn(event.nextUserId)).nextVersion()
    }

    private fun handleGameFinished(state: GameState, event: GameFinished): GameState {
        return state.copy(status = GameStatus.FINISHED, winnerId = event.winnerId).nextVersion()
    }
}
