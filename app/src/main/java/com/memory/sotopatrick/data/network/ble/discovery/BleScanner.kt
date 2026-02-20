package com.memory.sotopatrick.data.network.ble.discovery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.mappers.toScanError
import com.memory.sotopatrick.data.network.ble.BLEConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BleScanner(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private var bleScanner: BluetoothLeScanner? = null

    private val _state = MutableStateFlow<BleDiscoveryState>(BleDiscoveryState.Idle)
    val state: StateFlow<BleDiscoveryState> = _state.asStateFlow()


    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            if (record.serviceUuids?.contains(ParcelUuid(BLEConstants.SERVICE_UUID)) != true) return

            // Extract player name and avatar from service data
            val serviceData = record.getServiceData(ParcelUuid(BLEConstants.SERVICE_UUID))
            val (avatar, playerName) = if (serviceData != null) {
                val payload = String(serviceData, Charsets.UTF_8)
                parsePayload(payload)
            } else {
                // Fallback if no service data
                Pair('?', result.device.name ?: "Unknown")
            }
            Log.d("BLE_SCANNER", "USER FOUND: $playerName (Avatar: $avatar)")

            val newDevice = ScannedDevice(
                address = result.device.address,
                avatar = avatar,
                name = playerName,
                rssi = result.rssi
            )

            _state.update { currentState ->
                if (currentState is BleDiscoveryState.Scanning) {
                    val currentList = currentState.devices
                    val index = currentList.indexOfFirst { it.address == newDevice.address }

                    val newList = if (index >= 0) {
                        currentList.toMutableList().apply { set(index, newDevice) }
                    } else {
                        currentList + newDevice
                    }
                    Log.d("BLE_SCANNER", "list: $newList")

                    BleDiscoveryState.Scanning(newList)
                } else {
                    BleDiscoveryState.Scanning(listOf(newDevice))
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanFailed(errorCode: Int) {
            _state.value = BleDiscoveryState.Error(errorCode.toScanError())
        }
    }

    private fun parsePayload(payload: String): Pair<Char, String> {
        // Format: "A|NAME" (single char avatar + delimiter + name)
        return if (payload.contains("|")) {
            val parts = payload.split("|", limit = 2)
            if (parts.size == 2 && parts[0].length == 1) {
                Pair(parts[0][0], parts[1])
            } else {
                Pair('A', payload) // Malformed, use entire payload as name
            }
        } else {
            // No delimiter found, assume it's just the name (legacy format)
            Pair('A', payload)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (_state.value is BleDiscoveryState.Scanning) return

        bleScanner = bluetoothAdapter.bluetoothLeScanner
        if (bleScanner == null) {
            _state.value = BleDiscoveryState.Error(BleDiscoveryError.NoScanner)
            return
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _state.value = BleDiscoveryState.Scanning(emptyList())

        bleScanner?.startScan(
            listOf(scanFilter),
            scanSettings,
            scanCallback
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
        bleScanner = null
        _state.value = BleDiscoveryState.Idle
    }
}