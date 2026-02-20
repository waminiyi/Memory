package com.memory.sotopatrick.domain.session

sealed class SessionState {
    data object Lobby : SessionState()
    data object Setup : SessionState()
    data object InGame : SessionState()
    data object Finished : SessionState()
    data object Terminated : SessionState()
}
