package com.memory.sotopatrick.domain.events

import com.memory.sotopatrick.domain.card.CardId
import com.memory.sotopatrick.domain.game.GameVersion
import com.memory.sotopatrick.domain.player.GamePlayer
import com.memory.sotopatrick.domain.player.UserAvatar
import com.memory.sotopatrick.domain.player.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface MemoMessage

// --- SYSTEM & CONFIG MESSAGES ---
@Serializable
sealed interface GeneralMessage : MemoMessage {
    val userId: UserId
    val protocolVersion: Int
}

@Serializable
@SerialName("SYS_JOIN")
data class PlayerIdentity(
    override val userId: UserId,
    override val protocolVersion: Int = PROTOCOL_VERSION,
    val playerName: String,
    val userAvatar: UserAvatar,
) : GeneralMessage

@Serializable
@SerialName("SYS_CONFIG")
data class SessionConfig(
    override val userId: UserId,
    override val protocolVersion: Int = PROTOCOL_VERSION,
    val rows: Int,
    val cols: Int,
    val shuffledCardSymbols: List<String>,
    val gamePlayers: List<GamePlayer>
) : GeneralMessage

@Serializable
@SerialName("SYS_LEAVE")
data class SessionLeft(
    override val userId: UserId,
    override val protocolVersion: Int = PROTOCOL_VERSION,
    val reason: String = "user_left"
) : GeneralMessage

@Serializable
@SerialName("SYS_REPLAY")
data class SessionReplayRequested(
    override val userId: UserId,
    override val protocolVersion: Int = PROTOCOL_VERSION
) : GeneralMessage

@Serializable
@JvmInline
value class EventId(val value: String = UUID.randomUUID().toString())

@Serializable
sealed interface GameEvent : MemoMessage {
    val eventId: EventId
    val timestamp: Long
    val senderId: UserId
}

@Serializable
@SerialName("GC")
data class GameStarted(
    override val eventId: EventId = EventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val senderId: UserId,
) : GameEvent

@Serializable
@SerialName("CR")
data class CardRevealed(
    override val eventId: EventId = EventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val senderId: UserId,
    val cardId: CardId
) : GameEvent

@Serializable
@SerialName("PR")
data class PairResolved(
    override val eventId: EventId = EventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val senderId: UserId,
    val card1: CardId,
    val card2: CardId,
    val matched: Boolean
) : GameEvent

@Serializable
@SerialName("TC")
data class TurnChanged(
    override val eventId: EventId = EventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val senderId: UserId,
    val nextUserId: UserId
) : GameEvent

@Serializable
@SerialName("GF")
data class GameFinished(
    override val eventId: EventId = EventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val senderId: UserId,
    val winnerId: UserId
) : GameEvent

@Serializable
sealed interface FeedbackEvent : MemoMessage {
    val originalEventId: EventId
    val responderId: UserId
}

@Serializable
@SerialName("ACK")
data class EventAck(
    override val originalEventId: EventId,
    override val responderId: UserId,
    val newVersion: GameVersion
) : FeedbackEvent

@Serializable
@SerialName("REJ")
data class EventRejected(
    override val originalEventId: EventId,
    override val responderId: UserId,
    val reason: RejectionReason,
    val currentVersion: GameVersion
) : FeedbackEvent

@Serializable
enum class RejectionReason {
    INVALID_TURN,
    CARD_ALREADY_REVEALED,
    GAME_NOT_STARTED,
    INVALID_STATE,
    VERSION_CONFLICT
}

fun PlayerIdentity.toGamePlayer() = GamePlayer(
    id = this.userId,
    name = this.playerName,
    userAvatar = this.userAvatar
)

const val PROTOCOL_VERSION = 1
