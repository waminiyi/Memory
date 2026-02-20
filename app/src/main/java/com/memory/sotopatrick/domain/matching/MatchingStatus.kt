package com.memory.sotopatrick.domain.matching

import com.memory.sotopatrick.domain.events.PlayerIdentity

sealed class MatchingStatus {
    object Idle : MatchingStatus()
    object WaitingForOpponent : MatchingStatus()
    object Connecting : MatchingStatus()
    object Connected : MatchingStatus()
    data class Ready(val players: List<PlayerIdentity>) :
        MatchingStatus()

    data class Failed(val reason: MatchFailureReason) : MatchingStatus()
    object Disconnected : MatchingStatus() //TODO
}

sealed class MatchFailureReason {
    data object ConnectionTimeout : MatchFailureReason()
    data object BluetoothUnavailable : MatchFailureReason()
    data object PermissionDenied : MatchFailureReason()
    data class TransportError(val detail: String) : MatchFailureReason()
    data class Unknown(val detail: String) : MatchFailureReason()
}
