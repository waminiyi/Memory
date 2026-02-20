package com.memory.sotopatrick.data.network.ble.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.network.ble.BLEConstants
import com.memory.sotopatrick.data.network.ble.dataservice.BleMessenger
import com.memory.sotopatrick.domain.matching.MatchingStatus
import com.memory.sotopatrick.domain.matching.MatchFailureReason
import com.memory.sotopatrick.domain.matching.PlayerMatchingService
import com.memory.sotopatrick.domain.discovery.NearbyUser
import com.memory.sotopatrick.domain.events.PlayerIdentity
import com.memory.sotopatrick.domain.network.MemoMessenger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BlePlayerMatchingService(
    private val context: Context,
    private val connectionManager: BleConnectionManager,
    private val bluetoothAdapter: BluetoothAdapter,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PlayerMatchingService {

    private val _connectionStatus =
        MutableStateFlow<MatchingStatus>(MatchingStatus.Idle)

    override val connectionStatus: Flow<MatchingStatus> = _connectionStatus.asStateFlow()

    init {
        CoroutineScope(ioDispatcher).launch {
            connectionManager.connectionStatus.collect { connectionState ->
                Log.d(TAG, "BleConnectionState: $connectionState")

                when (connectionState) {
                    is BleConnectionState.Idle -> _connectionStatus.emit(MatchingStatus.Idle)
                    is BleConnectionState.Connecting -> _connectionStatus.emit(MatchingStatus.Connecting)
                    is BleConnectionState.Connected -> _connectionStatus.emit(MatchingStatus.Connected)
                    is BleConnectionState.Ready -> Unit
                    is BleConnectionState.Error -> _connectionStatus.emit(
                        MatchingStatus.Failed(classifyBleError(connectionState.message))
                    )

                    is BleConnectionState.Disconnected -> _connectionStatus.emit(MatchingStatus.Disconnected)
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    override suspend fun hostMatch(localPlayer: PlayerIdentity): Result<MemoMessenger> {
        return runCatching {
            _connectionStatus.emit(MatchingStatus.Connecting)
            val endpoint = connectionManager.createHostEndpoint().getOrThrow()
            _connectionStatus.emit(MatchingStatus.WaitingForOpponent)
            val messenger = BleMessenger(endpoint)
            messenger.exchangeIdentities(
                isHost = true,
                localPlayer = localPlayer
            )
            messenger
        }.onFailure { exception ->
            Log.e(TAG, "Host match failed: ${exception.message}", exception)
            _connectionStatus.emit(
                MatchingStatus.Failed(
                    classifyBleError(exception.message ?: "host_match_failed")
                )
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun joinMatch(
        host: NearbyUser,
        localPlayer: PlayerIdentity
    ): Result<MemoMessenger> {
        val device = bluetoothAdapter.getRemoteDevice(host.playerAddress.value)
        return runCatching {
            _connectionStatus.emit(MatchingStatus.Connecting)
            val endpoint = connectionManager.createClientEndpoint(device).getOrThrow()
            val messenger = BleMessenger(endpoint)
            messenger.exchangeIdentities(
                isHost = false,
                localPlayer = localPlayer
            )
            messenger
        }.onFailure { exception ->
            Log.e(TAG, "Join match failed: ${exception.message}", exception)
            _connectionStatus.emit(
                MatchingStatus.Failed(
                    classifyBleError(exception.message ?: "join_match_failed")
                )
            )
        }
    }

    private suspend fun BleMessenger.exchangeIdentities(
        isHost: Boolean,
        localPlayer: PlayerIdentity
    ) {
        val timeoutMs = BLEConstants.IDENTITY_EXCHANGE_TIMEOUT_MS

        if (isHost) {
            val guestIdentity = withTimeout(timeoutMs) {
                incomingMessages
                    .filterIsInstance<PlayerIdentity>()
                    .first()
            }

            sendMessage(localPlayer)

            _connectionStatus.emit(
                MatchingStatus.Ready(listOf(localPlayer, guestIdentity))
            )
        } else {
            sendMessage(localPlayer)

            val hostIdentity = withTimeout(timeoutMs) {
                incomingMessages
                    .filterIsInstance<PlayerIdentity>()
                    .first()
            }

            _connectionStatus.emit(
                MatchingStatus.Ready(listOf(hostIdentity, localPlayer))
            )
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun disconnect() {
        runCatching { connectionManager.close() }
        _connectionStatus.emit(MatchingStatus.Disconnected)
    }

    private fun classifyBleError(message: String): MatchFailureReason {
        val lower = message.lowercase()
        return when {
            "timeout" in lower -> MatchFailureReason.ConnectionTimeout
            "bluetooth" in lower || "disabled" in lower -> MatchFailureReason.BluetoothUnavailable
            "permission" in lower -> MatchFailureReason.PermissionDenied
            else -> MatchFailureReason.TransportError(message)
        }
    }

    companion object {
        private const val TAG = "BLE_PLAYER_MATCHING"
    }
}
