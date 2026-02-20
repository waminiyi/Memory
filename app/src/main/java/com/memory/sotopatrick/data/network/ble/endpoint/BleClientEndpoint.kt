package com.memory.sotopatrick.data.network.ble.endpoint

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattClient
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow


class BleClientEndpoint(
    private val client: BleGattClient
) : BleGattEndpoint {
    override val dataReceived: Flow<ByteArray> = client.incomingData

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun send(data: ByteArray): Boolean = client.send(data).also {
        Log.d(
            "BleClientEndpoint",
            "sent data $data: $it"
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun close() {
        client.close()
    }
}
