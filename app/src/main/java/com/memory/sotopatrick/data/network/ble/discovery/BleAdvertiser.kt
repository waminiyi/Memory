package com.memory.sotopatrick.data.network.ble.discovery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.mappers.toAdvertiseError
import com.memory.sotopatrick.data.network.ble.BLEConstants
import com.memory.sotopatrick.data.network.ble.BLEConstants.ADVERTISING_TIMEOUT_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class BleAdvertiser(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private val _state = MutableStateFlow<BleDiscoveryState>(BleDiscoveryState.Idle)
    val state: StateFlow<BleDiscoveryState> = _state.asStateFlow()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BleAdvertiser", "Advertising started")
            _state.value = BleDiscoveryState.Advertising
        }

        override fun onStartFailure(errorCode: Int) {
            Log.d("BleAdvertiserLE", "Advertising start failed code: $errorCode")
            _state.value = BleDiscoveryState.Error(errorCode.toAdvertiseError())
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun startAdvertising(playerName: String, avatarCode: Char) {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(ADVERTISING_TIMEOUT_MS)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val payload = "$avatarCode|$playerName"

        // Truncate to fit in service data (max ~18 bytes for name)
        val truncatedPayload = if (payload.toByteArray(Charsets.UTF_8).size > 20) {
            val maxNameLength = 18 // 20 - 1 (avatar) - 1 (delimiter)
            "$avatarCode|${playerName.take(maxNameLength)}..."
        } else {
            payload
        }

        val payloadBytes = truncatedPayload.toByteArray(Charsets.UTF_8)

        Log.i("BLE_ADVERTISER", "payload size: ${payloadBytes.size}")

        // PRIMARY PACKET: Service UUID only
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        // SCAN RESPONSE: Player name + avatar in service data
        val scanResponseData = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(BLEConstants.SERVICE_UUID), payloadBytes)
            .setIncludeDeviceName(false)
            .build()

        bleAdvertiser?.startAdvertising(settings, data, scanResponseData, advertiseCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        bleAdvertiser = null
        _state.value = BleDiscoveryState.Idle
    }
}
