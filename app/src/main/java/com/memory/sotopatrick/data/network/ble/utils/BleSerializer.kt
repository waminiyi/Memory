package com.memory.sotopatrick.data.network.ble.utils

import com.memory.sotopatrick.domain.events.MemoMessage
import kotlinx.serialization.json.Json

class BleSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // "t" is shorter than "type", saving bytes in every BLE fragment
        classDiscriminator = "t"
    }

    /**
     * Serializes any GameMessage (GameEvent or FeedbackEvent) into a ByteArray.
     */
    fun serialize(message: MemoMessage): ByteArray {
        return json.encodeToString(message).toByteArray(Charsets.UTF_8)
    }

    /**
     * Deserializes raw bytes back into the specific GameMessage subclass.
     * Polymorphism handles the logic—no manual string parsing required.
     */
    fun deserialize(data: ByteArray): MemoMessage {
        val jsonString = data.decodeToString()
        return try {
            json.decodeFromString<MemoMessage>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode BLE packet: $jsonString", e)
        }
    }
}