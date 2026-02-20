package com.memory.sotopatrick.data.network.ble

import java.util.UUID

object BLEConstants {
    val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    val EVENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
    val GAME_INFO_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
    val DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MAX_MTU = 512
    const val FRAGMENT_OVERHEAD = 50
    const val RESPONSE_TIMEOUT_MS = 5000L

    const val CONNECTION_TIMEOUT_MS = 30_001L

    const val ADVERTISING_TIMEOUT_MS = 20000

    const val FRAGMENT_TRANSMISSION_DELAY = 30L // milliseconds

    const val SCAN_PERIOD: Long = 10_000L

    const val IDENTITY_EXCHANGE_TIMEOUT_MS = 15_000L
}