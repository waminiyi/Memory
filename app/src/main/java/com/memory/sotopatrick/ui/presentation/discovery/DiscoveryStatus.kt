package com.memory.sotopatrick.ui.presentation.discovery

import com.memory.sotopatrick.domain.discovery.NearbyUser

data class DiscoveryScreenUiState(
    val hasBluetoothPermission: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val currentUsername: String = "Player",
    val avatarCode: Char = 'A',
    val isHost: Boolean = false,
    val errorMessage: String? = null,

    val status: DiscoveryStatus = DiscoveryStatus.Idle
) {
    val canProceed: Boolean =
        currentUsername.isNotBlank() &&
                errorMessage == null &&
                status !is DiscoveryStatus.Error
}

sealed class DiscoveryStatus {

    object Idle : DiscoveryStatus()

    object Advertising : DiscoveryStatus()

    data class Searching(
        val nearbyPlayers: List<NearbyUser> = emptyList()
    ) : DiscoveryStatus()

    data class Error(
        val message: String
    ) : DiscoveryStatus()
}
