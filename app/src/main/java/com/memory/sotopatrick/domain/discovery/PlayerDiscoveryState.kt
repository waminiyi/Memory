package com.memory.sotopatrick.domain.discovery

sealed class PlayerDiscoveryState {
    object AdvertisingAsPlayer : PlayerDiscoveryState()

    data class SearchingPlayers(
        val nearbyPlayers: List<NearbyUser>
    ) : PlayerDiscoveryState()

    object Idle : PlayerDiscoveryState()

    data class Error(val reason: DiscoveryFailureReason) : PlayerDiscoveryState()

}

sealed class DiscoveryFailureReason {
    data object DiscoveryTimeout : DiscoveryFailureReason()
    data object BluetoothUnavailable : DiscoveryFailureReason()
    data object PermissionDenied : DiscoveryFailureReason()
    data class Unknown(val detail: String) : DiscoveryFailureReason()
}