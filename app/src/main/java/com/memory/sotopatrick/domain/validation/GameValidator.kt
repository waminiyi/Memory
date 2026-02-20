package com.memory.sotopatrick.domain.validation

import com.memory.sotopatrick.domain.game.*
import com.memory.sotopatrick.domain.events.*
import com.memory.sotopatrick.domain.card.CardState

class GameValidator {
    fun validate(state: GameState, event: GameEvent): Result<Unit> {
        return when (event) {
            is GameStarted -> validateStart(state, event)
            is CardRevealed -> validateReveal(state, event)
            is PairResolved -> validateResolution(state, event)
            is TurnChanged -> validateTurn(state, event)
            is GameFinished -> validateGameFinished(state, event)
        }
    }

    private fun validateStart(state: GameState, event: GameStarted): Result<Unit> {
        val sender = state.gamePlayers.find { it.id == event.senderId }
        return when {
            state.status != GameStatus.WAITING ->
                Result.failure(DomainError.GameError.GameAlreadyInProgress.toException())
            sender?.isHost != true ->
                Result.failure(DomainError.GameError.OnlyHostCanStart.toException())
            state.gamePlayers.size < 2 ->
                Result.failure(DomainError.GameError.NotEnoughPlayers.toException())
            else -> Result.success(Unit)
        }
    }

    private fun validateReveal(state: GameState, event: CardRevealed): Result<Unit> {
        val card = state.cards.find { it.id == event.cardId }
        return when {
            state.status != GameStatus.IN_PROGRESS ->
                Result.failure(DomainError.GameError.GameNotStarted.toException())
            state.currentTurn?.userId != event.senderId ->
                Result.failure(DomainError.GameError.NotYourTurn.toException())
            card?.state != CardState.HIDDEN ->
                Result.failure(DomainError.GameError.CardNotHidden.toException())
            state.currentTurn.revealedCards.size >= 2 ->
                Result.failure(DomainError.GameError.TooManyCardsRevealed.toException())
            else -> Result.success(Unit)
        }
    }

    private fun validateResolution(state: GameState, event: PairResolved): Result<Unit> {
        return if (state.currentTurn?.revealedCards?.size == 2) Result.success(Unit)
        else Result.failure(DomainError.GameError.InsufficientRevealedCards.toException())
    }

    private fun validateTurn(state: GameState, event: TurnChanged): Result<Unit> {
        return if (state.gamePlayers.any { it.id == event.nextUserId }) Result.success(Unit)
        else Result.failure(DomainError.GameError.InvalidTargetPlayer.toException())
    }

    private fun validateGameFinished(state: GameState, event: GameFinished): Result<Unit> {
        return when {
            state.status != GameStatus.IN_PROGRESS ->
                Result.failure(DomainError.GameError.GameNotStarted.toException())
            // Note: We intentionally do NOT check that all cards are matched here.
            // The remote peer may send GameFinished before our local state has fully
            // caught up with the preceding PairResolved. The orchestrator already
            // verifies all cards are matched before emitting GameFinished locally.
            else -> Result.success(Unit)
        }
    }
}
