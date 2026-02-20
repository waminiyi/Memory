package com.memory.sotopatrick.data.network.ble.endpoint

import kotlinx.coroutines.flow.Flow

interface BleGattEndpoint {
    val dataReceived: Flow<ByteArray>
    suspend fun send(data: ByteArray): Boolean

    fun close()
}