package com.memory.sotopatrick.domain.card

import kotlinx.serialization.Serializable

enum class CardState { HIDDEN, REVEALED, MATCHED }

@JvmInline
@Serializable
value class CardId(val value: String)
data class Card(
    val id: CardId,
    val symbol: String,
    val state: CardState
)
