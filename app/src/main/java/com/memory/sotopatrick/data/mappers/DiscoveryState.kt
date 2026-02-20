package com.memory.sotopatrick.data.mappers

import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.ScanCallback
import com.memory.sotopatrick.data.network.ble.discovery.BleDiscoveryError
import com.memory.sotopatrick.domain.discovery.DiscoveryFailureReason

fun BleDiscoveryError.toFailureReason(): DiscoveryFailureReason = when (this) {
    is BleDiscoveryError.Timeout -> DiscoveryFailureReason.DiscoveryTimeout
    is BleDiscoveryError.BluetoothDisabled -> DiscoveryFailureReason.BluetoothUnavailable
    is BleDiscoveryError.PermissionDenied -> DiscoveryFailureReason.PermissionDenied
    is BleDiscoveryError.ScanFailed,
    is BleDiscoveryError.ScanAlreadyStarted,
    is BleDiscoveryError.AdvertiseFailed,
    is BleDiscoveryError.AdvertiseDataTooLarge,
    is BleDiscoveryError.AdvertiseTooManyAdvertisers,
    is BleDiscoveryError.FeatureUnsupported -> DiscoveryFailureReason.Unknown(toDisplayMessage())

    is BleDiscoveryError.Unknown -> DiscoveryFailureReason.Unknown(detail)
    BleDiscoveryError.NoScanner -> DiscoveryFailureReason.Unknown(toDisplayMessage())
}

fun BleDiscoveryError.toDisplayMessage(): String = when (this) {
    is BleDiscoveryError.ScanFailed -> "Scan failed to start"
    is BleDiscoveryError.ScanAlreadyStarted -> "Scan already in progress"
    is BleDiscoveryError.FeatureUnsupported -> "BLE not supported on this device"
    is BleDiscoveryError.AdvertiseDataTooLarge -> "Advertise payload too large"
    is BleDiscoveryError.AdvertiseTooManyAdvertisers -> "Too many active advertisers"
    is BleDiscoveryError.AdvertiseFailed -> "Advertising failed to start"
    is BleDiscoveryError.BluetoothDisabled -> "Bluetooth is disabled"
    is BleDiscoveryError.PermissionDenied -> "Bluetooth permission denied"
    is BleDiscoveryError.Timeout -> "Discovery timed out"
    is BleDiscoveryError.Unknown -> "Unexpected error: $detail"
    BleDiscoveryError.NoScanner -> "Null Scanner"
}

// In BleScanner
fun Int.toScanError(): BleDiscoveryError = when (this) {
    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> BleDiscoveryError.ScanAlreadyStarted
    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> BleDiscoveryError.ScanFailed
    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> BleDiscoveryError.FeatureUnsupported
    else -> BleDiscoveryError.Unknown("Scan error code: $this")
}

// In BleAdvertiser
fun Int.toAdvertiseError(): BleDiscoveryError = when (this) {
    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> BleDiscoveryError.AdvertiseDataTooLarge
    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> BleDiscoveryError.AdvertiseTooManyAdvertisers
    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> BleDiscoveryError.AdvertiseFailed
    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> BleDiscoveryError.FeatureUnsupported
    else -> BleDiscoveryError.Unknown("Advertise error code: $this")
}
