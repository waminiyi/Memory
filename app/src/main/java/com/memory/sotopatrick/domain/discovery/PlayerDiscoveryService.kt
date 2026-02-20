package com.memory.sotopatrick.domain.discovery

import com.memory.sotopatrick.domain.events.PlayerIdentity
import kotlinx.coroutines.flow.Flow

interface PlayerDiscoveryService {

    val playerDiscoveryState: Flow<PlayerDiscoveryState>

    suspend fun startSearchingPlayers()

    suspend fun startAdvertisingAsPlayer(player: PlayerIdentity)

    suspend fun stopAdvertising()

    suspend fun stopSearching()

}
