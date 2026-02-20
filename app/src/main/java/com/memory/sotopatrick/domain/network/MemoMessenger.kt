package com.memory.sotopatrick.domain.network

import com.memory.sotopatrick.domain.events.*
import kotlinx.coroutines.flow.Flow

data class SendPolicy(
    val maxRetries: Int = 3
)

sealed interface TransportHealth {
    data object Healthy : TransportHealth
    data class Degraded(val reason: String) : TransportHealth
}

interface MemoMessenger {
    /** Flow of all incoming messages from the opponent */
    val incomingMessages: Flow<MemoMessage>

    /** Sends a new game action (e.g., CardRevealed) */
    suspend fun sendMessage(event: MemoMessage, policy: SendPolicy = SendPolicy())

    /** Suspends until a specific EventId is acknowledged or rejected */
    suspend fun waitForAck(eventId: EventId): FeedbackEvent

    /** Health signal for transport monitoring and backpressure decisions */
    fun onTransportHealth(): Flow<TransportHealth>
}
