package com.memory.sotopatrick.data.network.ble.connection

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.network.ble.BLEConstants.CONNECTION_TIMEOUT_MS
import com.memory.sotopatrick.data.network.ble.endpoint.BleClientEndpoint
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattClient
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattEndpoint
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattServer
import com.memory.sotopatrick.data.network.ble.endpoint.BleHostEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class BleConnectionManager(
    private val context: Context
) {
    private var currentEndpoint: BleGattEndpoint? = null

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _connectionStatus =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionStatus: StateFlow<BleConnectionState> = _connectionStatus.asStateFlow()

    private var statusObservationJob: Job? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun createHostEndpoint(): Result<BleGattEndpoint> = withContext(Dispatchers.IO) {
        Log.i(TAG, "createHostEndpoint: Starting Host/Server setup")
        runCatching {
            validateBluetooth()
            statusObservationJob?.cancel()
            closeCurrentEndpoint()
            val server = BleGattServer(context)
            statusObservationJob = scope.launch {
                server.state.collect { state ->
                    Log.d(TAG, "Host State Update: $state")
                    handleInternalStateChange(state)
                }
            }
            Log.d(TAG, "Host: Waiting for ready signal (Timeout: ${CONNECTION_TIMEOUT_MS}ms)")
            withTimeout(CONNECTION_TIMEOUT_MS) {
                server.waitForReady()//TODO: change approach
            }

            BleHostEndpoint(server).also {
                currentEndpoint = it
                Log.i(TAG, "Host endpoint successfully created and ready")
            }
        }.onFailure {
            Log.e(TAG, "Host creation failed: ${it.message}")
            handleConnectionError(it)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun createClientEndpoint(device: BluetoothDevice): Result<BleGattEndpoint> =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "createClientEndpoint: Connecting to ${device.address}")
            runCatching {
                validateBluetooth()
                statusObservationJob?.cancel()
                closeCurrentEndpoint()

                val client = BleGattClient(context, device)

                // Observe state changes
                statusObservationJob = scope.launch {
                    client.state.collect { state ->
                        Log.d(TAG, "Client State Update: $state")
                        handleInternalStateChange(state)
                    }
                }
                Log.d(TAG, "Client: Waiting for ready signal (Timeout: ${CONNECTION_TIMEOUT_MS}ms)")
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    client.waitForReady() //TODO: change approach
                }

                BleClientEndpoint(client).also {
                    currentEndpoint = it
                    Log.i(TAG, "Client endpoint successfully created and ready")
                }

            }.onFailure {
                Log.e(TAG, "Client creation failed: ${it.message}")
                handleConnectionError(it)
            }
        }

    private fun updateConnectionState(state: BleConnectionState) {
        _connectionStatus.value = state
    }

    private fun validateBluetooth() {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        require(bluetoothManager?.adapter?.isEnabled == true) { "Bluetooth is disabled" }
    }

    private fun cleanupPreviousSession() {
        Log.d(TAG, "Cleaning up previous connection session...")
        statusObservationJob?.cancel()
        closeCurrentEndpoint()
    }

    private fun closeCurrentEndpoint() {
        if (currentEndpoint != null) {
            Log.d(TAG, "Closing current GATT endpoint")
            currentEndpoint?.close()
            currentEndpoint = null
        }
    }

    private fun handleConnectionError(error: Throwable) {
        val message = when (error) {
            is TimeoutCancellationException -> "Connection timeout"
            is SecurityException -> "Missing permissions"
            else -> error.message ?: "Unknown error"
        }
        Log.e(TAG, "Handling Connection Error: $message")
        updateConnectionState(BleConnectionState.Error(message))
    }

    private fun handleInternalStateChange(state: BleConnectionState) {
        when (state) {
            is BleConnectionState.Connecting -> updateConnectionState(BleConnectionState.Connecting)
            is BleConnectionState.Connected -> updateConnectionState(
                BleConnectionState.Connected(
                    state.deviceAddress
                )
            )

            is BleConnectionState.Disconnected -> {
                Log.w(TAG, "State changed to Disconnected.")
                updateConnectionState(BleConnectionState.Disconnected)
                // When we lose physical connection, the endpoint is dead.
                closeCurrentEndpoint()
            }

            is BleConnectionState.Error -> updateConnectionState(BleConnectionState.Error(state.message))
            else -> Unit
        }
    }

    fun close() {
        Log.i(TAG, "Closing ConnectionManager and cancelling scope")
        cleanupPreviousSession()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        updateConnectionState(BleConnectionState.Disconnected)
    }

    companion object {
        private const val TAG = "BLE_CONNECTION_MANAGER"
    }
}
