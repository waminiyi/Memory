package com.memory.sotopatrick.data.network.ble.dataservice

import android.util.Log
import com.memory.sotopatrick.domain.events.*
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.network.SendPolicy
import com.memory.sotopatrick.domain.network.TransportHealth
import com.memory.sotopatrick.data.network.ble.BLEConstants
import com.memory.sotopatrick.data.network.ble.utils.BleFragmentReassembler
import com.memory.sotopatrick.data.network.ble.utils.BleFragmenter
import com.memory.sotopatrick.data.network.ble.utils.BleSerializer
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattEndpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BleMessenger(
    private val bleGattEndpoint: BleGattEndpoint,
    private val serializer: BleSerializer = BleSerializer(),
    private val fragmenter: BleFragmenter = BleFragmenter(),
    private val reassembler: BleFragmentReassembler = BleFragmentReassembler(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : MemoMessenger {

    /**
     * Thread-safe message ID counter.
     * Wraps around at 255 — suitable for BLE's small header space.
     */
    private val nextMessageId = AtomicInteger(0)

    private fun getNextMessageId(): Int {
        return nextMessageId.getAndUpdate { (it + 1) % 256 }
    }

    private val _incomingMessages = MutableSharedFlow<MemoMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<MemoMessage> = _incomingMessages.asSharedFlow()
    private val _transportHealth = MutableSharedFlow<TransportHealth>(replay = 1, extraBufferCapacity = 8)

    private val pendingAcks = ConcurrentHashMap<EventId, CompletableDeferred<FeedbackEvent>>()
    private val cachedAcks = ConcurrentHashMap<EventId, FeedbackEvent>()

    init {
        listenToEndpoint()
    }

    private fun listenToEndpoint() {
        scope.launch {
            bleGattEndpoint.dataReceived.collect { packet ->
                // Defensive copy before parsing/reassembly to avoid downstream mutation.
                val bytes = packet.copyOf()

                // 1. Decode raw bytes into Fragment [Header | Payload]
                val fragment = fragmenter.decode(bytes) ?: return@collect

                // 2. Reassemble fragments into full message ByteArray
                val completePayload = reassembler.addFragment(fragment) ?: return@collect

                try {
                    // 3. Polymorphic deserialization into GameMessage (Event or Feedback)
                    val decoded = serializer.deserialize(completePayload)
                    Log.d("Message received", "listenToEndpoint:  $decoded")

                    handleIncoming(decoded)
                } catch (e: Exception) {
                    Log.e("BLEDataService", "Failed to deserialize packet", e)
                    // Drop any partial assembly state to prevent cascading decode failures
                    // if we just processed mixed or malformed fragment sequences.
                    reassembler.clear()
                    _transportHealth.tryEmit(
                        TransportHealth.Degraded("deserialization_failed: ${e.message}")
                    )
                }
            }
        }
    }

    /**
     * Routes the decoded message to the appropriate handlers.
     */
    private suspend fun handleIncoming(message: MemoMessage) {
        when (message) {
            is GameEvent, is GeneralMessage -> {
                _incomingMessages.emit(message)
            }

            is FeedbackEvent -> {
                val pending = pendingAcks[message.originalEventId]
                if (pending != null) {
                    pending.complete(message)
                } else {
                    // ACK/REJ can arrive before waitForAck registers its deferred.
                    cachedAcks.putIfAbsent(message.originalEventId, message)
                }

                _incomingMessages.emit(message)
            }

        }
    }

    override suspend fun sendMessage(event: MemoMessage, policy: SendPolicy) {
        val bytes = serializer.serialize(event)
        sendLargeData(bytes, policy)
    }

    /**
     * Slices data into BLE-compatible chunks and sends them sequentially.
     * Throws [IOException] if any fragment fails after exhausting all retries.
     */
    private suspend fun sendLargeData(data: ByteArray, policy: SendPolicy) {
        val messageId = getNextMessageId()
        val fragments = fragmenter.fragment(data, messageId)
        Log.d(
            "BLEDataService",
            "sendLargeData: msgId=$messageId, ${fragments.size} fragments, ${data.size} bytes"
        )
        fragments.forEach { fragment ->
            var success = false
            var attempt = 0
            while (!success && attempt < policy.maxRetries) {
                attempt++
                val packet = fragmenter.encode(fragment)
                success = bleGattEndpoint.send(packet)
                if (!success) {
                    Log.e(
                        "BLEDataService",
                        "Failed to send fragment ${fragment.index} for msg $messageId (attempt $attempt/${policy.maxRetries})"
                    )
                    _transportHealth.tryEmit(TransportHealth.Degraded("fragment_send_failed"))
                } else {
                    Log.d(
                        "BLEDataService",
                        "sent fragment ${fragment.index} for msg $messageId"
                    )
                    _transportHealth.tryEmit(TransportHealth.Healthy)
                }
                // Prevents GATT buffer overflow
                delay(BLEConstants.FRAGMENT_TRANSMISSION_DELAY)
            }
            if (!success) {
                throw IOException(
                    "Failed to send fragment ${fragment.index}/${fragments.size} for message $messageId after ${policy.maxRetries} retries"
                )
            }
        }
    }

    override suspend fun waitForAck(eventId: EventId): FeedbackEvent {
        cachedAcks.remove(eventId)?.let { return it }

        val deferred = CompletableDeferred<FeedbackEvent>()
        pendingAcks[eventId] = deferred

        cachedAcks.remove(eventId)?.let { deferred.complete(it) }

        return try {
            withTimeout(BLEConstants.RESPONSE_TIMEOUT_MS) {
                deferred.await()
            }
        } finally {
            pendingAcks.remove(eventId)
        }
    }

    override fun onTransportHealth(): Flow<TransportHealth> = _transportHealth.asSharedFlow()
}
