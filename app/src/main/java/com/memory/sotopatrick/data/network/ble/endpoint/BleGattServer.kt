package com.memory.sotopatrick.data.network.ble.endpoint

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

class BleGattServer(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bleGattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private val subscribedDevices = mutableSetOf<String>()
    private var _incomingData: MutableStateFlow<ByteArray> = MutableStateFlow(ByteArray(0))
    val incomingData: StateFlow<ByteArray> = _incomingData

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupAsFlow() = callbackFlow {
        Log.i(TAG, "setupAsFlow: Initializing GATT Server")
        subscribedDevices.clear()

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager


        val gattServerCallback = object : BluetoothGattServerCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int
            ) {
                val statusName = BleUtils.getStatusName(status)
                val stateName = BleUtils.getConnectionStateName(newState)
                Log.d(
                    TAG,
                    "Connection Change [${device.address}] -> Status: $statusName, NewState: $stateName"
                )

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(
                            TAG,
                            "Physical link established with ${device.address}. Waiting for subscription..."
                        )
                        // Don't emit state - wait for subscription
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(TAG, "Physical link lost with ${device.address}")
                        subscribedDevices.remove(device.address)

                        // Always emit Disconnected when our connected device drops,
                        // regardless of whether it had completed subscription handshake.
                        if (device.address == connectedDevice?.address) {
                            Log.d(
                                TAG,
                                "Connected device disconnected. Emitting Disconnected state."
                            )
                            connectedDevice = null
                            trySend(BleConnectionState.Disconnected)
                        }
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.v(TAG, "Read Request from ${device.address} for ${characteristic.uuid}")
                if (characteristic.uuid == BLEConstants.GAME_INFO_CHARACTERISTIC_UUID) {
                    bleGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                Log.v(TAG, "Write Request from ${device.address}. Bytes: ${value.size}")
                if (characteristic.uuid == BLEConstants.EVENT_CHARACTERISTIC_UUID) {

                    if (subscribedDevices.contains(device.address)) {
                        _incomingData.value = value
                    } else {
                        Log.w(TAG, "Refused write: ${device.address} is not subscribed.")
                    }
                    if (responseNeeded) {
                        bleGattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null
                        )
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                val isEnabling =
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val action = if (isEnabling) "SUBSCRIBE" else "UNSUBSCRIBE"

                Log.i(TAG, "Descriptor Write ($action) from ${device.address}")

                if (isEnabling) {
                    val isNewSubscription = subscribedDevices.add(device.address)

                    if (isNewSubscription) {
                        Log.d(TAG, "New subscriber confirmed: ${device.address}")
                        connectedDevice = device

                        // ✅ Emit both states in order, only for new subscriptions
                        trySend(BleConnectionState.Connected(device.address))
                        trySend(BleConnectionState.Ready)
                    } else {
                        Log.d(TAG, "Re-subscription from ${device.address} (already known)")
                    }
                } else {
                    subscribedDevices.remove(device.address)
                    Log.d(TAG, "Subscriber removed: ${device.address}")

                    if (device.address == connectedDevice?.address) {
                        connectedDevice = null
                        trySend(BleConnectionState.Disconnected)
                    }
                }

                if (responseNeeded) {
                    bleGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                Log.d(
                    TAG,
                    "onServiceAdded: UUID=${service.uuid}, Status=${BleUtils.getStatusName(status)}"
                )
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                Log.v(
                    TAG,
                    "Notification sent to ${device.address}, Status=${BleUtils.getStatusName(status)}"
                )
            }

        }

        bleGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = createGattService()
        val added = bleGattServer?.addService(service) ?: false

        if (!added) {
            Log.e(TAG, "Failed to add GATT Service to server")
            trySend(BleConnectionState.Error("Failed to add service"))
            close()
        } else {
            Log.i(TAG, "Server started. Service added")
        }

        awaitClose {
            Log.w(TAG, "Flow awaitClose: Shutting down GATT Server and clearing subscriptions")
            bleGattServer?.close()
            bleGattServer = null
            connectedDevice = null
            subscribedDevices.clear()
        }
    }

    @SuppressLint("MissingPermission")
    private val serverFlow: SharedFlow<BleConnectionState> = setupAsFlow()
        .shareIn(
            scope = scope,
            started = SharingStarted.Lazily,
            replay = 1
        )

    val state: StateFlow<BleConnectionState> = serverFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = BleConnectionState.Idle
        )

    suspend fun waitForReady() {
        Log.d(TAG, "waitForReady: Waiting for client subscription...")
        state.first { it is BleConnectionState.Ready || it is BleConnectionState.Error }
            .let {
                Log.d(TAG, "waitForReady: Resolved with $it")
                if (it is BleConnectionState.Error) throw IOException(it.message)
            }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun send(data: ByteArray): Boolean {
        val device = connectedDevice ?: return false
        if (!subscribedDevices.contains(device.address)) {
            Log.w(TAG, "send: Device ${device.address} not in subscription list")
            return false
        }
        val characteristic = bleGattServer?.getService(BLEConstants.SERVICE_UUID)
            ?.getCharacteristic(BLEConstants.EVENT_CHARACTERISTIC_UUID) ?: return false

        Log.v(TAG, "send: Notifying ${device.address} (${data.size} bytes)")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bleGattServer?.notifyCharacteristicChanged(
                device, characteristic, false, data
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            bleGattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        Log.i(TAG, "close: Definitive shutdown of GATT Server")
        subscribedDevices.clear()
        bleGattServer?.let { server ->
            connectedDevice?.let {
                server.cancelConnection(it)

            }
            server.clearServices()
            server.close()
        }
        connectedDevice = null
        bleGattServer = null
    }

    private fun createGattService(): BluetoothGattService {
        val service = BluetoothGattService(
            BLEConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        service.addCharacteristic(createEventCharacteristic())
        service.addCharacteristic(createGameInfoCharacteristic())

        return service
    }

    private fun createEventCharacteristic() = BluetoothGattCharacteristic(
        BLEConstants.EVENT_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ or
                BluetoothGattCharacteristic.PERMISSION_WRITE
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                BLEConstants.DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or
                        BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
    }

    private fun createGameInfoCharacteristic() = BluetoothGattCharacteristic(
        BLEConstants.GAME_INFO_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    )

    companion object {
        private const val TAG = "MEMORY_BLE_GATT_SERVER"
    }
}


