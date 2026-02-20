package com.memory.sotopatrick.domain.game

import android.util.Log
import com.memory.sotopatrick.domain.card.Card
import com.memory.sotopatrick.domain.card.CardId
import com.memory.sotopatrick.domain.card.CardState
import com.memory.sotopatrick.domain.events.*
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.UserId
import com.memory.sotopatrick.domain.turn.Turn
import com.memory.sotopatrick.domain.validation.GameValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashSet

/**
 * P2PGameManager: The Aggregate Root Coordinator.
 * Handles the "Source of Truth" for the game state and network synchronization.
 */
class P2PGameManager(
    val localUserId: UserId,
    config: SessionConfig,
    private val memoMessenger: MemoMessenger,
    private val validator: GameValidator = GameValidator(),
    private val eventHandler: GameEventHandler = GameEventHandler(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val stateMutex = Mutex()
    private val processedIncomingEventIds = LinkedHashSet<EventId>()
    private val _gameState = MutableStateFlow<GameState?>(createInitialState(config))
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()
    private val _gameEvents = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val gameEvents: SharedFlow<GameEvent> = _gameEvents.asSharedFlow()
    private val _sessionLeftEvents = MutableSharedFlow<SessionLeft>(extraBufferCapacity = 8)
    val sessionLeftEvents: SharedFlow<SessionLeft> = _sessionLeftEvents.asSharedFlow()
    private val _sessionReplayRequestedEvents =
        MutableSharedFlow<SessionReplayRequested>(extraBufferCapacity = 8)
    val sessionReplayRequestedEvents: SharedFlow<SessionReplayRequested> =
        _sessionReplayRequestedEvents.asSharedFlow()

    init {
        scope.launch {
            memoMessenger.incomingMessages.collect { message ->
                when (message) {
                    is GameEvent -> handleIncomingMessage(message)
                    is SessionConfig -> handleIncomingSessionConfig(message)
                    is SessionLeft -> _sessionLeftEvents.emit(message)
                    is SessionReplayRequested -> _sessionReplayRequestedEvents.emit(message)
                    else -> Unit
                }
            }
        }
    }


    private fun createInitialState(config: SessionConfig): GameState {
        val cards = config.shuffledCardSymbols.mapIndexed { index, symbol ->
            Card(
                id = CardId(index.toString()),
                symbol = symbol,
                state = CardState.HIDDEN
            )
        }

        return GameState(
            version = GameVersion(1),
            status = GameStatus.IN_PROGRESS,
            rows = config.rows,
            cols = config.cols,
            gamePlayers = config.gamePlayers,
            cards = cards,
            currentTurn = Turn(userId = config.userId),
            winnerId = null
        )
    }

    /**
     * Primary entry point for local actions (Clicking cards, starting game).
     * Follows the "Optimistic Lock" pattern with network ACK.
     */
    suspend fun processAction(event: GameEvent, maxRetries: Int = 3): Result<Unit> =
        stateMutex.withLock {
            Log.d(TAG, "process action: $event")

            val current = _gameState.value
                ?: return Result.failure(Exception("Game state not initialized"))

            // 1. Domain Validation
            validator.validate(current, event).onFailure { return Result.failure(it) }

            var lastException: Exception? = null

            // 2. Retry Loop
            for (attempt in 1..maxRetries) {
                try {
                    Log.d(TAG, "Attempt $attempt: sending ${event.eventId}")

                    // Transmit
                    memoMessenger.sendMessage(event)

                    // Wait for Remote Authority/Peer Acknowledgement
                    // This should throw a TimeoutException if no Ack is received
                    val response = memoMessenger.waitForAck(event.eventId)

                    return when (response) {
                        is EventAck -> {
                            val newState = eventHandler.apply(current, event)
                            _gameState.value = newState
                            _gameEvents.emit(event)
                            Result.success(Unit)
                        }

                        is EventRejected -> {
                            Result.failure(Exception("Action rejected: ${response.reason}"))
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Attempt $attempt failed: ${e.message}")

                    if (attempt < maxRetries) {
                        // Exponential backoff: 200ms, 400ms, 800ms...
                        val delayTime = 200L * attempt
                        delay(delayTime)
                    }
                }
            }

            return Result.failure(lastException ?: Exception("Failed after $maxRetries attempts"))
        }

    suspend fun sendLeave(reason: String = "user_left"): Result<Unit> {
        return try {
            memoMessenger.sendMessage(
                SessionLeft(
                    userId = localUserId,
                    reason = reason
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendReplayRequested(): Result<Unit> {
        return try {
            memoMessenger.sendMessage(
                SessionReplayRequested(
                    userId = localUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Handles events coming from the other device.
     */
    private suspend fun handleIncomingMessage(message: MemoMessage) = stateMutex.withLock {
        val current = _gameState.value ?: return@withLock

        Log.d(TAG, "handleIncomingMessage: $message")

        if (message is GameEvent) {
            if (processedIncomingEventIds.contains(message.eventId)) {
                return@withLock
            }

            validator.validate(current, message).onSuccess {
                rememberIncomingEvent(message.eventId)
                // 1. Apply transition
                val newState = eventHandler.apply(current, message)
                _gameState.value = newState
                _gameEvents.emit(message)

                // 2. Send Ack back to peer
                memoMessenger.sendMessage(
                    EventAck(
                        originalEventId = message.eventId,
                        responderId = localUserId,
                        newVersion = newState.version
                    )
                )
            }.onFailure { error ->
                // 3. Reject if invalid (desynchronization)
                memoMessenger.sendMessage(
                    EventRejected(
                        originalEventId = message.eventId,
                        responderId = localUserId,
                        reason = RejectionReason.INVALID_STATE,
                        currentVersion = current.version
                    )
                )
            }
        }
    }

    private suspend fun handleIncomingSessionConfig(config: SessionConfig) = stateMutex.withLock {
        applySessionConfig(config)
    }

    private suspend fun applySessionConfig(config: SessionConfig) {
        processedIncomingEventIds.clear()
        _gameState.value = createInitialState(config)
        _gameEvents.emit(GameStarted(senderId = config.userId))
    }

    private fun rememberIncomingEvent(eventId: EventId) {
        processedIncomingEventIds.add(eventId)
        if (processedIncomingEventIds.size > MAX_PROCESSED_EVENT_IDS) {
            val first = processedIncomingEventIds.first()
            processedIncomingEventIds.remove(first)
        }
    }

    /**
     * Cancels the internal coroutine scope, stopping all message listening.
     * Must be called when the game session ends (e.g., from ViewModel.onCleared()).
     */
    fun close() {
        scope.cancel()
    }

    companion object {
        private const val MAX_PROCESSED_EVENT_IDS = 512
        private const val TAG="P2PGameManager"
    }
}
