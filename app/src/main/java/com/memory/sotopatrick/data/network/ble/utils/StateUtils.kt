package com.memory.sotopatrick.data.network.ble.utils

object BleUtils {

    /**
     * Converts GATT status codes (like 0, 133, 8, etc.) to String names.
     */
    fun getStatusName(status: Int): String {
        return when (status) {
            0 -> "GATT_SUCCESS"
            0x02 -> "GATT_READ_NOT_PERMITTED"
            0x03 -> "GATT_WRITE_NOT_PERMITTED"
            0x05 -> "GATT_INSUFFICIENT_AUTHENTICATION"
            0x06 -> "GATT_REQUEST_NOT_SUPPORTED"
            0x07 -> "GATT_INVALID_OFFSET"
            0x08 -> "GATT_INSUFFICIENT_AUTHORIZATION"
            0x0D -> "GATT_INVALID_ATTRIBUTE_LENGTH"
            0x0F -> "GATT_INSUFFICIENT_ENCRYPTION"
            0x8F -> "GATT_CONNECTION_CONGESTED"
            0x93 -> "GATT_CONNECTION_TIMEOUT" // Very common for range issues
            0x101 -> "GATT_FAILURE"
            133 -> "GATT_ERROR (0x85 - Generic Error/Timeout)" // Most common Android BLE error
            19 -> "GATT_CONN_TERMINATE_PEER_USER (0x13)" // Remote device disconnected
            22 -> "GATT_CONN_TERMINATE_LOCAL_HOST (0x16)" // Local device disconnected
            34 -> "GATT_CONN_LMP_TIMEOUT (0x22)"
            62 -> "GATT_CONN_FAIL_ESTABLISH (0x3E)"
            else -> "UNKNOWN_STATUS ($status)"
        }
    }

    /**
     * Converts BluetoothProfile connection states to String names.
     */
    fun getConnectionStateName(state: Int): String {
        return when (state) {
            0 -> "STATE_DISCONNECTED"
            1 -> "STATE_CONNECTING"
            2 -> "STATE_CONNECTED"
            3 -> "STATE_DISCONNECTING"
            else -> "UNKNOWN_STATE ($state)"
        }
    }
}