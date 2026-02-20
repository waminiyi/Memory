package com.memory.sotopatrick.domain.validation

sealed interface DomainError {
    val errorCode: String
    val message: String

    fun toException(): Exception = GameException(this)

    sealed interface GameError : DomainError {
        data object NotYourTurn : GameError {
            override val errorCode = "GAME_NOT_YOUR_TURN"
            override val message = "It is not your turn"
        }

        data object GameNotStarted : GameError {
            override val errorCode = "GAME_NOT_STARTED"
            override val message = "Game has not started yet"
        }

        data object CardAlreadyRevealed : GameError {
            override val errorCode = "GAME_CARD_ALREADY_REVEALED"
            override val message = "Card is already revealed"
        }

        data object TooManyCardsRevealed : GameError {
            override val errorCode = "GAME_TOO_MANY_REVEALED"
            override val message = "Already 2 cards revealed this turn"
        }

        data object GameAlreadyInProgress : GameError {
            override val errorCode = "GAME_ALREADY_IN_PROGRESS"
            override val message = "Game is already in progress"
        }

        data object OnlyHostCanStart : GameError {
            override val errorCode = "GAME_ONLY_HOST_CAN_START"
            override val message = "Only the host can start the game"
        }

        data object NotEnoughPlayers : GameError {
            override val errorCode = "GAME_NOT_ENOUGH_PLAYERS"
            override val message = "Need at least 2 players"
        }

        data object InvalidTargetPlayer : GameError {
            override val errorCode = "GAME_INVALID_TARGET_PLAYER"
            override val message = "Target player does not exist"
        }

        data object GameNotComplete : GameError {
            override val errorCode = "GAME_NOT_COMPLETE"
            override val message = "Not all cards have been matched yet"
        }

        data object InsufficientRevealedCards : GameError {
            override val errorCode = "GAME_INSUFFICIENT_REVEALED"
            override val message = "Must have 2 revealed cards to resolve"
        }

        data object CardNotHidden : GameError {
            override val errorCode = "GAME_CARD_NOT_HIDDEN"
            override val message = "Card is not in hidden state"
        }
    }
}

class GameException(val error: DomainError) : Exception(error.message)
