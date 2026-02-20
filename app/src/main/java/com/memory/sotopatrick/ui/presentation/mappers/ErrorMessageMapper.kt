package com.memory.sotopatrick.ui.presentation.mappers

import com.memory.sotopatrick.domain.discovery.DiscoveryFailureReason
import com.memory.sotopatrick.domain.matching.MatchFailureReason

fun DiscoveryFailureReason.toDisplayMessage(): String = when (this) {
    DiscoveryFailureReason.BluetoothUnavailable -> "Bluetooth is unavailable"
    DiscoveryFailureReason.DiscoveryTimeout -> "Discovery timed out"
    DiscoveryFailureReason.PermissionDenied -> "Bluetooth permission denied"
    is DiscoveryFailureReason.Unknown -> detail
}

fun MatchFailureReason.toDisplayMessage(): String = when (this) {
    MatchFailureReason.BluetoothUnavailable -> "Bluetooth unavailable"
    MatchFailureReason.ConnectionTimeout -> "Connection timeout"
    MatchFailureReason.PermissionDenied -> "Permission denied"
    is MatchFailureReason.TransportError -> "Transport error"
    is MatchFailureReason.Unknown -> "Unknown error"
}
