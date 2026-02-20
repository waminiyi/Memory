package com.memory.sotopatrick.data.network.ble.endpoint

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow


class BleHostEndpoint(
    private val server: BleGattServer
) : BleGattEndpoint {
    override val dataReceived: Flow<ByteArray> = server.incomingData

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun send(data: ByteArray): Boolean = server.send(data = data).also {
        Log.d(
            "BleClientEndpoint",
            "sent data $data: $it"
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun close() {
        server.close()
    }
}
