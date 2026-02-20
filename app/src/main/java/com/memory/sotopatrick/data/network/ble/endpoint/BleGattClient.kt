package com.memory.sotopatrick.data.network.ble.endpoint

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.network.ble.BLEConstants
import com.memory.sotopatrick.data.network.ble.connection.BleConnectionState
import com.memory.sotopatrick.data.network.ble.utils.BleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

class BleGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bleGattClient: BluetoothGatt? = null
    private var _incomingData: MutableStateFlow<ByteArray> = MutableStateFlow(ByteArray(0))
    val incomingData: StateFlow<ByteArray> = _incomingData

    @SuppressLint("MissingPermission")
    private val connectionFlow = connectAsFlow()
        .shareIn(
            scope = scope,
            started = SharingStarted.Lazily,
            replay = 1
        )

    val state: StateFlow<BleConnectionState> = connectionFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = BleConnectionState.Disconnected
        )

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun waitForReady() {
        Log.d(TAG, "waitForReady: Suspended, waiting for Connected state...")
        state.first { it is BleConnectionState.Ready || it is BleConnectionState.Error }
            .let {
                Log.d(TAG, "waitForReady: Resumed with state: $it")
                if (it is BleConnectionState.Error) throw IOException(it.message)
            }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectAsFlow() = callbackFlow {
        Log.d(TAG, "connectAsFlow: Initializing flow for ${device.address}")
        val callback = object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

                val statusName = BleUtils.getStatusName(status)
                val stateName = BleUtils.getConnectionStateName(newState)

                Log.i(
                    TAG,
                    "Connection Change [${gatt.device.address}] -> Status: $statusName ($status), NewState: $stateName"
                )

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        //trySend(BleConnectionState.Connected(gatt.device.address))
                        Log.d(TAG, "Link established. Requesting MTU...")

                        gatt.requestMtu(BLEConstants.MAX_MTU)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(TAG, "Disconnected from server.")
                        trySend(BleConnectionState.Disconnected)
                        //  close()
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val statusName = BleUtils.getStatusName(status)
                Log.d(TAG, "onMtuChanged: MTU set to $mtu, Status: $statusName")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "MTU negotiation failed with status: $statusName ($status)")
                    trySend(BleConnectionState.Error("MTU negotiation failed: $statusName"))
                    return
                }
                gatt.discoverServices()
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val statusName = BleUtils.getStatusName(status)
                Log.d(TAG, "onServicesDiscovered: Status: $statusName")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Service discovery failed with status: $statusName ($status)")
                    trySend(BleConnectionState.Error("Service discovery failed: $statusName"))
                    return
                }

                val service = gatt.getService(BLEConstants.SERVICE_UUID)
                val characteristic =
                    service?.getCharacteristic(BLEConstants.EVENT_CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    Log.d(TAG, "Service/Characteristic found. Enabling Notifications...")
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(BLEConstants.DESCRIPTOR_UUID)

                    descriptor?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(it)
                        Log.d(TAG, "Descriptor write initiated...")
                    } ?: Log.e(TAG, "Config descriptor not found!")
                    trySend(BleConnectionState.Connected(gatt.device.address))
                } else {
                    Log.e(TAG, "Required UUIDs not found in GATT table.")
                    trySend(BleConnectionState.Error("Required characteristic not found"))
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.v(TAG, "Incoming data: ${characteristic.value.size} bytes")
                if (characteristic.uuid == BLEConstants.EVENT_CHARACTERISTIC_UUID) {
                    _incomingData.value = characteristic.value.copyOf()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val statusName = BleUtils.getStatusName(status)
                Log.d(TAG, "onCharacteristicWrite, Status=$statusName")

            }


            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                val statusName = BleUtils.getStatusName(status)
                Log.i(TAG, "onDescriptorWrite: Handshake complete. Status: $statusName")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    trySend(BleConnectionState.Ready)
                } else {
                    trySend(BleConnectionState.Error("Handshake failed: $statusName"))
                }
            }

            override fun onServiceChanged(gatt: BluetoothGatt) {
                Log.w(
                    TAG,
                    "onServiceChanged: Server invalidated GATT database! Forcing Disconnect."
                )
                trySend(BleConnectionState.Disconnected)
            }
        }

        trySend(BleConnectionState.Connecting)
        Log.d(TAG, "connectGatt() called for ${device.address}")
        bleGattClient = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

        awaitClose {
            Log.d(TAG, "Flow awaitClose: Cleaning up GATT resources")
            bleGattClient?.disconnect()
            bleGattClient?.close()
            bleGattClient = null
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun send(data: ByteArray): Boolean {
        Log.d(TAG, "send: Requesting write for ${data.size} bytes")
        val gatt = bleGattClient ?: run {
            Log.e("BleGattClient", "GATT client is null")
            return false
        }

        val service = gatt.getService(BLEConstants.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BLEConstants.EVENT_CHARACTERISTIC_UUID)

        if (characteristic == null) {
            Log.e(TAG, "send: Target characteristic not found")
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        Log.i(TAG, "close: Manually shutting down client")
        scope.cancel()
        bleGattClient?.disconnect()
        bleGattClient?.close()
        bleGattClient = null
    }

    companion object {
        private const val TAG = "MEMORY_BLE_GATT_CLIENT"
    }
}
