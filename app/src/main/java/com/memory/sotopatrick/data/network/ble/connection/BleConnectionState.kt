package com.memory.sotopatrick.data.network.ble.connection

sealed interface BleConnectionState {
    data object Idle : BleConnectionState

    data object Connecting : BleConnectionState
    data class Connected(val deviceAddress: String) : BleConnectionState
    data object Ready : BleConnectionState

    data object Disconnected : BleConnectionState

    data class Error(val message: String) : BleConnectionState
}