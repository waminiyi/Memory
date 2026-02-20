package com.memory.sotopatrick.data.network.ble.discovery

sealed interface BleDiscoveryState {
    data object Idle : BleDiscoveryState

    data class Scanning(val devices: List<ScannedDevice>) : BleDiscoveryState
    data object Advertising : BleDiscoveryState
    data class Error(val code: BleDiscoveryError) : BleDiscoveryState
}

sealed class BleDiscoveryError {
    // Scanner errors

    data object NoScanner : BleDiscoveryError()
    data object ScanFailed : BleDiscoveryError()
    data object ScanAlreadyStarted : BleDiscoveryError()
    data object FeatureUnsupported : BleDiscoveryError()

    // Advertiser errors
    data object AdvertiseDataTooLarge : BleDiscoveryError()
    data object AdvertiseTooManyAdvertisers : BleDiscoveryError()
    data object AdvertiseFailed : BleDiscoveryError()

    // System
    data object BluetoothDisabled : BleDiscoveryError()
    data object PermissionDenied : BleDiscoveryError()
    data object Timeout : BleDiscoveryError()
    data class Unknown(val detail: String) : BleDiscoveryError()
}