package com.memory.sotopatrick.domain.game

import com.memory.sotopatrick.domain.card.Card
import com.memory.sotopatrick.domain.player.GamePlayer
import com.memory.sotopatrick.domain.player.UserId
import com.memory.sotopatrick.domain.turn.Turn
import kotlinx.serialization.Serializable


@JvmInline
@Serializable
value class GameId(val value: String)

@JvmInline
@Serializable
value class GameVersion(val value: Long)

enum class GameStatus {
    WAITING, IN_PROGRESS, FINISHED
}

data class GameState(
    val version: GameVersion,
    val status: GameStatus,
    val rows: Int,
    val cols: Int,
    val gamePlayers: List<GamePlayer>,
    val cards: List<Card>,
    val currentTurn: Turn?,
    val winnerId: UserId? = null
) {
    fun nextVersion() = copy(version = GameVersion(version.value + 1))

    fun isPlayerTurn(userId: UserId) =
        currentTurn?.userId == userId


}
