package com.memory.sotopatrick.domain.matching

import com.memory.sotopatrick.domain.discovery.NearbyUser
import com.memory.sotopatrick.domain.events.PlayerIdentity
import com.memory.sotopatrick.domain.network.MemoMessenger
import kotlinx.coroutines.flow.Flow

interface PlayerMatchingService {

    suspend fun hostMatch(localPlayer: PlayerIdentity): Result<MemoMessenger>

    suspend fun joinMatch(
        host: NearbyUser, localPlayer: PlayerIdentity
    ): Result<MemoMessenger>

    suspend fun disconnect()

    val connectionStatus: Flow<MatchingStatus>
}