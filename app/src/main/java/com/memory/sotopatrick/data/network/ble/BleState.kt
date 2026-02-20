/*
package com.memory.sotopatrick.data.network.ble

import com.memory.sotopatrick.data.network.ble.discovery.ScannedDevice

sealed interface BleState {
    // Idle/Initial
    data object Idle : BleState

    // Discovery
    data class Scanning(val devices: List<ScannedDevice>) : BleState
    data object Advertising : BleState

    // Connection Lifecycle
    data object Connecting : BleState
    data class Connected(val deviceAddress: String) : BleState
    data object Ready : BleState


    // Data Transfer
    data class DataReceived(val data: ByteArray) : BleState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DataReceived
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    // Termination
    data object Disconnected : BleState

    // Error
    data class Error(val message: String) : BleState
}*/
