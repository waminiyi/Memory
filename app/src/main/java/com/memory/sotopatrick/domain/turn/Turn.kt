package com.memory.sotopatrick.domain.turn

import com.memory.sotopatrick.domain.card.CardId
import com.memory.sotopatrick.domain.player.UserId

data class Turn(
    val userId: UserId,
    val revealedCards: List<CardId> = emptyList()
) {
    fun canReveal() = revealedCards.size < 2
}
